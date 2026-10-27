package kr.needteacher.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import kr.needteacher.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Import({TestcontainersConfiguration.class, AuthTestConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthAcceptanceIT {

    private static final String CODES = "/api/v1/auth/email-codes";
    private static final String VERIFY = CODES + "/verify";
    private static final String SIGNUP = "/api/v1/auth/signup";
    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String ME = "/api/v1/me";
    private static final String PROFILES = "/api/v1/tutor-profiles";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicLong TEST_NUMBER = new AtomicLong();
    private static final Pattern COOKIE_AGE = Pattern.compile("(?i)(^|;)\\s*Max-Age=1209600($|;)");
    private static final Pattern COOKIE_PATH = Pattern.compile("(?i)(^|;)\\s*Path=/($|;)");

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private java.time.Clock clockBean;

    @Autowired
    private GenericContainer<?> authMailpitContainer;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Environment environment;

    private AdjustableClock clock;
    private AuthMailInbox inbox;

    @BeforeEach
    void resetTime() {
        clock = (AdjustableClock) clockBean;
        clock.set(Instant.parse("2030-01-01T00:00:00Z").plus(Duration.ofDays(TEST_NUMBER.getAndIncrement() * 3)));
        inbox = new AuthMailInbox(authMailpitContainer);
    }

    @Test
    void requestIsGenericForExistingAndNewEmailAndNormalizesAddress() throws Exception {
        AuthHttpClient client = client();
        String existing = email();
        JsonNode account = createAccount(client, existing, "TEACHER");
        assertAccount(account, existing, "TEACHER");
        clock.advanceSeconds(59);
        assertRetryAfter(client.post(CODES, emailJson(" " + existing.toUpperCase(Locale.ROOT) + " ")), 429, 1);
        clock.advanceSeconds(1);

        HttpResponse<String> existingResponse =
                client.post(CODES, emailJson(" " + existing.toUpperCase(Locale.ROOT) + " "));
        assertStatus(existingResponse, 202);
        assertEquals("", existingResponse.body());
        String existingCode = inbox.codeFor(existing, 2);
        AuthHttpClient returning = client();
        JsonNode login = verify(returning, " " + existing.toUpperCase(Locale.ROOT) + " ", existingCode);
        assertEquals("LOGGED_IN", login.path("result").asString());
        assertAccount(login.path("account"), existing, "TEACHER");
        assertEquals(
                account.path("id").asString(), login.path("account").path("id").asString());

        String fresh = email();
        HttpResponse<String> freshResponse = client.post(CODES, emailJson(fresh));
        assertStatus(freshResponse, 202);
        assertEquals(existingResponse.body(), freshResponse.body());
        assertEquals(
                existingResponse.headers().firstValue("Content-Type"),
                freshResponse.headers().firstValue("Content-Type"));
        inbox.codeFor(fresh, 1);
    }

    @Test
    void emailAndCodeShapesAreValidatedBeforeAttemptCounting() throws Exception {
        AuthHttpClient client = client();
        String email = email();
        assertField(client.post(CODES, emailJson("invalid")), "email");
        String longest = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(57) + ".com";
        String overlong = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(58) + ".com";
        assertEquals(254, longest.length());
        assertEquals(255, overlong.length());
        requestCode(client, longest, 1);
        assertField(client.post(CODES, emailJson(overlong)), "email");
        String code = requestCode(client, email, 1);
        for (int index = 0; index < 6; index++) {
            assertField(client.post(VERIFY, verifyJson(email, "12x456")), "code");
        }
        assertField(client.post(VERIFY, verifyJson(email, "12345")), "code");
        assertField(client.post(VERIFY, verifyJson(email, "1234567")), "code");
        assertField(client.post(VERIFY, verifyJson("invalid", code)), "email");
        assertEquals(
                "SIGNUP_REQUIRED", verify(client, email, code).path("result").asString());
    }

    @Test
    void codeExpiresAtTenMinutesAndResendingInvalidatesOldCode() throws Exception {
        AuthHttpClient client = client();
        Instant start = clock.instant();
        String beforeExpiry = email();
        String atExpiry = email();
        String first = requestCode(client, beforeExpiry, 1);
        String second = requestCode(client, atExpiry, 1);

        clock.set(start.plusSeconds(599));
        assertEquals(
                "SIGNUP_REQUIRED",
                verify(client, beforeExpiry, first).path("result").asString());
        clock.set(start.plusSeconds(600));
        assertError(client.post(VERIFY, verifyJson(atExpiry, second)), 400, "INVALID_CODE");

        String resendEmail = email();
        String old = requestCode(client, resendEmail, 1);
        clock.advanceSeconds(60);
        String replacement = requestCode(client, resendEmail, 2);
        assertNotEquals(old, replacement);
        assertError(client.post(VERIFY, verifyJson(resendEmail, old)), 400, "INVALID_CODE");
        assertEquals(
                "SIGNUP_REQUIRED",
                verify(client, resendEmail, replacement).path("result").asString());
    }

    @Test
    void fourWrongCodesStillAllowSuccessButFiveAcrossClientsInvalidateCode() throws Exception {
        AuthHttpClient first = client();
        AuthHttpClient second = client();
        String fourEmail = email();
        String fourCode = requestCode(first, fourEmail, 1);
        for (int index = 0; index < 4; index++) {
            assertError(first.post(VERIFY, verifyJson(fourEmail, wrong(fourCode))), 400, "INVALID_CODE");
        }
        assertEquals(
                "SIGNUP_REQUIRED",
                verify(first, fourEmail, fourCode).path("result").asString());

        String fiveEmail = email();
        String fiveCode = requestCode(first, fiveEmail, 1);
        for (int index = 0; index < 3; index++) {
            assertError(first.post(VERIFY, verifyJson(fiveEmail, wrong(fiveCode))), 400, "INVALID_CODE");
        }
        for (int index = 0; index < 2; index++) {
            assertError(
                    second.post(VERIFY, verifyJson(fiveEmail.toUpperCase(Locale.ROOT), wrong(fiveCode))),
                    400,
                    "INVALID_CODE");
        }
        assertEquals(0, storedCodes(fiveEmail));
        assertError(first.post(VERIFY, verifyJson(fiveEmail, fiveCode)), 400, "INVALID_CODE");
        assertError(second.post(VERIFY, verifyJson(fiveEmail, fiveCode)), 400, "INVALID_CODE");
        assertRetryAfter(second.post(CODES, emailJson(fiveEmail)), 429, 60);
        clock.advanceSeconds(60);
        requestCode(second, fiveEmail, 2);
    }

    @Test
    void missingExpiredConsumedAndWrongCodesHaveTheSamePublicError() throws Exception {
        AuthHttpClient client = client();
        String missing = email();
        JsonNode expected = assertError(client.post(VERIFY, verifyJson(missing, "123456")), 400, "INVALID_CODE");

        String wrongEmail = email();
        String wrongCode = requestCode(client, wrongEmail, 1);
        assertSameError(expected, client.post(VERIFY, verifyJson(wrongEmail, wrong(wrongCode))));

        String replacedEmail = email();
        String replacedCode = requestCode(client, replacedEmail, 1);
        clock.advanceSeconds(60);
        requestCode(client, replacedEmail, 2);
        assertSameError(expected, client.post(VERIFY, verifyJson(replacedEmail, replacedCode)));

        String exhaustedEmail = email();
        String exhaustedCode = requestCode(client, exhaustedEmail, 1);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertSameError(expected, client.post(VERIFY, verifyJson(exhaustedEmail, wrong(exhaustedCode))));
        }
        assertSameError(expected, client.post(VERIFY, verifyJson(exhaustedEmail, exhaustedCode)));

        String expiredEmail = email();
        String expiredCode = requestCode(client, expiredEmail, 1);
        clock.advanceSeconds(600);
        assertSameError(expected, client.post(VERIFY, verifyJson(expiredEmail, expiredCode)));

        String consumedEmail = email();
        String consumedCode = requestCode(client, consumedEmail, 1);
        verify(client, consumedEmail, consumedCode);
        assertSameError(expected, client.post(VERIFY, verifyJson(consumedEmail, consumedCode)));
    }

    @Test
    void simultaneousCorrectVerificationsConsumeCodeOnlyOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 3; round++) {
                AuthHttpClient first = client();
                AuthHttpClient second = client();
                String email = email();
                String code = requestCode(first, email, 1);
                CountDownLatch start = new CountDownLatch(1);
                Future<HttpResponse<String>> left = executor.submit(() -> {
                    start.await();
                    return first.post(VERIFY, verifyJson(email, code));
                });
                Future<HttpResponse<String>> right = executor.submit(() -> {
                    start.await();
                    return second.post(VERIFY, verifyJson(email, code));
                });
                start.countDown();
                HttpResponse<String> firstResponse = left.get();
                HttpResponse<String> secondResponse = right.get();
                assertEquals(
                        List.of(200, 400),
                        List.of(firstResponse.statusCode(), secondResponse.statusCode()).stream()
                                .sorted()
                                .toList());
                HttpResponse<String> rejected = firstResponse.statusCode() == 400 ? firstResponse : secondResponse;
                assertError(rejected, 400, "INVALID_CODE");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sixtySecondLimitPersistsAfterCodeConsumptionAndDoesNotReplaceItOnRejection() throws Exception {
        AuthHttpClient client = client();
        String email = email();
        String code = requestCode(client, email, 1);
        Instant sent = clock.instant();
        clock.set(sent.plusMillis(59_700));
        assertRetryAfter(client.post(CODES, emailJson(email)), 429, 1);
        assertEquals(
                "SIGNUP_REQUIRED", verify(client, email, code).path("result").asString());
        clock.advanceSeconds(1);
        requestCode(client, email, 2);
    }

    @Test
    void dailyLimitUsesSuccessfulSendsAndOpensAtTwentyFourHourBoundary() throws Exception {
        AuthHttpClient first = client();
        AuthHttpClient second = client();
        String email = email();
        Instant firstSend = clock.instant();
        String lastCode = null;
        for (int send = 1; send <= 10; send++) {
            if (send > 1) {
                clock.advanceSeconds(60);
            }
            lastCode = requestCode(first, email, send);
        }
        clock.advanceSeconds(60);
        assertRetryAfter(second.post(CODES, emailJson(email.toUpperCase(Locale.ROOT))), 429, 85800);
        assertEquals(
                "SIGNUP_REQUIRED", verify(first, email, lastCode).path("result").asString());
        clock.set(firstSend.plus(Duration.ofDays(1)).minusSeconds(1));
        assertRetryAfter(second.post(CODES, emailJson(email)), 429, 1);
        clock.set(firstSend.plus(Duration.ofDays(1)));
        requestCode(first, email, 11);
    }

    @Test
    void consumedAndInvalidatedCodesAreRemovedAndPlaintextIsNeverStored() throws Exception {
        AuthHttpClient client = client();
        String consumedEmail = email();
        String code = requestCode(client, consumedEmail, 1);
        String row = jdbc.queryForObject(
                "SELECT row_to_json(c)::text FROM email_codes c WHERE email = ?", String.class, consumedEmail);
        assertNotNull(row);
        assertFalse(row.contains(code), "plaintext verification code must not be stored");
        assertEquals(1, storedCodes(consumedEmail));
        verify(client, consumedEmail, code);
        assertEquals(0, storedCodes(consumedEmail));

        String firstExpired = email();
        String secondExpired = email();
        requestCode(client, firstExpired, 1);
        requestCode(client, secondExpired, 1);
        clock.advanceSeconds(600);
        requestCode(client, email(), 1);
        assertEquals(0, storedCodes(firstExpired));
        assertEquals(0, storedCodes(secondExpired));
    }

    @Test
    void pendingSignupProofUsesRotatedSessionAndCsrfToken() throws Exception {
        AuthHttpClient client = client();
        String initialEmail = email();
        verify(client, initialEmail, requestCode(client, initialEmail, 1));
        String oldSession = client.cookie("SESSION");
        String oldCsrf = client.cookie("XSRF-TOKEN");
        assertNotNull(oldSession);
        assertNotNull(oldCsrf);

        String email = email();
        String code = requestCode(client, email, 1);
        HttpResponse<String> verified = client.post(VERIFY, verifyJson(email, code));
        assertStatus(verified, 200);
        assertEquals("SIGNUP_REQUIRED", body(verified).path("result").asString());
        assertNotEquals(oldSession, client.cookie("SESSION"));
        assertNotEquals(oldCsrf, client.cookie("XSRF-TOKEN"));
        assertSetCookie(verified, "SESSION");
        assertSetCookie(verified, "XSRF-TOKEN");

        AuthHttpClient stale = client.copy();
        stale.cookie("SESSION", oldSession);
        assertError(stale.post(SIGNUP, signupJson("TEACHER", true)), 401, "UNAUTHORIZED");
        assertError(client.postWithCsrf(SIGNUP, signupJson("TEACHER", true), oldCsrf), 403, "FORBIDDEN");
        String preSignupSession = client.cookie("SESSION");
        String preSignupCsrf = client.cookie("XSRF-TOKEN");
        HttpResponse<String> signupResponse = client.post(SIGNUP, signupJson("TEACHER", true));
        assertStatus(signupResponse, 201);
        assertEquals(
                ME,
                URI.create(signupResponse.headers().firstValue("Location").orElseThrow())
                        .getPath());
        JsonNode account = body(signupResponse);
        assertAccount(account, email, "TEACHER");
        assertNotEquals(preSignupSession, client.cookie("SESSION"));
        assertSetCookie(signupResponse, "XSRF-TOKEN");
        assertNotEquals(preSignupCsrf, client.cookie("XSRF-TOKEN"));
        assertError(client.postWithCsrf(LOGOUT, null, preSignupCsrf), 403, "FORBIDDEN");
        AuthHttpClient beforeSignup = client.copy();
        beforeSignup.cookie("SESSION", preSignupSession);
        assertError(beforeSignup.get(ME), 401, "UNAUTHORIZED");
        assertError(beforeSignup.post(SIGNUP, signupJson("TEACHER", true)), 401, "UNAUTHORIZED");
    }

    @Test
    void pendingSignupProofExpiresAtTenMinutesAndCanBeReplacedByAnotherEmail() throws Exception {
        AuthHttpClient first = client();
        String firstEmail = email();
        verify(first, firstEmail, requestCode(first, firstEmail, 1));
        clock.advanceSeconds(599);
        assertAccount(signup(first, "PARENT", true), firstEmail, "PARENT");

        AuthHttpClient expired = client();
        String expiredEmail = email();
        verify(expired, expiredEmail, requestCode(expired, expiredEmail, 1));
        clock.advanceSeconds(600);
        assertError(expired.post(SIGNUP, signupJson("TEACHER", true)), 401, "UNAUTHORIZED");

        AuthHttpClient replaced = client();
        String discarded = email();
        String current = email();
        verify(replaced, discarded, requestCode(replaced, discarded, 1));
        verify(replaced, current, requestCode(replaced, current, 1));
        assertAccount(signup(replaced, "TEACHER", true), current, "TEACHER");
    }

    @Test
    void signupRejectsInvalidRoleAndAgeWithoutCreatingAccount() throws Exception {
        AuthHttpClient client = client();
        String email = email();
        verify(client, email, requestCode(client, email, 1));
        assertField(client.post(SIGNUP, signupJson("STUDENT", true)), "role");
        assertField(client.post(SIGNUP, signupJson("TEACHER", false)), "ageOver14");
        assertField(client.post(SIGNUP, "{\"ageOver14\":true}"), "role");
        assertField(client.post(SIGNUP, "{\"role\":\"TEACHER\"}"), "ageOver14");
        assertAccount(signup(client, "TEACHER", true), email, "TEACHER");
        assertError(client.post(SIGNUP, signupJson("TEACHER", true)), 409, "ALREADY_SIGNED_UP");
        assertError(client.post(SIGNUP, signupJson("STUDENT", false)), 409, "ALREADY_SIGNED_UP");
    }

    @Test
    void signupRequiresVerifiedEmailAndDetectsAccountCreatedByAnotherSession() throws Exception {
        AuthHttpClient noProof = client();
        String email = email();
        requestCode(noProof, email, 1);
        assertError(noProof.post(SIGNUP, signupJson("TEACHER", true)), 401, "UNAUTHORIZED");
        assertError(noProof.post(SIGNUP, signupJson("STUDENT", false)), 401, "UNAUTHORIZED");

        AuthHttpClient first = client();
        verify(first, email, inbox.codeFor(email, 1));
        clock.advanceSeconds(60);
        AuthHttpClient second = client();
        verify(second, email, requestCode(second, email, 2));
        signup(second, "PARENT", true);
        assertError(first.post(SIGNUP, signupJson("PARENT", true)), 409, "ALREADY_SIGNED_UP");
    }

    @Test
    void existingAccountLoginRotatesCookiesAndPreservesRole() throws Exception {
        AuthHttpClient first = client();
        String email = email();
        JsonNode created = createAccount(first, email, "PARENT");
        clock.advanceSeconds(60);

        AuthHttpClient returning = client();
        String other = email();
        verify(returning, other, requestCode(returning, other, 1));
        String oldSession = returning.cookie("SESSION");
        String oldCsrf = returning.cookie("XSRF-TOKEN");
        HttpResponse<String> response = returning.post(VERIFY, verifyJson(email, requestCode(returning, email, 2)));
        assertStatus(response, 200);
        JsonNode loggedIn = body(response);
        assertEquals("LOGGED_IN", loggedIn.path("result").asString());
        assertEquals(
                created.path("id").asString(),
                loggedIn.path("account").path("id").asString());
        assertAccount(loggedIn.path("account"), email, "PARENT");
        assertNotEquals(oldSession, returning.cookie("SESSION"));
        assertNotEquals(oldCsrf, returning.cookie("XSRF-TOKEN"));
        assertSetCookie(response, "SESSION");
        assertSetCookie(response, "XSRF-TOKEN");
        AuthHttpClient stale = returning.copy();
        stale.cookie("SESSION", oldSession);
        assertError(stale.get(ME), 401, "UNAUTHORIZED");
        assertError(returning.postWithCsrf(LOGOUT, null, oldCsrf), 403, "FORBIDDEN");
        assertEquals(
                created.path("id").asString(),
                body(returning.get(ME)).path("id").asString());
    }

    @Test
    void sessionCookieHasRequiredAttributesAndIdleTimeoutIsFourteenDays() throws Exception {
        AuthHttpClient client = client();
        String email = email();
        verify(client, email, requestCode(client, email, 1));
        HttpResponse<String> signup = client.post(SIGNUP, signupJson("TEACHER", true));
        assertStatus(signup, 201);
        String sessionCookie = setCookie(signup, "SESSION");
        assertTrue(sessionCookie.toLowerCase(Locale.ROOT).contains("httponly"));
        assertTrue(sessionCookie.toLowerCase(Locale.ROOT).contains("samesite=lax"));
        assertTrue(COOKIE_PATH.matcher(sessionCookie).find());
        assertTrue(COOKIE_AGE.matcher(sessionCookie).find());
        assertEquals(Duration.ofDays(14), environment.getProperty("server.servlet.session.timeout", Duration.class));
    }

    @Test
    void logoutIsIdempotentButCsrfProtectedAndInvalidatesSession() throws Exception {
        AuthHttpClient client = client();
        createAccount(client, email(), "TEACHER");
        String oldSession = client.cookie("SESSION");
        assertError(client.postWithCsrf(LOGOUT, null, null), 403, "FORBIDDEN");
        assertError(client.postWithCsrf(LOGOUT, null, "wrong"), 403, "FORBIDDEN");
        assertStatus(client.post(LOGOUT, null), 204);
        AuthHttpClient stale = client.copy();
        stale.cookie("SESSION", oldSession);
        assertError(stale.get(ME), 401, "UNAUTHORIZED");
        assertError(client.get(ME), 401, "UNAUTHORIZED");

        AuthHttpClient anonymous = client();
        assertError(anonymous.postWithCsrf(LOGOUT, null, null), 403, "FORBIDDEN");
        assertStatus(anonymous.post(LOGOUT, null), 204);
    }

    @Test
    void logoutDiscardsPendingSignupProof() throws Exception {
        AuthHttpClient client = client();
        String email = email();
        verify(client, email, requestCode(client, email, 1));
        String oldSession = client.cookie("SESSION");
        assertStatus(client.post(LOGOUT, null), 204);
        assertStatus(client.get("/api/ping"), 200);
        AuthHttpClient stale = client.copy();
        stale.cookie("SESSION", oldSession);
        assertStatus(stale.get("/api/ping"), 200);
        assertError(stale.post(SIGNUP, signupJson("TEACHER", true)), 401, "UNAUTHORIZED");
        assertError(client.post(SIGNUP, signupJson("TEACHER", true)), 401, "UNAUTHORIZED");
    }

    @Test
    void allStateChangingEndpointsRejectMissingOrWrongCsrfBeforeOtherErrors() throws Exception {
        AuthHttpClient client = client();
        requestCode(client, email(), 1);
        String payload = emailJson(email());
        List<String> paths = List.of(CODES, VERIFY, SIGNUP, LOGOUT);
        for (String path : paths) {
            assertError(client.postWithCsrf(path, payload, null), 403, "FORBIDDEN");
            assertError(client.postWithCsrf(path, payload, "incorrect"), 403, "FORBIDDEN");
        }
        assertError(client.deleteWithCsrf(ME, null), 403, "FORBIDDEN");
        assertError(client.deleteWithCsrf(ME, "incorrect"), 403, "FORBIDDEN");
        assertError(client.delete(ME), 401, "UNAUTHORIZED");
    }

    @Test
    void teacherCanUseProfileApisAndWithdrawalRevokesOtherSessionAndDeletesProfile() throws Exception {
        AuthHttpClient owner = client();
        String email = email();
        JsonNode firstAccount = createAccount(owner, email, "TEACHER");
        String profileId = createProfile(owner);
        assertEquals(
                firstAccount.path("id").asString(),
                jdbc.queryForObject(
                        "SELECT owner_id FROM tutor_profiles WHERE id = CAST(? AS uuid)", String.class, profileId));
        assertStatus(owner.patch(PROFILES + "/" + profileId + "/visibility", "{\"visibility\":\"PUBLIC\"}"), 200);
        assertStatus(owner.get(PROFILES + "/" + profileId), 200);
        assertTrue(body(owner.get(PROFILES)).path("items").toString().contains(profileId));
        clock.advanceSeconds(60);

        AuthHttpClient second = client();
        assertEquals(
                "LOGGED_IN",
                verify(second, email, requestCode(second, email, 2))
                        .path("result")
                        .asString());
        String oldOwnerSession = owner.cookie("SESSION");
        assertError(owner.deleteWithCsrf(ME, null), 403, "FORBIDDEN");
        assertStatus(owner.delete(ME), 204);
        AuthHttpClient oldOwner = owner.copy();
        oldOwner.cookie("SESSION", oldOwnerSession);
        assertError(oldOwner.get(ME), 401, "UNAUTHORIZED");
        assertError(second.get(ME), 401, "UNAUTHORIZED");
        assertError(second.post(PROFILES, profileJson()), 401, "UNAUTHORIZED");
        assertError(owner.get(PROFILES + "/" + profileId), 404, "NOT_FOUND");
        assertFalse(body(owner.get(PROFILES)).path("items").toString().contains(profileId));

        clock.advanceSeconds(60);
        AuthHttpClient rejoined = client();
        assertEquals(
                "SIGNUP_REQUIRED",
                verify(rejoined, email, requestCode(rejoined, email, 3))
                        .path("result")
                        .asString());
        JsonNode newAccount = signup(rejoined, "TEACHER", true);
        assertNotEquals(
                firstAccount.path("id").asString(), newAccount.path("id").asString());
        assertError(rejoined.get(ME + "/tutor-profile"), 404, "NOT_FOUND");
    }

    @Test
    void parentCannotCreateProfileAndRoleCannotChangeAfterLogin() throws Exception {
        AuthHttpClient parent = client();
        String email = email();
        JsonNode account = createAccount(parent, email, "PARENT");
        assertError(parent.post(PROFILES, profileJson()), 403, "FORBIDDEN");
        clock.advanceSeconds(60);
        AuthHttpClient again = client();
        JsonNode response = verify(again, email, requestCode(again, email, 2));
        assertEquals("LOGGED_IN", response.path("result").asString());
        assertEquals(
                account.path("id").asString(),
                response.path("account").path("id").asString());
        assertEquals("PARENT", body(again.get(ME)).path("role").asString());
        assertError(again.post(PROFILES, profileJson()), 403, "FORBIDDEN");
        assertStatus(again.post(LOGOUT, null), 204);
    }

    private AuthHttpClient client() throws Exception {
        AuthHttpClient client = new AuthHttpClient(port);
        assertStatus(client.get("/api/ping"), 200);
        assertNotNull(client.cookie("XSRF-TOKEN"));
        return client;
    }

    private String requestCode(AuthHttpClient client, String email, int messageCount) throws Exception {
        HttpResponse<String> response = client.post(CODES, emailJson(email));
        assertStatus(response, 202);
        assertEquals("", response.body());
        return inbox.codeFor(email.toLowerCase(Locale.ROOT).trim(), messageCount);
    }

    private JsonNode verify(AuthHttpClient client, String email, String code) throws Exception {
        HttpResponse<String> response = client.post(VERIFY, verifyJson(email, code));
        assertStatus(response, 200);
        return body(response);
    }

    private JsonNode signup(AuthHttpClient client, String role, boolean ageOver14) throws Exception {
        HttpResponse<String> response = client.post(SIGNUP, signupJson(role, ageOver14));
        assertStatus(response, 201);
        assertEquals(
                ME,
                URI.create(response.headers().firstValue("Location").orElseThrow())
                        .getPath());
        return body(response);
    }

    private JsonNode createAccount(AuthHttpClient client, String email, String role) throws Exception {
        assertEquals(
                "SIGNUP_REQUIRED",
                verify(client, email, requestCode(client, email, 1))
                        .path("result")
                        .asString());
        JsonNode account = signup(client, role, true);
        assertAccount(account, email, role);
        assertEquals(
                account.path("id").asString(), body(client.get(ME)).path("id").asString());
        return account;
    }

    private String createProfile(AuthHttpClient client) throws Exception {
        HttpResponse<String> response = client.post(PROFILES, profileJson());
        assertStatus(response, 201);
        String id = body(response).path("id").asString();
        assertEquals(UUID.fromString(id).toString(), id);
        assertEquals(
                PROFILES + "/" + id, response.headers().firstValue("Location").orElseThrow());
        return id;
    }

    private int storedCodes(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM email_codes WHERE email = ?", Integer.class, email);
    }

    private void assertAccount(JsonNode account, String email, String role) {
        assertEquals(
                UUID.fromString(account.path("id").asString()).toString(),
                account.path("id").asString());
        assertEquals(email, account.path("email").asString());
        assertEquals(role, account.path("role").asString());
        assertFalse(account.path("createdAt").asString().isBlank());
        Instant.parse(account.path("createdAt").asString());
    }

    private JsonNode assertField(HttpResponse<String> response, String field) throws Exception {
        JsonNode error = assertError(response, 400, "VALIDATION_FAILED");
        assertTrue(error.path("fieldErrors").isArray());
        assertTrue(error.path("fieldErrors").toString().contains("\"field\":\"" + field + "\""));
        return error;
    }

    private JsonNode assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertStatus(response, status);
        JsonNode error = body(response);
        assertEquals(code, error.path("code").asString());
        assertFalse(error.path("message").asString().isBlank());
        return error;
    }

    private void assertSameError(JsonNode expected, HttpResponse<String> response) throws Exception {
        JsonNode actual = assertError(response, 400, "INVALID_CODE");
        assertEquals(expected.path("message").asString(), actual.path("message").asString());
    }

    private void assertRetryAfter(HttpResponse<String> response, int status, int seconds) throws Exception {
        assertError(response, status, "TOO_MANY_REQUESTS");
        assertEquals(
                seconds,
                Integer.parseInt(response.headers().firstValue("Retry-After").orElseThrow()));
    }

    private void assertStatus(HttpResponse<String> response, int expected) {
        assertEquals(expected, response.statusCode(), response.body());
    }

    private void assertSetCookie(HttpResponse<String> response, String name) {
        assertFalse(setCookie(response, name).isBlank());
    }

    private String setCookie(HttpResponse<String> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith(name + "="))
                .filter(value -> !value.startsWith(name + "=;"))
                .reduce((ignored, latest) -> latest)
                .orElseThrow();
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        return JSON.readTree(response.body());
    }

    private String emailJson(String email) {
        ObjectNode json = JSON.createObjectNode();
        json.put("email", email);
        return JSON.writeValueAsString(json);
    }

    private String verifyJson(String email, String code) {
        ObjectNode json = JSON.createObjectNode();
        json.put("email", email);
        json.put("code", code);
        return JSON.writeValueAsString(json);
    }

    private String signupJson(String role, boolean ageOver14) {
        ObjectNode json = JSON.createObjectNode();
        json.put("role", role);
        json.put("ageOver14", ageOver14);
        return JSON.writeValueAsString(json);
    }

    private String profileJson() {
        ObjectNode request = JSON.createObjectNode();
        request.put("displayName", "선생님");
        request.putArray("subjectCodes").add("MATH");
        request.putArray("gradeCodes").add("MIDDLE_1");
        request.putArray("dongCodes").add("BEOMEO_1");
        ObjectNode tuition = request.putObject("tuition");
        tuition.put("amountWon", 50000);
        tuition.put("unit", "PER_SESSION");
        tuition.put("sessionMinutes", 60);
        request.put("declarationType", "REPORTED");
        request.put("registrationNumber", "12345");
        request.put("university", "대구대학교");
        request.put("department", "수학과");
        return JSON.writeValueAsString(request);
    }

    private String email() {
        return "auth-" + UUID.randomUUID() + "@example.test";
    }

    private String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }
}

package kr.needteacher.tutorprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.needteacher.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TutorProfileAcceptanceIT {

    private static final String PROFILES = "/api/v1/tutor-profiles";
    private static final String ME = "/api/v1/me/tutor-profile";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Test
    void teacherCreatesHiddenProfileWithOwnerFieldsAndLocation() throws Exception {
        String owner = newOwner();
        ObjectNode request = validProfile();
        request.put("displayName", "가".repeat(20));
        request.putArray("subjectCodes")
                .add("MATH")
                .add("ENGLISH")
                .add("SCIENCE")
                .add("CODING")
                .add("ESSAY");

        MvcResult result = mvc.perform(post(PROFILES)
                        .with(user(owner).roles("TEACHER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = body(result);
        String id = body.path("id").asString();
        assertFalse(id.isBlank());
        assertEquals(id, UUID.fromString(id).toString());
        assertEquals(
                PROFILES + "/" + id,
                URI.create(result.getResponse().getHeader("Location")).getPath());
        assertEquals("HIDDEN", body.path("visibility").asString());
        assertEquals("가".repeat(20), body.path("displayName").asString());
        assertEquals(5, body.path("subjectCodes").size());
        assertEquals(50000, body.path("tuition").path("amountWon").asInt());
        assertEquals("PER_SESSION", body.path("tuition").path("unit").asString());
        assertEquals("REPORTED", body.path("declarationType").asString());
        assertEquals("12345", body.path("registrationNumber").asString());
        assertFalse(body.path("universityEmailVerified").asBoolean());
        assertFalse(body.path("createdAt").asString().isBlank());
        assertFalse(body.path("updatedAt").asString().isBlank());
        assertFalse(body.has("ownerId"));
        assertFalse(body.has("email"));
        assertFalse(body.has("highSchool"));
    }

    @Test
    void createRequiresTeacherAndOneProfilePerOwner() throws Exception {
        ObjectNode request = validProfile();
        assertError(
                mvc.perform(post(PROFILES)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JSON.writeValueAsString(request)))
                        .andExpect(status().isUnauthorized())
                        .andReturn(),
                "UNAUTHORIZED");
        assertError(
                mvc.perform(post(PROFILES)
                                .with(user(newOwner()).roles("PARENT"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JSON.writeValueAsString(request)))
                        .andExpect(status().isForbidden())
                        .andReturn(),
                "FORBIDDEN");

        String owner = newOwner();
        create(owner, request);
        assertError(
                mvc.perform(post(PROFILES)
                                .with(user(owner).roles("TEACHER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JSON.writeValueAsString(request)))
                        .andExpect(status().isConflict())
                        .andReturn(),
                "PROFILE_ALREADY_EXISTS");
    }

    @Test
    void createRejectsFieldBoundariesAndUnknownCodes() throws Exception {
        ObjectNode request = validProfile();
        request.put("displayName", "");
        assertValidation(request, "displayName");
        request = validProfile();
        request.put("displayName", "가".repeat(21));
        assertValidation(request, "displayName");
        request = validProfile();
        request.put("university", "");
        assertValidation(request, "university");
        request = validProfile();
        request.put("university", "U".repeat(51));
        assertValidation(request, "university");
        request = validProfile();
        request.put("department", "");
        assertValidation(request, "department");
        request = validProfile();
        request.put("department", "D".repeat(51));
        assertValidation(request, "department");
        request = validProfile();
        request.put("introduction", "i".repeat(1001));
        assertValidation(request, "introduction");
        request = validProfile();
        request.putArray("subjectCodes");
        assertValidation(request, "subjectCodes");
        request = validProfile();
        request.putArray("subjectCodes")
                .add("MATH")
                .add("ENGLISH")
                .add("SCIENCE")
                .add("CODING")
                .add("ESSAY")
                .add("SOCIAL");
        assertValidation(request, "subjectCodes");
        request = validProfile();
        request.putArray("subjectCodes").add("MATH").add("MATH");
        assertValidation(request, "subjectCodes");
        request = validProfile();
        request.putArray("gradeCodes");
        assertValidation(request, "gradeCodes");
        request = validProfile();
        request.putArray("gradeCodes").add("MIDDLE_1").add("MIDDLE_1");
        assertValidation(request, "gradeCodes");
        request = validProfile();
        request.putArray("dongCodes");
        assertValidation(request, "dongCodes");
        request = validProfile();
        request.putArray("dongCodes").add("BEOMEO_1").add("BEOMEO_1");
        assertValidation(request, "dongCodes");
        request = validProfile();
        request.putArray("subjectCodes").add("UNKNOWN");
        assertValidation(request, "subjectCodes");
        request = validProfile();
        request.putArray("gradeCodes").add("UNKNOWN");
        assertValidation(request, "gradeCodes");
        request = validProfile();
        request.putArray("dongCodes").add("UNKNOWN");
        assertValidation(request, "dongCodes");
        request = validProfile();
        request.put("highSchool", "경신고");
        assertValidation(request, "highSchool");
    }

    @Test
    void createRejectsInvalidTuitionAndDeclarationCombinations() throws Exception {
        ObjectNode request = validProfile();
        ((ObjectNode) request.path("tuition")).put("amountWon", 0);
        assertValidation(request, "tuition.amountWon");
        request = validProfile();
        ((ObjectNode) request.path("tuition")).put("amountWon", 10000001);
        assertValidation(request, "tuition.amountWon");
        request = validProfile();
        ((ObjectNode) request.path("tuition")).remove("sessionMinutes");
        assertValidation(request, "tuition.sessionMinutes");
        request = validProfile();
        ((ObjectNode) request.path("tuition")).put("sessionMinutes", 9);
        assertValidation(request, "tuition.sessionMinutes");
        request = validProfile();
        ((ObjectNode) request.path("tuition")).put("sessionMinutes", 601);
        assertValidation(request, "tuition.sessionMinutes");
        request = validProfile();
        ((ObjectNode) request.path("tuition")).put("sessionsPerMonth", 4);
        assertValidation(request, "tuition.sessionsPerMonth");
        request = monthlyProfile();
        ((ObjectNode) request.path("tuition")).remove("sessionMinutes");
        assertValidation(request, "tuition.sessionMinutes");
        request = monthlyProfile();
        ((ObjectNode) request.path("tuition")).remove("sessionsPerMonth");
        assertValidation(request, "tuition.sessionsPerMonth");
        request = monthlyProfile();
        ((ObjectNode) request.path("tuition")).put("sessionsPerMonth", 0);
        assertValidation(request, "tuition.sessionsPerMonth");
        request = monthlyProfile();
        ((ObjectNode) request.path("tuition")).put("sessionsPerMonth", 32);
        assertValidation(request, "tuition.sessionsPerMonth");
        request = validProfile();
        request.remove("registrationNumber");
        assertValidation(request, "registrationNumber");
        request = validProfile();
        request.put("registrationNumber", "");
        assertValidation(request, "registrationNumber");
        request = validProfile();
        request.put("registrationNumber", "R".repeat(31));
        assertValidation(request, "registrationNumber");
        request = validProfile();
        request.put("declarationType", "ENROLLED_EXEMPT");
        assertValidation(request, "registrationNumber");
        request = validProfile();
        request.put("declarationType", "UNKNOWN");
        assertValidation(request, "declarationType");
    }

    @Test
    void acceptsInclusiveUpperBoundValues() throws Exception {
        ObjectNode perSession = validProfile();
        perSession.put("university", "U".repeat(50));
        perSession.put("department", "D".repeat(50));
        perSession.put("introduction", "i".repeat(1000));
        perSession.put("registrationNumber", "R".repeat(30));
        ((ObjectNode) perSession.path("tuition")).put("amountWon", 10000000);
        ((ObjectNode) perSession.path("tuition")).put("sessionMinutes", 600);
        JsonNode created = create(newOwner(), perSession);
        assertEquals(perSession.path("introduction"), created.path("introduction"));
        assertEquals(10000000, created.path("tuition").path("amountWon").asInt());
        assertEquals(600, created.path("tuition").path("sessionMinutes").asInt());
        ObjectNode perMonth = monthlyProfile();
        ((ObjectNode) perMonth.path("tuition")).put("sessionsPerMonth", 31);
        assertEquals(
                31,
                create(newOwner(), perMonth)
                        .path("tuition")
                        .path("sessionsPerMonth")
                        .asInt());
    }

    @Test
    void monthlyExemptProfileCanBeCreatedWithoutRegistrationNumber() throws Exception {
        String owner = newOwner();
        JsonNode profile = create(owner, monthlyProfile());
        assertEquals("PER_MONTH", profile.path("tuition").path("unit").asString());
        assertEquals(4, profile.path("tuition").path("sessionsPerMonth").asInt());
        assertEquals(60, profile.path("tuition").path("sessionMinutes").asInt());
        assertEquals("ENROLLED_EXEMPT", profile.path("declarationType").asString());
        assertTrue(profile.path("registrationNumber").isMissingNode()
                || profile.path("registrationNumber").isNull());
        String id = profile.path("id").asString();
        setVisibility(owner, id, "PUBLIC");
        JsonNode detail = body(
                mvc.perform(get(PROFILES + "/" + id)).andExpect(status().isOk()).andReturn());
        assertEquals(50000, detail.path("tuition").path("amountWon").asInt());
        assertEquals("PER_MONTH", detail.path("tuition").path("unit").asString());
        assertEquals(60, detail.path("tuition").path("sessionMinutes").asInt());
        assertEquals(4, detail.path("tuition").path("sessionsPerMonth").asInt());
        assertEquals("수학을 가르칩니다.", detail.path("introduction").asString());
    }

    @Test
    void acceptsEveryPublishedSubjectGradeAndDongCode() throws Exception {
        ObjectNode first = validProfile();
        first.putArray("subjectCodes")
                .add("KOREAN")
                .add("ENGLISH")
                .add("MATH")
                .add("SCIENCE")
                .add("SOCIAL");
        first.putArray("gradeCodes")
                .add("ELEMENTARY_1_3")
                .add("ELEMENTARY_4_6")
                .add("MIDDLE_1")
                .add("MIDDLE_2")
                .add("MIDDLE_3")
                .add("HIGH_1")
                .add("HIGH_2")
                .add("HIGH_3")
                .add("REPEATER");
        first.putArray("dongCodes")
                .add("BEOMEO_1")
                .add("BEOMEO_2")
                .add("BEOMEO_3")
                .add("BEOMEO_4")
                .add("MANCHON_1")
                .add("MANCHON_2")
                .add("MANCHON_3")
                .add("HWANGGEUM_1")
                .add("HWANGGEUM_2")
                .add("SUSEONG_1GA")
                .add("SUSEONG_23GA")
                .add("SUSEONG_4GA")
                .add("JUNG")
                .add("SANG")
                .add("DUSAN")
                .add("JISAN_1")
                .add("JISAN_2")
                .add("BEOMMUL_1")
                .add("BEOMMUL_2")
                .add("PA")
                .add("GOSAN_1")
                .add("GOSAN_2")
                .add("GOSAN_3");
        JsonNode created = create(newOwner(), first);
        assertCodes(first.path("subjectCodes"), created.path("subjectCodes"));
        assertCodes(first.path("gradeCodes"), created.path("gradeCodes"));
        assertCodes(first.path("dongCodes"), created.path("dongCodes"));
        ObjectNode second = validProfile();
        second.putArray("subjectCodes")
                .add("KOREAN_HISTORY")
                .add("ESSAY")
                .add("CODING")
                .add("SECOND_LANGUAGE");
        assertCodes(second.path("subjectCodes"), create(newOwner(), second).path("subjectCodes"));
    }

    @Test
    void meReturnsHiddenOwnerProfileAndRejectsOtherRoles() throws Exception {
        String owner = newOwner();
        JsonNode created = create(owner, validProfile());
        JsonNode mine = body(mvc.perform(get(ME).with(user(owner).roles("TEACHER")))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals(created.path("id").asString(), mine.path("id").asString());
        assertEquals("HIDDEN", mine.path("visibility").asString());
        assertFalse(mine.path("createdAt").asString().isBlank());
        assertError(mvc.perform(get(ME)).andExpect(status().isUnauthorized()).andReturn(), "UNAUTHORIZED");
        assertError(
                mvc.perform(get(ME).with(user(newOwner()).roles("PARENT")))
                        .andExpect(status().isForbidden())
                        .andReturn(),
                "FORBIDDEN");
        assertError(
                mvc.perform(get(ME).with(user(newOwner()).roles("TEACHER")))
                        .andExpect(status().isNotFound())
                        .andReturn(),
                "NOT_FOUND");
    }

    @Test
    void patchChangesOnlySentFieldsAndNullClearsIntroduction() throws Exception {
        String owner = newOwner();
        JsonNode created = create(owner, validProfile());
        String id = created.path("id").asString();
        String createdAt = created.path("createdAt").asString();
        JsonNode changed = body(mvc.perform(patch(PROFILES + "/" + id)
                        .with(user(owner).roles("TEACHER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"새 이름\",\"introduction\":null}"))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals("새 이름", changed.path("displayName").asString());
        assertEquals(created.path("tuition"), changed.path("tuition"));
        assertEquals(created.path("subjectCodes"), changed.path("subjectCodes"));
        assertTrue(changed.path("introduction").isMissingNode()
                || changed.path("introduction").isNull());
        assertEquals(createdAt, changed.path("createdAt").asString());
        String updatedAt = changed.path("updatedAt").asString();
        JsonNode unchanged = body(mvc.perform(patch(PROFILES + "/" + id)
                        .with(user(owner).roles("TEACHER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals(updatedAt, unchanged.path("updatedAt").asString());
    }

    @Test
    void patchEnforcesAuthenticationOwnershipExistenceAndValidation() throws Exception {
        String owner = newOwner();
        String id = create(owner, validProfile()).path("id").asString();
        assertError(
                mvc.perform(patch(PROFILES + "/" + id)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"displayName\":\"X\"}"))
                        .andExpect(status().isUnauthorized())
                        .andReturn(),
                "UNAUTHORIZED");
        assertError(
                mvc.perform(patch(PROFILES + "/" + id)
                                .with(user(newOwner()).roles("TEACHER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"displayName\":\"X\"}"))
                        .andExpect(status().isForbidden())
                        .andReturn(),
                "FORBIDDEN");
        assertError(
                mvc.perform(patch(PROFILES + "/" + id)
                                .with(user(newOwner()).roles("PARENT"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"displayName\":\"X\"}"))
                        .andExpect(status().isForbidden())
                        .andReturn(),
                "FORBIDDEN");
        assertError(
                mvc.perform(patch(PROFILES + "/" + UUID.randomUUID())
                                .with(user(owner).roles("TEACHER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"displayName\":\"X\"}"))
                        .andExpect(status().isNotFound())
                        .andReturn(),
                "NOT_FOUND");
        assertValidationResult(
                mvc.perform(patch(PROFILES + "/" + id)
                                .with(user(owner).roles("TEACHER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"displayName\":\"\"}"))
                        .andExpect(status().isBadRequest())
                        .andReturn(),
                "displayName");
        assertValidationResult(
                mvc.perform(patch(PROFILES + "/" + id)
                                .with(user(owner).roles("TEACHER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"highSchool\":\"경신고\"}"))
                        .andExpect(status().isBadRequest())
                        .andReturn(),
                "highSchool");
    }

    @Test
    void patchValidatesTheMergedProfileAndLeavesRejectedChangesUnapplied() throws Exception {
        String owner = newOwner();
        String id = create(owner, validProfile()).path("id").asString();
        assertValidationResult(
                mvc.perform(patch(PROFILES + "/" + id)
                                .with(user(owner).roles("TEACHER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"declarationType\":\"ENROLLED_EXEMPT\"}"))
                        .andExpect(status().isBadRequest())
                        .andReturn(),
                "registrationNumber");
        assertValidationResult(
                mvc.perform(patch(PROFILES + "/" + id)
                                .with(user(owner).roles("TEACHER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"tuition\":{\"unit\":\"PER_MONTH\"}}"))
                        .andExpect(status().isBadRequest())
                        .andReturn(),
                "tuition.sessionsPerMonth");
        assertValidationResult(
                mvc.perform(patch(PROFILES + "/" + id)
                                .with(user(owner).roles("TEACHER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"subjectCodes\":[\"UNKNOWN\"]}"))
                        .andExpect(status().isBadRequest())
                        .andReturn(),
                "subjectCodes");
        JsonNode unchanged = body(mvc.perform(get(ME).with(user(owner).roles("TEACHER")))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals("REPORTED", unchanged.path("declarationType").asString());
        assertEquals("12345", unchanged.path("registrationNumber").asString());
        assertEquals("PER_SESSION", unchanged.path("tuition").path("unit").asString());
        assertEquals("MATH", unchanged.path("subjectCodes").get(0).asString());
    }

    @Test
    void visibilityControlsPublicDetailAndCannotExposeHiddenProfile() throws Exception {
        String owner = newOwner();
        String id = create(owner, validProfile()).path("id").asString();
        assertError(
                mvc.perform(get(PROFILES + "/" + id).with(user(owner).roles("TEACHER")))
                        .andExpect(status().isNotFound())
                        .andReturn(),
                "NOT_FOUND");

        JsonNode shown = body(mvc.perform(patch(PROFILES + "/" + id + "/visibility")
                        .with(user(owner).roles("TEACHER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals(id, shown.path("id").asString());
        assertEquals("PUBLIC", shown.path("visibility").asString());
        assertFalse(shown.path("updatedAt").asString().isBlank());
        JsonNode detail = body(
                mvc.perform(get(PROFILES + "/" + id)).andExpect(status().isOk()).andReturn());
        assertEquals(id, detail.path("id").asString());
        assertEquals("12345", detail.path("registrationNumber").asString());
        assertEquals("수학을 가르칩니다.", detail.path("introduction").asString());
        assertEquals(50000, detail.path("tuition").path("amountWon").asInt());
        assertEquals("PER_SESSION", detail.path("tuition").path("unit").asString());
        assertEquals(60, detail.path("tuition").path("sessionMinutes").asInt());
        assertFalse(detail.path("universityEmailVerified").asBoolean());
        assertFalse(detail.has("visibility"));
        assertFalse(detail.has("createdAt"));
        assertFalse(detail.has("ownerId"));
        assertFalse(detail.has("email"));
        assertFalse(detail.has("highSchool"));

        mvc.perform(patch(PROFILES + "/" + id + "/visibility")
                        .with(user(owner).roles("TEACHER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"HIDDEN\"}"))
                .andExpect(status().isOk());
        assertError(
                mvc.perform(get(PROFILES + "/" + id))
                        .andExpect(status().isNotFound())
                        .andReturn(),
                "NOT_FOUND");
    }

    @Test
    void visibilityRejectsWrongActorsMissingProfileAndInvalidValue() throws Exception {
        String owner = newOwner();
        String id = create(owner, validProfile()).path("id").asString();
        String path = PROFILES + "/" + id + "/visibility";
        assertError(
                mvc.perform(patch(path)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"visibility\":\"PUBLIC\"}"))
                        .andExpect(status().isUnauthorized())
                        .andReturn(),
                "UNAUTHORIZED");
        assertError(
                mvc.perform(patch(path)
                                .with(user(newOwner()).roles("TEACHER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"visibility\":\"PUBLIC\"}"))
                        .andExpect(status().isForbidden())
                        .andReturn(),
                "FORBIDDEN");
        assertError(
                mvc.perform(patch(path)
                                .with(user(newOwner()).roles("PARENT"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"visibility\":\"PUBLIC\"}"))
                        .andExpect(status().isForbidden())
                        .andReturn(),
                "FORBIDDEN");
        assertError(
                mvc.perform(patch(PROFILES + "/" + UUID.randomUUID() + "/visibility")
                                .with(user(owner).roles("TEACHER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"visibility\":\"PUBLIC\"}"))
                        .andExpect(status().isNotFound())
                        .andReturn(),
                "NOT_FOUND");
        assertValidationResult(
                mvc.perform(patch(path)
                                .with(user(owner).roles("TEACHER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"visibility\":\"ARCHIVED\"}"))
                        .andExpect(status().isBadRequest())
                        .andReturn(),
                "visibility");
    }

    @Test
    void searchUsesOrWithinFiltersAndAndAcrossFiltersAndHidesDrafts() throws Exception {
        ObjectNode first = validProfile();
        first.putArray("subjectCodes").add("MATH");
        first.putArray("dongCodes").add("BEOMEO_1");
        first.putArray("gradeCodes").add("MIDDLE_1");
        String firstId = publish(newOwner(), first);
        ObjectNode second = monthlyProfile();
        second.putArray("subjectCodes").add("ENGLISH");
        second.putArray("dongCodes").add("BEOMEO_2");
        second.putArray("gradeCodes").add("MIDDLE_1");
        String secondId = publish(newOwner(), second);
        ObjectNode wrongDong = validProfile();
        wrongDong.putArray("dongCodes").add("JISAN_1");
        String wrongDongId = publish(newOwner(), wrongDong);
        ObjectNode wrongSubject = validProfile();
        wrongSubject.putArray("subjectCodes").add("SCIENCE");
        String wrongSubjectId = publish(newOwner(), wrongSubject);
        ObjectNode wrongGrade = validProfile();
        wrongGrade.putArray("gradeCodes").add("HIGH_1");
        String wrongGradeId = publish(newOwner(), wrongGrade);
        String hiddenId = create(newOwner(), first).path("id").asString();

        List<JsonNode> found =
                searchAll("dongCodes", "BEOMEO_1,BEOMEO_2", "subjectCodes", "MATH,ENGLISH", "gradeCodes", "MIDDLE_1");
        List<String> ids =
                found.stream().map(item -> item.path("id").asString()).toList();
        assertTrue(ids.contains(firstId));
        assertTrue(ids.contains(secondId));
        assertFalse(ids.contains(wrongDongId));
        assertFalse(ids.contains(wrongSubjectId));
        assertFalse(ids.contains(wrongGradeId));
        assertFalse(ids.contains(hiddenId));

        List<String> byDong = searchIds("dongCodes", "BEOMEO_1");
        assertTrue(byDong.contains(firstId));
        assertFalse(byDong.contains(wrongDongId));
        List<String> bySubject = searchIds("subjectCodes", "MATH");
        assertTrue(bySubject.contains(firstId));
        assertFalse(bySubject.contains(wrongSubjectId));
        List<String> byGrade = searchIds("gradeCodes", "MIDDLE_1");
        assertTrue(byGrade.contains(firstId));
        assertFalse(byGrade.contains(wrongGradeId));
        assertTrue(searchIds("dongCodes", "BEOMEO_1,BEOMEO_2").contains(secondId));
        assertTrue(searchIds("subjectCodes", "MATH,ENGLISH").contains(secondId));
        assertTrue(searchIds("gradeCodes", "MIDDLE_1,HIGH_1").contains(wrongGradeId));

        JsonNode card = found.stream()
                .filter(item -> firstId.equals(item.path("id").asString()))
                .findFirst()
                .orElseThrow();
        assertEquals("12345", card.path("registrationNumber").asString());
        assertEquals(50000, card.path("tuition").path("amountWon").asInt());
        assertEquals("PER_SESSION", card.path("tuition").path("unit").asString());
        assertEquals(60, card.path("tuition").path("sessionMinutes").asInt());
        assertEquals("대구대학교", card.path("university").asString());
        assertEquals("수학과", card.path("department").asString());
        assertCodes(first.path("subjectCodes"), card.path("subjectCodes"));
        assertCodes(first.path("gradeCodes"), card.path("gradeCodes"));
        assertCodes(first.path("dongCodes"), card.path("dongCodes"));
        assertFalse(card.path("updatedAt").asString().isBlank());
        assertFalse(card.has("introduction"));
        assertFalse(card.has("visibility"));
        assertFalse(card.has("createdAt"));
        assertFalse(card.has("ownerId"));
        assertFalse(card.has("email"));
        JsonNode monthlyCard = found.stream()
                .filter(item -> secondId.equals(item.path("id").asString()))
                .findFirst()
                .orElseThrow();
        assertEquals("PER_MONTH", monthlyCard.path("tuition").path("unit").asString());
        assertEquals(60, monthlyCard.path("tuition").path("sessionMinutes").asInt());
        assertEquals(4, monthlyCard.path("tuition").path("sessionsPerMonth").asInt());
    }

    @Test
    void searchPaginatesAndRejectsInvalidQuery() throws Exception {
        String firstId = publish(newOwner(), validProfile());
        String secondId = publish(newOwner(), validProfile());
        for (int index = 0; index < 19; index++) {
            publish(newOwner(), validProfile());
        }
        JsonNode firstPage = body(mvc.perform(get(PROFILES).param("limit", "1"))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals(1, firstPage.path("items").size());
        assertFalse(firstPage.path("nextCursor").isNull());
        assertFalse(firstPage.path("nextCursor").asString().isBlank());
        JsonNode defaultPage =
                body(mvc.perform(get(PROFILES)).andExpect(status().isOk()).andReturn());
        assertEquals(20, defaultPage.path("items").size());
        assertFalse(defaultPage.path("nextCursor").isNull());
        assertFalse(defaultPage.path("nextCursor").asString().isBlank());

        List<JsonNode> all = searchAll();
        List<String> ids = all.stream().map(item -> item.path("id").asString()).toList();
        assertTrue(ids.contains(firstId));
        assertTrue(ids.contains(secondId));
        assertEquals(ids.size(), ids.stream().distinct().count());
        assertInvalidQuery("subjectCodes", "UNKNOWN");
        assertInvalidQuery("dongCodes", "UNKNOWN");
        assertInvalidQuery("gradeCodes", "UNKNOWN");
        assertInvalidQuery("limit", "0");
        assertInvalidQuery("limit", "51");
        assertInvalidQuery("cursor", "invalid-cursor");
    }

    @Test
    void editingAndRepublishingMoveProfilesToFront() throws Exception {
        String firstOwner = newOwner();
        String firstId = publish(firstOwner, validProfile());
        String secondOwner = newOwner();
        String secondId = publish(secondOwner, validProfile());
        Thread.sleep(30);
        mvc.perform(patch(PROFILES + "/" + firstId)
                        .with(user(firstOwner).roles("TEACHER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"수정된 선생님\"}"))
                .andExpect(status().isOk());
        assertBefore(firstId, secondId);
        Thread.sleep(30);
        mvc.perform(patch(PROFILES + "/" + secondId + "/visibility")
                        .with(user(secondOwner).roles("TEACHER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"HIDDEN\"}"))
                .andExpect(status().isOk());
        assertFalse(searchAll().stream()
                .anyMatch(item -> secondId.equals(item.path("id").asString())));
        Thread.sleep(30);
        mvc.perform(patch(PROFILES + "/" + secondId + "/visibility")
                        .with(user(secondOwner).roles("TEACHER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isOk());
        assertBefore(secondId, firstId);
        assertError(
                mvc.perform(get(PROFILES + "/" + UUID.randomUUID()))
                        .andExpect(status().isNotFound())
                        .andReturn(),
                "NOT_FOUND");
    }

    private ObjectNode validProfile() {
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
        request.put("introduction", "수학을 가르칩니다.");
        return request;
    }

    private ObjectNode monthlyProfile() {
        ObjectNode request = validProfile();
        ObjectNode tuition = (ObjectNode) request.path("tuition");
        tuition.put("unit", "PER_MONTH");
        tuition.put("sessionsPerMonth", 4);
        request.put("declarationType", "ENROLLED_EXEMPT");
        request.remove("registrationNumber");
        return request;
    }

    private JsonNode create(String owner, ObjectNode request) throws Exception {
        return body(mvc.perform(post(PROFILES)
                        .with(user(owner).roles("TEACHER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private String publish(String owner, ObjectNode request) throws Exception {
        String id = create(owner, request).path("id").asString();
        setVisibility(owner, id, "PUBLIC");
        return id;
    }

    private void setVisibility(String owner, String id, String visibility) throws Exception {
        mvc.perform(patch(PROFILES + "/" + id + "/visibility")
                        .with(user(owner).roles("TEACHER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"" + visibility + "\"}"))
                .andExpect(status().isOk());
    }

    private void assertValidation(ObjectNode request, String field) throws Exception {
        assertValidationResult(
                mvc.perform(post(PROFILES)
                                .with(user(newOwner()).roles("TEACHER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JSON.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andReturn(),
                field);
    }

    private void assertValidationResult(MvcResult result, String field) throws Exception {
        JsonNode error = body(result);
        assertEquals("VALIDATION_FAILED", error.path("code").asString());
        assertFalse(error.path("message").asString().isBlank());
        assertTrue(error.path("fieldErrors").isArray());
        boolean present = false;
        for (JsonNode item : error.path("fieldErrors")) {
            if (field.equals(item.path("field").asString())) {
                present = true;
            }
        }
        assertTrue(present, "fieldErrors must contain " + field);
    }

    private void assertInvalidQuery(String parameter, String value) throws Exception {
        assertError(
                mvc.perform(get(PROFILES).param(parameter, value))
                        .andExpect(status().isBadRequest())
                        .andReturn(),
                "INVALID_QUERY");
    }

    private void assertError(MvcResult result, String code) throws Exception {
        JsonNode error = body(result);
        assertEquals(code, error.path("code").asString());
        assertFalse(error.path("message").asString().isBlank());
    }

    private JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private List<JsonNode> searchAll(String... filters) throws Exception {
        List<JsonNode> all = new ArrayList<>();
        String cursor = null;
        do {
            var request = get(PROFILES).param("limit", "50");
            for (int index = 0; index < filters.length; index += 2) {
                request.param(filters[index], filters[index + 1]);
            }
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            JsonNode page = body(mvc.perform(request).andExpect(status().isOk()).andReturn());
            assertTrue(page.path("items").isArray());
            assertTrue(page.path("items").size() <= 50);
            for (JsonNode item : page.path("items")) {
                all.add(item);
            }
            JsonNode next = page.path("nextCursor");
            assertFalse(next.isMissingNode());
            cursor = next.isNull() ? null : next.asString();
            if (cursor != null) {
                assertFalse(cursor.isBlank());
            }
            assertTrue(all.size() < 10000, "cursor did not terminate");
        } while (cursor != null);
        return all;
    }

    private List<String> searchIds(String... filters) throws Exception {
        return searchAll(filters).stream()
                .map(item -> item.path("id").asString())
                .toList();
    }

    private void assertBefore(String earlierId, String laterId) throws Exception {
        List<String> ids =
                searchAll().stream().map(item -> item.path("id").asString()).toList();
        assertTrue(ids.indexOf(earlierId) >= 0);
        assertTrue(ids.indexOf(laterId) >= 0);
        assertTrue(ids.indexOf(earlierId) < ids.indexOf(laterId));
    }

    private void assertCodes(JsonNode expected, JsonNode actual) {
        assertEquals(expected.size(), actual.size());
        List<String> values = new ArrayList<>();
        for (JsonNode code : actual) {
            values.add(code.asString());
        }
        for (JsonNode code : expected) {
            assertTrue(values.contains(code.asString()));
        }
    }

    private String newOwner() {
        return "teacher-" + UUID.randomUUID();
    }
}

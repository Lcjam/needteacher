package kr.needteacher.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class AuthMailInbox {

    private static final Pattern SIX_DIGITS = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient client = HttpClient.newHttpClient();
    private final String baseUrl;

    AuthMailInbox(GenericContainer<?> mailpit) {
        baseUrl = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
    }

    String codeFor(String email, int expectedMessages) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        String query = URLEncoder.encode("to:" + email, StandardCharsets.UTF_8);
        while (System.nanoTime() < deadline) {
            JsonNode search = fetch("/api/v1/search?query=" + query);
            JsonNode messages = search.path("messages");
            if (messages.isArray() && messages.size() >= expectedMessages) {
                assertEquals(expectedMessages, messages.size(), "only successful sends create mail");
                String id = messages.get(0).path("ID").asString();
                assertFalse(id.isBlank());
                JsonNode message = fetch("/api/v1/message/" + id);
                assertEquals(email, message.path("To").get(0).path("Address").asString());
                String body = message.path("Text").asString();
                if (body.isBlank()) {
                    body = message.path("HTML").asString();
                }
                Matcher code = SIX_DIGITS.matcher(body);
                assertTrue(code.find(), "mail must contain a six-digit code");
                String value = code.group();
                assertFalse(code.find(), "mail must contain exactly one six-digit code");
                return value;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("no message arrived for " + email);
    }

    private JsonNode fetch(String path) throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }
}

package kr.needteacher.account;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class AuthHttpClient {

    private final HttpClient transport = HttpClient.newHttpClient();
    private final String baseUrl;
    private final Map<String, String> cookies = new LinkedHashMap<>();

    AuthHttpClient(int port) {
        baseUrl = "http://localhost:" + port;
    }

    AuthHttpClient copy() {
        AuthHttpClient copied = new AuthHttpClient(URI.create(baseUrl).getPort());
        copied.cookies.putAll(cookies);
        return copied;
    }

    void cookie(String name, String value) {
        cookies.put(name, value);
    }

    String cookie(String name) {
        return cookies.get(name);
    }

    HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send("GET", path, null, null);
    }

    HttpResponse<String> post(String path, String json) throws IOException, InterruptedException {
        return send("POST", path, json, cookie("XSRF-TOKEN"));
    }

    HttpResponse<String> postWithCsrf(String path, String json, String token) throws IOException, InterruptedException {
        return send("POST", path, json, token);
    }

    HttpResponse<String> delete(String path) throws IOException, InterruptedException {
        return send("DELETE", path, null, cookie("XSRF-TOKEN"));
    }

    HttpResponse<String> deleteWithCsrf(String path, String token) throws IOException, InterruptedException {
        return send("DELETE", path, null, token);
    }

    HttpResponse<String> patch(String path, String json) throws IOException, InterruptedException {
        return send("PATCH", path, json, cookie("XSRF-TOKEN"));
    }

    private HttpResponse<String> send(String method, String path, String json, String csrfToken)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (!cookies.isEmpty()) {
            builder.header(
                    "Cookie",
                    cookies.entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .collect(Collectors.joining("; ")));
        }
        if (csrfToken != null) {
            builder.header("X-XSRF-TOKEN", csrfToken);
        }
        HttpRequest.BodyPublisher body = json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
        if (json != null) {
            builder.header("Content-Type", "application/json");
        }
        HttpResponse<String> response =
                transport.send(builder.method(method, body).build(), HttpResponse.BodyHandlers.ofString());
        List<String> setCookies = response.headers().allValues("Set-Cookie");
        for (String header : setCookies) {
            for (HttpCookie parsed : HttpCookie.parse(header)) {
                if (parsed.getMaxAge() == 0) {
                    cookies.remove(parsed.getName());
                } else {
                    cookies.put(parsed.getName(), parsed.getValue());
                }
            }
        }
        return response;
    }
}

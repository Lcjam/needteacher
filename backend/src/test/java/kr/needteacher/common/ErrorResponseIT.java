package kr.needteacher.common;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import kr.needteacher.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ErrorResponseIT {

    private static final Cookie CSRF_COOKIE = new Cookie("XSRF-TOKEN", "csrf-token-val");
    private static final String CSRF_HEADER = "csrf-token-val";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("깨진 JSON 요청은 400 MALFORMED_REQUEST와 표준 오류 형식을 반환한다")
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/tutor-profiles")
                        .with(user("teacher1").roles("TEACHER"))
                        .cookie(CSRF_COOKIE)
                        .header("X-XSRF-TOKEN", CSRF_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청 본문이 올바르지 않거나 비어 있습니다."));
    }

    @Test
    @DisplayName("본문 없는 요청은 400 MALFORMED_REQUEST와 표준 오류 형식을 반환한다")
    void emptyBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/tutor-profiles")
                        .with(user("teacher1").roles("TEACHER"))
                        .cookie(CSRF_COOKIE)
                        .header("X-XSRF-TOKEN", CSRF_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청 본문이 올바르지 않거나 비어 있습니다."));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 415 UNSUPPORTED_MEDIA_TYPE과 표준 오류 형식을 반환한다")
    void unsupportedMediaTypeReturns415() throws Exception {
        mockMvc.perform(post("/api/v1/tutor-profiles")
                        .with(user("teacher1").roles("TEACHER"))
                        .cookie(CSRF_COOKIE)
                        .header("X-XSRF-TOKEN", CSRF_HEADER)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 미디어 타입입니다."));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405 METHOD_NOT_ALLOWED와 표준 오류 형식을 반환한다")
    void methodNotAllowedReturns405() throws Exception {
        mockMvc.perform(delete("/api/v1/tutor-profiles/" + UUID.randomUUID())
                        .with(user("teacher1").roles("TEACHER"))
                        .cookie(CSRF_COOKIE)
                        .header("X-XSRF-TOKEN", CSRF_HEADER))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 HTTP 메서드입니다."));
    }

    @Test
    @DisplayName("인증된 사용자의 없는 경로는 404 NOT_FOUND와 표준 오류 형식을 반환한다")
    void notFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/non-existent-path").with(user("teacher1").roles("TEACHER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("요청한 경로를 찾을 수 없습니다."));
    }
}

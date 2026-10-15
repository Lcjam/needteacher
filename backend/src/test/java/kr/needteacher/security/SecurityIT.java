package kr.needteacher.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import kr.needteacher.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("익명 GET /api/ping 요청은 200 pong을 반환한다")
    void anonymousPingReturns200() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    @DisplayName("익명으로 보호된 경로에 접근하면 401 JSON을 반환한다")
    void anonymousProtectedEndpointReturns401Json() throws Exception {
        mockMvc.perform(get("/api/any-protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    @Test
    @DisplayName("로그인 사용자의 상태 변경 요청에 CSRF 토큰이 없으면 403 JSON을 반환한다")
    void authenticatedPostWithoutCsrfReturns403Json() throws Exception {
        mockMvc.perform(post("/api/nonexistent").with(user("teacher").roles("TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    @Test
    @DisplayName("로그인 사용자의 상태 변경 요청에 잘못된 CSRF 토큰을 보내면 403 JSON을 반환한다")
    void authenticatedPostWithInvalidCsrfReturns403Json() throws Exception {
        mockMvc.perform(post("/api/nonexistent")
                        .with(user("teacher").roles("TEACHER"))
                        .cookie(new Cookie("XSRF-TOKEN", "real-token"))
                        .header("X-XSRF-TOKEN", "wrong-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    @Test
    @DisplayName("CSRF 쿠키 왕복: GET 요청으로 받은 XSRF-TOKEN 쿠키를 헤더로 전달하면 POST 요청이 보안을 통과한다")
    void csrfCookieRoundTripAllowsStateChangingRequest() throws Exception {
        MvcResult getResult = mockMvc.perform(get("/api/ping")).andReturn();
        Cookie xsrfCookie = getResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrfCookie)
                .as("첫 GET /api/ping 요청 시 XSRF-TOKEN 쿠키가 발급되어야 한다")
                .isNotNull();

        String token = xsrfCookie.getValue();
        assertThat(token).isNotBlank();

        mockMvc.perform(post("/api/nonexistent")
                        .with(user("teacher").roles("TEACHER"))
                        .cookie(xsrfCookie)
                        .header("X-XSRF-TOKEN", token))
                .andExpect(status().isNotFound());
    }
}

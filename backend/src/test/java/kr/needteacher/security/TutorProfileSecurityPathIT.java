package kr.needteacher.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import kr.needteacher.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TutorProfileSecurityPathIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("익명 GET /api/v1/tutor-profiles/{비-UUID} 요청은 401 UNAUTHORIZED를 반환한다")
    void anonymousNonUuidSegmentReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/tutor-profiles/some-non-uuid-string"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    @Test
    @DisplayName("익명 GET /api/v1/tutor-profiles 목록 요청은 200 OK를 반환한다")
    void anonymousListEndpointReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/tutor-profiles")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("익명 GET /api/v1/tutor-profiles/{UUID} 요청은 보안 필터를 통과하여 404 NOT_FOUND를 반환한다")
    void anonymousValidUuidEndpointPassesSecurity() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/tutor-profiles/" + nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("익명 GET /api/v1/me/tutor-profile 요청은 401 UNAUTHORIZED를 반환한다")
    void anonymousMyProfileReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/me/tutor-profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("학부모 GET /api/v1/me/tutor-profile 요청은 403 FORBIDDEN을 반환한다")
    void parentMyProfileReturns403() throws Exception {
        mockMvc.perform(
                        get("/api/v1/me/tutor-profile")
                                .with(org.springframework.security.test.web.servlet.request
                                        .SecurityMockMvcRequestPostProcessors.user("parent1")
                                        .roles("PARENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}

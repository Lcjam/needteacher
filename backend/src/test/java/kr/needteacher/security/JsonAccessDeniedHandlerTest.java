package kr.needteacher.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class JsonAccessDeniedHandlerTest {

    @Test
    @DisplayName("접근 권한이 없는 요청에 403 JSON 응답을 반환한다")
    void handleReturns403Json() throws IOException {
        JsonAccessDeniedHandler handler = new JsonAccessDeniedHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("접근 거부"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("{\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}");
    }
}

package kr.needteacher.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("ValidationException을 400 VALIDATION_FAILED로 처리한다")
    void handleValidationException() {
        ValidationException ex = new ValidationException("field1", "reason1");
        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().fieldErrors()).hasSize(1);
        assertThat(response.getBody().fieldErrors().get(0).field()).isEqualTo("field1");
        assertThat(response.getBody().fieldErrors().get(0).reason()).isEqualTo("reason1");
    }

    @Test
    @DisplayName("InvalidQueryException을 400 INVALID_QUERY로 처리한다")
    void handleInvalidQueryException() {
        InvalidQueryException ex = new InvalidQueryException("잘못된 쿼리입니다.");
        ResponseEntity<ErrorResponse> response = handler.handleInvalidQueryException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_QUERY");
        assertThat(response.getBody().message()).isEqualTo("잘못된 쿼리입니다.");
    }

    @Test
    @DisplayName("NotFoundException을 404 NOT_FOUND로 처리한다")
    void handleNotFoundException() {
        NotFoundException ex = new NotFoundException("대상이 없습니다.");
        ResponseEntity<ErrorResponse> response = handler.handleNotFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("대상이 없습니다.");
    }

    @Test
    @DisplayName("ForbiddenException을 403 FORBIDDEN으로 처리한다")
    void handleForbiddenException() {
        ForbiddenException ex = new ForbiddenException("권한이 없습니다.");
        ResponseEntity<ErrorResponse> response = handler.handleForbiddenException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().message()).isEqualTo("권한이 없습니다.");
    }

    @Test
    @DisplayName("ConflictException을 지정된 코드와 409로 처리한다")
    void handleConflictException() {
        ConflictException ex = new ConflictException("PROFILE_ALREADY_EXISTS", "이미 존재합니다.");
        ResponseEntity<ErrorResponse> response = handler.handleConflictException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("PROFILE_ALREADY_EXISTS");
        assertThat(response.getBody().message()).isEqualTo("이미 존재합니다.");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException: PathVariable이면 404 NOT_FOUND로 처리한다")
    void handleTypeMismatchPathVariable() throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("dummyPathVariable", String.class);
        MethodParameter param = new MethodParameter(method, 0);

        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("not-a-uuid", String.class, "id", param, null);
        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException: 쿼리 파라미터이면 400 INVALID_QUERY로 처리한다")
    void handleTypeMismatchQueryParam() throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("dummyRequestParam", String.class);
        MethodParameter param = new MethodParameter(method, 0);

        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("abc", Integer.class, "limit", param, null);
        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_QUERY");
        assertThat(response.getBody().message()).contains("limit");

        // parameter가 null인 경우에도 400 INVALID_QUERY
        MethodArgumentTypeMismatchException exNullParam =
                new MethodArgumentTypeMismatchException("abc", Integer.class, "limit", null, null);
        ResponseEntity<ErrorResponse> response2 = handler.handleTypeMismatch(exNullParam);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("기타 예외는 500 INTERNAL_ERROR로 처리하고 상세 내용을 감춘다")
    void handleAllUncaughtException() {
        Exception ex = new RuntimeException("DB 비밀번호가 포함된 스택 트레이스");
        ResponseEntity<ErrorResponse> response = handler.handleAllUncaughtException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("DB 비밀번호");
        assertThat(response.getBody().message()).isEqualTo("서버 내부 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("handleHttpMessageNotReadable은 400 MALFORMED_REQUEST를 반환한다")
    void handleHttpMessageNotReadable() throws Exception {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("JSON parse error", new MockHttpInputMessage(new byte[0]));
        ResponseEntity<Object> response = handler.handleException(ex, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertThat(body.code()).isEqualTo("MALFORMED_REQUEST");
        assertThat(body.message()).isEqualTo("요청 본문이 올바르지 않거나 비어 있습니다.");
    }

    @Test
    @DisplayName("handleHttpMediaTypeNotSupported는 415 UNSUPPORTED_MEDIA_TYPE을 반환한다")
    void handleHttpMediaTypeNotSupported() throws Exception {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<Object> response = handler.handleException(ex, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertThat(body.code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        assertThat(body.message()).isEqualTo("지원하지 않는 미디어 타입입니다.");
    }

    @Test
    @DisplayName("handleHttpRequestMethodNotSupported는 405 METHOD_NOT_ALLOWED를 반환한다")
    void handleHttpRequestMethodNotSupported() throws Exception {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST", List.of("GET"));
        ResponseEntity<Object> response = handler.handleException(ex, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertThat(body.code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(body.message()).isEqualTo("지원하지 않는 HTTP 메서드입니다.");
    }

    @Test
    @DisplayName("handleNoResourceFoundException은 404 NOT_FOUND를 반환한다")
    void handleNoResourceFoundException() throws Exception {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/not-found", "Not found");
        ResponseEntity<Object> response = handler.handleException(ex, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertThat(body.code()).isEqualTo("NOT_FOUND");
        assertThat(body.message()).isEqualTo("요청한 경로를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("handleExceptionInternal은 상태 코드에 맞는 표준 ErrorResponse를 반환한다")
    void handleExceptionInternal() {
        Exception ex = new RuntimeException("내부 오류");

        ResponseEntity<Object> res400 = handler.handleExceptionInternal(
                ex, null, new HttpHeaders(), HttpStatusCode.valueOf(400), (WebRequest) null);
        assertThat(res400.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((ErrorResponse) res400.getBody()).code()).isEqualTo("MALFORMED_REQUEST");
        assertThat(((ErrorResponse) res400.getBody()).message()).isEqualTo("요청 본문이 올바르지 않거나 비어 있습니다.");

        ResponseEntity<Object> res404 = handler.handleExceptionInternal(
                ex, null, new HttpHeaders(), HttpStatusCode.valueOf(404), (WebRequest) null);
        assertThat(res404.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(((ErrorResponse) res404.getBody()).code()).isEqualTo("NOT_FOUND");
        assertThat(((ErrorResponse) res404.getBody()).message()).isEqualTo("요청한 경로를 찾을 수 없습니다.");

        ResponseEntity<Object> res405 = handler.handleExceptionInternal(
                ex, null, new HttpHeaders(), HttpStatusCode.valueOf(405), (WebRequest) null);
        assertThat(res405.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(((ErrorResponse) res405.getBody()).code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(((ErrorResponse) res405.getBody()).message()).isEqualTo("지원하지 않는 HTTP 메서드입니다.");

        ResponseEntity<Object> res415 = handler.handleExceptionInternal(
                ex, null, new HttpHeaders(), HttpStatusCode.valueOf(415), (WebRequest) null);
        assertThat(res415.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(((ErrorResponse) res415.getBody()).code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        assertThat(((ErrorResponse) res415.getBody()).message()).isEqualTo("지원하지 않는 미디어 타입입니다.");

        ResponseEntity<Object> res406 = handler.handleExceptionInternal(
                ex, null, new HttpHeaders(), HttpStatusCode.valueOf(406), (WebRequest) null);
        assertThat(res406.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(((ErrorResponse) res406.getBody()).code()).isEqualTo("BAD_REQUEST");
        assertThat(((ErrorResponse) res406.getBody()).message()).isEqualTo("요청 처리 중 오류가 발생했습니다.");

        ResponseEntity<Object> res500 = handler.handleExceptionInternal(
                ex, null, new HttpHeaders(), HttpStatusCode.valueOf(500), (WebRequest) null);
        assertThat(res500.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(((ErrorResponse) res500.getBody()).code()).isEqualTo("INTERNAL_ERROR");
        assertThat(((ErrorResponse) res500.getBody()).message()).isEqualTo("서버 내부 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("ErrorResponse와 FieldErrorDetail 레코드 필드를 검증한다")
    void errorResponseFields() {
        ErrorResponse res1 = new ErrorResponse("CODE", "MSG");
        assertThat(res1.code()).isEqualTo("CODE");
        assertThat(res1.message()).isEqualTo("MSG");
        assertThat(res1.fieldErrors()).isEmpty();

        FieldErrorDetail fed = new FieldErrorDetail("field", "reason");
        assertThat(fed.field()).isEqualTo("field");
        assertThat(fed.reason()).isEqualTo("reason");

        ValidationException vex = new ValidationException("메시지", List.of(fed));
        assertThat(vex.getMessage()).isEqualTo("메시지");
        assertThat(vex.getFieldErrors()).hasSize(1);

        ValidationException vexNull = new ValidationException("메시지", (List<FieldErrorDetail>) null);
        assertThat(vexNull.getFieldErrors()).isEmpty();
    }

    @SuppressWarnings("unused")
    private void dummyPathVariable(@PathVariable("id") String id) {}

    @SuppressWarnings("unused")
    private void dummyRequestParam(@RequestParam("limit") String limit) {}
}

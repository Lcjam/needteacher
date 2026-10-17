package kr.needteacher.common;

import java.io.Serial;
import java.util.List;

public class ValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<FieldErrorDetail> fieldErrors;

    public ValidationException(String message, List<FieldErrorDetail> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors != null ? List.copyOf(fieldErrors) : List.of();
    }

    public ValidationException(String field, String reason) {
        this("요청 값이 올바르지 않습니다.", List.of(new FieldErrorDetail(field, reason)));
    }

    public List<FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }
}

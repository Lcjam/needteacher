package kr.needteacher.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldErrorDetail> fieldErrors) {

    public ErrorResponse(String code, String message) {
        this(code, message, List.of());
    }
}

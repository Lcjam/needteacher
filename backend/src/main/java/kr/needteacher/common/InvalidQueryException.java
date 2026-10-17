package kr.needteacher.common;

import java.io.Serial;

public class InvalidQueryException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidQueryException(String message) {
        super(message);
    }
}

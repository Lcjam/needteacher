package kr.needteacher.tutorprofile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import kr.needteacher.common.InvalidQueryException;
import org.springframework.stereotype.Component;

@Component
public class CursorEncoder {

    public record Cursor(Instant updatedAt, UUID id) {}

    public String encode(Instant updatedAt, UUID id) {
        if (updatedAt == null || id == null) {
            return null;
        }
        String raw = updatedAt.toString() + "_" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public Cursor decode(String cursorStr) {
        if (cursorStr == null || cursorStr.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursorStr);
            String raw = new String(bytes, StandardCharsets.UTF_8);
            int idx = raw.lastIndexOf('_');
            if (idx <= 0 || idx >= raw.length() - 1) {
                throw new InvalidQueryException("올바르지 않은 커서 형식입니다.");
            }
            String instantStr = raw.substring(0, idx);
            String uuidStr = raw.substring(idx + 1);
            Instant updatedAt = Instant.parse(instantStr);
            UUID id = UUID.fromString(uuidStr);
            return new Cursor(updatedAt, id);
        } catch (InvalidQueryException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidQueryException("올바르지 않은 커서입니다.");
        }
    }
}

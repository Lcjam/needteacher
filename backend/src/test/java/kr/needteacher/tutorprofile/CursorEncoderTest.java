package kr.needteacher.tutorprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import kr.needteacher.common.InvalidQueryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CursorEncoderTest {

    private CursorEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new CursorEncoder();
    }

    @Test
    @DisplayName("encode와 decode 왕복이 정상 동작한다")
    void roundTrip() {
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        UUID id = UUID.randomUUID();

        String encoded = encoder.encode(now, id);
        assertThat(encoded).isNotNull().isNotBlank();

        CursorEncoder.Cursor cursor = encoder.decode(encoded);
        assertThat(cursor).isNotNull();
        assertThat(cursor.updatedAt()).isEqualTo(now);
        assertThat(cursor.id()).isEqualTo(id);
    }

    @Test
    @DisplayName("encode 시 파라미터가 null이면 null을 반환한다")
    void encodeNulls() {
        assertThat(encoder.encode(null, UUID.randomUUID())).isNull();
        assertThat(encoder.encode(Instant.now(), null)).isNull();
    }

    @Test
    @DisplayName("decode 시 null이거나 빈 문자열이면 null을 반환한다")
    void decodeEmpty() {
        assertThat(encoder.decode(null)).isNull();
        assertThat(encoder.decode("")).isNull();
        assertThat(encoder.decode("   ")).isNull();
    }

    @Test
    @DisplayName("decode 시 형식이 잘못되면 InvalidQueryException이 발생한다")
    void decodeInvalid() {
        assertThatThrownBy(() -> encoder.decode("invalid-base64!@#$")).isInstanceOf(InvalidQueryException.class);

        String completelyNoUnderscore =
                Base64.getUrlEncoder().encodeToString("noseparatorhere".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> encoder.decode(completelyNoUnderscore))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessage("올바르지 않은 커서 형식입니다.");

        String onlyUnderscore = Base64.getUrlEncoder().encodeToString("_".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> encoder.decode(onlyUnderscore))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessage("올바르지 않은 커서 형식입니다.");

        String startsWithUnderscore =
                Base64.getUrlEncoder().encodeToString("_trailing".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> encoder.decode(startsWithUnderscore))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessage("올바르지 않은 커서 형식입니다.");

        String endsWithUnderscore = Base64.getUrlEncoder().encodeToString("leading_".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> encoder.decode(endsWithUnderscore))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessage("올바르지 않은 커서 형식입니다.");

        String badInstant = Base64.getUrlEncoder()
                .encodeToString(("not-an-instant_" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> encoder.decode(badInstant))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessage("올바르지 않은 커서입니다.");

        String badUuid = Base64.getUrlEncoder()
                .encodeToString((Instant.now().toString() + "_not-a-uuid").getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> encoder.decode(badUuid))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessage("올바르지 않은 커서입니다.");
    }
}

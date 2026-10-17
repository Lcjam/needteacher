package kr.needteacher.tutorprofile;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TutorProfileSearchItemResponseTest {

    @Test
    @DisplayName("from 메서드가 엔티티 필드를 온전히 복사하여 응답 객체를 생성한다")
    void fromCopiesAllFields() {
        UUID id = UUID.randomUUID();
        String ownerId = "owner123";
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        TuitionVo tuition = new TuitionVo(50000, TuitionUnit.PER_SESSION, 60, null);

        TutorProfile profile = new TutorProfile(
                id,
                ownerId,
                ProfileVisibility.PUBLIC,
                "김선생",
                "경북대학교",
                "수학과",
                "소개글",
                tuition,
                DeclarationType.REPORTED,
                "12345",
                false,
                now,
                now,
                Set.of(SubjectCode.MATH),
                Set.of(GradeCode.MIDDLE_1),
                Set.of(DongCode.BEOMEO_1));

        TutorProfileSearchItemResponse response = TutorProfileSearchItemResponse.from(profile);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.displayName()).isEqualTo("김선생");
        assertThat(response.university()).isEqualTo("경북대학교");
        assertThat(response.department()).isEqualTo("수학과");
        assertThat(response.subjectCodes()).containsExactly(SubjectCode.MATH);
        assertThat(response.gradeCodes()).containsExactly(GradeCode.MIDDLE_1);
        assertThat(response.dongCodes()).containsExactly(DongCode.BEOMEO_1);
        assertThat(response.tuition()).isEqualTo(new TuitionDto(50000, TuitionUnit.PER_SESSION, 60, null));
        assertThat(response.declarationType()).isEqualTo(DeclarationType.REPORTED);
        assertThat(response.registrationNumber()).isEqualTo("12345");
        assertThat(response.universityEmailVerified()).isFalse();
        assertThat(response.updatedAt()).isEqualTo(now);
    }
}

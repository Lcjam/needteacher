package kr.needteacher.tutorprofile;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TutorProfileEntityTest {

    @Test
    @DisplayName("TutorProfile 엔티티의 getter와 setter를 검증한다")
    void entityGettersSetters() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        TuitionVo tuition = new TuitionVo(50000, TuitionUnit.PER_SESSION, 60, null);

        TutorProfile profile = new TutorProfile(
                id,
                "teacher1",
                ProfileVisibility.HIDDEN,
                "선생님",
                "경북대",
                "수학과",
                "소개",
                tuition,
                DeclarationType.REPORTED,
                "12345",
                false,
                now,
                now,
                null,
                null,
                null);

        assertThat(profile.getId()).isEqualTo(id);
        assertThat(profile.getOwnerId()).isEqualTo("teacher1");
        assertThat(profile.getVisibility()).isEqualTo(ProfileVisibility.HIDDEN);
        assertThat(profile.getDisplayName()).isEqualTo("선생님");
        assertThat(profile.getUniversity()).isEqualTo("경북대");
        assertThat(profile.getDepartment()).isEqualTo("수학과");
        assertThat(profile.getIntroduction()).isEqualTo("소개");
        assertThat(profile.getTuition()).isEqualTo(tuition);
        assertThat(profile.getDeclarationType()).isEqualTo(DeclarationType.REPORTED);
        assertThat(profile.getRegistrationNumber()).isEqualTo("12345");
        assertThat(profile.isUniversityEmailVerified()).isFalse();
        assertThat(profile.getCreatedAt()).isEqualTo(now);
        assertThat(profile.getUpdatedAt()).isEqualTo(now);
        assertThat(profile.getSubjectCodes()).isEmpty();
        assertThat(profile.getGradeCodes()).isEmpty();
        assertThat(profile.getDongCodes()).isEmpty();

        // Setter 테스트
        profile.setVisibility(ProfileVisibility.PUBLIC);
        assertThat(profile.getVisibility()).isEqualTo(ProfileVisibility.PUBLIC);

        profile.setDisplayName("새이름");
        assertThat(profile.getDisplayName()).isEqualTo("새이름");

        profile.setUniversity("새대학");
        assertThat(profile.getUniversity()).isEqualTo("새대학");

        profile.setDepartment("새학과");
        assertThat(profile.getDepartment()).isEqualTo("새학과");

        profile.setIntroduction("새소개");
        assertThat(profile.getIntroduction()).isEqualTo("새소개");

        TuitionVo newTuition = new TuitionVo(100000, TuitionUnit.PER_MONTH, 90, 8);
        profile.setTuition(newTuition);
        assertThat(profile.getTuition()).isEqualTo(newTuition);

        profile.setDeclarationType(DeclarationType.ENROLLED_EXEMPT);
        assertThat(profile.getDeclarationType()).isEqualTo(DeclarationType.ENROLLED_EXEMPT);

        profile.setRegistrationNumber(null);
        assertThat(profile.getRegistrationNumber()).isNull();

        Instant updated = Instant.now();
        profile.setUpdatedAt(updated);
        assertThat(profile.getUpdatedAt()).isEqualTo(updated);

        profile.setSubjectCodes(Set.of(SubjectCode.MATH));
        assertThat(profile.getSubjectCodes()).containsExactly(SubjectCode.MATH);
        profile.setSubjectCodes(null);
        assertThat(profile.getSubjectCodes()).isEmpty();

        profile.setGradeCodes(Set.of(GradeCode.HIGH_1));
        assertThat(profile.getGradeCodes()).containsExactly(GradeCode.HIGH_1);
        profile.setGradeCodes(null);
        assertThat(profile.getGradeCodes()).isEmpty();

        profile.setDongCodes(Set.of(DongCode.BEOMEO_1));
        assertThat(profile.getDongCodes()).containsExactly(DongCode.BEOMEO_1);
        profile.setDongCodes(null);
        assertThat(profile.getDongCodes()).isEmpty();
    }

    @Test
    @DisplayName("TuitionVo 및 TuitionDto 변환을 검증한다")
    void tuitionVoAndDto() {
        TuitionVo vo = new TuitionVo(50000, TuitionUnit.PER_SESSION, 60, null);
        assertThat(vo.getAmountWon()).isEqualTo(50000);
        assertThat(vo.getUnit()).isEqualTo(TuitionUnit.PER_SESSION);
        assertThat(vo.getSessionMinutes()).isEqualTo(60);
        assertThat(vo.getSessionsPerMonth()).isNull();

        TuitionDto dto = TuitionDto.from(vo);
        assertThat(dto).isNotNull();
        assertThat(dto.amountWon()).isEqualTo(50000);
        assertThat(dto.unit()).isEqualTo(TuitionUnit.PER_SESSION);
        assertThat(dto.sessionMinutes()).isEqualTo(60);
        assertThat(dto.sessionsPerMonth()).isNull();

        assertThat(TuitionDto.from(null)).isNull();

        UUID visId = UUID.randomUUID();
        Instant now = Instant.now();
        TutorProfileVisibilityResponse visRes =
                new TutorProfileVisibilityResponse(visId, ProfileVisibility.PUBLIC, now);
        assertThat(visRes.id()).isEqualTo(visId);
        assertThat(visRes.visibility()).isEqualTo(ProfileVisibility.PUBLIC);
        assertThat(visRes.updatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("TutorProfileSearchResponse 레코드를 검증한다")
    void searchResponseRecord() {
        TutorProfileSearchResponse res = new TutorProfileSearchResponse(java.util.List.of(), "cursor");
        assertThat(res.items()).isEmpty();
        assertThat(res.nextCursor()).isEqualTo("cursor");
    }
}

package kr.needteacher.tutorprofile;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TutorProfilePublicDetailResponse(
        UUID id,
        String displayName,
        Set<SubjectCode> subjectCodes,
        Set<GradeCode> gradeCodes,
        Set<DongCode> dongCodes,
        TuitionDto tuition,
        DeclarationType declarationType,
        String registrationNumber,
        String university,
        String department,
        String introduction,
        boolean universityEmailVerified,
        Instant updatedAt) {

    public static TutorProfilePublicDetailResponse from(TutorProfile entity) {
        return new TutorProfilePublicDetailResponse(
                entity.getId(),
                entity.getDisplayName(),
                new LinkedHashSet<>(entity.getSubjectCodes()),
                new LinkedHashSet<>(entity.getGradeCodes()),
                new LinkedHashSet<>(entity.getDongCodes()),
                TuitionDto.from(entity.getTuition()),
                entity.getDeclarationType(),
                entity.getRegistrationNumber(),
                entity.getUniversity(),
                entity.getDepartment(),
                entity.getIntroduction(),
                entity.isUniversityEmailVerified(),
                entity.getUpdatedAt());
    }
}

package kr.needteacher.tutorprofile;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TutorProfileSearchItemResponse(
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
        boolean universityEmailVerified,
        Instant updatedAt) {

    public static TutorProfileSearchItemResponse from(TutorProfile entity) {
        return new TutorProfileSearchItemResponse(
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
                entity.isUniversityEmailVerified(),
                entity.getUpdatedAt());
    }
}

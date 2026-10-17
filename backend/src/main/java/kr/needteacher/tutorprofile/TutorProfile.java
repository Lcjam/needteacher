package kr.needteacher.tutorprofile;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tutor_profiles")
public class TutorProfile {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, unique = true, length = 100)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private ProfileVisibility visibility;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "university", nullable = false, length = 100)
    private String university;

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @Column(name = "introduction", length = 2000)
    private String introduction;

    @Embedded
    private TuitionVo tuition;

    @Enumerated(EnumType.STRING)
    @Column(name = "declaration_type", nullable = false, length = 30)
    private DeclarationType declarationType;

    @Column(name = "registration_number", length = 50)
    private String registrationNumber;

    @Column(name = "university_email_verified", nullable = false)
    private boolean universityEmailVerified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @org.hibernate.annotations.BatchSize(size = 100)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tutor_profile_subjects", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "subject_code", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private Set<SubjectCode> subjectCodes = new LinkedHashSet<>();

    @org.hibernate.annotations.BatchSize(size = 100)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tutor_profile_grades", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "grade_code", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private Set<GradeCode> gradeCodes = new LinkedHashSet<>();

    @org.hibernate.annotations.BatchSize(size = 100)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tutor_profile_dongs", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "dong_code", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private Set<DongCode> dongCodes = new LinkedHashSet<>();

    protected TutorProfile() {}

    public TutorProfile(
            UUID id,
            String ownerId,
            ProfileVisibility visibility,
            String displayName,
            String university,
            String department,
            String introduction,
            TuitionVo tuition,
            DeclarationType declarationType,
            String registrationNumber,
            boolean universityEmailVerified,
            Instant createdAt,
            Instant updatedAt,
            Set<SubjectCode> subjectCodes,
            Set<GradeCode> gradeCodes,
            Set<DongCode> dongCodes) {
        this.id = id;
        this.ownerId = ownerId;
        this.visibility = visibility;
        this.displayName = displayName;
        this.university = university;
        this.department = department;
        this.introduction = introduction;
        this.tuition = tuition;
        this.declarationType = declarationType;
        this.registrationNumber = registrationNumber;
        this.universityEmailVerified = universityEmailVerified;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.subjectCodes = subjectCodes != null ? new LinkedHashSet<>(subjectCodes) : new LinkedHashSet<>();
        this.gradeCodes = gradeCodes != null ? new LinkedHashSet<>(gradeCodes) : new LinkedHashSet<>();
        this.dongCodes = dongCodes != null ? new LinkedHashSet<>(dongCodes) : new LinkedHashSet<>();
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public ProfileVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(ProfileVisibility visibility) {
        this.visibility = visibility;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getUniversity() {
        return university;
    }

    public void setUniversity(String university) {
        this.university = university;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getIntroduction() {
        return introduction;
    }

    public void setIntroduction(String introduction) {
        this.introduction = introduction;
    }

    public TuitionVo getTuition() {
        return tuition;
    }

    public void setTuition(TuitionVo tuition) {
        this.tuition = tuition;
    }

    public DeclarationType getDeclarationType() {
        return declarationType;
    }

    public void setDeclarationType(DeclarationType declarationType) {
        this.declarationType = declarationType;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public boolean isUniversityEmailVerified() {
        return universityEmailVerified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Set<SubjectCode> getSubjectCodes() {
        return subjectCodes;
    }

    public void setSubjectCodes(Set<SubjectCode> subjectCodes) {
        this.subjectCodes = subjectCodes != null ? new LinkedHashSet<>(subjectCodes) : new LinkedHashSet<>();
    }

    public Set<GradeCode> getGradeCodes() {
        return gradeCodes;
    }

    public void setGradeCodes(Set<GradeCode> gradeCodes) {
        this.gradeCodes = gradeCodes != null ? new LinkedHashSet<>(gradeCodes) : new LinkedHashSet<>();
    }

    public Set<DongCode> getDongCodes() {
        return dongCodes;
    }

    public void setDongCodes(Set<DongCode> dongCodes) {
        this.dongCodes = dongCodes != null ? new LinkedHashSet<>(dongCodes) : new LinkedHashSet<>();
    }
}

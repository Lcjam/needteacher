package kr.needteacher.tutorprofile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import kr.needteacher.common.ConflictException;
import kr.needteacher.common.ForbiddenException;
import kr.needteacher.common.InvalidQueryException;
import kr.needteacher.common.NotFoundException;
import kr.needteacher.common.ValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Service
@Transactional
public class TutorProfileService {

    private final TutorProfileRepository repository;
    private final TutorProfileValidator validator;
    private final CursorEncoder cursorEncoder;

    public TutorProfileService(
            TutorProfileRepository repository, TutorProfileValidator validator, CursorEncoder cursorEncoder) {
        this.repository = repository;
        this.validator = validator;
        this.cursorEncoder = cursorEncoder;
    }

    public TutorProfileOwnerResponse createProfile(String ownerId, JsonNode request) {
        if (repository.existsByOwnerId(ownerId)) {
            throw new ConflictException("PROFILE_ALREADY_EXISTS", "이미 프로필이 존재합니다.");
        }

        validator.validateCreate(request);

        UUID id = UUID.randomUUID();
        String displayName = request.path("displayName").asString();
        String university = request.path("university").asString();
        String department = request.path("department").asString();
        String introduction =
                (request.has("introduction") && !request.get("introduction").isNull())
                        ? request.get("introduction").asString()
                        : null;

        Set<SubjectCode> subjects = parseSubjectCodes(request.path("subjectCodes"));
        Set<GradeCode> grades = parseGradeCodes(request.path("gradeCodes"));
        Set<DongCode> dongs = parseDongCodes(request.path("dongCodes"));

        JsonNode tuitionNode = request.path("tuition");
        int amountWon = tuitionNode.path("amountWon").asInt();
        TuitionUnit unit = TuitionUnit.valueOf(tuitionNode.path("unit").asString());
        int sessionMinutes = tuitionNode.path("sessionMinutes").asInt();
        Integer sessionsPerMonth = (unit == TuitionUnit.PER_MONTH)
                ? tuitionNode.path("sessionsPerMonth").asInt()
                : null;
        TuitionVo tuition = new TuitionVo(amountWon, unit, sessionMinutes, sessionsPerMonth);

        DeclarationType declarationType =
                DeclarationType.valueOf(request.path("declarationType").asString());
        String registrationNumber = (declarationType == DeclarationType.REPORTED)
                ? request.path("registrationNumber").asString()
                : null;

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        TutorProfile profile = new TutorProfile(
                id,
                ownerId,
                ProfileVisibility.HIDDEN,
                displayName,
                university,
                department,
                introduction,
                tuition,
                declarationType,
                registrationNumber,
                false,
                now,
                now,
                subjects,
                grades,
                dongs);

        try {
            TutorProfile saved = repository.saveAndFlush(profile);
            return TutorProfileOwnerResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("PROFILE_ALREADY_EXISTS", "이미 프로필이 존재합니다.");
        }
    }

    @Transactional(readOnly = true)
    public TutorProfileOwnerResponse getMyProfile(String ownerId) {
        TutorProfile profile =
                repository.findByOwnerId(ownerId).orElseThrow(() -> new NotFoundException("프로필이 존재하지 않습니다."));
        return TutorProfileOwnerResponse.from(profile);
    }

    @Transactional(readOnly = true)
    public TutorProfilePublicDetailResponse getPublicProfile(UUID id) {
        TutorProfile profile = repository
                .findByIdAndVisibility(id, ProfileVisibility.PUBLIC)
                .orElseThrow(() -> new NotFoundException("프로필을 찾을 수 없습니다."));
        return TutorProfilePublicDetailResponse.from(profile);
    }

    public TutorProfileOwnerResponse patchProfile(String ownerId, UUID id, JsonNode patchNode) {
        TutorProfile profile = repository.findById(id).orElseThrow(() -> new NotFoundException("프로필을 찾을 수 없습니다."));

        if (!Objects.equals(profile.getOwnerId(), ownerId)) {
            throw new ForbiddenException("수정 권한이 없습니다.");
        }

        if (patchNode == null || !patchNode.isObject()) {
            throw new ValidationException("요청 본문이 올바르지 않습니다.", List.of());
        }

        ObjectNode merged = JsonNodeFactory.instance.objectNode();
        merged.put("displayName", profile.getDisplayName());
        merged.put("university", profile.getUniversity());
        merged.put("department", profile.getDepartment());
        if (profile.getIntroduction() != null) {
            merged.put("introduction", profile.getIntroduction());
        }

        ArrayNode subjectsNode = merged.putArray("subjectCodes");
        for (SubjectCode s : profile.getSubjectCodes()) {
            subjectsNode.add(s.name());
        }

        ArrayNode gradesNode = merged.putArray("gradeCodes");
        for (GradeCode g : profile.getGradeCodes()) {
            gradesNode.add(g.name());
        }

        ArrayNode dongsNode = merged.putArray("dongCodes");
        for (DongCode d : profile.getDongCodes()) {
            dongsNode.add(d.name());
        }

        ObjectNode tuitionNode = merged.putObject("tuition");
        TuitionVo curTuition = profile.getTuition();
        if (curTuition != null) {
            if (curTuition.getAmountWon() != null) {
                tuitionNode.put("amountWon", curTuition.getAmountWon());
            }
            if (curTuition.getUnit() != null) {
                tuitionNode.put("unit", curTuition.getUnit().name());
            }
            if (curTuition.getSessionMinutes() != null) {
                tuitionNode.put("sessionMinutes", curTuition.getSessionMinutes());
            }
            if (curTuition.getSessionsPerMonth() != null) {
                tuitionNode.put("sessionsPerMonth", curTuition.getSessionsPerMonth());
            }
        }

        if (profile.getDeclarationType() != null) {
            merged.put("declarationType", profile.getDeclarationType().name());
        }
        if (profile.getRegistrationNumber() != null) {
            merged.put("registrationNumber", profile.getRegistrationNumber());
        }

        if (patchNode.has("highSchool")) {
            merged.set("highSchool", patchNode.get("highSchool"));
        }
        if (patchNode.has("displayName")) {
            merged.set("displayName", patchNode.get("displayName"));
        }
        if (patchNode.has("university")) {
            merged.set("university", patchNode.get("university"));
        }
        if (patchNode.has("department")) {
            merged.set("department", patchNode.get("department"));
        }
        if (patchNode.has("introduction")) {
            JsonNode introNode = patchNode.get("introduction");
            if (introNode == null || introNode.isNull()) {
                merged.remove("introduction");
            } else {
                merged.set("introduction", introNode);
            }
        }
        if (patchNode.has("subjectCodes")) {
            merged.set("subjectCodes", patchNode.get("subjectCodes"));
        }
        if (patchNode.has("gradeCodes")) {
            merged.set("gradeCodes", patchNode.get("gradeCodes"));
        }
        if (patchNode.has("dongCodes")) {
            merged.set("dongCodes", patchNode.get("dongCodes"));
        }
        if (patchNode.has("tuition")) {
            JsonNode patchTuition = patchNode.get("tuition");
            if (patchTuition != null && patchTuition.isObject()) {
                if (patchTuition.has("amountWon")) {
                    tuitionNode.set("amountWon", patchTuition.get("amountWon"));
                }
                if (patchTuition.has("unit")) {
                    tuitionNode.set("unit", patchTuition.get("unit"));
                }
                if (patchTuition.has("sessionMinutes")) {
                    tuitionNode.set("sessionMinutes", patchTuition.get("sessionMinutes"));
                }
                if (patchTuition.has("sessionsPerMonth")) {
                    JsonNode spm = patchTuition.get("sessionsPerMonth");
                    if (spm == null || spm.isNull()) {
                        tuitionNode.remove("sessionsPerMonth");
                    } else {
                        tuitionNode.set("sessionsPerMonth", spm);
                    }
                }
            } else {
                merged.set("tuition", patchTuition);
            }
        }
        if (patchNode.has("declarationType")) {
            merged.set("declarationType", patchNode.get("declarationType"));
        }
        if (patchNode.has("registrationNumber")) {
            JsonNode regNode = patchNode.get("registrationNumber");
            if (regNode == null || regNode.isNull()) {
                merged.remove("registrationNumber");
            } else {
                merged.set("registrationNumber", regNode);
            }
        }

        validator.validateCreate(merged);

        String newDisplayName = merged.path("displayName").asString();
        String newUniversity = merged.path("university").asString();
        String newDepartment = merged.path("department").asString();
        String newIntroduction =
                (merged.has("introduction") && !merged.get("introduction").isNull())
                        ? merged.get("introduction").asString()
                        : null;
        Set<SubjectCode> newSubjects = parseSubjectCodes(merged.path("subjectCodes"));
        Set<GradeCode> newGrades = parseGradeCodes(merged.path("gradeCodes"));
        Set<DongCode> newDongs = parseDongCodes(merged.path("dongCodes"));

        JsonNode tNode = merged.path("tuition");
        int amountWon = tNode.path("amountWon").asInt();
        TuitionUnit unit = TuitionUnit.valueOf(tNode.path("unit").asString());
        int sessionMinutes = tNode.path("sessionMinutes").asInt();
        Integer sessionsPerMonth =
                (unit == TuitionUnit.PER_MONTH) ? tNode.path("sessionsPerMonth").asInt() : null;
        TuitionVo newTuition = new TuitionVo(amountWon, unit, sessionMinutes, sessionsPerMonth);

        DeclarationType newDeclarationType =
                DeclarationType.valueOf(merged.path("declarationType").asString());
        String newRegistrationNumber = (newDeclarationType == DeclarationType.REPORTED)
                ? merged.path("registrationNumber").asString()
                : null;

        boolean changed = !Objects.equals(profile.getDisplayName(), newDisplayName)
                || !Objects.equals(profile.getUniversity(), newUniversity)
                || !Objects.equals(profile.getDepartment(), newDepartment)
                || !Objects.equals(profile.getIntroduction(), newIntroduction)
                || !Objects.equals(profile.getSubjectCodes(), newSubjects)
                || !Objects.equals(profile.getGradeCodes(), newGrades)
                || !Objects.equals(profile.getDongCodes(), newDongs)
                || !Objects.equals(profile.getTuition(), newTuition)
                || !Objects.equals(profile.getDeclarationType(), newDeclarationType)
                || !Objects.equals(profile.getRegistrationNumber(), newRegistrationNumber);

        if (changed) {
            profile.setDisplayName(newDisplayName);
            profile.setUniversity(newUniversity);
            profile.setDepartment(newDepartment);
            profile.setIntroduction(newIntroduction);
            profile.setSubjectCodes(newSubjects);
            profile.setGradeCodes(newGrades);
            profile.setDongCodes(newDongs);
            profile.setTuition(newTuition);
            profile.setDeclarationType(newDeclarationType);
            profile.setRegistrationNumber(newRegistrationNumber);
            profile.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        }

        return TutorProfileOwnerResponse.from(profile);
    }

    public TutorProfileVisibilityResponse updateVisibility(String ownerId, UUID id, JsonNode request) {
        TutorProfile profile = repository.findById(id).orElseThrow(() -> new NotFoundException("프로필을 찾을 수 없습니다."));

        if (!Objects.equals(profile.getOwnerId(), ownerId)) {
            throw new ForbiddenException("수정 권한이 없습니다.");
        }

        if (request == null || !request.isObject() || !request.has("visibility")) {
            throw new ValidationException("visibility", "가시성 설정은 필수입니다.");
        }
        JsonNode visNode = request.get("visibility");
        if (visNode == null || !visNode.isString()) {
            throw new ValidationException("visibility", "올바르지 않은 가시성 값입니다.");
        }
        String visibilityStr = visNode.asString();

        ProfileVisibility newVisibility;
        if ("PUBLIC".equals(visibilityStr)) {
            newVisibility = ProfileVisibility.PUBLIC;
        } else if ("HIDDEN".equals(visibilityStr)) {
            newVisibility = ProfileVisibility.HIDDEN;
        } else {
            throw new ValidationException("visibility", "올바르지 않은 가시성 값입니다: " + visibilityStr);
        }

        if (profile.getVisibility() != newVisibility) {
            profile.setVisibility(newVisibility);
            profile.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        }

        return new TutorProfileVisibilityResponse(profile.getId(), profile.getVisibility(), profile.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public TutorProfileSearchResponse search(
            String dongCodesParam,
            String subjectCodesParam,
            String gradeCodesParam,
            Integer limitParam,
            String cursorParam) {
        int limit = (limitParam == null) ? 20 : limitParam;
        if (limit < 1 || limit > 50) {
            throw new InvalidQueryException("limit은 1 이상 50 이하이어야 합니다.");
        }

        Set<DongCode> dongs = parseQueryDongCodes(dongCodesParam);
        Set<SubjectCode> subjects = parseQuerySubjectCodes(subjectCodesParam);
        Set<GradeCode> grades = parseQueryGradeCodes(gradeCodesParam);

        CursorEncoder.Cursor cursor = cursorEncoder.decode(cursorParam);

        List<TutorProfile> results =
                repository.search(ProfileVisibility.PUBLIC, dongs, subjects, grades, cursor, limit + 1);

        String nextCursor = null;
        List<TutorProfile> pageItems;
        if (results.size() > limit) {
            pageItems = results.subList(0, limit);
            TutorProfile last = pageItems.get(limit - 1);
            nextCursor = cursorEncoder.encode(last.getUpdatedAt(), last.getId());
        } else {
            pageItems = results;
        }

        List<TutorProfileSearchItemResponse> items =
                pageItems.stream().map(TutorProfileSearchItemResponse::from).toList();

        return new TutorProfileSearchResponse(items, nextCursor);
    }

    private Set<SubjectCode> parseSubjectCodes(JsonNode node) {
        Set<SubjectCode> set = new LinkedHashSet<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                set.add(SubjectCode.valueOf(item.asString()));
            }
        }
        return set;
    }

    private Set<GradeCode> parseGradeCodes(JsonNode node) {
        Set<GradeCode> set = new LinkedHashSet<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                set.add(GradeCode.valueOf(item.asString()));
            }
        }
        return set;
    }

    private Set<DongCode> parseDongCodes(JsonNode node) {
        Set<DongCode> set = new LinkedHashSet<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                set.add(DongCode.valueOf(item.asString()));
            }
        }
        return set;
    }

    private Set<DongCode> parseQueryDongCodes(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }
        Set<DongCode> set = new LinkedHashSet<>();
        for (String part : param.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                set.add(DongCode.valueOf(trimmed));
            } catch (Exception e) {
                throw new InvalidQueryException("알 수 없는 동 코드입니다: " + trimmed);
            }
        }
        return set.isEmpty() ? null : set;
    }

    private Set<SubjectCode> parseQuerySubjectCodes(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }
        Set<SubjectCode> set = new LinkedHashSet<>();
        for (String part : param.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                set.add(SubjectCode.valueOf(trimmed));
            } catch (Exception e) {
                throw new InvalidQueryException("알 수 없는 과목 코드입니다: " + trimmed);
            }
        }
        return set.isEmpty() ? null : set;
    }

    private Set<GradeCode> parseQueryGradeCodes(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }
        Set<GradeCode> set = new LinkedHashSet<>();
        for (String part : param.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                set.add(GradeCode.valueOf(trimmed));
            } catch (Exception e) {
                throw new InvalidQueryException("알 수 없는 학년 코드입니다: " + trimmed);
            }
        }
        return set.isEmpty() ? null : set;
    }
}

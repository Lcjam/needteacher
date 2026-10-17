package kr.needteacher.tutorprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kr.needteacher.common.ConflictException;
import kr.needteacher.common.ForbiddenException;
import kr.needteacher.common.InvalidQueryException;
import kr.needteacher.common.NotFoundException;
import kr.needteacher.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class TutorProfileServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private TutorProfileRepository repository;
    private TutorProfileValidator validator;
    private CursorEncoder cursorEncoder;
    private TutorProfileService service;

    @BeforeEach
    void setUp() {
        repository = mock(TutorProfileRepository.class);
        validator = new TutorProfileValidator();
        cursorEncoder = new CursorEncoder();
        service = new TutorProfileService(repository, validator, cursorEncoder);
    }

    private ObjectNode sampleCreateJson() {
        ObjectNode node = JSON.createObjectNode();
        node.put("displayName", "선생님");
        node.put("university", "경북대");
        node.put("department", "수학과");
        node.put("introduction", "소개글");
        node.putArray("subjectCodes").add("MATH");
        node.putArray("gradeCodes").add("MIDDLE_1");
        node.putArray("dongCodes").add("BEOMEO_1");

        ObjectNode tuition = node.putObject("tuition");
        tuition.put("amountWon", 50000);
        tuition.put("unit", "PER_SESSION");
        tuition.put("sessionMinutes", 60);

        node.put("declarationType", "REPORTED");
        node.put("registrationNumber", "12345");
        return node;
    }

    private TutorProfile sampleEntity(String ownerId) {
        Instant past = Instant.parse("2020-01-01T00:00:00Z");
        return new TutorProfile(
                UUID.randomUUID(),
                ownerId,
                ProfileVisibility.HIDDEN,
                "선생님",
                "경북대",
                "수학과",
                "소개글",
                new TuitionVo(50000, TuitionUnit.PER_SESSION, 60, null),
                DeclarationType.REPORTED,
                "12345",
                false,
                past,
                past,
                Set.of(SubjectCode.MATH),
                Set.of(GradeCode.MIDDLE_1),
                Set.of(DongCode.BEOMEO_1));
    }

    @Test
    @DisplayName("createProfile: 이미 프로필이 존재하면 ConflictException이 발생한다")
    void createProfileAlreadyExists() {
        given(repository.existsByOwnerId("teacher1")).willReturn(true);
        assertThatThrownBy(() -> service.createProfile("teacher1", sampleCreateJson()))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((ConflictException) e).getCode()).isEqualTo("PROFILE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("createProfile: 동시 생성 시 UNIQUE 제약조건 위반이 발생하면 409 ConflictException으로 변환한다")
    void createProfileConcurrentConflict() {
        given(repository.existsByOwnerId("teacher1")).willReturn(false);
        given(repository.saveAndFlush(any(TutorProfile.class)))
                .willThrow(new DataIntegrityViolationException("unique constraint violated"));

        assertThatThrownBy(() -> service.createProfile("teacher1", sampleCreateJson()))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((ConflictException) e).getCode()).isEqualTo("PROFILE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("createProfile: 유효하지 않은 요청이면 ValidationException이 발생한다")
    void createProfileValidationFailure() {
        ObjectNode node = sampleCreateJson();
        node.put("displayName", "");
        assertThatThrownBy(() -> service.createProfile("teacher1", node)).isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("createProfile: 일반 회당 과외비 프로필 생성 시 모든 필드가 정확하게 저장된다")
    void createProfileNormalSuccess() {
        ObjectNode node = sampleCreateJson();
        given(repository.existsByOwnerId("teacher1")).willReturn(false);
        given(repository.saveAndFlush(any(TutorProfile.class))).willAnswer(inv -> inv.getArgument(0));

        TutorProfileOwnerResponse res = service.createProfile("teacher1", node);
        assertThat(res).isNotNull();
        assertThat(res.displayName()).isEqualTo("선생님");
        assertThat(res.university()).isEqualTo("경북대");
        assertThat(res.department()).isEqualTo("수학과");
        assertThat(res.introduction()).isEqualTo("소개글");
        assertThat(res.subjectCodes()).containsExactly(SubjectCode.MATH);
        assertThat(res.gradeCodes()).containsExactly(GradeCode.MIDDLE_1);
        assertThat(res.dongCodes()).containsExactly(DongCode.BEOMEO_1);
        assertThat(res.tuition().amountWon()).isEqualTo(50000);
        assertThat(res.tuition().unit()).isEqualTo(TuitionUnit.PER_SESSION);
        assertThat(res.tuition().sessionMinutes()).isEqualTo(60);
        assertThat(res.tuition().sessionsPerMonth()).isNull();
        assertThat(res.declarationType()).isEqualTo(DeclarationType.REPORTED);
        assertThat(res.registrationNumber()).isEqualTo("12345");
        assertThat(res.visibility()).isEqualTo(ProfileVisibility.HIDDEN);
    }

    @Test
    @DisplayName("createProfile: 월별 과외비 및 재학생 면제 프로필 생성이 성공한다")
    void createProfileMonthlySuccess() {
        ObjectNode node = sampleCreateJson();
        ObjectNode tuition = (ObjectNode) node.path("tuition");
        tuition.put("unit", "PER_MONTH");
        tuition.put("sessionsPerMonth", 4);
        node.put("declarationType", "ENROLLED_EXEMPT");
        node.remove("registrationNumber");

        given(repository.existsByOwnerId("teacher1")).willReturn(false);
        given(repository.saveAndFlush(any(TutorProfile.class))).willAnswer(inv -> inv.getArgument(0));

        TutorProfileOwnerResponse res = service.createProfile("teacher1", node);
        assertThat(res).isNotNull();
        assertThat(res.tuition().unit()).isEqualTo(TuitionUnit.PER_MONTH);
        assertThat(res.tuition().sessionsPerMonth()).isEqualTo(4);
        assertThat(res.declarationType()).isEqualTo(DeclarationType.ENROLLED_EXEMPT);
        assertThat(res.registrationNumber()).isNull();
    }

    @Test
    @DisplayName("getMyProfile: 프로필이 없으면 NotFoundException, 있으면 DTO 반환")
    void getMyProfile() {
        given(repository.findByOwnerId("teacher1")).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getMyProfile("teacher1")).isInstanceOf(NotFoundException.class);

        TutorProfile profile = sampleEntity("teacher1");
        given(repository.findByOwnerId("teacher1")).willReturn(Optional.of(profile));
        TutorProfileOwnerResponse res = service.getMyProfile("teacher1");
        assertThat(res.displayName()).isEqualTo("선생님");
    }

    @Test
    @DisplayName("getPublicProfile: 공개 프로필이 없으면 NotFoundException, 있으면 DTO 반환")
    void getPublicProfile() {
        UUID id = UUID.randomUUID();
        given(repository.findByIdAndVisibility(id, ProfileVisibility.PUBLIC)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPublicProfile(id)).isInstanceOf(NotFoundException.class);

        TutorProfile profile = sampleEntity("teacher1");
        profile.setVisibility(ProfileVisibility.PUBLIC);
        given(repository.findByIdAndVisibility(id, ProfileVisibility.PUBLIC)).willReturn(Optional.of(profile));

        TutorProfilePublicDetailResponse res = service.getPublicProfile(id);
        assertThat(res.displayName()).isEqualTo("선생님");
    }

    @Test
    @DisplayName("patchProfile: 본문이 객체가 아니면 ValidationException을 던진다")
    void patchProfileNonObjectBody() {
        UUID id = UUID.randomUUID();
        TutorProfile profile = sampleEntity("teacher1");
        given(repository.findById(id)).willReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.patchProfile("teacher1", id, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.patchProfile("teacher1", id, JSON.createArrayNode()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("patchProfile: 대상 없음, 권한 없음, 변경 없는 요청 시 updatedAt 유지")
    void patchProfileBasicTests() {
        UUID id = UUID.randomUUID();
        given(repository.findById(id)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.patchProfile("teacher1", id, JSON.createObjectNode()))
                .isInstanceOf(NotFoundException.class);

        TutorProfile profile = sampleEntity("teacher1");
        Instant originalUpdatedAt = profile.getUpdatedAt();
        given(repository.findById(id)).willReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.patchProfile("otherTeacher", id, JSON.createObjectNode()))
                .isInstanceOf(ForbiddenException.class);

        // 변경 없는 빈 객체 전송 시 updatedAt 유지
        TutorProfileOwnerResponse emptyRes = service.patchProfile("teacher1", id, JSON.createObjectNode());
        assertThat(emptyRes.displayName()).isEqualTo("선생님");
        assertThat(emptyRes.updatedAt()).isEqualTo(originalUpdatedAt);

        // 동일한 값 전송 시 updatedAt 유지
        ObjectNode samePatch = JSON.createObjectNode();
        samePatch.put("displayName", profile.getDisplayName());
        TutorProfileOwnerResponse sameRes = service.patchProfile("teacher1", id, samePatch);
        assertThat(sameRes.displayName()).isEqualTo("선생님");
        assertThat(sameRes.updatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    @DisplayName("patchProfile: 필드 부분 변경 및 updatedAt 갱신")
    void patchProfileSuccess() {
        UUID id = UUID.randomUUID();
        TutorProfile profile = sampleEntity("teacher1");
        Instant originalUpdatedAt = profile.getUpdatedAt();
        given(repository.findById(id)).willReturn(Optional.of(profile));

        ObjectNode patch = JSON.createObjectNode();
        patch.put("displayName", "새이름");
        patch.put("university", "새대학");
        patch.put("department", "새학과");
        patch.putNull("introduction");
        patch.putArray("subjectCodes").add("ENGLISH");
        patch.putArray("gradeCodes").add("HIGH_1");
        patch.putArray("dongCodes").add("MANCHON_1");

        ObjectNode t = patch.putObject("tuition");
        t.put("amountWon", 60000);
        t.put("unit", "PER_MONTH");
        t.put("sessionMinutes", 90);
        t.put("sessionsPerMonth", 8);

        patch.put("declarationType", "REPORTED");
        patch.put("registrationNumber", "99999");

        TutorProfileOwnerResponse patched = service.patchProfile("teacher1", id, patch);
        assertThat(patched.displayName()).isEqualTo("새이름");
        assertThat(patched.university()).isEqualTo("새대학");
        assertThat(patched.department()).isEqualTo("새학과");
        assertThat(patched.introduction()).isNull();
        assertThat(patched.subjectCodes()).containsExactly(SubjectCode.ENGLISH);
        assertThat(patched.gradeCodes()).containsExactly(GradeCode.HIGH_1);
        assertThat(patched.dongCodes()).containsExactly(DongCode.MANCHON_1);
        assertThat(patched.tuition().amountWon()).isEqualTo(60000);
        assertThat(patched.tuition().unit()).isEqualTo(TuitionUnit.PER_MONTH);
        assertThat(patched.tuition().sessionMinutes()).isEqualTo(90);
        assertThat(patched.tuition().sessionsPerMonth()).isEqualTo(8);
        assertThat(patched.declarationType()).isEqualTo(DeclarationType.REPORTED);
        assertThat(patched.registrationNumber()).isEqualTo("99999");
        assertThat(patched.updatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    @DisplayName("patchProfile: 타입 오류 시 생성 검증에 의해 400 ValidationException이 발생한다")
    void patchProfileTypeErrors() {
        UUID id = UUID.randomUUID();
        TutorProfile profile = sampleEntity("teacher1");
        given(repository.findById(id)).willReturn(Optional.of(profile));

        // displayName에 숫자
        ObjectNode p1 = JSON.createObjectNode();
        p1.put("displayName", 123);
        assertThatThrownBy(() -> service.patchProfile("teacher1", id, p1)).isInstanceOf(ValidationException.class);

        // subjectCodes에 객체
        ObjectNode p2 = JSON.createObjectNode();
        p2.putArray("subjectCodes").addObject();
        assertThatThrownBy(() -> service.patchProfile("teacher1", id, p2)).isInstanceOf(ValidationException.class);

        // tuition.amountWon에 소수점
        ObjectNode p3 = JSON.createObjectNode();
        p3.putObject("tuition").put("amountWon", 1.5);
        assertThatThrownBy(() -> service.patchProfile("teacher1", id, p3)).isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("patchProfile: 개별 필드 단독 변경 및 ENROLLED_EXEMPT 전환")
    void patchProfileIndividualFields() {
        UUID id = UUID.randomUUID();
        TutorProfile profile = sampleEntity("teacher1");
        given(repository.findById(id)).willReturn(Optional.of(profile));

        // university만
        ObjectNode p1 = JSON.createObjectNode();
        p1.put("university", "서울대학교");
        assertThat(service.patchProfile("teacher1", id, p1).university()).isEqualTo("서울대학교");

        // department만
        ObjectNode p2 = JSON.createObjectNode();
        p2.put("department", "컴퓨터공학과");
        assertThat(service.patchProfile("teacher1", id, p2).department()).isEqualTo("컴퓨터공학과");

        // introduction 문자열
        ObjectNode p3 = JSON.createObjectNode();
        p3.put("introduction", "새 소개글");
        assertThat(service.patchProfile("teacher1", id, p3).introduction()).isEqualTo("새 소개글");

        // declarationType만 ENROLLED_EXEMPT로 보내면 기존 registrationNumber 때문에 ValidationException
        ObjectNode p4Invalid = JSON.createObjectNode();
        p4Invalid.put("declarationType", "ENROLLED_EXEMPT");
        assertThatThrownBy(() -> service.patchProfile("teacher1", id, p4Invalid))
                .isInstanceOf(ValidationException.class);

        // declarationType -> ENROLLED_EXEMPT 와 registrationNumber -> null 함께 전달 시 성공
        ObjectNode p4 = JSON.createObjectNode();
        p4.put("declarationType", "ENROLLED_EXEMPT");
        p4.putNull("registrationNumber");
        TutorProfileOwnerResponse res4 = service.patchProfile("teacher1", id, p4);
        assertThat(res4.declarationType()).isEqualTo(DeclarationType.ENROLLED_EXEMPT);
        assertThat(res4.registrationNumber()).isNull();

        // tuition 내부 필드 부분 병합 (amountWon만 변경)
        ObjectNode pAmountOnly = JSON.createObjectNode();
        pAmountOnly.putObject("tuition").put("amountWon", 80000);
        TutorProfileOwnerResponse resAmount = service.patchProfile("teacher1", id, pAmountOnly);
        assertThat(resAmount.tuition().amountWon()).isEqualTo(80000);
        assertThat(resAmount.tuition().unit()).isEqualTo(profile.getTuition().getUnit());
        assertThat(resAmount.tuition().sessionMinutes())
                .isEqualTo(profile.getTuition().getSessionMinutes());

        // tuition 내부 필드 부분 병합 (unit과 sessionsPerMonth 변경)
        ObjectNode pMonthly = JSON.createObjectNode();
        ObjectNode tMonthly = pMonthly.putObject("tuition");
        tMonthly.put("unit", "PER_MONTH");
        tMonthly.put("sessionsPerMonth", 12);
        TutorProfileOwnerResponse resMonthly = service.patchProfile("teacher1", id, pMonthly);
        assertThat(resMonthly.tuition().unit()).isEqualTo(TuitionUnit.PER_MONTH);
        assertThat(resMonthly.tuition().sessionsPerMonth()).isEqualTo(12);

        // tuition.sessionsPerMonth를 null로 제거
        ObjectNode pPerSession = JSON.createObjectNode();
        ObjectNode tPerSession = pPerSession.putObject("tuition");
        tPerSession.put("unit", "PER_SESSION");
        tPerSession.putNull("sessionsPerMonth");
        TutorProfileOwnerResponse resPerSession = service.patchProfile("teacher1", id, pPerSession);
        assertThat(resPerSession.tuition().unit()).isEqualTo(TuitionUnit.PER_SESSION);
        assertThat(resPerSession.tuition().sessionsPerMonth()).isNull();

        // tuition이 객체가 아닌 경우
        ObjectNode pBadTuition = JSON.createObjectNode();
        pBadTuition.put("tuition", "bad");
        assertThatThrownBy(() -> service.patchProfile("teacher1", id, pBadTuition))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("updateVisibility: 대상 없음, 권한 없음, 올바르지 않은 값, 정상 갱신 및 동일 값 시 updatedAt 유지")
    void updateVisibilityTests() {
        UUID id = UUID.randomUUID();
        given(repository.findById(id)).willReturn(Optional.empty());
        ObjectNode req = JSON.createObjectNode();
        req.put("visibility", "PUBLIC");
        assertThatThrownBy(() -> service.updateVisibility("teacher1", id, req)).isInstanceOf(NotFoundException.class);

        TutorProfile profile = sampleEntity("teacher1"); // 기본 HIDDEN
        Instant originalUpdatedAt = profile.getUpdatedAt();
        given(repository.findById(id)).willReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.updateVisibility("other", id, req)).isInstanceOf(ForbiddenException.class);

        // 본문이 null이거나 객체가 아니거나 visibility가 누락/비문자열/알 수 없는 값일 때
        assertThatThrownBy(() -> service.updateVisibility("teacher1", id, null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.updateVisibility("teacher1", id, JSON.createArrayNode()))
                .isInstanceOf(ValidationException.class);

        ObjectNode emptyReq = JSON.createObjectNode();
        assertThatThrownBy(() -> service.updateVisibility("teacher1", id, emptyReq))
                .isInstanceOf(ValidationException.class);

        ObjectNode nonStringReq = JSON.createObjectNode();
        nonStringReq.set("visibility", JSON.createObjectNode());
        assertThatThrownBy(() -> service.updateVisibility("teacher1", id, nonStringReq))
                .isInstanceOf(ValidationException.class);

        ObjectNode invalidValReq = JSON.createObjectNode();
        invalidValReq.put("visibility", "ARCHIVED");
        assertThatThrownBy(() -> service.updateVisibility("teacher1", id, invalidValReq))
                .isInstanceOf(ValidationException.class);

        // 동일한 값(HIDDEN -> HIDDEN) 요청 시 updatedAt 유지
        ObjectNode sameReq = JSON.createObjectNode();
        sameReq.put("visibility", "HIDDEN");
        TutorProfileVisibilityResponse sameRes = service.updateVisibility("teacher1", id, sameReq);
        assertThat(sameRes.visibility()).isEqualTo(ProfileVisibility.HIDDEN);
        assertThat(sameRes.updatedAt()).isEqualTo(originalUpdatedAt);

        // 변경 요청(HIDDEN -> PUBLIC) 시 updatedAt 갱신
        ObjectNode publicReq = JSON.createObjectNode();
        publicReq.put("visibility", "PUBLIC");
        TutorProfileVisibilityResponse publicRes = service.updateVisibility("teacher1", id, publicReq);
        assertThat(publicRes.visibility()).isEqualTo(ProfileVisibility.PUBLIC);
        assertThat(publicRes.updatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    @DisplayName("search: limit 범위 검증 및 쿼리 파라미터 파싱 처리")
    void searchValidation() {
        assertThatThrownBy(() -> service.search(null, null, null, 0, null)).isInstanceOf(InvalidQueryException.class);
        assertThatThrownBy(() -> service.search(null, null, null, 51, null)).isInstanceOf(InvalidQueryException.class);

        assertThatThrownBy(() -> service.search("INVALID_DONG", null, null, 20, null))
                .isInstanceOf(InvalidQueryException.class);
        assertThatThrownBy(() -> service.search(null, "INVALID_SUBJECT", null, 20, null))
                .isInstanceOf(InvalidQueryException.class);
        assertThatThrownBy(() -> service.search(null, null, "INVALID_GRADE", 20, null))
                .isInstanceOf(InvalidQueryException.class);

        // limit 경계값 1, 50 통과
        given(repository.search(eq(ProfileVisibility.PUBLIC), any(), any(), any(), any(), any(Integer.class)))
                .willReturn(List.of());
        assertThatCode(() -> service.search(null, null, null, 1, null)).doesNotThrowAnyException();
        assertThatCode(() -> service.search(null, null, null, 50, null)).doesNotThrowAnyException();

        // 빈 문자열 및 콤마 구분 다중 코드 파싱 통과
        assertThatCode(() -> service.search("", "  ", null, 10, null)).doesNotThrowAnyException();
        assertThatCode(() -> service.search(", ,", ", ,", ", ,", 10, null)).doesNotThrowAnyException();
        assertThatCode(() -> service.search("BEOMEO_1, ,BEOMEO_2", "MATH, ,ENGLISH", "MIDDLE_1, ,HIGH_1", 10, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("search: 페이징 및 커서 생성 분기 커버")
    void searchPagination() {
        List<TutorProfile> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            list.add(sampleEntity("teacher" + i));
        }

        given(repository.search(eq(ProfileVisibility.PUBLIC), any(), any(), any(), any(), eq(3)))
                .willReturn(list);

        // limit = 2 (results.size() > limit 분기)
        TutorProfileSearchResponse res = service.search("BEOMEO_1", "MATH", "MIDDLE_1", 2, null);
        assertThat(res.items()).hasSize(2);
        assertThat(res.nextCursor()).isNotNull();

        // limit = 5 (results.size() <= limit 분기)
        given(repository.search(eq(ProfileVisibility.PUBLIC), any(), any(), any(), any(), eq(6)))
                .willReturn(list);
        TutorProfileSearchResponse res2 = service.search(null, null, null, 5, null);
        assertThat(res2.items()).hasSize(3);
        assertThat(res2.nextCursor()).isNull();
    }

    @Test
    @DisplayName("search: 쿼리 파라미터가 비어있을 때 null, 존재할 때 정확한 Set으로 repository에 전달된다")
    @SuppressWarnings("unchecked")
    void searchQueryParametersPassedCorrectly() {
        given(repository.search(eq(ProfileVisibility.PUBLIC), any(), any(), any(), any(), any(Integer.class)))
                .willReturn(List.of());

        // null과 빈 파라미터는 null로 전달
        service.search(", ,", ", ,", ", ,", 10, null);
        org.mockito.ArgumentCaptor<Set<DongCode>> dongCaptor = org.mockito.ArgumentCaptor.forClass(Set.class);
        org.mockito.ArgumentCaptor<Set<SubjectCode>> subCaptor = org.mockito.ArgumentCaptor.forClass(Set.class);
        org.mockito.ArgumentCaptor<Set<GradeCode>> gradeCaptor = org.mockito.ArgumentCaptor.forClass(Set.class);

        org.mockito.Mockito.verify(repository, org.mockito.Mockito.atLeastOnce())
                .search(
                        eq(ProfileVisibility.PUBLIC),
                        dongCaptor.capture(),
                        subCaptor.capture(),
                        gradeCaptor.capture(),
                        any(),
                        eq(11));

        assertThat(dongCaptor.getValue()).isNull();
        assertThat(subCaptor.getValue()).isNull();
        assertThat(gradeCaptor.getValue()).isNull();

        // 실제 값이 주어졌을 때
        service.search("BEOMEO_1", "MATH", "MIDDLE_1", 10, null);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.atLeastOnce())
                .search(
                        eq(ProfileVisibility.PUBLIC),
                        dongCaptor.capture(),
                        subCaptor.capture(),
                        gradeCaptor.capture(),
                        any(),
                        eq(11));

        assertThat(dongCaptor.getValue()).containsExactly(DongCode.BEOMEO_1);
        assertThat(subCaptor.getValue()).containsExactly(SubjectCode.MATH);
        assertThat(gradeCaptor.getValue()).containsExactly(GradeCode.MIDDLE_1);
    }

    @Test
    @DisplayName("createProfile: introduction 누락 또는 null일 때 정상 처리")
    void createProfileIntroductionNull() {
        given(repository.existsByOwnerId("teacher1")).willReturn(false);
        given(repository.saveAndFlush(any(TutorProfile.class))).willAnswer(inv -> inv.getArgument(0));

        // introduction 누락
        ObjectNode n1 = sampleCreateJson();
        n1.remove("introduction");
        assertThat(service.createProfile("teacher1", n1).introduction()).isNull();

        // introduction null
        ObjectNode n2 = sampleCreateJson();
        n2.putNull("introduction");
        assertThat(service.createProfile("teacher1", n2).introduction()).isNull();
    }
}

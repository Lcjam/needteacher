package kr.needteacher.tutorprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class TutorProfileControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private TutorProfileService service;
    private TutorProfileController controller;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        service = mock(TutorProfileService.class);
        controller = new TutorProfileController(service);
        authentication = mock(Authentication.class);
        given(authentication.getName()).willReturn("teacher1");
    }

    private TutorProfileOwnerResponse sampleOwnerResponse(UUID id) {
        return new TutorProfileOwnerResponse(
                id,
                ProfileVisibility.HIDDEN,
                "선생님",
                Set.of(SubjectCode.MATH),
                Set.of(GradeCode.MIDDLE_1),
                Set.of(DongCode.BEOMEO_1),
                new TuitionDto(50000, TuitionUnit.PER_SESSION, 60, null),
                DeclarationType.REPORTED,
                "12345",
                "경북대",
                "수학과",
                "소개",
                false,
                Instant.now(),
                Instant.now());
    }

    @Test
    @DisplayName("createProfile: 201 Created와 Location 헤더를 반환한다")
    void createProfile() {
        UUID id = UUID.randomUUID();
        ObjectNode req = JSON.createObjectNode();
        given(service.createProfile("teacher1", req)).willReturn(sampleOwnerResponse(id));

        ResponseEntity<TutorProfileOwnerResponse> response = controller.createProfile(authentication, req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/api/v1/tutor-profiles/" + id));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(id);
        assertThat(response.getBody().displayName()).isEqualTo("선생님");
    }

    @Test
    @DisplayName("getMyProfile: 200 OK와 내 프로필을 반환한다")
    void getMyProfile() {
        UUID id = UUID.randomUUID();
        given(service.getMyProfile("teacher1")).willReturn(sampleOwnerResponse(id));

        ResponseEntity<TutorProfileOwnerResponse> response = controller.getMyProfile(authentication);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(id);
        assertThat(response.getBody().displayName()).isEqualTo("선생님");
    }

    @Test
    @DisplayName("getPublicProfile: 200 OK와 공개 상세를 반환한다")
    void getPublicProfile() {
        UUID id = UUID.randomUUID();
        TutorProfilePublicDetailResponse publicDetail = new TutorProfilePublicDetailResponse(
                id,
                "선생님",
                Set.of(SubjectCode.MATH),
                Set.of(GradeCode.MIDDLE_1),
                Set.of(DongCode.BEOMEO_1),
                new TuitionDto(50000, TuitionUnit.PER_SESSION, 60, null),
                DeclarationType.REPORTED,
                "12345",
                "경북대",
                "수학과",
                "소개",
                false,
                Instant.now());
        given(service.getPublicProfile(id)).willReturn(publicDetail);

        ResponseEntity<TutorProfilePublicDetailResponse> response = controller.getPublicProfile(id);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(id);
        assertThat(response.getBody().displayName()).isEqualTo("선생님");
    }

    @Test
    @DisplayName("patchProfile: 200 OK와 수정된 프로필을 반환한다")
    void patchProfile() {
        UUID id = UUID.randomUUID();
        ObjectNode patch = JSON.createObjectNode();
        given(service.patchProfile("teacher1", id, patch)).willReturn(sampleOwnerResponse(id));

        ResponseEntity<TutorProfileOwnerResponse> response = controller.patchProfile(authentication, id, patch);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(id);
        assertThat(response.getBody().displayName()).isEqualTo("선생님");
    }

    @Test
    @DisplayName("updateVisibility: 200 OK와 변경된 가시성 응답을 반환한다")
    void updateVisibility() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        ObjectNode req = JSON.createObjectNode();
        req.put("visibility", "PUBLIC");
        TutorProfileVisibilityResponse visResponse =
                new TutorProfileVisibilityResponse(id, ProfileVisibility.PUBLIC, now);
        given(service.updateVisibility("teacher1", id, req)).willReturn(visResponse);

        ResponseEntity<TutorProfileVisibilityResponse> response = controller.updateVisibility(authentication, id, req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(id);
        assertThat(response.getBody().visibility()).isEqualTo(ProfileVisibility.PUBLIC);
        assertThat(response.getBody().updatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("searchProfiles: 200 OK와 검색 결과를 반환한다")
    void searchProfiles() {
        TutorProfileSearchResponse searchRes = new TutorProfileSearchResponse(List.of(), null);
        given(service.search("BEOMEO_1", "MATH", "MIDDLE_1", 10, "cursor")).willReturn(searchRes);

        ResponseEntity<TutorProfileSearchResponse> response =
                controller.searchProfiles("BEOMEO_1", "MATH", "MIDDLE_1", 10, "cursor");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().items()).isEmpty();
        assertThat(response.getBody().nextCursor()).isNull();
    }
}

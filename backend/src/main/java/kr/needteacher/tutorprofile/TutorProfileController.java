package kr.needteacher.tutorprofile;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class TutorProfileController {

    private final TutorProfileService service;

    public TutorProfileController(TutorProfileService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/tutor-profiles")
    public ResponseEntity<TutorProfileOwnerResponse> createProfile(
            Authentication authentication, @RequestBody JsonNode request) {
        String ownerId = authentication.getName();
        TutorProfileOwnerResponse response = service.createProfile(ownerId, request);
        URI location = URI.create("/api/v1/tutor-profiles/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/api/v1/me/tutor-profile")
    public ResponseEntity<TutorProfileOwnerResponse> getMyProfile(Authentication authentication) {
        String ownerId = authentication.getName();
        return ResponseEntity.ok(service.getMyProfile(ownerId));
    }

    @GetMapping("/api/v1/tutor-profiles/{id}")
    public ResponseEntity<TutorProfilePublicDetailResponse> getPublicProfile(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(service.getPublicProfile(id));
    }

    @PatchMapping("/api/v1/tutor-profiles/{id}")
    public ResponseEntity<TutorProfileOwnerResponse> patchProfile(
            Authentication authentication,
            @PathVariable("id") UUID id,
            @RequestBody(required = false) JsonNode patchNode) {
        String ownerId = authentication.getName();
        return ResponseEntity.ok(service.patchProfile(ownerId, id, patchNode));
    }

    @PatchMapping("/api/v1/tutor-profiles/{id}/visibility")
    public ResponseEntity<TutorProfileVisibilityResponse> updateVisibility(
            Authentication authentication, @PathVariable("id") UUID id, @RequestBody JsonNode request) {
        String ownerId = authentication.getName();
        return ResponseEntity.ok(service.updateVisibility(ownerId, id, request));
    }

    @GetMapping("/api/v1/tutor-profiles")
    public ResponseEntity<TutorProfileSearchResponse> searchProfiles(
            @RequestParam(name = "dongCodes", required = false) String dongCodes,
            @RequestParam(name = "subjectCodes", required = false) String subjectCodes,
            @RequestParam(name = "gradeCodes", required = false) String gradeCodes,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "cursor", required = false) String cursor) {
        return ResponseEntity.ok(service.search(dongCodes, subjectCodes, gradeCodes, limit, cursor));
    }
}

package kr.needteacher.tutorprofile;

import java.time.Instant;
import java.util.UUID;

public record TutorProfileVisibilityResponse(UUID id, ProfileVisibility visibility, Instant updatedAt) {}

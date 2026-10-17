package kr.needteacher.tutorprofile;

import java.util.List;

public record TutorProfileSearchResponse(List<TutorProfileSearchItemResponse> items, String nextCursor) {}

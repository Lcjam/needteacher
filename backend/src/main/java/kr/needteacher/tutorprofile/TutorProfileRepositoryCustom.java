package kr.needteacher.tutorprofile;

import java.util.List;
import java.util.Set;

public interface TutorProfileRepositoryCustom {

    List<TutorProfile> search(
            ProfileVisibility visibility,
            Set<DongCode> dongCodes,
            Set<SubjectCode> subjectCodes,
            Set<GradeCode> gradeCodes,
            CursorEncoder.Cursor cursor,
            int limit);
}

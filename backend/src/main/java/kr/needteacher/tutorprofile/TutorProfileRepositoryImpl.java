package kr.needteacher.tutorprofile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class TutorProfileRepositoryImpl implements TutorProfileRepositoryCustom {

    private final EntityManager em;

    public TutorProfileRepositoryImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<TutorProfile> search(
            ProfileVisibility visibility,
            Set<DongCode> dongCodes,
            Set<SubjectCode> subjectCodes,
            Set<GradeCode> gradeCodes,
            CursorEncoder.Cursor cursor,
            int limit) {
        StringBuilder jpql = new StringBuilder("SELECT p FROM TutorProfile p WHERE p.visibility = :visibility");
        List<String> conditions = new ArrayList<>();

        if (dongCodes != null && !dongCodes.isEmpty()) {
            conditions.add("EXISTS (SELECT 1 FROM p.dongCodes d WHERE d IN :dongCodes)");
        }
        if (subjectCodes != null && !subjectCodes.isEmpty()) {
            conditions.add("EXISTS (SELECT 1 FROM p.subjectCodes s WHERE s IN :subjectCodes)");
        }
        if (gradeCodes != null && !gradeCodes.isEmpty()) {
            conditions.add("EXISTS (SELECT 1 FROM p.gradeCodes g WHERE g IN :gradeCodes)");
        }
        if (cursor != null) {
            conditions.add("(p.updatedAt < :cursorUpdatedAt OR (p.updatedAt = :cursorUpdatedAt AND p.id < :cursorId))");
        }

        for (String cond : conditions) {
            jpql.append(" AND ").append(cond);
        }

        jpql.append(" ORDER BY p.updatedAt DESC, p.id DESC");

        TypedQuery<TutorProfile> query = em.createQuery(jpql.toString(), TutorProfile.class);
        query.setParameter("visibility", visibility);

        if (dongCodes != null && !dongCodes.isEmpty()) {
            query.setParameter("dongCodes", dongCodes);
        }
        if (subjectCodes != null && !subjectCodes.isEmpty()) {
            query.setParameter("subjectCodes", subjectCodes);
        }
        if (gradeCodes != null && !gradeCodes.isEmpty()) {
            query.setParameter("gradeCodes", gradeCodes);
        }
        if (cursor != null) {
            query.setParameter("cursorUpdatedAt", cursor.updatedAt());
            query.setParameter("cursorId", cursor.id());
        }

        query.setMaxResults(limit);
        return query.getResultList();
    }
}

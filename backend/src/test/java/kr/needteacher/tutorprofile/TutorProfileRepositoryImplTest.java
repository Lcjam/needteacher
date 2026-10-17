package kr.needteacher.tutorprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TutorProfileRepositoryImplTest {

    private EntityManager em;
    private TutorProfileRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        em = mock(EntityManager.class);
        repository = new TutorProfileRepositoryImpl(em);
    }

    @Test
    @DisplayName("search: 모든 필터와 커서가 제공되었을 때 올바른 JPQL과 파라미터를 설정한다")
    @SuppressWarnings("unchecked")
    void searchWithAllFiltersAndCursor() {
        TypedQuery<TutorProfile> query = mock(TypedQuery.class);
        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
        given(em.createQuery(jpqlCaptor.capture(), eq(TutorProfile.class))).willReturn(query);
        TutorProfile profile = mock(TutorProfile.class);
        given(query.getResultList()).willReturn(List.of(profile));

        Instant cursorTime = Instant.now();
        UUID cursorId = UUID.randomUUID();
        CursorEncoder.Cursor cursor = new CursorEncoder.Cursor(cursorTime, cursorId);
        Set<DongCode> dongs = Set.of(DongCode.BEOMEO_1);
        Set<SubjectCode> subjects = Set.of(SubjectCode.MATH);
        Set<GradeCode> grades = Set.of(GradeCode.MIDDLE_1);

        List<TutorProfile> results = repository.search(ProfileVisibility.PUBLIC, dongs, subjects, grades, cursor, 10);

        assertThat(results).containsExactly(profile);
        String jpql = jpqlCaptor.getValue();
        assertThat(jpql)
                .startsWith("SELECT p FROM TutorProfile p WHERE p.visibility = :visibility")
                .doesNotContain("DISTINCT")
                .contains("EXISTS (SELECT 1 FROM p.dongCodes d WHERE d IN :dongCodes)")
                .contains("EXISTS (SELECT 1 FROM p.subjectCodes s WHERE s IN :subjectCodes)")
                .contains("EXISTS (SELECT 1 FROM p.gradeCodes g WHERE g IN :gradeCodes)")
                .contains("(p.updatedAt < :cursorUpdatedAt OR (p.updatedAt = :cursorUpdatedAt AND p.id < :cursorId))")
                .endsWith("ORDER BY p.updatedAt DESC, p.id DESC");

        verify(query).setParameter("visibility", ProfileVisibility.PUBLIC);
        verify(query).setParameter("dongCodes", dongs);
        verify(query).setParameter("subjectCodes", subjects);
        verify(query).setParameter("gradeCodes", grades);
        verify(query).setParameter("cursorUpdatedAt", cursorTime);
        verify(query).setParameter("cursorId", cursorId);
        verify(query).setMaxResults(10);
    }

    @Test
    @DisplayName("search: 필터와 커서가 없을 때 기본 JPQL만 생성한다")
    @SuppressWarnings("unchecked")
    void searchWithNoFilters() {
        TypedQuery<TutorProfile> query = mock(TypedQuery.class);
        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
        given(em.createQuery(jpqlCaptor.capture(), eq(TutorProfile.class))).willReturn(query);
        given(query.getResultList()).willReturn(List.of());

        List<TutorProfile> results = repository.search(ProfileVisibility.PUBLIC, null, null, null, null, 20);

        assertThat(results).isEmpty();
        String jpql = jpqlCaptor.getValue();
        assertThat(jpql)
                .isEqualTo(
                        "SELECT p FROM TutorProfile p WHERE p.visibility = :visibility ORDER BY p.updatedAt DESC, p.id DESC");

        verify(query).setParameter("visibility", ProfileVisibility.PUBLIC);
        verify(query).setMaxResults(20);
    }

    @Test
    @DisplayName("search: 빈 Set이 주어졌을 때 조건절을 추가하지 않는다")
    @SuppressWarnings("unchecked")
    void searchWithEmptySets() {
        TypedQuery<TutorProfile> query = mock(TypedQuery.class);
        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
        given(em.createQuery(jpqlCaptor.capture(), eq(TutorProfile.class))).willReturn(query);
        given(query.getResultList()).willReturn(List.of());

        List<TutorProfile> results =
                repository.search(ProfileVisibility.PUBLIC, Set.of(), Set.of(), Set.of(), null, 20);

        assertThat(results).isEmpty();
        String jpql = jpqlCaptor.getValue();
        assertThat(jpql).doesNotContain("p.dongCodes");
        assertThat(jpql).doesNotContain("p.subjectCodes");
        assertThat(jpql).doesNotContain("p.gradeCodes");

        verify(query).setParameter("visibility", ProfileVisibility.PUBLIC);
    }

    @Test
    @DisplayName("search: 단독 필터 각각이 정상적으로 JPQL과 파라미터에 반영된다")
    @SuppressWarnings("unchecked")
    void searchWithIndividualFilters() {
        TypedQuery<TutorProfile> query = mock(TypedQuery.class);
        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);
        given(em.createQuery(jpqlCaptor.capture(), eq(TutorProfile.class))).willReturn(query);
        given(query.getResultList()).willReturn(List.of());

        // dongCodes만
        repository.search(ProfileVisibility.PUBLIC, Set.of(DongCode.BEOMEO_1), null, null, null, 10);
        assertThat(jpqlCaptor.getValue()).contains("p.dongCodes").doesNotContain("p.subjectCodes");

        // subjectCodes만
        repository.search(ProfileVisibility.PUBLIC, null, Set.of(SubjectCode.MATH), null, null, 10);
        assertThat(jpqlCaptor.getValue()).contains("p.subjectCodes").doesNotContain("p.gradeCodes");

        // gradeCodes만
        repository.search(ProfileVisibility.PUBLIC, null, null, Set.of(GradeCode.MIDDLE_1), null, 10);
        assertThat(jpqlCaptor.getValue()).contains("p.gradeCodes").doesNotContain("p.dongCodes");
    }
}

package kr.needteacher.tutorprofile;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TutorProfileRepository extends JpaRepository<TutorProfile, UUID>, TutorProfileRepositoryCustom {

    Optional<TutorProfile> findByOwnerId(String ownerId);

    boolean existsByOwnerId(String ownerId);

    Optional<TutorProfile> findByIdAndVisibility(UUID id, ProfileVisibility visibility);
}

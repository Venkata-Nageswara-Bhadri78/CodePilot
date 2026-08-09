package com.developer.copilot.jobs.repository;

import com.developer.copilot.jobs.entity.JobEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<JobEntity, Long> {

    Optional<JobEntity> findByIdAndUserId(Long id, Long userId);

    Page<JobEntity> findAllByUserId(Long userId, Pageable pageable);

    List<JobEntity> findAllByUserId(Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    void deleteByIdAndUserId(Long id, Long userId);

    /**
     * Used to enforce "a user cannot add the same job posting twice", matched against the
     * canonicalized {@code sourceUrl} hash rather than the raw URL itself.
     */
    boolean existsByUserIdAndSourceUrlHash(Long userId, String sourceUrlHash);

    /**
     * Same duplicate check, excluding the job currently being updated (so re-saving a job
     * with its own unchanged URL is never flagged as a duplicate of itself).
     */
    boolean existsByUserIdAndSourceUrlHashAndIdNot(Long userId, String sourceUrlHash, Long id);

    @Query("SELECT j FROM JobEntity j WHERE j.user.id = :userId AND " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(j.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(j.company) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(j.location) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(j.industry) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(j.sourcePlatform) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<JobEntity> searchJobsByUserId(
            @Param("userId") Long userId,
            @Param("search") String search,
            Pageable pageable
    );
}

package com.developer.copilot.jobs.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.developer.copilot.jobs.entity.JobDraftEntity;

public interface JobDraftRepository extends JpaRepository<JobDraftEntity, Long> {

    Optional<JobDraftEntity> findByIdAndUserId(Long id, Long userId);

    Page<JobDraftEntity> findAllByUserId(Long userId, Pageable pageable);

    Optional<JobDraftEntity> findByUserIdAndSourceUrlHash(Long userId, String sourceUrlHash);

    boolean existsByUserIdAndSourceUrlHashAndIdNot(Long userId, String sourceUrlHash, Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM JobDraftEntity d WHERE d.user.id = :userId AND d.sourceUrlHash = :sourceUrlHash")
    int deleteByUserIdAndSourceUrlHash(@Param("userId") Long userId, @Param("sourceUrlHash") String sourceUrlHash);
}

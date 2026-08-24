package com.developer.copilot.user.repository;

import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    List<Resume> findByUserProfile(UserProfile userProfile);

    List<Resume> findByUserProfileAndActiveTrue(UserProfile userProfile);

    List<Resume> findByUserProfileAndActiveTrueOrderByCreatedAtDesc(UserProfile userProfile);

    Optional<Resume> findByIdAndUserProfileAndActiveTrue(Long id, UserProfile userProfile);

    Optional<Resume> findByChecksumAndUserProfileAndActiveTrue(String checksum, UserProfile userProfile);

    Optional<Resume> findByHighPriorityTrueAndUserProfileAndActiveTrue(UserProfile userProfile);

    long countByUserProfileAndActiveTrue(UserProfile userProfile);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Resume r SET r.highPriority = false WHERE r.userProfile = :profile AND r.active = true")
    void clearHighPriorityForProfile(@Param("profile") UserProfile profile);
}
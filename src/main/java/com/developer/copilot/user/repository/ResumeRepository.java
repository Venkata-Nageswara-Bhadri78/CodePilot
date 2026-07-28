package com.developer.copilot.user.repository;

import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    List<Resume> findByUserProfileAndActiveTrue(UserProfile userProfile);
    Optional<Resume> findByIdAndUserProfileAndActiveTrue(Long id, UserProfile userProfile);
    Optional<Resume> findByChecksumAndUserProfileAndActiveTrue(String checksum, UserProfile userProfile);
    Optional<Resume> findByHighPriorityTrueAndUserProfileAndActiveTrue(UserProfile userProfile);
    long countByUserProfileAndActiveTrue(UserProfile userProfile);
}
package com.developer.copilot.user.repository;

import com.developer.copilot.user.entity.UserProfile;
import com.developer.copilot.user.entity.WorkExperience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkExperienceRepository extends JpaRepository<WorkExperience, Long> {

    List<WorkExperience> findByUserProfile(UserProfile userProfile);

    Optional<WorkExperience> findByIdAndUserProfile(Long id, UserProfile userProfile);

    long countByUserProfile(UserProfile userProfile);

}

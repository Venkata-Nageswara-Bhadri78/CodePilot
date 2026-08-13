package com.developer.copilot.user.repository;

import com.developer.copilot.user.entity.Education;
import com.developer.copilot.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EducationRepository extends JpaRepository<Education, Long> {

    List<Education> findByUserProfile(UserProfile userProfile);

    Optional<Education> findByIdAndUserProfile(Long id, UserProfile userProfile);

}

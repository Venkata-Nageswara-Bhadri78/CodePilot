package com.developer.copilot.user.repository;

import com.developer.copilot.user.entity.AdditionalProfileInformation;
import com.developer.copilot.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdditionalProfileInformationRepository extends JpaRepository<AdditionalProfileInformation, Long> {

    List<AdditionalProfileInformation> findByUserProfile(UserProfile userProfile);

    Optional<AdditionalProfileInformation> findByIdAndUserProfile(Long id, UserProfile userProfile);

}

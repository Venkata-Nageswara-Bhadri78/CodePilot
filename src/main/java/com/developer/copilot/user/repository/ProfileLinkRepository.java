package com.developer.copilot.user.repository;

import com.developer.copilot.user.entity.ProfileLink;
import com.developer.copilot.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfileLinkRepository extends JpaRepository<ProfileLink, Long> {

    List<ProfileLink> findByUserProfile(UserProfile userProfile);

    Optional<ProfileLink> findByIdAndUserProfile(Long id, UserProfile userProfile);

    long countByUserProfile(UserProfile userProfile);

}

package com.developer.copilot.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.developer.copilot.auth.entity.UserIdentity;
import com.developer.copilot.auth.enums.IdentityProvider;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    Optional<UserIdentity> findByProviderAndProviderSubject(IdentityProvider provider, String providerSubject);

    Optional<UserIdentity> findByUserIdAndProvider(Long userId, IdentityProvider provider);

    boolean existsByUserIdAndProvider(Long userId, IdentityProvider provider);
}

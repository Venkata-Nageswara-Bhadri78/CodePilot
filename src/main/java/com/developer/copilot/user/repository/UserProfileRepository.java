package com.developer.copilot.user.repository;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.user.entity.UserProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUser(User user);

    boolean existsByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM UserProfile p WHERE p.user = :user")
    Optional<UserProfile> findByUserForUpdate(@Param("user") User user);

}
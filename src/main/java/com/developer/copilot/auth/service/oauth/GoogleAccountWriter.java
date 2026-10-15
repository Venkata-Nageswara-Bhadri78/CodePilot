package com.developer.copilot.auth.service.oauth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.entity.UserIdentity;
import com.developer.copilot.auth.enums.IdentityProvider;
import com.developer.copilot.auth.enums.Role;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.auth.exception.OnboardingIncompleteException;
import com.developer.copilot.auth.exception.ResourceAlreadyExistsException;
import com.developer.copilot.auth.repository.UserIdentityRepository;
import com.developer.copilot.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GoogleAccountWriter {

    public static final String EMAIL_ALREADY_EXISTS =
            "An account with this email already exists. Sign in with your email and password.";

    public static final String EMAIL_MISMATCH =
            "The Google account email does not match this account.";

    public static final String GOOGLE_LINKED_TO_OTHER_USER =
            "This Google account is already linked to another user.";

    public static final String USER_ALREADY_HAS_GOOGLE =
            "A Google account is already linked to this user.";

    public static final String ACCOUNT_DISABLED = "This account is disabled.";

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;

    @Transactional
    public User loginOrCreate(VerifiedGoogleIdentity identity) {
        UserIdentity link = userIdentityRepository
                .findByProviderAndProviderSubject(IdentityProvider.GOOGLE, identity.subject())
                .orElse(null);
        if (link != null) {
            return authenticateLinked(link, identity.email());
        }
        if (userRepository.findByEmail(identity.email()).isPresent()) {
            throw new ResourceAlreadyExistsException(EMAIL_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUsername(null);
        user.setPassword(null);
        user.setEmail(identity.email());
        user.setFullName(GoogleProfileNames.fullName(identity.name(), identity.email()));
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setTokenVersion(0);
        userRepository.saveAndFlush(user);

        UserIdentity created = new UserIdentity();
        created.setUser(user);
        created.setProvider(IdentityProvider.GOOGLE);
        created.setProviderSubject(identity.subject());
        created.setProviderEmail(identity.email());
        userIdentityRepository.saveAndFlush(created);
        return user;
    }

    @Transactional
    public User recover(VerifiedGoogleIdentity identity) {
        UserIdentity link = userIdentityRepository
                .findByProviderAndProviderSubject(IdentityProvider.GOOGLE, identity.subject())
                .orElse(null);
        if (link != null) {
            return authenticateLinked(link, identity.email());
        }
        if (userRepository.findByEmail(identity.email()).isPresent()) {
            throw new ResourceAlreadyExistsException(EMAIL_ALREADY_EXISTS);
        }
        throw new ResourceAlreadyExistsException("The request conflicts with existing data. Please retry.");
    }

    /**
     * Attaches a verified Google subject to an existing account. Does not create a user
     * and does not change username, password, email, name, or role.
     */
    @Transactional
    public void link(Long userId, VerifiedGoogleIdentity identity) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User is not authenticated."));
        requireActive(user);
        if (user.getUsername() == null) {
            throw new OnboardingIncompleteException();
        }
        if (!user.getEmail().equals(identity.email())) {
            throw new ResourceAlreadyExistsException(EMAIL_MISMATCH);
        }

        UserIdentity bySubject = userIdentityRepository
                .findByProviderAndProviderSubject(IdentityProvider.GOOGLE, identity.subject())
                .orElse(null);
        if (bySubject != null) {
            if (!bySubject.getUser().getId().equals(user.getId())) {
                throw new ResourceAlreadyExistsException(GOOGLE_LINKED_TO_OTHER_USER);
            }
            touchProviderEmail(bySubject, identity.email());
            return;
        }

        UserIdentity own = userIdentityRepository
                .findByUserIdAndProvider(user.getId(), IdentityProvider.GOOGLE)
                .orElse(null);
        if (own != null) {
            throw new ResourceAlreadyExistsException(USER_ALREADY_HAS_GOOGLE);
        }

        UserIdentity created = new UserIdentity();
        created.setUser(user);
        created.setProvider(IdentityProvider.GOOGLE);
        created.setProviderSubject(identity.subject());
        created.setProviderEmail(identity.email());
        userIdentityRepository.saveAndFlush(created);
    }

    @Transactional
    public void linkAfterConflict(Long userId, VerifiedGoogleIdentity identity) {
        UserIdentity bySubject = userIdentityRepository
                .findByProviderAndProviderSubject(IdentityProvider.GOOGLE, identity.subject())
                .orElse(null);
        if (bySubject != null && bySubject.getUser().getId().equals(userId)) {
            touchProviderEmail(bySubject, identity.email());
            return;
        }
        throw new ResourceAlreadyExistsException(GOOGLE_LINKED_TO_OTHER_USER);
    }

    private void touchProviderEmail(UserIdentity link, String email) {
        if (email != null && !email.equals(link.getProviderEmail())) {
            link.setProviderEmail(email);
            userIdentityRepository.save(link);
        }
    }

    private User authenticateLinked(UserIdentity link, String email) {
        User user = requireActive(link.getUser());
        touchProviderEmail(link, email);
        return user;
    }

    private static User requireActive(User user) {
        if (user == null
                || !Boolean.TRUE.equals(user.getEnabled())
                || !Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new InvalidCredentialsException(ACCOUNT_DISABLED);
        }
        return user;
    }
}

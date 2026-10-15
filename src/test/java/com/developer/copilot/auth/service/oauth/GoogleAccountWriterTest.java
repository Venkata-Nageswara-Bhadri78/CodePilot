package com.developer.copilot.auth.service.oauth;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.entity.UserIdentity;
import com.developer.copilot.auth.enums.IdentityProvider;
import com.developer.copilot.auth.enums.Role;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.auth.exception.OnboardingIncompleteException;
import com.developer.copilot.auth.exception.ResourceAlreadyExistsException;
import com.developer.copilot.auth.repository.UserIdentityRepository;
import com.developer.copilot.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAccountWriterTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserIdentityRepository userIdentityRepository;

    @InjectMocks
    private GoogleAccountWriter writer;

    private VerifiedGoogleIdentity identity;

    @BeforeEach
    void setUp() {
        identity = new VerifiedGoogleIdentity("google-sub", "ada@example.com", "Ada Lovelace");
    }

    @Test
    void loginOrCreate_newUser_leavesUsernameAndPasswordEmpty() {
        when(userIdentityRepository.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(8L);
            return saved;
        });
        when(userIdentityRepository.saveAndFlush(any(UserIdentity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = writer.loginOrCreate(identity);

        assertEquals(8L, user.getId());
        assertNull(user.getUsername());
        assertNull(user.getPassword());
        assertEquals("ada@example.com", user.getEmail());
        assertEquals("Ada Lovelace", user.getFullName());
        assertEquals(Role.USER, user.getRole());
        assertTrue(user.getEnabled());
        assertTrue(user.getEmailVerified());
        assertEquals(0, user.getTokenVersion());

        ArgumentCaptor<UserIdentity> identityCaptor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).saveAndFlush(identityCaptor.capture());
        assertEquals(IdentityProvider.GOOGLE, identityCaptor.getValue().getProvider());
        assertEquals("google-sub", identityCaptor.getValue().getProviderSubject());
    }

    @Test
    void loginOrCreate_shortGoogleName_usesEmailLocalPart() {
        identity = new VerifiedGoogleIdentity("google-sub", "ab@example.com", "Jo");
        when(userIdentityRepository.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("ab@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userIdentityRepository.saveAndFlush(any(UserIdentity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = writer.loginOrCreate(identity);

        assertEquals("abuser", user.getFullName());
        assertNull(user.getUsername());
    }

    @Test
    void loginOrCreate_returningCompleteUser_updatesProviderEmail() {
        User user = activeUser("ada");
        UserIdentity link = link(user, "old@example.com");
        when(userIdentityRepository.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(link));

        User signedIn = writer.loginOrCreate(identity);

        assertEquals(user, signedIn);
        assertEquals("ada@example.com", link.getProviderEmail());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void loginOrCreate_returningIncompleteUser_keepsNullUsername() {
        User user = activeUser(null);
        when(userIdentityRepository.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(link(user, "ada@example.com")));

        User signedIn = writer.loginOrCreate(identity);

        assertNull(signedIn.getUsername());
    }

    @Test
    void loginOrCreate_emailCollision_doesNotLink() {
        when(userIdentityRepository.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(activeUser("ada")));

        ResourceAlreadyExistsException ex = assertThrows(
                ResourceAlreadyExistsException.class, () -> writer.loginOrCreate(identity));

        assertEquals(GoogleAccountWriter.EMAIL_ALREADY_EXISTS, ex.getMessage());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void loginOrCreate_disabledLinkedUser_isUnauthorized() {
        User user = activeUser("ada");
        user.setEnabled(false);
        when(userIdentityRepository.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(link(user, "ada@example.com")));

        InvalidCredentialsException ex = assertThrows(
                InvalidCredentialsException.class, () -> writer.loginOrCreate(identity));
        assertEquals(GoogleAccountWriter.ACCOUNT_DISABLED, ex.getMessage());
    }

    @Test
    void loginOrCreate_identityUniqueRace_isLeftToCaller() {
        when(userIdentityRepository.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(DataIntegrityViolationException.class, () -> writer.loginOrCreate(identity));
    }

    @Test
    void recover_afterIdentityRace_returnsExistingUser() {
        User user = activeUser("ada");
        when(userIdentityRepository.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(link(user, "ada@example.com")));

        assertEquals(user, writer.recover(identity));
    }

    @Test
    void recover_whenEmailExistsWithoutThisSubject_doesNotAttach() {
        when(userIdentityRepository.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(activeUser("ada")));

        ResourceAlreadyExistsException ex = assertThrows(
                ResourceAlreadyExistsException.class, () -> writer.recover(identity));
        assertEquals(GoogleAccountWriter.EMAIL_ALREADY_EXISTS, ex.getMessage());
    }

    @Test
    void link_attachesGoogleWithoutChangingTheAccount() {
        User user = activeUser("ada");
        user.setPassword("hashed-password");
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(userIdentityRepository.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(userIdentityRepository.findByUserIdAndProvider(4L, IdentityProvider.GOOGLE)).thenReturn(Optional.empty());
        when(userIdentityRepository.saveAndFlush(any(UserIdentity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.link(4L, identity);

        ArgumentCaptor<UserIdentity> captor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).saveAndFlush(captor.capture());
        assertEquals(user, captor.getValue().getUser());
        assertEquals("google-sub", captor.getValue().getProviderSubject());
        assertEquals("ada", user.getUsername());
        assertEquals("hashed-password", user.getPassword());
        assertEquals("ada@example.com", user.getEmail());
        assertEquals("Ada Lovelace", user.getFullName());
        assertEquals(Role.USER, user.getRole());
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void link_sameSubject_isIdempotent() {
        User user = activeUser("ada");
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(userIdentityRepository.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(link(user, "ada@example.com")));

        writer.link(4L, identity);

        verify(userIdentityRepository, never()).saveAndFlush(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void link_emailMismatch_doesNotLink() {
        User user = activeUser("ada");
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));

        ResourceAlreadyExistsException ex = assertThrows(
                ResourceAlreadyExistsException.class,
                () -> writer.link(4L, new VerifiedGoogleIdentity("google-sub", "other@example.com", "Other")));

        assertEquals(GoogleAccountWriter.EMAIL_MISMATCH, ex.getMessage());
        verify(userIdentityRepository, never()).saveAndFlush(any());
    }

    @Test
    void link_subjectOwnedByAnotherUser_isRejected() {
        User owner = activeUser("ada");
        User other = activeUser("ada");
        other.setId(9L);
        when(userRepository.findById(9L)).thenReturn(Optional.of(other));
        other.setEmail("ada@example.com");
        when(userIdentityRepository.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(link(owner, "ada@example.com")));

        ResourceAlreadyExistsException ex = assertThrows(
                ResourceAlreadyExistsException.class, () -> writer.link(9L, identity));

        assertEquals(GoogleAccountWriter.GOOGLE_LINKED_TO_OTHER_USER, ex.getMessage());
        verify(userIdentityRepository, never()).saveAndFlush(any());
    }

    @Test
    void link_userAlreadyHasDifferentGoogle_isRejected() {
        User user = activeUser("ada");
        UserIdentity existing = link(user, "ada@example.com");
        existing.setProviderSubject("other-sub");
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(userIdentityRepository.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(userIdentityRepository.findByUserIdAndProvider(4L, IdentityProvider.GOOGLE))
                .thenReturn(Optional.of(existing));

        ResourceAlreadyExistsException ex = assertThrows(
                ResourceAlreadyExistsException.class, () -> writer.link(4L, identity));

        assertEquals(GoogleAccountWriter.USER_ALREADY_HAS_GOOGLE, ex.getMessage());
        verify(userIdentityRepository, never()).saveAndFlush(any());
    }

    @Test
    void link_withoutUsername_isRejected() {
        User user = activeUser(null);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));

        assertThrows(OnboardingIncompleteException.class, () -> writer.link(4L, identity));
        verify(userIdentityRepository, never()).saveAndFlush(any());
    }

    @Test
    void linkAfterConflict_sameUser_succeeds() {
        User user = activeUser("ada");
        when(userIdentityRepository.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(link(user, "ada@example.com")));

        writer.linkAfterConflict(4L, identity);

        verify(userIdentityRepository, never()).saveAndFlush(any());
    }

    @Test
    void linkAfterConflict_otherUser_isRejected() {
        User other = activeUser("ada");
        other.setId(9L);
        when(userIdentityRepository.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(link(other, "ada@example.com")));

        ResourceAlreadyExistsException ex = assertThrows(
                ResourceAlreadyExistsException.class, () -> writer.linkAfterConflict(4L, identity));
        assertEquals(GoogleAccountWriter.GOOGLE_LINKED_TO_OTHER_USER, ex.getMessage());
    }

    @Test
    void loginOrCreate_afterLink_usesTheSameAccount() {
        User user = activeUser("ada");
        user.setPassword("hashed-password");
        when(userIdentityRepository.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(link(user, "ada@example.com")));

        User signedIn = writer.loginOrCreate(identity);

        assertEquals(user, signedIn);
        assertEquals("ada", signedIn.getUsername());
        assertEquals("hashed-password", signedIn.getPassword());
        verify(userRepository, never()).saveAndFlush(any());
    }

    private static User activeUser(String username) {
        User user = new User();
        user.setId(4L);
        user.setUsername(username);
        user.setEmail("ada@example.com");
        user.setFullName("Ada Lovelace");
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setTokenVersion(0);
        return user;
    }

    private static UserIdentity link(User user, String providerEmail) {
        UserIdentity link = new UserIdentity();
        link.setUser(user);
        link.setProvider(IdentityProvider.GOOGLE);
        link.setProviderSubject("google-sub");
        link.setProviderEmail(providerEmail);
        return link;
    }
}

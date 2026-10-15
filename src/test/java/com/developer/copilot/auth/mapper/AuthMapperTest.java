package com.developer.copilot.auth.mapper;

import com.developer.copilot.auth.dto.UserResponse;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.enums.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthMapperTest {

    @Test
    void toUserResponse_copiesPublicFieldsOnly() {
        User user = new User();
        user.setId(9L);
        user.setUsername("johndoe");
        user.setFullName("John Doe");
        user.setEmail("john@example.com");
        user.setPassword("secret-hash");
        user.setRole(Role.USER);

        UserResponse response = new AuthMapper().toUserResponse(user);

        assertEquals(9L, response.getId());
        assertEquals("johndoe", response.getUsername());
        assertEquals("John Doe", response.getFullName());
        assertEquals("john@example.com", response.getEmail());
        assertEquals(Role.USER, response.getRole());
        assertTrue(response.isPasswordConfigured());
        assertFalse(response.isGoogleLinked());
    }

    @Test
    void toUserResponse_canMarkGoogleLinked() {
        User user = new User();
        user.setId(9L);
        user.setEmail("ada@example.com");
        user.setFullName("Ada Lovelace");
        user.setRole(Role.USER);

        UserResponse response = new AuthMapper().toUserResponse(user, true);

        assertTrue(response.isGoogleLinked());
        assertFalse(response.isPasswordConfigured());
    }

    @Test
    void toUserResponse_passwordConfiguredIsFalseWhenPasswordMissing() {
        User user = new User();
        user.setId(9L);
        user.setFullName("Ada Lovelace");
        user.setEmail("ada@example.com");
        user.setRole(Role.USER);

        UserResponse response = new AuthMapper().toUserResponse(user);

        assertFalse(response.isPasswordConfigured());
        assertEquals(null, response.getUsername());
    }
}

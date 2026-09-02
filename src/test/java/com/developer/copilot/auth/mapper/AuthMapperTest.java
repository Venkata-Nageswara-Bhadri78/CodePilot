package com.developer.copilot.auth.mapper;

import com.developer.copilot.auth.dto.UserResponse;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.enums.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    }
}

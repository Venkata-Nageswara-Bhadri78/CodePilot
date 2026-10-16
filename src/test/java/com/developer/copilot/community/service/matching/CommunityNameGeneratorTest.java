package com.developer.copilot.community.service.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.developer.copilot.community.repository.CommunityRepository;

@ExtendWith(MockitoExtension.class)
class CommunityNameGeneratorTest {

    @Mock
    private CommunityRepository communityRepository;

    @Test
    void nameIsReadableAndDoesNotUseTheJobId() {
        CommunityNameGenerator generator = new CommunityNameGenerator(communityRepository);
        when(communityRepository.existsByCommunityName("Backend Engineer at Northwind")).thenReturn(false);

        String name = generator.generate("  Backend   Engineer ", "Northwind", "a".repeat(64), false);

        assertEquals("Backend Engineer at Northwind", name);
        assertTrue(name.length() <= 255);
    }

    @Test
    void collisionAppendsCanonicalKeyAndStaysDeterministic() {
        CommunityNameGenerator generator = new CommunityNameGenerator(communityRepository);
        when(communityRepository.existsByCommunityName("Backend Engineer at Northwind")).thenReturn(true);
        String key = "abcdef1234567890" + "0".repeat(48);

        String first = generator.generate("Backend Engineer", "Northwind", key, false);
        String second = generator.generate("Backend Engineer", "Northwind", key, false);

        assertEquals("Backend Engineer at Northwind-abcdef12", first);
        assertEquals(first, second);
        assertNotEquals("Backend Engineer at Northwind", first);
    }
}

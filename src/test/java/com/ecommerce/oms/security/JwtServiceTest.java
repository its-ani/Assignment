package com.ecommerce.oms.security;

import com.ecommerce.oms.domain.User;
import com.ecommerce.oms.domain.UserRole;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;
    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3600000L); // 1 hour

        testUser = User.builder()
                .id(UUID.randomUUID())
                .name("John Doe")
                .email("john.doe@example.com")
                .passwordHash("hashedpassword")
                .role(UserRole.CUSTOMER)
                .build();
    }

    @Test
    void testGenerateAndValidateToken() {
        String token = jwtService.generateToken(testUser);
        assertNotNull(token);

        assertTrue(jwtService.validateToken(token, testUser.getEmail()));
        assertFalse(jwtService.validateToken(token, "other.email@example.com"));

        assertEquals(testUser.getEmail(), jwtService.extractEmail(token));
        assertEquals(testUser.getId(), jwtService.extractUserId(token));
        assertEquals(testUser.getRole().name(), jwtService.extractRole(token));
    }

    @Test
    void testTokenExpiration() {
        // Set expiration to negative value (expired immediately)
        ReflectionTestUtils.setField(jwtService, "expirationMs", -1000L);
        String token = jwtService.generateToken(testUser);

        assertThrows(ExpiredJwtException.class, () -> {
            jwtService.extractEmail(token);
        });
    }

    @Test
    void testTamperedToken() {
        String token = jwtService.generateToken(testUser);
        String tamperedToken = token + "modified";

        assertThrows(SignatureException.class, () -> {
            jwtService.extractEmail(tamperedToken);
        });
    }
}

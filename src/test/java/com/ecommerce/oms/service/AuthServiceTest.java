package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.User;
import com.ecommerce.oms.domain.UserRole;
import com.ecommerce.oms.dto.AuthResponse;
import com.ecommerce.oms.dto.LoginRequest;
import com.ecommerce.oms.dto.RegisterRequest;
import com.ecommerce.oms.dto.UserSummary;
import com.ecommerce.oms.repository.UserRepository;
import com.ecommerce.oms.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User testUser;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .name("Jane Doe")
                .email("jane.doe@example.com")
                .password("password123")
                .role(UserRole.ADMIN) // Attempting to register as ADMIN
                .build();

        loginRequest = LoginRequest.builder()
                .email("jane.doe@example.com")
                .password("password123")
                .build();

        testUser = User.builder()
                .id(UUID.randomUUID())
                .name("Jane Doe")
                .email("jane.doe@example.com")
                .passwordHash("hashedpassword")
                .role(UserRole.CUSTOMER)
                .build();
    }

    @Test
    void testRegisterSuccessfullyForcesCustomerRole() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("hashedpassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User userToSave = invocation.getArgument(0);
            userToSave.setId(testUser.getId()); // Simulate DB assigning UUID
            return userToSave;
        });

        UserSummary summary = authService.register(registerRequest);

        assertNotNull(summary);
        assertEquals(testUser.getEmail(), summary.getEmail());
        assertEquals(UserRole.CUSTOMER, summary.getRole()); // Verify role was forced to CUSTOMER
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testRegisterDuplicateEmailThrowsException() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(registerRequest));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testLoginSuccessfully() {
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(), loginRequest.getPassword()
        );
        when(authenticationManager.authenticate(authToken)).thenReturn(null);
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(testUser)).thenReturn("valid.jwt.token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("valid.jwt.token", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(testUser.getEmail(), response.getUser().getEmail());
    }

    @Test
    void testLoginInvalidCredentialsThrowsException() {
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(), loginRequest.getPassword()
        );
        when(authenticationManager.authenticate(authToken)).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));
    }
}

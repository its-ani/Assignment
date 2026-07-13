package com.ecommerce.oms.controller;

import com.ecommerce.oms.domain.User;
import com.ecommerce.oms.domain.UserRole;
import com.ecommerce.oms.dto.LoginRequest;
import com.ecommerce.oms.dto.RegisterRequest;
import com.ecommerce.oms.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // Clear all users except the default admin user seeded by DatabaseInitializer
        userRepository.findAll().forEach(user -> {
            if (!"admin@ecommerce.com".equals(user.getEmail())) {
                userRepository.delete(user);
            }
        });

        // Re-seed default admin if missing
        if (!userRepository.existsByEmail("admin@ecommerce.com")) {
            User defaultAdmin = User.builder()
                    .name("Administrator")
                    .email("admin@ecommerce.com")
                    .passwordHash(passwordEncoder.encode("AdminPass123!"))
                    .role(UserRole.ADMIN)
                    .build();
            userRepository.save(defaultAdmin);
        }
    }

    @Test
    void testRegisterCustomerSuccessfullyAndPreventsRoleEscalation() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .name("Alice Smith")
                .email("alice.smith@example.com")
                .password("securepassword")
                .role(UserRole.ADMIN) // Attempting to register as ADMIN
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Alice Smith")))
                .andExpect(jsonPath("$.email", is("alice.smith@example.com")))
                .andExpect(jsonPath("$.role", is("CUSTOMER"))); // Role must be CUSTOMER, not ADMIN

        // Double check database status
        User user = userRepository.findByEmail("alice.smith@example.com").orElseThrow();
        assertEquals(UserRole.CUSTOMER, user.getRole());
    }

    @Test
    void testRegisterValidationFailures() throws Exception {
        RegisterRequest invalidRequest = RegisterRequest.builder()
                .name("") // Blank
                .email("invalid-email") // Not email
                .password("short") // Less than 8 characters
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.errors.name", notNullValue()))
                .andExpect(jsonPath("$.errors.email", notNullValue()))
                .andExpect(jsonPath("$.errors.password", notNullValue()));
    }

    @Test
    void testLoginSuccessfully() throws Exception {
        // Create user
        User user = User.builder()
                .name("Bob Jones")
                .email("bob.jones@example.com")
                .passwordHash(passwordEncoder.encode("bobspassword"))
                .role(UserRole.CUSTOMER)
                .build();
        userRepository.save(user);

        LoginRequest loginRequest = LoginRequest.builder()
                .email("bob.jones@example.com")
                .password("bobspassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", is(86400000))) // Matches our default 24h
                .andExpect(jsonPath("$.user.email", is("bob.jones@example.com")))
                .andExpect(jsonPath("$.user.role", is("CUSTOMER")));
    }

    @Test
    void testLoginFailures() throws Exception {
        LoginRequest badLogin = LoginRequest.builder()
                .email("nonexistent@example.com")
                .password("password")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", containsString("Invalid email or password")));
    }

    @Test
    void testProtectedMeEndpoint() throws Exception {
        // 1. Unauthorized Access
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isForbidden()); // Filter chain throws 403 or 401 when no token is present.
                // Wait, default Spring Security returns 403 Forbidden for unauthorized requests if no entrypoint is set.
                // Let's verify what happens.

        // 2. Authorized Access
        User user = User.builder()
                .name("Charlie Brown")
                .email("charlie.brown@example.com")
                .passwordHash(passwordEncoder.encode("charliepass"))
                .role(UserRole.CUSTOMER)
                .build();
        userRepository.save(user);

        LoginRequest loginRequest = LoginRequest.builder()
                .email("charlie.brown@example.com")
                .password("charliepass")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseContent).get("token").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("charlie.brown@example.com")))
                .andExpect(jsonPath("$.role", is("CUSTOMER")));
    }

    @Test
    void testRoleEnforcementOnAdminRegister() throws Exception {
        // Create customer and login to get customer token
        User customer = User.builder()
                .name("Customer User")
                .email("customer@example.com")
                .passwordHash(passwordEncoder.encode("customerpass"))
                .role(UserRole.CUSTOMER)
                .build();
        userRepository.save(customer);

        LoginRequest customerLogin = LoginRequest.builder()
                .email("customer@example.com")
                .password("customerpass")
                .build();

        MvcResult customerLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerLogin)))
                .andReturn();
        String customerToken = objectMapper.readTree(customerLoginResult.getResponse().getContentAsString()).get("token").asText();

        RegisterRequest newStaffRequest = RegisterRequest.builder()
                .name("Warehouse Worker")
                .email("worker@example.com")
                .password("workerpassword")
                .role(UserRole.WAREHOUSE_STAFF)
                .build();

        // 1. CUSTOMER attempts to call admin/register -> 403 Forbidden
        mockMvc.perform(post("/api/v1/auth/admin/register")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStaffRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")))
                .andExpect(jsonPath("$.message", containsString("You do not have permission")));

        // 2. ADMIN calls admin/register -> 201 Created
        LoginRequest adminLogin = LoginRequest.builder()
                .email("admin@ecommerce.com")
                .password("AdminPass123!") // Seeded by DatabaseInitializer
                .build();

        MvcResult adminLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andReturn();
        String adminToken = objectMapper.readTree(adminLoginResult.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(post("/api/v1/auth/admin/register")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStaffRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("worker@example.com")))
                .andExpect(jsonPath("$.role", is("WAREHOUSE_STAFF")));
    }
}

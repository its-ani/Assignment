package com.ecommerce.oms.controller;

import com.ecommerce.oms.domain.Discount;
import com.ecommerce.oms.domain.DiscountType;
import com.ecommerce.oms.domain.User;
import com.ecommerce.oms.domain.UserRole;
import com.ecommerce.oms.dto.DiscountRequest;
import com.ecommerce.oms.repository.DiscountRepository;
import com.ecommerce.oms.repository.UserRepository;
import com.ecommerce.oms.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiscountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DiscountRepository discountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String customerToken;
    private User customerUser;

    @BeforeEach
    void setUp() {
        discountRepository.deleteAll();
        userRepository.deleteAll();

        User admin = User.builder()
                .name("Admin User")
                .email("admin@test.com")
                .passwordHash("pass")
                .role(UserRole.ADMIN)
                .build();
        userRepository.save(admin);
        adminToken = jwtService.generateToken(admin);

        customerUser = User.builder()
                .name("Customer User")
                .email("customer@test.com")
                .passwordHash("pass")
                .role(UserRole.CUSTOMER)
                .build();
        userRepository.save(customerUser);
        customerToken = jwtService.generateToken(customerUser);
    }

    @Test
    void testAdminCreateDiscount() throws Exception {
        DiscountRequest request = DiscountRequest.builder()
                .code("SAVE10")
                .type(DiscountType.PERCENTAGE)
                .value(new BigDecimal("10.00"))
                .validFrom(Instant.now().minus(1, ChronoUnit.HOURS))
                .validTo(Instant.now().plus(24, ChronoUnit.HOURS))
                .minOrderValue(new BigDecimal("50.00"))
                .active(true)
                .build();

        mockMvc.perform(post("/api/v1/discounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.code", is("SAVE10")))
                .andExpect(jsonPath("$.value", is(10.00)))
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    void testCustomerCannotCreateDiscount() throws Exception {
        DiscountRequest request = DiscountRequest.builder()
                .code("SAVE10")
                .type(DiscountType.PERCENTAGE)
                .value(new BigDecimal("10.00"))
                .validFrom(Instant.now())
                .validTo(Instant.now().plus(24, ChronoUnit.HOURS))
                .minOrderValue(new BigDecimal("50.00"))
                .active(true)
                .build();

        mockMvc.perform(post("/api/v1/discounts")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdminUpdateAndSoftDeleteDiscount() throws Exception {
        Discount discount = Discount.builder()
                .code("FLAT5")
                .type(DiscountType.FLAT)
                .value(new BigDecimal("5.00"))
                .validFrom(Instant.now().minus(1, ChronoUnit.HOURS))
                .validTo(Instant.now().plus(24, ChronoUnit.HOURS))
                .minOrderValue(BigDecimal.ZERO)
                .active(true)
                .build();
        discount = discountRepository.save(discount);

        DiscountRequest updateReq = DiscountRequest.builder()
                .code("FLAT5")
                .type(DiscountType.FLAT)
                .value(new BigDecimal("6.00"))
                .validFrom(Instant.now().minus(1, ChronoUnit.HOURS))
                .validTo(Instant.now().plus(24, ChronoUnit.HOURS))
                .minOrderValue(BigDecimal.ZERO)
                .active(true)
                .build();

        mockMvc.perform(put("/api/v1/discounts/" + discount.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value", is(6.00)));

        // Soft delete
        mockMvc.perform(delete("/api/v1/discounts/" + discount.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Discount softDeleted = discountRepository.findById(discount.getId()).orElseThrow();
        assertFalse(softDeleted.isActive());
    }

    @Test
    void testCustomerValidateDiscount() throws Exception {
        Discount discount = Discount.builder()
                .code("SAVE20")
                .type(DiscountType.PERCENTAGE)
                .value(new BigDecimal("20.00"))
                .validFrom(Instant.now().minus(1, ChronoUnit.HOURS))
                .validTo(Instant.now().plus(24, ChronoUnit.HOURS))
                .minOrderValue(new BigDecimal("100.00"))
                .active(true)
                .build();
        discountRepository.save(discount);

        // Valid code
        mockMvc.perform(get("/api/v1/discounts/validate")
                        .header("Authorization", "Bearer " + customerToken)
                        .param("code", "SAVE20")
                        .param("cartTotal", "150.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("SAVE20")))
                .andExpect(jsonPath("$.discountAmount", is(30.00)))
                .andExpect(jsonPath("$.eligible", is(true)));

        // Below min order value
        mockMvc.perform(get("/api/v1/discounts/validate")
                        .header("Authorization", "Bearer " + customerToken)
                        .param("code", "SAVE20")
                        .param("cartTotal", "80.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Minimum order value")));
    }
}

package com.ecommerce.oms.controller;

import com.ecommerce.oms.domain.User;
import com.ecommerce.oms.dto.AuthResponse;
import com.ecommerce.oms.dto.LoginRequest;
import com.ecommerce.oms.dto.RegisterRequest;
import com.ecommerce.oms.dto.UserSummary;
import com.ecommerce.oms.repository.UserRepository;
import com.ecommerce.oms.security.SecurityUtils;
import com.ecommerce.oms.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<UserSummary> register(@Valid @RequestBody RegisterRequest request) {
        UserSummary summary = authService.register(request);
        return new ResponseEntity<>(summary, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserSummary> registerAdminOrStaff(@Valid @RequestBody RegisterRequest request) {
        UserSummary summary = authService.registerAdminOrStaff(request);
        return new ResponseEntity<>(summary, HttpStatus.CREATED);
    }

    @GetMapping("/me")
    public ResponseEntity<UserSummary> getMe() {
        String email = SecurityUtils.getCurrentUserEmail()
                .orElseThrow(() -> new IllegalStateException("User is not authenticated"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserSummary summary = UserSummary.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();

        return ResponseEntity.ok(summary);
    }
}

package com.ecommerce.oms.config;

import com.ecommerce.oms.domain.User;
import com.ecommerce.oms.domain.UserRole;
import com.ecommerce.oms.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User defaultAdmin = User.builder()
                    .name("Administrator")
                    .email("admin@ecommerce.com")
                    .passwordHash(passwordEncoder.encode("AdminPass123!"))
                    .role(UserRole.ADMIN)
                    .build();

            userRepository.save(defaultAdmin);
            System.out.println("--- System bootstrapped with default Administrator user: admin@ecommerce.com ---");
        }
    }
}

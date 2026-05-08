package com.decentralized.degree.vault.decentralizeddegreevault.Config;

import com.decentralized.degree.vault.decentralizeddegreevault.dto.User;
import com.decentralized.degree.vault.decentralizeddegreevault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the database with a default admin user on application startup.
 * <p>
 * Skips insertion if a user with the same email already exists,
 * making this safe for repeated restarts.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String seedEmail = "yahyasid45@gmail.com";

        if (userRepository.existsByEmail(seedEmail)) {
            log.info("Seed user already exists: {}", seedEmail);
            return;
        }

        User admin = User.builder()
                .email(seedEmail)
                .password(passwordEncoder.encode("12345678"))
                .role("ROLE_ADMIN")
                .build();

        userRepository.save(admin);
        log.info("✅ Seed admin user created — email: {}, name: yahya", seedEmail);
    }
}

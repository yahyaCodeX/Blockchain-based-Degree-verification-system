package com.decentralized.degree.vault.decentralizeddegreevault.Config;

import com.decentralized.degree.vault.decentralizeddegreevault.Service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 6 configuration for stateless JWT authentication.
 *
 * <h3>Security rules</h3>
 * <ul>
 *   <li><b>Public</b> — auth endpoints, degree verification, health check</li>
 *   <li><b>Protected (ROLE_ADMIN)</b> — degree issuance, batch upload, stats, admin</li>
 * </ul>
 *
 * <p>CSRF is disabled because the API is stateless (no cookies / sessions).
 * Every protected request must include a valid {@code Authorization: Bearer <token>} header.</p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    // ────────────────────────────────────────────────────────────────────────
    //  Security Filter Chain
    // ────────────────────────────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Disable CSRF — stateless API, no session cookies
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Authorisation rules (order matters — first match wins)
                .authorizeHttpRequests(auth -> auth

                        // ── Public endpoints ─────────────────────────────
                        // Authentication (login / register)
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // Degree verification — employers verify without login
                        .requestMatchers(HttpMethod.POST, "/api/v1/degrees/verify/**").permitAll()

                        // Health check
                        .requestMatchers("/api/v1/degrees/health").permitAll()

                        // ── Admin-only endpoints ─────────────────────────
                        // Single degree issuance
                        .requestMatchers(HttpMethod.POST, "/api/v1/degrees/issue").hasRole("ADMIN")

                        // Batch issuance (CSV + ZIP upload)
                        .requestMatchers(HttpMethod.POST, "/api/v1/degrees/issue/batch").hasRole("ADMIN")

                        // Batch status polling
                        .requestMatchers(HttpMethod.GET, "/api/v1/degrees/batch/*/status").hasRole("ADMIN")

                        // On-chain statistics
                        .requestMatchers(HttpMethod.GET, "/api/v1/degrees/stats").hasRole("ADMIN")

                        // University admin address
                        .requestMatchers(HttpMethod.GET, "/api/v1/degrees/admin").hasRole("ADMIN")

                        // ── Everything else requires authentication ──────
                        .anyRequest().authenticated()
                )

                // 3. Stateless session — no HttpSession, no JSESSIONID cookie
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 4. Wire custom authentication provider
                .authenticationProvider(authenticationProvider())

                // 5. Insert JWT filter before Spring's username/password filter
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Authentication Beans
    // ────────────────────────────────────────────────────────────────────────

    /**
     * DAO-based authentication provider that uses our {@link CustomUserDetailsService}
     * and BCrypt password encoding.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes the {@link AuthenticationManager} as a bean so it can be injected
     * into the AuthController for manual authentication during login.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCrypt password encoder — industry-standard adaptive hashing.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

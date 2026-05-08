package com.decentralized.degree.vault.decentralizeddegreevault.Controller;

import com.decentralized.degree.vault.decentralizeddegreevault.dto.AuthRequest;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.AuthResponse;
import com.decentralized.degree.vault.decentralizeddegreevault.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for authentication operations.
 * <p>
 * All endpoints under {@code /api/v1/auth} are public (see {@code SecurityConfig}).
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    // ────────────────────────────────────────────────────────────────────────
    //  POST /api/v1/auth/login
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Authenticates a university registrar and returns a signed JWT.
     *
     * <h4>Request body</h4>
     * <pre>{@code
     * {
     *   "email": "admin@university.edu",
     *   "password": "securePassword123"
     * }
     * }</pre>
     *
     * <h4>Success response (200)</h4>
     * <pre>{@code
     * {
     *   "token": "eyJhbGciOiJIUzI1NiIs..."
     * }
     * }</pre>
     *
     * @param request the login credentials
     * @return JWT wrapped in an {@link AuthResponse}
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid AuthRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        try {
            // 1. Authenticate via Spring Security's AuthenticationManager
            //    This delegates to DaoAuthenticationProvider → CustomUserDetailsService
            //    and verifies the BCrypt-hashed password.
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            // 2. Extract the authenticated UserDetails
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            // 3. Generate a signed JWT containing email + roles
            String token = jwtUtil.generateToken(userDetails);

            log.info("Login successful for email: {}", request.getEmail());
            return ResponseEntity.ok(new AuthResponse(token));

        } catch (BadCredentialsException e) {
            log.warn("Login failed — bad credentials for email: {}", request.getEmail());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Authentication failed",
                            "message", "Invalid email or password"
                    ));
        } catch (Exception e) {
            log.error("Unexpected error during login: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Login failed",
                            "message", "An unexpected error occurred"
                    ));
        }
    }
}

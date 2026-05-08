package com.decentralized.degree.vault.decentralizeddegreevault.Config;

import com.decentralized.degree.vault.decentralizeddegreevault.Service.CustomUserDetailsService;
import com.decentralized.degree.vault.decentralizeddegreevault.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter that executes once per request.
 * <p>
 * Workflow:
 * <ol>
 *   <li>Extract the {@code Authorization: Bearer <token>} header.</li>
 *   <li>Parse the JWT and extract the email (subject).</li>
 *   <li>If the user is not already authenticated, load {@link UserDetails}
 *       from the database and validate the token.</li>
 *   <li>On success, set a {@link UsernamePasswordAuthenticationToken}
 *       into the {@link SecurityContextHolder} so downstream filters and
 *       controllers see the request as authenticated.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // ── 1. Extract the Authorization header ──────────────────────────
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            // ── 2. Parse the token and extract the email ─────────────────
            final String userEmail = jwtUtil.extractUsername(jwt);

            // ── 3. Authenticate if not already set in SecurityContext ────
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                if (jwtUtil.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authenticated user: {}", userEmail);
                }
            }
        } catch (Exception e) {
            log.warn("JWT authentication failed: {}", e.getMessage());
            // Don't throw — just let the request continue unauthenticated.
            // Spring Security will return 401 if the endpoint requires auth.
        }

        filterChain.doFilter(request, response);
    }
}

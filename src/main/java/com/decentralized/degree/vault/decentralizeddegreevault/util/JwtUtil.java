package com.decentralized.degree.vault.decentralizeddegreevault.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility component for creating, parsing, and validating JSON Web Tokens.
 * <p>
 * Uses the <a href="https://github.com/jwtk/jjwt">jjwt 0.12.x</a> modern API
 * with HMAC-SHA256 signing ({@code Keys.hmacShaKeyFor}).
 * </p>
 *
 * <h3>Injected properties (from {@code application.properties}):</h3>
 * <ul>
 *   <li>{@code jwt.secret} — Base64-encoded 256-bit secret key</li>
 *   <li>{@code jwt.expiration-ms} — access-token TTL in milliseconds</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMs;

    /**
     * Constructs the util and eagerly derives the HMAC key from the
     * Base64-encoded secret so it is validated at startup.
     *
     * @param secret       Base64-encoded 256-bit secret
     * @param expirationMs token time-to-live in milliseconds
     */
    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
        log.info("JwtUtil initialised – token TTL = {} ms", expirationMs);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Token Generation
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Generates a signed JWT for the given authenticated user.
     * <p>
     * The token contains:
     * <ul>
     *   <li><b>sub</b> — the user's email address</li>
     *   <li><b>roles</b> — comma-separated list of granted authorities</li>
     *   <li><b>iat</b> — issued-at timestamp</li>
     *   <li><b>exp</b> — expiration timestamp</li>
     * </ul>
     *
     * @param userDetails the authenticated user
     * @return a compact JWS string
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();

        // Pack all granted authorities (e.g. ROLE_ADMIN) into a "roles" claim
        String roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        extraClaims.put("roles", roles);

        return buildToken(extraClaims, userDetails.getUsername());
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Token Extraction
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Extracts the subject (email) from the token.
     *
     * @param token the compact JWS string
     * @return the email stored in the {@code sub} claim
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts a single claim using a resolver function.
     *
     * @param token          the compact JWS string
     * @param claimsResolver a function that pulls the desired claim
     * @param <T>            return type of the claim
     * @return the resolved claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parses the token and returns the full {@link Claims} body.
     * <p>
     * Uses the modern jjwt 0.12.x builder API:
     * {@code Jwts.parser().verifyWith(key).build().parseSignedClaims(token)}.
     * </p>
     *
     * @param token the compact JWS string
     * @return parsed claims
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Token Validation
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Validates a token against the provided {@link UserDetails}.
     * <p>
     * A token is valid when:
     * <ol>
     *   <li>The {@code sub} claim matches the user's email, AND</li>
     *   <li>The token has not expired.</li>
     * </ol>
     *
     * @param token       the compact JWS string
     * @param userDetails the user to validate against
     * @return {@code true} if the token is valid
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    /**
     * Checks whether the token's expiration date is before the current time.
     *
     * @param token the compact JWS string
     * @return {@code true} if the token has expired
     */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ────────────────────────────────────────────────────────────────────────

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(signingKey)
                .compact();
    }
}

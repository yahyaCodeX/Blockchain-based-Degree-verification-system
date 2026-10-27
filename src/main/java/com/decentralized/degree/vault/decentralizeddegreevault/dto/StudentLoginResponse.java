package com.decentralized.degree.vault.decentralizeddegreevault.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO returned upon successful student authentication.
 * Includes a signed Student JWT token for stateless enterprise authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentLoginResponse {
    private String token;
    @Builder.Default
    private String tokenType = "Bearer";
    private long expiresIn;
    @Builder.Default
    private String role = "ROLE_STUDENT";
    private VerifyDegreeResponse degree;
}

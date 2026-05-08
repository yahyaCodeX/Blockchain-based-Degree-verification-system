package com.decentralized.degree.vault.decentralizeddegreevault.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload returned after successful authentication.
 * Contains the signed JWT access token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
}

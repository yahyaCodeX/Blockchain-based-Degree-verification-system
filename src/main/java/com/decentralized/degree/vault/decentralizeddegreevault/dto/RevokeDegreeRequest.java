package com.decentralized.degree.vault.decentralizeddegreevault.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevokeDegreeRequest {
    @NotBlank(message = "globalCredentialId is required")
    private String globalCredentialId;

    @NotBlank(message = "Revocation reason is required")
    private String reason;
}

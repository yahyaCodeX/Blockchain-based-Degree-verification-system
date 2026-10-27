package com.decentralized.degree.vault.decentralizeddegreevault.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentLoginRequest {
    @NotBlank(message = "Roll Number, GUCI, or Email is required")
    private String identifier;

    @NotBlank(message = "Access PIN is required")
    private String pin;
}

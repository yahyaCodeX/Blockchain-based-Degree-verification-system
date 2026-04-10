package com.decentralized.degree.vault.decentralizeddegreevault.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for transaction response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private String transactionHash;
    private String status;
    private String message;
}


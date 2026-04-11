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
    private String issuerAddress;

    public TransactionResponse(String transactionHash, String status, String message) {
        this.transactionHash = transactionHash;
        this.status = status;
        this.message = message;
        this.issuerAddress = null;
    }
}

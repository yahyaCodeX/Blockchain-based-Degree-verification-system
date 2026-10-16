package com.decentralized.degree.vault.decentralizeddegreevault.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for transaction response
 */
@Data
@NoArgsConstructor
public class TransactionResponse {
    private String transactionHash;
    private String status;
    private String message;
    private String issuerAddress;
    private String verificationUrl;
    private String qrCodeBase64;

    public TransactionResponse(String transactionHash, String status, String message) {
        this.transactionHash = transactionHash;
        this.status = status;
        this.message = message;
        this.issuerAddress = null;
        this.verificationUrl = null;
        this.qrCodeBase64 = null;
    }

    public TransactionResponse(String transactionHash, String status, String message, String issuerAddress) {
        this.transactionHash = transactionHash;
        this.status = status;
        this.message = message;
        this.issuerAddress = issuerAddress;
        this.verificationUrl = null;
        this.qrCodeBase64 = null;
    }

    public TransactionResponse(
            String transactionHash,
            String status,
            String message,
            String issuerAddress,
            String verificationUrl,
            String qrCodeBase64
    ) {
        this.transactionHash = transactionHash;
        this.status = status;
        this.message = message;
        this.issuerAddress = issuerAddress;
        this.verificationUrl = verificationUrl;
        this.qrCodeBase64 = qrCodeBase64;
    }
}

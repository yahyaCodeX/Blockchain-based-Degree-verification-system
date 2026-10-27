package com.decentralized.degree.vault.decentralizeddegreevault.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyDegreeResponse {
    private String globalCredentialId;
    private String universityCode;
    private String rollNo;
    private String status;
    private String message;
    private String studentName;
    private String fatherName;
    private String department;
    private String cgpa;
    private String dataHash;
    private String ipfsCid;
    private String issuerAddress;
    private long issueDate;
    private boolean verified;
    private boolean authentic;
    private boolean tampered;
    private String integrityMessage;
    private String verificationUrl;
    private String qrCodeBase64;
    private boolean revoked;
    private String revocationReason;
    private Long revocationDate;
    private String revocationTxHash;
    private String studentEmail;
    private String studentPin;
}


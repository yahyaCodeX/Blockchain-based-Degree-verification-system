package com.decentralized.degree.vault.decentralizeddegreevault.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for degree verification response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyDegreeResponse {
    private String degreeId;
    private String status;
    private String message;
    private String studentId;
    private String studentName;
    private String fatherName;
    private String department;
    private String cgpa;
    private String documentHash;
    private String ipfsCid;
    private String issuerAddress;
    private long issueDate;
    private boolean verified;
    private boolean authentic;
    private boolean tampered;
    private String integrityMessage;
}

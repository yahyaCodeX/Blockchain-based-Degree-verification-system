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
    private String studentId;
    private String documentHash;
    private String ipfsCid;
    private long issueDate;
    private boolean verified;
}


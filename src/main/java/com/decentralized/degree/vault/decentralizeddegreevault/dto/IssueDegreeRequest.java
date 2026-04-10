package com.decentralized.degree.vault.decentralizeddegreevault.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for issuing a degree
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssueDegreeRequest {
    private String degreeId;
    private String studentId;
    private String documentHash;
    private String ipfsCid;
}


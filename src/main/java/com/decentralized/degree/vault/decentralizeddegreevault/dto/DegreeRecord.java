package com.decentralized.degree.vault.decentralizeddegreevault.dto;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class DegreeRecord {
    @Id
    private String degreeId;
    private String studentId;
    private String studentName;
    private String fatherName;
    private String department;
    private String cgpa;
    private String documentHash;
    private String ipfsCid;
    private String issuerAddress;
    private long issueDate;
}

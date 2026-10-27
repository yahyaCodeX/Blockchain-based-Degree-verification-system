package com.decentralized.degree.vault.decentralizeddegreevault.dto;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class DegreeRecord {
    @Id
    private String globalCredentialId;
    private String universityCode;
    private String rollNo;
    private String studentId;
    private String studentName;
    private String fatherName;
    private String department;
    private String cgpa;
    private String dataHash;
    private String ipfsCid;
    private String issuerAddress;
    private long issueDate;
    private boolean isRevoked = false;
    private String revocationReason;
    private Long revocationDate;
    private String revocationTxHash;
    private String studentEmail;
    private String studentPin;
    private boolean emailSent = false;

    public DegreeRecord(
            String globalCredentialId,
            String universityCode,
            String rollNo,
            String studentName,
            String cgpa,
            String dataHash,
            String ipfsCid,
            String issuerAddress,
            long issueDate
    ) {
        this.globalCredentialId = globalCredentialId;
        this.universityCode = universityCode;
        this.rollNo = rollNo;
        this.studentName = studentName;
        this.cgpa = cgpa;
        this.dataHash = dataHash;
        this.ipfsCid = ipfsCid;
        this.issuerAddress = issuerAddress;
        this.issueDate = issueDate;
    }

    public DegreeRecord(
            String rollNo,
            String studentId,
            String studentName,
            String fatherName,
            String department,
            String cgpa,
            String dataHash,
            String ipfsCid,
            String issuerAddress,
            long issueDate
    ) {
        this.globalCredentialId = null;
        this.universityCode = null;
        this.rollNo = rollNo;
        this.studentId = studentId;
        this.studentName = studentName;
        this.fatherName = fatherName;
        this.department = department;
        this.cgpa = cgpa;
        this.dataHash = dataHash;
        this.ipfsCid = ipfsCid;
        this.issuerAddress = issuerAddress;
        this.issueDate = issueDate;
    }

    @PrePersist
    @PreUpdate
    void populateGlobalCredentialId() {
        if ((globalCredentialId == null || globalCredentialId.isBlank())
                && universityCode != null && rollNo != null
                && !universityCode.isBlank() && !rollNo.isBlank()) {
            globalCredentialId = universityCode.trim() + "-" + rollNo.trim();
        }
    }
}


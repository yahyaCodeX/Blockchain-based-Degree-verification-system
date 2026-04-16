package com.decentralized.degree.vault.decentralizeddegreevault.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for issuing a degree
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssueDegreeRequest {
    @NotNull(message = "Degree ID cannot be null")
    @NotBlank(message = "Degree ID cannot be blank")
    private String degreeId;

    @NotNull(message = "Student ID cannot be null")
    @NotBlank(message = "Student ID cannot be blank")
    private String studentId;

    @NotNull(message = "Student name cannot be null")
    @NotBlank(message = "Student name cannot be blank")
    private String studentName;

    @NotNull(message = "Father name cannot be null")
    @NotBlank(message = "Father name cannot be blank")
    private String fatherName;

    @NotNull(message = "Department cannot be null")
    @NotBlank(message = "Department cannot be blank")
    private String department;

    @NotNull(message = "CGPA cannot be null")
    @NotBlank(message = "CGPA cannot be blank")
    private String cgpa;

    private String documentHash;

    private String ipfsCid;
}

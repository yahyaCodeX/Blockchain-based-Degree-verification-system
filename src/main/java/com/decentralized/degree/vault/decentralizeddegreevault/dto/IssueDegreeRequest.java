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
    @NotNull(message = "University code cannot be null")
    @NotBlank(message = "University code cannot be blank")
    private String universityCode;

    @NotNull(message = "Roll number cannot be null")
    @NotBlank(message = "Roll number cannot be blank")
    private String rollNo;

    private String studentId;

    @NotNull(message = "Student name cannot be null")
    @NotBlank(message = "Student name cannot be blank")
    private String studentName;

    @NotNull(message = "CGPA cannot be null")
    @NotBlank(message = "CGPA cannot be blank")
    private String cgpa;

    private String fatherName;
    private String department;

    private String documentHash;

    private String ipfsCid;
}

package com.decentralized.degree.vault.decentralizeddegreevault.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing the result of processing a single row in a batch CSV upload.
 * Each row in the batch gets its own BatchUploadResult tracking success/failure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchUploadResult {
    /** The row number in the CSV file (1-indexed, excluding header) */
    private int row;

    /** The degree ID from the CSV row */
    private String degreeId;

    /** The student ID from the CSV row */
    private String studentId;

    /** Status of this row's processing: SUCCESS or FAILED */
    private String status;

    /** The blockchain transaction hash if successful, null otherwise */
    private String transactionHash;

    /** Error message if processing failed, null otherwise */
    private String error;

    /** Timestamp when this row was processed */
    private LocalDateTime processedAt;
}

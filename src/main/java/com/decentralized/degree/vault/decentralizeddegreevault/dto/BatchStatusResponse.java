package com.decentralized.degree.vault.decentralizeddegreevault.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing the overall status of a batch degree issuance job.
 * Tracks progress from PENDING → PROCESSING → COMPLETED/PARTIAL_FAILURE.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchStatusResponse {
    /** Unique identifier for this batch job (UUID) */
    private String batchId;

    /** Overall status: PENDING, PROCESSING, COMPLETED, or PARTIAL_FAILURE */
    private String overallStatus;

    /** Total number of records in the CSV file */
    private int totalRecords;

    /** Number of successfully processed records */
    private int successCount;

    /** Number of records that failed processing */
    private int failureCount;

    /** Detailed results for each row in the batch */
    private List<BatchUploadResult> results;

    /** Timestamp when the batch job was created/started */
    private LocalDateTime startedAt;

    /** Timestamp when the batch job completed processing */
    private LocalDateTime completedAt;
}

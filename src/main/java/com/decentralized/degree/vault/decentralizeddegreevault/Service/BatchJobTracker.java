package com.decentralized.degree.vault.decentralizeddegreevault.Service;

import com.decentralized.degree.vault.decentralizeddegreevault.dto.BatchStatusResponse;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.BatchUploadResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory batch job tracker that manages the lifecycle of batch degree issuance jobs.
 * Tracks each batch from creation through processing to completion.
 *
 * <p>Current implementation uses ConcurrentHashMap for thread-safe in-memory storage.
 * This is sufficient for single-instance deployments and development/testing.</p>
 *
 * // KAFKA-READY: Replace ConcurrentHashMap with Kafka producer.
 * // Each updateResult() call becomes a Kafka message to topic 'degree-batch-results'.
 * // A Kafka consumer service would update a persistent DB record.
 * // BatchJobTracker becomes BatchJobProducer with kafkaTemplate.send()
 * //
 * // Migration steps:
 * // 1. Replace ConcurrentHashMap with KafkaTemplate<String, BatchUploadResult>
 * // 2. createBatch() → Insert initial record into DB + send START message to Kafka
 * // 3. updateResult() → kafkaTemplate.send("degree-batch-results", batchId, result)
 * // 4. completeBatch() → Send COMPLETE message to Kafka topic
 * // 5. getBatchStatus() → Query persistent DB instead of in-memory map
 * // 6. A separate @KafkaListener consumer aggregates results into the DB
 */
@Slf4j
@Service
public class BatchJobTracker {

    private final ConcurrentHashMap<String, BatchStatusResponse> batchJobs = new ConcurrentHashMap<>();

    /**
     * Creates and registers a new batch job with PENDING status.
     *
     * @param batchId      unique identifier for the batch (UUID)
     * @param totalRecords total number of records to be processed
     */
    // KAFKA-READY: This would also produce a message to 'degree-batch-lifecycle' topic
    // with event type CREATE, allowing other services to react to new batch jobs.
    public void createBatch(String batchId, int totalRecords) {
        BatchStatusResponse status = new BatchStatusResponse();
        status.setBatchId(batchId);
        status.setOverallStatus("PENDING");
        status.setTotalRecords(totalRecords);
        status.setSuccessCount(0);
        status.setFailureCount(0);
        status.setResults(new ArrayList<>());
        status.setStartedAt(LocalDateTime.now());
        status.setCompletedAt(null);

        batchJobs.put(batchId, status);
        log.info("Batch job created: {} with {} records", batchId, totalRecords);
    }

    /**
     * Updates the batch job with the result of processing a single row.
     * Automatically transitions status from PENDING to PROCESSING on first update.
     *
     * @param batchId the batch identifier
     * @param result  the processing result for a single row
     */
    // KAFKA-READY: This method becomes kafkaTemplate.send("degree-batch-results", batchId, result).
    // The Kafka consumer on the other end would aggregate results into a persistent store.
    public void updateResult(String batchId, BatchUploadResult result) {
        BatchStatusResponse status = batchJobs.get(batchId);
        if (status == null) {
            log.warn("Attempted to update non-existent batch: {}", batchId);
            return;
        }

        // Thread-safe update using synchronized block on the status object
        synchronized (status) {
            status.setOverallStatus("PROCESSING");
            status.getResults().add(result);

            if ("SUCCESS".equals(result.getStatus())) {
                status.setSuccessCount(status.getSuccessCount() + 1);
            } else {
                status.setFailureCount(status.getFailureCount() + 1);
            }
        }

        log.debug("Batch {} - Row {} processed: {} (degreeId: {})",
                batchId, result.getRow(), result.getStatus(), result.getDegreeId());
    }

    /**
     * Marks the batch job as completed.
     * Sets the overall status to COMPLETED (all success) or PARTIAL_FAILURE (some failures).
     *
     * @param batchId the batch identifier
     */
    // KAFKA-READY: This would produce a COMPLETE event to 'degree-batch-lifecycle' topic
    // and trigger downstream notifications (email, webhook, etc.) via Kafka consumers.
    public void completeBatch(String batchId) {
        BatchStatusResponse status = batchJobs.get(batchId);
        if (status == null) {
            log.warn("Attempted to complete non-existent batch: {}", batchId);
            return;
        }

        synchronized (status) {
            if (status.getFailureCount() > 0) {
                status.setOverallStatus("PARTIAL_FAILURE");
            } else {
                status.setOverallStatus("COMPLETED");
            }
            status.setCompletedAt(LocalDateTime.now());
        }

        log.info("Batch {} completed — success: {}, failures: {}, status: {}",
                batchId, status.getSuccessCount(), status.getFailureCount(), status.getOverallStatus());
    }

    /**
     * Retrieves the current status of a batch job.
     *
     * @param batchId the batch identifier
     * @return the batch status response, or null if not found
     */
    // KAFKA-READY: This would query a persistent database (e.g., MySQL/PostgreSQL)
    // instead of the in-memory ConcurrentHashMap, enabling multi-instance deployments.
    public BatchStatusResponse getBatchStatus(String batchId) {
        return batchJobs.get(batchId);
    }
}

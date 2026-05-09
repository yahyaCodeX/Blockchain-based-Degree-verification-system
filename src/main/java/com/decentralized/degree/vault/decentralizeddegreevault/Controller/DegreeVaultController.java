package com.decentralized.degree.vault.decentralizeddegreevault.Controller;

import com.decentralized.degree.vault.decentralizeddegreevault.Service.BatchJobTracker;
import com.decentralized.degree.vault.decentralizeddegreevault.Service.DegreeVaultService;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.BatchStatusResponse;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.IssueDegreeRequest;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.TransactionResponse;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.VerifyDegreeResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for Degree Vault operations.
 * Supports single degree issuance, verification, batch CSV upload, and statistics.
 */
@RestController
@RequestMapping("/api/v1/degrees")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DegreeVaultController {

    private static final Logger log = LoggerFactory.getLogger(DegreeVaultController.class);

    private final DegreeVaultService degreeVaultService;
    private final BatchJobTracker batchJobTracker;

    public DegreeVaultController(DegreeVaultService degreeVaultService, BatchJobTracker batchJobTracker) {
        this.degreeVaultService = degreeVaultService;
        this.batchJobTracker = batchJobTracker;
    }

    /**
     * Issue a new degree
     * POST /api/v1/degrees/issue
     */
    @PostMapping(value = "/issue", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TransactionResponse> issueDegree(
            @RequestPart("file") MultipartFile file,
            @RequestPart("data") @Valid IssueDegreeRequest request) {
        log.info("Received request to issue degree: {}", request.getDegreeId());

        if (hasMissingIssueFields(request)) {
            return ResponseEntity.badRequest()
                    .body(new TransactionResponse(null, "ERROR", "All required fields must be non-empty"));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new TransactionResponse(null, "ERROR", "File must not be empty"));
        }

        TransactionResponse response = degreeVaultService.issueDegree(file, request);

        if ("SUCCESS".equals(response.getStatus())) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Verify a degree
     * POST /api/v1/degrees/verify/{degreeId}
     */
    @PostMapping(value = "/verify/{degreeId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerifyDegreeResponse> verifyDegree(
            @PathVariable("degreeId") String degreeId,
            @RequestPart("file") MultipartFile file) {
        log.info("Received request to verify degree: {}", degreeId);

        if (isBlank(degreeId)) {
            return ResponseEntity.badRequest().body(new VerifyDegreeResponse(
                    null,
                    "INVALID_REQUEST",
                    "degreeId path variable is required",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    false,
                    false,
                    false,
                    "Invalid Request"
            ));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new VerifyDegreeResponse(
                    degreeId,
                    "INVALID_REQUEST",
                    "File must not be empty",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    false,
                    false,
                    false,
                    "No file provided for verification."
            ));
        }

        VerifyDegreeResponse response = degreeVaultService.verifyDegree(degreeId, file);

        if ("ERROR".equals(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        // NOT_FOUND is still a valid verify outcome, so return 200 with an explicit status/message.
        return ResponseEntity.ok(response);
    }

    /**
     * Get university admin address
     * GET /api/v1/degrees/admin
     */
    @GetMapping("/admin")
    public ResponseEntity<String> getUniversityAdmin() {
        log.info("Received request to fetch university admin");

        try {
            String adminAddress = degreeVaultService.getUniversityAdmin();
            return ResponseEntity.ok(adminAddress);
        } catch (Exception e) {
            log.error("Error fetching university admin: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint
     * GET /api/v1/degrees/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Degree Vault Backend is running");
    }

    // ==================================================================================
    // BATCH PROCESSING ENDPOINTS
    // ==================================================================================

    /**
     * Upload a CSV file for batch degree issuance.
     * The file is processed asynchronously and a batchId is returned immediately.
     * <p>
     * POST /api/v1/degrees/issue/batch
     * Content-Type: multipart/form-data
     * Field: "file" — the CSV file
     * <p>
     * Expected CSV format (with header):
     * degreeId,studentId,documentHash,ipfsCid
     *
     * @param csvFile the CSV file containing degree records
     * @param zipFile the ZIP file containing degree PDFs
     * @return 202 Accepted with batchId and status polling URL
     */
    // KAFKA-READY: In a Kafka architecture, this endpoint would:
    // 1. Parse CSV and produce messages to 'degree-batch-commands' topic
    // 2. Return 202 immediately with a batchId
    // 3. Kafka consumers would process each degree independently
    @PostMapping(value = "/issue/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> issueBatch(
            @RequestPart("csvFile") MultipartFile csvFile,
            @RequestPart("zipFile") MultipartFile zipFile) {
        log.info("Received batch upload request (CSV + ZIP)");

        // Validate files are not empty
        if (csvFile == null || csvFile.isEmpty() || zipFile == null || zipFile.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Files must not be empty");
            errorResponse.put("message", "Please upload both csvFile and zipFile");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Validate CSV
        String originalCsvName = csvFile.getOriginalFilename();
        if (originalCsvName == null || !originalCsvName.toLowerCase().endsWith(".csv")) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid CSV file type");
            errorResponse.put("message", "Please upload a valid .csv file");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Validate ZIP
        String originalZipName = zipFile.getOriginalFilename();
        if (originalZipName == null || !originalZipName.toLowerCase().endsWith(".zip")) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid ZIP file type");
            errorResponse.put("message", "Please upload a valid .zip file containing the PDFs");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        String batchId= UUID.randomUUID().toString();
        try {
            java.nio.file.Path tempCsv = java.nio.file.Files.createTempFile("batch_csv_" + batchId, ".csv");
            csvFile.transferTo(tempCsv.toFile());

            java.nio.file.Path tempZip = java.nio.file.Files.createTempFile("batch_zip_" + batchId, ".zip");
            zipFile.transferTo(tempZip.toFile());

            // Fire and forget — async processing starts in background
            CompletableFuture<String> futureId = degreeVaultService.issueBatchAsync(batchId, tempCsv, tempZip);

            Map<String, Object> response = new HashMap<>();
            response.put("batchId", batchId);
            response.put("message", "Batch processing started");
            response.put("statusUrl", "/api/v1/degrees/batch/" + batchId + "/status");

            log.info("Batch upload accepted with batchId: {}", batchId);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (Exception e) {
            log.error("Error initiating batch upload: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to initiate batch processing");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Poll the status of a batch degree issuance job.
     * <p>
     * GET /api/v1/degrees/batch/{batchId}/status
     *
     * @param batchId the UUID of the batch job
     * @return 200 with BatchStatusResponse if found, 404 if not found
     */
    @GetMapping("/batch/{batchId}/status")
    public ResponseEntity<?> getBatchStatus(@PathVariable("batchId") String batchId) {
        log.info("Received request to check batch status: {}", batchId);

        BatchStatusResponse status = batchJobTracker.getBatchStatus(batchId);

        if (status == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Batch not found");
            errorResponse.put("message", "No batch job found with id: " + batchId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Get on-chain statistics: total degrees issued and max batch size.
     * <p>
     * GET /api/v1/degrees/stats
     *
     * @return JSON with totalDegreesIssued and maxBatchSize
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        log.info("Received request to fetch degree stats");

        try {
            BigInteger totalDegrees = degreeVaultService.getDegreeCount();
            BigInteger maxBatchSize = degreeVaultService.getBatchSize();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalDegreesIssued", totalDegrees.intValue());
            stats.put("maxBatchSize", maxBatchSize.intValue());

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error fetching stats: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to fetch stats");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ==================================================================================
    // HELPER METHODS
    // ==================================================================================

    private boolean hasMissingIssueFields(IssueDegreeRequest request) {
        return isBlank(request.getDegreeId())
                || isBlank(request.getStudentId())
                || isBlank(request.getStudentName())
                || isBlank(request.getFatherName())
                || isBlank(request.getDepartment())
                || isBlank(request.getCgpa());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

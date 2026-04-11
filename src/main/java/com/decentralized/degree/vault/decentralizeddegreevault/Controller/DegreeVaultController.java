package com.decentralized.degree.vault.decentralizeddegreevault.Controller;

import com.decentralized.degree.vault.decentralizeddegreevault.Service.DegreeVaultService;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.IssueDegreeRequest;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.TransactionResponse;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.VerifyDegreeResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Degree Vault operations
 */
@RestController
@RequestMapping("/api/v1/degrees")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DegreeVaultController {

    private static final Logger log = LoggerFactory.getLogger(DegreeVaultController.class);

    private final DegreeVaultService degreeVaultService;

    public DegreeVaultController(DegreeVaultService degreeVaultService) {
        this.degreeVaultService = degreeVaultService;
    }

    /**
     * Issue a new degree
     * POST /api/v1/degrees/issue
     */
    @PostMapping("/issue")
    public ResponseEntity<TransactionResponse> issueDegree(@Valid @RequestBody IssueDegreeRequest request) {
        log.info("Received request to issue degree: {}", request.getDegreeId());

        if (hasMissingIssueFields(request)) {
            return ResponseEntity.badRequest()
                    .body(new TransactionResponse(null, "ERROR", "All required fields must be non-empty"));
        }

        TransactionResponse response = degreeVaultService.issueDegree(request);

        if ("SUCCESS".equals(response.getStatus())) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Verify a degree
     * GET /api/v1/degrees/verify/{degreeId}
     */
    @GetMapping("/verify/{degreeId}")
    public ResponseEntity<VerifyDegreeResponse> verifyDegree(@PathVariable String degreeId) {
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
                    false
            ));
        }

        VerifyDegreeResponse response = degreeVaultService.verifyDegree(degreeId);

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

    private boolean hasMissingIssueFields(IssueDegreeRequest request) {
        return isBlank(request.getDegreeId())
                || isBlank(request.getStudentId())
                || isBlank(request.getStudentName())
                || isBlank(request.getFatherName())
                || isBlank(request.getDepartment())
                || isBlank(request.getCgpa())
                || isBlank(request.getDocumentHash())
                || isBlank(request.getIpfsCid());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

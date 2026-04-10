package com.decentralized.degree.vault.decentralizeddegreevault.Controller;

import com.decentralized.degree.vault.decentralizeddegreevault.Service.DegreeVaultService;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.IssueDegreeRequest;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.TransactionResponse;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.VerifyDegreeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Degree Vault operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/degrees")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DegreeVaultController {

    private final DegreeVaultService degreeVaultService;

    public DegreeVaultController(DegreeVaultService degreeVaultService) {
        this.degreeVaultService = degreeVaultService;
    }

    /**
     * Issue a new degree
     * POST /api/v1/degrees/issue
     */
    @PostMapping("/issue")
    public ResponseEntity<TransactionResponse> issueDegree(@RequestBody IssueDegreeRequest request) {
        log.info("Received request to issue degree: {}", request.getDegreeId());

        if (request.getDegreeId() == null || request.getDegreeId().isEmpty() ||
            request.getStudentId() == null || request.getStudentId().isEmpty() ||
            request.getDocumentHash() == null || request.getDocumentHash().isEmpty() ||
            request.getIpfsCid() == null || request.getIpfsCid().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new TransactionResponse(null, "ERROR", "All fields are required"));
        }

        TransactionResponse response = degreeVaultService.issueDegree(request);

        if ("SUCCESS".equals(response.getStatus())) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Verify a degree
     * GET /api/v1/degrees/verify/{degreeId}
     */
    @GetMapping("/verify/{degreeId}")
    public ResponseEntity<VerifyDegreeResponse> verifyDegree(@PathVariable String degreeId) {
        log.info("Received request to verify degree: {}", degreeId);

        if (degreeId == null || degreeId.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        VerifyDegreeResponse response = degreeVaultService.verifyDegree(degreeId);

        if (response.isVerified()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
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
}


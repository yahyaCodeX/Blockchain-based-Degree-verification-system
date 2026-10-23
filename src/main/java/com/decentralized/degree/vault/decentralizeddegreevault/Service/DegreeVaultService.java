package com.decentralized.degree.vault.decentralizeddegreevault.Service;

import com.decentralized.degree.vault.decentralizeddegreevault.dto.IssueDegreeRequest;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.TransactionResponse;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.RevokeDegreeRequest;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.VerifyDegreeResponse;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.DegreeRecord;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.BatchUploadResult;
import com.decentralized.degree.vault.decentralizeddegreevault.util.FileHashingUtil;
import com.decentralized.degree.vault.decentralizeddegreevault.util.QrCodeUtil;
import com.decentralized.degree.vault.decentralizeddegreevault.repository.DegreeRecordRepository;
import com.example.contract.DegreeVault;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tuples.generated.Tuple8;
import org.web3j.tx.exceptions.ContractCallException;
import org.web3j.tx.gas.ContractGasProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service layer for interacting with DegreeVault smart contract.
 * Supports single degree issuance, verification, and async batch processing via CSV upload.
 */
// KAFKA-READY: In a Kafka-based architecture, the batch processing methods would consume
// from a Kafka topic instead of being directly called via @Async. Each CSV row would be
// published as a separate message to 'degree-issue-commands' topic, and a @KafkaListener
// method would process each message independently, enabling horizontal scaling.
@Slf4j
@Service
public class DegreeVaultService {

    private final Web3j web3j;
    private final ContractGasProvider gasProvider;
    private final String contractAddress;
    private final String privateKey;
    private final DegreeRecordRepository degreeRecordRepository;
    private final PinataService pinataService;
    private final BatchJobTracker batchJobTracker;
    private final String verificationBaseUrl;

    @Value("${batch.max-size:25}")
    private int maxBatchSize;

    public DegreeVaultService(
            Web3j web3j,
            ContractGasProvider gasProvider,
            @Value("${contract.address}") String contractAddress,
            @Value("${wallet.private-key}") String privateKey,
            DegreeRecordRepository degreeRecordRepository,
            PinataService pinataService,
            BatchJobTracker batchJobTracker,
            @Value("${verification.base-url}") String verificationBaseUrl
    ) {
        this.web3j = web3j;
        this.gasProvider = gasProvider;
        this.contractAddress = contractAddress;
        this.privateKey = privateKey;
        this.degreeRecordRepository = degreeRecordRepository;
        this.pinataService = pinataService;
        this.batchJobTracker = batchJobTracker;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    /**
     * Issues a new GUCI-based degree to the blockchain.
     */
    public TransactionResponse issueDegree(MultipartFile file, IssueDegreeRequest request) {
        try {
            String globalCredentialId = buildGlobalCredentialId(request.getUniversityCode(), request.getRollNo());
            String dataHash = calculateDataHash(request.getUniversityCode(), request.getRollNo(), request.getStudentName(), request.getCgpa());

            Credentials credentials = Credentials.create(privateKey);
            String issuerAddress = credentials.getAddress();

            String ipfsCid = pinataService.uploadFile(file);
            request.setDocumentHash(dataHash);
            request.setIpfsCid(ipfsCid);

            DegreeVault contract = DegreeVault.load(contractAddress, web3j, credentials, gasProvider);

            if (degreeRecordRepository.existsById(globalCredentialId)) {
                return new TransactionResponse(null, "FAILED", "Degree already exists", issuerAddress, null, null);
            }

            Optional<Tuple8<String, String, String, String, String, String, String, BigInteger>> existingDegree =
                    findDegreeOnChain(contract, globalCredentialId);
            if (existingDegree.isPresent()) {
                Tuple8<String, String, String, String, String, String, String, BigInteger> onChain = existingDegree.get();
                if (!dataHash.equals(onChain.component6())) {
                    return new TransactionResponse(null, "FAILED", "Degree already exists on blockchain with a different data hash", issuerAddress, null, null);
                }

                DegreeRecord record = new DegreeRecord(
                        globalCredentialId,
                        request.getUniversityCode(),
                        request.getRollNo(),
                        request.getStudentName(),
                        request.getCgpa(),
                        dataHash,
                        ipfsCid,
                        issuerAddress,
                        onChain.component8().longValue()
                );
                degreeRecordRepository.save(record);

                String verificationUrl = buildVerificationUrl(globalCredentialId);
                return new TransactionResponse(null, "SUCCESS", "Degree already existed on blockchain; metadata synced", issuerAddress, verificationUrl, generateQrCode(verificationUrl));
            }

            log.info("Issuing degree: {} for university: {}", globalCredentialId, request.getUniversityCode());

            RemoteFunctionCall<org.web3j.protocol.core.methods.response.TransactionReceipt> txFunction =
                    contract.issueDegree(
                            globalCredentialId,
                            request.getUniversityCode(),
                            request.getRollNo(),
                            request.getStudentName(),
                            request.getCgpa(),
                            dataHash
                    );

            var transactionReceipt = txFunction.send();
            if (transactionReceipt.isStatusOK()) {
                DegreeRecord record = new DegreeRecord(
                        globalCredentialId,
                        request.getUniversityCode(),
                        request.getRollNo(),
                        request.getStudentName(),
                        request.getCgpa(),
                        dataHash,
                        ipfsCid,
                        issuerAddress,
                        System.currentTimeMillis() / 1000
                );
                record.setStudentId(request.getStudentId());
                record.setFatherName(request.getFatherName());
                record.setDepartment(request.getDepartment());
                degreeRecordRepository.save(record);

                String verificationUrl = buildVerificationUrl(globalCredentialId);
                return new TransactionResponse(
                        transactionReceipt.getTransactionHash(),
                        "SUCCESS",
                        "Degree issued successfully",
                        issuerAddress,
                        verificationUrl,
                        generateQrCode(verificationUrl)
                );
            }

            return new TransactionResponse(transactionReceipt.getTransactionHash(), "FAILED", "Transaction failed", issuerAddress, null, null);
        } catch (Exception e) {
            log.error("Error issuing degree: {}", e.getMessage(), e);
            return new TransactionResponse(null, "ERROR", "Error issuing degree: " + e.getMessage(), null, null, null);
        }
    }

    private Optional<Tuple8<String, String, String, String, String, String, String, BigInteger>> findDegreeOnChain(DegreeVault contract, String globalCredentialId) {
        try {
            RemoteFunctionCall<Tuple8<String, String, String, String, String, String, String, BigInteger>> txFunction = contract.verifyDegree(globalCredentialId);
            return Optional.of(txFunction.send());
        } catch (Exception ex) {
            if (ex instanceof ContractCallException && isDegreeNotFoundRevert((ContractCallException) ex)) {
                return Optional.empty();
            }
            throw new RuntimeException("Failed to check whether degree exists on-chain: " + ex.getMessage(), ex);
        }
    }

    /**
     * Verifies a degree using its GUCI.
     */
    public VerifyDegreeResponse verifyDegree(String globalCredentialId, MultipartFile file) {
        try {
            Credentials credentials = Credentials.create(privateKey);
            DegreeVault contract = DegreeVault.load(contractAddress, web3j, credentials, gasProvider);

            RemoteFunctionCall<Tuple8<String, String, String, String, String, String, String, BigInteger>> txFunction =
                    contract.verifyDegree(globalCredentialId);

            Tuple8<String, String, String, String, String, String, String, BigInteger> result;
            try {
                result = txFunction.send();
            } catch (ContractCallException ex) {
                if (isDegreeNotFoundRevert(ex)) {
                    return buildUnverifiedResponse(globalCredentialId, "Degree not found on blockchain");
                }
                throw ex;
            }

            String onChainGuci = result.component1();
            String universityCode = result.component2();
            String rollNo = result.component3();
            String studentName = result.component4();
            String cgpa = result.component5();
            String dataHash = result.component6();
            String issuerAddress = result.component7();
            long issueDateLong = result.component8().longValue();

            Optional<DegreeRecord> metadataRecord = degreeRecordRepository.findById(globalCredentialId);
            String fatherName = metadataRecord.map(DegreeRecord::getFatherName).orElse("N/A");
            String department = metadataRecord.map(DegreeRecord::getDepartment).orElse("N/A");
            String ipfsCid = metadataRecord.map(DegreeRecord::getIpfsCid).orElse("N/A");

            // Check revocation status (on-chain + database)
            boolean isRevoked = false;
            String revocationReason = null;
            Long revocationDate = null;
            String revocationTxHash = null;

            try {
                var revTuple = contract.getRevocationStatus(globalCredentialId).send();
                if (Boolean.TRUE.equals(revTuple.component1())) {
                    isRevoked = true;
                    revocationReason = revTuple.component2();
                    revocationDate = revTuple.component4().longValue();
                }
            } catch (Exception ex) {
                log.debug("Contract getRevocationStatus call: {}", ex.getMessage());
            }

            if (metadataRecord.isPresent() && metadataRecord.get().isRevoked()) {
                isRevoked = true;
                if (revocationReason == null || revocationReason.isBlank()) {
                    revocationReason = metadataRecord.get().getRevocationReason();
                }
                if (revocationDate == null || revocationDate == 0L) {
                    revocationDate = metadataRecord.get().getRevocationDate();
                }
                revocationTxHash = metadataRecord.get().getRevocationTxHash();
            }

            boolean hashMatches = dataHash.equals(metadataRecord.map(DegreeRecord::getDataHash).orElse(dataHash));
            String status;
            String message;
            String integrityMessage;
            boolean authentic;
            boolean verified;
            boolean tampered;

            if (isRevoked) {
                status = "REVOKED";
                authentic = false;
                verified = false;
                tampered = false;
                message = "DEGREE REVOKED: This degree has been officially revoked by the university registrar.";
                integrityMessage = "Official Revocation Reason: " + (revocationReason != null && !revocationReason.isBlank() ? revocationReason : "Revoked by issuing authority");
            } else if (hashMatches) {
                status = "VERIFIED";
                authentic = true;
                verified = true;
                tampered = false;
                message = "Degree verified successfully";
                integrityMessage = "Credential is authentic. Verified by issuing university on blockchain.";
            } else {
                status = "TAMPERED";
                authentic = false;
                verified = true;
                tampered = true;
                message = "Document integrity check failed";
                integrityMessage = "Warning: Stored data does not match the official blockchain record.";
            }

            String verificationUrl = buildVerificationUrl(globalCredentialId);
            String qrBase64 = authentic ? generateQrCode(verificationUrl) : null;

            return VerifyDegreeResponse.builder()
                    .globalCredentialId(globalCredentialId)
                    .universityCode(universityCode)
                    .rollNo(rollNo)
                    .studentName(studentName)
                    .fatherName(fatherName)
                    .department(department)
                    .cgpa(cgpa)
                    .dataHash(dataHash)
                    .ipfsCid(ipfsCid)
                    .issuerAddress(issuerAddress)
                    .issueDate(issueDateLong)
                    .status(status)
                    .message(message)
                    .verified(verified)
                    .authentic(authentic)
                    .tampered(tampered)
                    .integrityMessage(integrityMessage)
                    .verificationUrl(verificationUrl)
                    .qrCodeBase64(qrBase64)
                    .revoked(isRevoked)
                    .revocationReason(revocationReason)
                    .revocationDate(revocationDate)
                    .revocationTxHash(revocationTxHash)
                    .build();
        } catch (Exception e) {
            log.error("Error verifying degree {}: {}", globalCredentialId, e.getMessage(), e);
            return VerifyDegreeResponse.builder()
                    .globalCredentialId(globalCredentialId)
                    .status("ERROR")
                    .message("Verification failed: " + e.getMessage())
                    .integrityMessage("Error during verification: " + e.getMessage())
                    .build();
        }
    }

    private boolean isDegreeNotFoundRevert(ContractCallException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("degree record not found") || normalized.contains("record not found");
    }

    private VerifyDegreeResponse buildUnverifiedResponse(String globalCredentialId, String message) {
        return VerifyDegreeResponse.builder()
                .globalCredentialId(globalCredentialId)
                .status("NOT_FOUND")
                .message(message)
                .integrityMessage("Degree record does not exist on the blockchain.")
                .build();
    }

    private String buildGlobalCredentialId(String universityCode, String rollNo) {
        return universityCode.trim() + "-" + rollNo.trim();
    }

    private String buildVerificationUrl(String globalCredentialId) {
        return verificationBaseUrl + globalCredentialId;
    }

    private String generateQrCode(String verificationUrl) {
        try {
            return QrCodeUtil.toBase64Png(verificationUrl, 220, 220);
        } catch (Exception e) {
            log.warn("Failed to generate QR code for {}: {}", verificationUrl, e.getMessage());
            return null;
        }
    }

    private String calculateDataHash(String universityCode, String rollNo, String studentName, String cgpa) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = universityCode.trim() + "|" + rollNo.trim() + "|" + studentName.trim() + "|" + cgpa.trim();
            byte[] hashBytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hashBytes.length);
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private String resolveIssuerAddress(DegreeVault contract, Credentials credentials) {
        try {
            return contract.universityAdmin().send();
        } catch (Exception ex) {
            log.warn("Falling back to signer address for issuer resolution: {}", ex.getMessage());
            return credentials.getAddress();
        }
    }

    /**
     * Gets the university admin address
     */
    public String getUniversityAdmin() {
        try {
            Credentials credentials = Credentials.create(privateKey);

            DegreeVault contract = DegreeVault.load(
                    contractAddress,
                    web3j,
                    credentials,
                    gasProvider
            );

            log.info("Fetching university admin address");
            String adminAddress = contract.universityAdmin().send();
            log.info("University admin: {}", adminAddress);

            return adminAddress;

        } catch (Exception e) {
            log.error("Error fetching university admin: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch university admin: " + e.getMessage());
        }
    }

    // ==================================================================================
    // BATCH PROCESSING METHODS
    // ==================================================================================

    /**
     * Asynchronously processes a CSV file containing batch degree issuance requests.
     * <p>
     * CSV format (with header row):
     * universityCode,rollNo,studentName,cgpa,studentId,fatherName,department
     * <p>
     * Processes rows in chunks (up to maxBatchSize) using the smart contract's
     * issueBatch() function. Each chunk is a single blockchain transaction.
     *
     * @param csvFile the uploaded CSV file
     * @param zipFile the uploaded ZIP file containing degree PDFs
     * @return CompletableFuture containing the batch ID for status tracking
     */
    // KAFKA-READY: In a Kafka architecture, this method would:
    // 1. Parse the CSV and produce individual messages to 'degree-issue-commands' topic
    // 2. Each message = one degree row with the batchId as the Kafka message key
    // 3. Multiple Kafka consumers can then process rows in parallel across instances
    // 4. The @Async annotation would be removed since Kafka handles async naturally
    @Async("batchExecutor")
    public CompletableFuture<String> issueBatchAsync(String batchId, Path csvFile, Path zipFile) {

        log.info("Starting batch processing with batchId: {}", batchId);

        Path tempDir = null;
        try {
            // Parse CSV file
            List<IssueDegreeRequest> degrees = parseCsvFile(csvFile);

            if (degrees.isEmpty()) {
                batchJobTracker.createBatch(batchId, 0);
                batchJobTracker.completeBatch(batchId);
                log.warn("Batch {} - CSV file is empty or contains no valid rows", batchId);
                return CompletableFuture.completedFuture(batchId);
            }

            // Register batch in tracker
            batchJobTracker.createBatch(batchId, degrees.size());

            // Unzip files to temporary directory
            tempDir = unzipToTempDir(zipFile, batchId);

            // Pre-process (hash and IPFS upload) and chunk
            List<IssueDegreeRequest> chunk = new ArrayList<>();
            List<Integer> chunkRows = new ArrayList<>();
            int rowCounter = 0;

            for (IssueDegreeRequest request : degrees) {
                rowCounter++;
                String expectedFileName = request.getStudentId() + ".pdf";
                Path pdfPath = tempDir.resolve(expectedFileName);

                if (!Files.exists(pdfPath)) {
                    BatchUploadResult errorResult = new BatchUploadResult();
                    errorResult.setRow(rowCounter);
                    errorResult.setRollNo(request.getRollNo());
                    errorResult.setStudentId(request.getStudentId());
                    errorResult.setStatus("FAILED");
                    errorResult.setError("Missing PDF file in ZIP: " + expectedFileName);
                    errorResult.setProcessedAt(LocalDateTime.now());
                    batchJobTracker.updateResult(batchId, errorResult);
                    continue;
                }

                try {
                    byte[] fileBytes = Files.readAllBytes(pdfPath);
                    String fileHash = FileHashingUtil.calculateSHA256(fileBytes);
                    request.setDocumentHash(fileHash);

                    String ipfsCid = pinataService.uploadBytes(fileBytes, expectedFileName);
                    request.setIpfsCid(ipfsCid);

                    chunk.add(request);
                    chunkRows.add(rowCounter);

                    if (chunk.size() >= maxBatchSize) {
                        processChunkOnChain(batchId, chunk, chunkRows);
                        chunk.clear();
                        chunkRows.clear();
                    }
                } catch (Exception e) {
                    BatchUploadResult errorResult = new BatchUploadResult();
                    errorResult.setRow(rowCounter);
                    errorResult.setRollNo(request.getRollNo());
                    errorResult.setStudentId(request.getStudentId());
                    errorResult.setStatus("FAILED");
                    errorResult.setError("Failed to process PDF: " + e.getMessage());
                    errorResult.setProcessedAt(LocalDateTime.now());
                    batchJobTracker.updateResult(batchId, errorResult);
                }
            }

            if (!chunk.isEmpty()) {
                processChunkOnChain(batchId, chunk, chunkRows);
            }

            // Mark batch as complete
            batchJobTracker.completeBatch(batchId);
            log.info("Batch {} processing completed", batchId);

        } catch (Exception e) {
            log.error("Fatal error processing batch {}: {}", batchId, e.getMessage(), e);
            // Ensure batch is marked complete even on fatal errors
            batchJobTracker.completeBatch(batchId);
        } finally {
            // Clean up temporary directory
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                         .sorted(Comparator.reverseOrder())
                         .map(Path::toFile)
                         .forEach(File::delete);
                } catch (Exception e) {
                    log.error("Failed to delete temp directory: {}", e.getMessage());
                }
            }
            try {
                if (csvFile != null) Files.deleteIfExists(csvFile);
            } catch (Exception e) {
                log.error("Failed to delete temp csv file: {}", e.getMessage());
            }
            try {
                if (zipFile != null) Files.deleteIfExists(zipFile);
            } catch (Exception e) {
                log.error("Failed to delete temp zip file: {}", e.getMessage());
            }
        }

        return CompletableFuture.completedFuture(batchId);
    }

    /**
     * Issues a batch of degrees on-chain using the smart contract's issueBatch() function.
     * Builds the four parallel arrays from the list of degree requests.
     *
     * @param degrees list of degree issuance requests (max 100)
     * @return TransactionResponse with the batch transaction hash and status
     */
    public TransactionResponse issueBatchOnChain(List<IssueDegreeRequest> degrees) {
        try {
            Credentials credentials = Credentials.create(privateKey);
            String issuerAddress = credentials.getAddress();

            DegreeVault contract = DegreeVault.load(
                    contractAddress,
                    web3j,
                    credentials,
                    gasProvider
            );

            // Build parallel arrays for the smart contract call
            List<String> globalCredentialIds = new ArrayList<>();
            List<String> universityCodes = new ArrayList<>();
            List<String> rollNos = new ArrayList<>();
            List<String> studentNames = new ArrayList<>();
            List<String> cgpAs = new ArrayList<>();
            List<String> dataHashes = new ArrayList<>();

            for (IssueDegreeRequest deg : degrees) {
                String uniCode = (deg.getUniversityCode() != null && !deg.getUniversityCode().isBlank())
                        ? deg.getUniversityCode().trim().toUpperCase() : "MUET";
                String roll = deg.getRollNo().trim().toUpperCase();
                String guci = buildGlobalCredentialId(uniCode, roll);
                String dataHash = calculateDataHash(uniCode, roll, deg.getStudentName(), deg.getCgpa());
                deg.setDocumentHash(dataHash);

                globalCredentialIds.add(guci);
                universityCodes.add(uniCode);
                rollNos.add(roll);
                studentNames.add(deg.getStudentName());
                cgpAs.add(deg.getCgpa());
                dataHashes.add(dataHash);
            }

            log.info("Issuing batch of {} degrees on-chain", degrees.size());

            // Call the 6-parameter issueBatch() function on the smart contract
            RemoteFunctionCall<org.web3j.protocol.core.methods.response.TransactionReceipt> txFunction =
                    contract.issueBatch(globalCredentialIds, universityCodes, rollNos, studentNames, cgpAs, dataHashes);

            var transactionReceipt = txFunction.send();

            if (transactionReceipt.isStatusOK()) {
                log.info("Batch transaction successful. Hash: {}", transactionReceipt.getTransactionHash());
                return new TransactionResponse(
                        transactionReceipt.getTransactionHash(),
                        "SUCCESS",
                        "Batch of " + degrees.size() + " degrees issued successfully",
                        issuerAddress
                );
            } else {
                log.error("Batch transaction failed with status: {}", transactionReceipt.getStatus());
                return new TransactionResponse(
                        transactionReceipt.getTransactionHash(),
                        "FAILED",
                        "Batch transaction failed on-chain",
                        null
                );
            }

        } catch (Exception e) {
            log.error("Error issuing batch on-chain: {}", e.getMessage(), e);
            return new TransactionResponse(
                    null,
                    "ERROR",
                    "Error issuing batch: " + e.getMessage(),
                    null
            );
        }
    }

    /**
     * Issues a single degree on-chain (used as fallback when batch fails).
     *
     * @param request the degree issuance request
     * @return TransactionResponse with the transaction hash and status
     */
    private TransactionResponse issueSingleOnChain(IssueDegreeRequest request) {
        try {
            Credentials credentials = Credentials.create(privateKey);
            String issuerAddress = credentials.getAddress();

            DegreeVault contract = DegreeVault.load(
                    contractAddress,
                    web3j,
                    credentials,
                    gasProvider
            );

            String uniCode = (request.getUniversityCode() != null && !request.getUniversityCode().isBlank())
                    ? request.getUniversityCode().trim().toUpperCase() : "MUET";
            String roll = request.getRollNo().trim().toUpperCase();
            String guci = buildGlobalCredentialId(uniCode, roll);
            String dataHash = calculateDataHash(uniCode, roll, request.getStudentName(), request.getCgpa());
            request.setDocumentHash(dataHash);

            RemoteFunctionCall<org.web3j.protocol.core.methods.response.TransactionReceipt> txFunction =
                    contract.issueDegree(
                            guci,
                            uniCode,
                            roll,
                            request.getStudentName(),
                            request.getCgpa(),
                            dataHash
                    );

            var transactionReceipt = txFunction.send();

            if (transactionReceipt.isStatusOK()) {
                return new TransactionResponse(
                        transactionReceipt.getTransactionHash(),
                        "SUCCESS",
                        "Degree issued successfully",
                        issuerAddress
                );
            } else {
                return new TransactionResponse(
                        transactionReceipt.getTransactionHash(),
                        "FAILED",
                        "Transaction failed",
                        null
                );
            }

        } catch (Exception e) {
            log.error("Error issuing single degree on-chain: {}", e.getMessage(), e);
            return new TransactionResponse(
                    null,
                    "ERROR",
                    "Error issuing degree: " + e.getMessage(),
                    null
            );
        }
    }

    /**
     * Parses a CSV file into a list of IssueDegreeRequest objects.
     * Expected CSV columns: universityCode, rollNo, studentName, cgpa, studentId, fatherName, department
     * The first row is treated as a header and skipped.
     *
     * @param file the uploaded CSV Path
     * @return list of parsed degree requests
     * @throws Exception if the file cannot be read
     */
    private List<IssueDegreeRequest> parseCsvFile(Path file) throws Exception {
        List<IssueDegreeRequest> degrees = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                // Skip header row
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                // Skip empty lines
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] columns = line.split(",", -1);
                if (columns.length < 6) {
                    log.warn("Skipping malformed CSV row (expected at least 6 columns, got {}): {}", columns.length, line);
                    continue;
                }

                IssueDegreeRequest request = new IssueDegreeRequest();
                if (columns.length >= 7) {
                    request.setUniversityCode(columns[0].trim().toUpperCase());
                    request.setRollNo(columns[1].trim().toUpperCase());
                    request.setStudentId(columns[2].trim());
                    request.setStudentName(columns[3].trim());
                    request.setFatherName(columns[4].trim());
                    request.setDepartment(columns[5].trim());
                    request.setCgpa(columns[6].trim());
                } else {
                    request.setUniversityCode("MUET");
                    request.setRollNo(columns[0].trim().toUpperCase());
                    request.setStudentId(columns[1].trim());
                    request.setStudentName(columns[2].trim());
                    request.setFatherName(columns[3].trim());
                    request.setDepartment(columns[4].trim());
                    request.setCgpa(columns[5].trim());
                }

                degrees.add(request);
            }
        }

        log.info("Parsed {} degree records from CSV", degrees.size());
        return degrees;
    }

    private Path unzipToTempDir(Path zipFile, String batchId) throws Exception {
        Path tempDir = Files.createTempDirectory("batch_" + batchId);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                if (!zipEntry.isDirectory()) {
                    // Flatten the directory structure by only using the file name
                    String fileName = Paths.get(zipEntry.getName()).getFileName().toString();
                    Path newFilePath = tempDir.resolve(fileName);
                    Files.copy(zis, newFilePath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipEntry = zis.getNextEntry();
            }
        }
        return tempDir;
    }

    private void processChunkOnChain(String batchId, List<IssueDegreeRequest> chunk, List<Integer> chunkRows) {
        try {
            TransactionResponse txResponse = issueBatchOnChain(chunk);

            for (int i = 0; i < chunk.size(); i++) {
                IssueDegreeRequest deg = chunk.get(i);
                int row = chunkRows.get(i);
                BatchUploadResult result = new BatchUploadResult();
                result.setRow(row);
                result.setRollNo(deg.getRollNo());
                result.setStudentId(deg.getStudentId());
                result.setProcessedAt(LocalDateTime.now());

                if ("SUCCESS".equals(txResponse.getStatus())) {
                    result.setStatus("SUCCESS");
                    result.setTransactionHash(txResponse.getTransactionHash());
                    result.setError(null);
                    saveDegreeToDatabase(deg, txResponse.getIssuerAddress());
                } else {
                    result.setStatus("FAILED");
                    result.setTransactionHash(txResponse.getTransactionHash());
                    result.setError(txResponse.getMessage());
                }
                batchJobTracker.updateResult(batchId, result);
            }
        } catch (Exception e) {
            log.warn("Batch chunk failed, falling back to individual issuance: {}", e.getMessage());
            for (int i = 0; i < chunk.size(); i++) {
                IssueDegreeRequest deg = chunk.get(i);
                int row = chunkRows.get(i);
                BatchUploadResult result = new BatchUploadResult();
                result.setRow(row);
                result.setRollNo(deg.getRollNo());
                result.setStudentId(deg.getStudentId());
                result.setProcessedAt(LocalDateTime.now());

                try {
                    TransactionResponse singleTx = issueSingleOnChain(deg);
                    if ("SUCCESS".equals(singleTx.getStatus())) {
                        result.setStatus("SUCCESS");
                        result.setTransactionHash(singleTx.getTransactionHash());
                        result.setError(null);
                        saveDegreeToDatabase(deg, singleTx.getIssuerAddress());
                    } else {
                        result.setStatus("FAILED");
                        result.setTransactionHash(singleTx.getTransactionHash());
                        result.setError(singleTx.getMessage());
                    }
                } catch (Exception ex) {
                    result.setStatus("FAILED");
                    result.setTransactionHash(null);
                    result.setError("Individual issuance failed: " + ex.getMessage());
                }
                batchJobTracker.updateResult(batchId, result);
            }
        }
    }

    private void saveDegreeToDatabase(IssueDegreeRequest request, String issuerAddress) {
        try {
            String uniCode = (request.getUniversityCode() != null && !request.getUniversityCode().isBlank())
                    ? request.getUniversityCode().trim().toUpperCase() : "MUET";
            String roll = request.getRollNo().trim().toUpperCase();
            String guci = buildGlobalCredentialId(uniCode, roll);

            DegreeRecord record = new DegreeRecord(
                    guci,
                    uniCode,
                    roll,
                    request.getStudentName(),
                    request.getCgpa(),
                    request.getDocumentHash(),
                    request.getIpfsCid(),
                    issuerAddress,
                    System.currentTimeMillis() / 1000 // Approximate issue date
            );
            record.setStudentId(request.getStudentId());
            record.setFatherName(request.getFatherName());
            record.setDepartment(request.getDepartment());
            degreeRecordRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to save degree metadata to DB for roll number {}: {}", request.getRollNo(), e.getMessage());
        }
    }

    /**
     * Gets the total count of degrees issued on-chain.
     *
     * @return the total number of degrees issued
     */
    public BigInteger getDegreeCount() {
        try {
            Credentials credentials = Credentials.create(privateKey);

            DegreeVault contract = DegreeVault.load(
                    contractAddress,
                    web3j,
                    credentials,
                    gasProvider
            );

            return contract.getDegreeCount().send();

        } catch (Exception e) {
            log.error("Error fetching degree count: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch degree count: " + e.getMessage());
        }
    }

    public List<DegreeRecord> getAllDegrees() {
        return degreeRecordRepository.findAllByOrderByIssueDateDesc();
    }

    public List<DegreeRecord> searchDegrees(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllDegrees();
        }
        return degreeRecordRepository.searchDegrees(query.trim());
    }

    public BigInteger getRevokedCount() {
        try {
            Credentials credentials = Credentials.create(privateKey);
            DegreeVault contract = DegreeVault.load(contractAddress, web3j, credentials, gasProvider);
            return contract.getRevokedCount().send();
        } catch (Exception e) {
            log.debug("Could not fetch on-chain revoked count: {}", e.getMessage());
            return BigInteger.valueOf(degreeRecordRepository.countByIsRevokedTrue());
        }
    }

    public TransactionResponse revokeDegree(RevokeDegreeRequest request) {
        String guci = request.getGlobalCredentialId().trim().toUpperCase();
        String reason = request.getReason().trim();

        log.info("Initiating on-chain revocation for GUCI: {} with reason: {}", guci, reason);

        try {
            Credentials credentials = Credentials.create(privateKey);
            String issuerAddress = credentials.getAddress();
            DegreeVault contract = DegreeVault.load(contractAddress, web3j, credentials, gasProvider);

            // Execute on-chain revocation transaction
            var txReceipt = contract.revokeDegree(guci, reason).send();

            if (!txReceipt.isStatusOK()) {
                return new TransactionResponse(txReceipt.getTransactionHash(), "FAILED", "On-chain revocation transaction failed", issuerAddress);
            }

            String txHash = txReceipt.getTransactionHash();

            // Update local database record
            Optional<DegreeRecord> opt = degreeRecordRepository.findById(guci);
            if (opt.isPresent()) {
                DegreeRecord record = opt.get();
                record.setRevoked(true);
                record.setRevocationReason(reason);
                record.setRevocationDate(System.currentTimeMillis() / 1000);
                record.setRevocationTxHash(txHash);
                degreeRecordRepository.save(record);
            }

            log.info("Degree {} successfully revoked on blockchain with tx: {}", guci, txHash);
            return new TransactionResponse(txHash, "SUCCESS", "Degree revoked successfully on blockchain", issuerAddress);

        } catch (Exception e) {
            log.error("Error revoking degree {}: {}", guci, e.getMessage(), e);
            return new TransactionResponse(null, "ERROR", "Failed to revoke degree: " + e.getMessage());
        }
    }

    /**
     * Gets the maximum batch size allowed by the smart contract.
     *
     * @return the max batch size
     */
    public BigInteger getBatchSize() {
        try {
            Credentials credentials = Credentials.create(privateKey);

            DegreeVault contract = DegreeVault.load(
                    contractAddress,
                    web3j,
                    credentials,
                    gasProvider
            );

            return contract.getBatchSize().send();

        } catch (Exception e) {
            log.error("Error fetching batch size: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch batch size: " + e.getMessage());
        }
    }
}




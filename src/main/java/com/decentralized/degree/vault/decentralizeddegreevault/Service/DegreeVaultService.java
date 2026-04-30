package com.decentralized.degree.vault.decentralizeddegreevault.Service;

import com.decentralized.degree.vault.decentralizeddegreevault.dto.IssueDegreeRequest;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.TransactionResponse;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.VerifyDegreeResponse;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.DegreeRecord;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.BatchUploadResult;
import com.decentralized.degree.vault.decentralizeddegreevault.util.FileHashingUtil;
import com.decentralized.degree.vault.decentralizeddegreevault.repository.DegreeRecordRepository;
import com.example.contract.DegreeVault;
import io.ipfs.api.IPFS;
import io.ipfs.api.NamedStreamable;
import io.ipfs.api.MerkleNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tuples.generated.Tuple4;
import org.web3j.tx.exceptions.ContractCallException;
import org.web3j.tx.gas.ContractGasProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    private final IPFS ipfs;
    private final BatchJobTracker batchJobTracker;

    @Value("${batch.max-size:100}")
    private int maxBatchSize;

    public DegreeVaultService(
            Web3j web3j,
            ContractGasProvider gasProvider,
            @Value("${contract.address}") String contractAddress,
            @Value("${wallet.private-key}") String privateKey,
            DegreeRecordRepository degreeRecordRepository,
            IPFS ipfs,
            BatchJobTracker batchJobTracker
    ) {
        this.web3j = web3j;
        this.gasProvider = gasProvider;
        this.contractAddress = contractAddress;
        this.privateKey = privateKey;
        this.degreeRecordRepository = degreeRecordRepository;
        this.ipfs = ipfs;
        this.batchJobTracker = batchJobTracker;
    }

    /**
     * Issues a new degree to the blockchain
     */
    public TransactionResponse issueDegree(MultipartFile file, IssueDegreeRequest request) {
        try {
            // Fast duplicate check to avoid unnecessary on-chain write attempts.
            if (degreeRecordRepository.existsById(request.getDegreeId())) {
                return new TransactionResponse(
                        null,
                        "FAILED",
                        "Degree already exists",
                        null
                );
            }

            // Create credentials from private key
            Credentials credentials = Credentials.create(privateKey);
            String issuerAddress = credentials.getAddress();

            // Calculate file hash
            String fileHash = FileHashingUtil.calculateSHA256(file);
            request.setDocumentHash(fileHash);

            // Upload to IPFS
            NamedStreamable.ByteArrayWrapper byteStream = new NamedStreamable.ByteArrayWrapper(file.getOriginalFilename(), file.getBytes());
            java.util.List<MerkleNode> response = ipfs.add(byteStream);
            String ipfsCid = response.get(0).hash.toBase58();
            request.setIpfsCid(ipfsCid);

            // Load the smart contract
            DegreeVault contract = DegreeVault.load(
                    contractAddress,
                    web3j,
                    credentials,
                    gasProvider
            );

            log.info("Issuing degree: {} for student: {}", request.getDegreeId(), request.getStudentId());

            // Send only on-chain fields required by the smart contract.
            RemoteFunctionCall<org.web3j.protocol.core.methods.response.TransactionReceipt> txFunction =
                    contract.issueDegree(
                            request.getDegreeId(),
                            request.getStudentId(),
                            request.getDocumentHash(),
                            request.getIpfsCid()
                    );

            // Send the transaction and wait for receipt
            var transactionReceipt = txFunction.send();

            if (transactionReceipt.isStatusOK()) {
                log.info("Degree issued successfully. Transaction hash: {}", transactionReceipt.getTransactionHash());

                // Persist full metadata off-chain only after successful blockchain write.
                DegreeRecord record = new DegreeRecord(
                        request.getDegreeId(),
                        request.getStudentId(),
                        request.getStudentName(),
                        request.getFatherName(),
                        request.getDepartment(),
                        request.getCgpa(),
                        request.getDocumentHash(),
                        request.getIpfsCid(),
                        issuerAddress,
                        System.currentTimeMillis() / 1000 // Approximate issue date
                );
                degreeRecordRepository.save(record);

                return new TransactionResponse(
                        transactionReceipt.getTransactionHash(),
                        "SUCCESS",
                        "Degree issued successfully",
                        issuerAddress
                );
            } else {
                log.error("Transaction failed with status: {}", transactionReceipt.getStatus());
                return new TransactionResponse(
                        transactionReceipt.getTransactionHash(),
                        "FAILED",
                        "Transaction failed",
                        null
                );
            }

        } catch (Exception e) {
            log.error("Error issuing degree: {}", e.getMessage(), e);
            return new TransactionResponse(
                    null,
                    "ERROR",
                    "Error issuing degree: " + e.getMessage(),
                    null
            );
        }
    }

    /**
     * Verifies a degree on the blockchain using Total Integrity logic.
     * Queries the contract by degreeId, then compares the uploaded file's
     * SHA-256 hash against the on-chain document hash.
     */
    public VerifyDegreeResponse verifyDegree(String degreeId, MultipartFile file) {
        try {
            // Step 1: Calculate SHA-256 hash of the uploaded file
            String fileHash = FileHashingUtil.calculateSHA256(file);

            // Step 2: Query the smart contract by degreeId
            Credentials credentials = Credentials.create(privateKey);
            DegreeVault contract = DegreeVault.load(
                    contractAddress,
                    web3j,
                    credentials,
                    gasProvider
            );

            log.info("Verifying degree on-chain for degreeId: {}", degreeId);

            RemoteFunctionCall<Tuple4<String, String, String, BigInteger>> txFunction =
                    contract.verifyDegree(degreeId);
            Tuple4<String, String, String, BigInteger> result;
            try {
                result = txFunction.send();
            } catch (ContractCallException ex) {
                if (isDegreeNotFoundRevert(ex)) {
                    log.info("Degree {} not found on-chain: {}", degreeId, ex.getMessage());
                    return buildUnverifiedResponse(degreeId, "Degree not found on blockchain");
                }
                throw ex;
            }

            // Step 3: Check if the degree exists on-chain (issueDate > 0)
            // Tuple4 components: (studentId, docHash, ipfsCid, issueDate)
            String studentId = result.component1();
            String onChainDocHash = result.component2();
            String ipfsCid = result.component3();
            long issueDateLong = result.component4().longValue();
            boolean existsOnChain = issueDateLong > 0;

            if (!existsOnChain) {
                return buildUnverifiedResponse(degreeId, "Degree not found on blockchain");
            }

            // Step 4 & 5: Crucial Hash Comparison — Total Integrity check
            boolean verified;
            boolean authentic;
            boolean tampered;
            String status;
            String integrityMessage;

            if (fileHash.equals(onChainDocHash)) {
                // Hashes match exactly — document is authentic
                status = "VERIFIED";
                verified = true;
                authentic = true;
                tampered = false;
                integrityMessage = "Document is authentic. File hash matches exactly with the blockchain record.";
            } else {
                // Hashes do NOT match — document has been tampered with
                status = "TAMPERED";
                verified = false;
                authentic = false;
                tampered = true;
                integrityMessage = "CRITICAL WARNING: The uploaded document has been tampered with and does not match the blockchain record.";
                log.warn("TAMPER DETECTED for degree {}: uploaded hash [{}] vs on-chain hash [{}]",
                        degreeId, fileHash, onChainDocHash);
            }

            // Step 6: Fetch metadata from database
            String issuerAddress = resolveIssuerAddress(contract, credentials);
            Optional<DegreeRecord> metadataRecord = degreeRecordRepository.findById(degreeId);

            String studentName = metadataRecord.map(DegreeRecord::getStudentName).orElse("N/A");
            String fatherName = metadataRecord.map(DegreeRecord::getFatherName).orElse("N/A");
            String department = metadataRecord.map(DegreeRecord::getDepartment).orElse("N/A");
            String cgpa = metadataRecord.map(DegreeRecord::getCgpa).orElse("N/A");

            String message;
            if (verified) {
                message = metadataRecord.isPresent()
                        ? "Degree verified on blockchain and metadata loaded from database"
                        : "Degree verified on blockchain. Metadata is not available in database yet";
            } else {
                message = "Document integrity check FAILED. The uploaded file does not match the blockchain record.";
            }

            if (metadataRecord.isEmpty()) {
                log.warn("Degree {} exists on-chain but metadata is missing in database", degreeId);
            }

            return new VerifyDegreeResponse(
                    degreeId,
                    status,
                    message,
                    studentId,
                    studentName,
                    fatherName,
                    department,
                    cgpa,
                    fileHash,
                    ipfsCid,
                    issuerAddress,
                    issueDateLong,
                    verified,
                    authentic,
                    tampered,
                    integrityMessage
            );

        } catch (Exception e) {
            log.error("Error verifying degree {}: {}", degreeId, e.getMessage(), e);
            return new VerifyDegreeResponse(
                    degreeId,
                    "ERROR",
                    "Verification failed due to blockchain/network issue",
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
                    "Error during verification: " + e.getMessage()
            );
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

    private VerifyDegreeResponse buildUnverifiedResponse(String degreeId, String message) {
        return new VerifyDegreeResponse(
                degreeId,
                "NOT_FOUND",
                message,
                null,
                "N/A",
                "N/A",
                "N/A",
                "N/A",
                null,
                null,
                null,
                0,
                false,
                false,
                false,
                "Degree record does not exist on the blockchain."
        );
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
     * degreeId,studentId,documentHash,ipfsCid
     * <p>
     * Processes rows in chunks (up to maxBatchSize) using the smart contract's
     * issueBatch() function. Each chunk is a single blockchain transaction.
     *
     * @param file the uploaded CSV file
     * @return CompletableFuture containing the batch ID for status tracking
     */
    // KAFKA-READY: In a Kafka architecture, this method would:
    // 1. Parse the CSV and produce individual messages to 'degree-issue-commands' topic
    // 2. Each message = one degree row with the batchId as the Kafka message key
    // 3. Multiple Kafka consumers can then process rows in parallel across instances
    // 4. The @Async annotation would be removed since Kafka handles async naturally
    @Async("batchExecutor")
    public CompletableFuture<String> issueBatchAsync(String batchId,MultipartFile csvFile, MultipartFile zipFile) {

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
                    errorResult.setDegreeId(request.getDegreeId());
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

                    NamedStreamable.ByteArrayWrapper byteStream = new NamedStreamable.ByteArrayWrapper(expectedFileName, fileBytes);
                    List<MerkleNode> response = ipfs.add(byteStream);
                    String ipfsCid = response.get(0).hash.toBase58();
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
                    errorResult.setDegreeId(request.getDegreeId());
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
            List<String> degreeIds = new ArrayList<>();
            List<String> studentIds = new ArrayList<>();
            List<String> docHashes = new ArrayList<>();
            List<String> ipfsCids = new ArrayList<>();

            for (IssueDegreeRequest deg : degrees) {
                degreeIds.add(deg.getDegreeId());
                studentIds.add(deg.getStudentId());
                docHashes.add(deg.getDocumentHash());
                ipfsCids.add(deg.getIpfsCid());
            }

            log.info("Issuing batch of {} degrees on-chain", degrees.size());

            // Call the issueBatch() function on the smart contract
            RemoteFunctionCall<org.web3j.protocol.core.methods.response.TransactionReceipt> txFunction =
                    contract.issueBatch(degreeIds, studentIds, docHashes, ipfsCids);

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

            RemoteFunctionCall<org.web3j.protocol.core.methods.response.TransactionReceipt> txFunction =
                    contract.issueDegree(
                            request.getDegreeId(),
                            request.getStudentId(),
                            request.getDocumentHash(),
                            request.getIpfsCid()
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
     * Expected CSV columns: degreeId, studentId, documentHash, ipfsCid
     * The first row is treated as a header and skipped.
     *
     * @param file the uploaded CSV MultipartFile
     * @return list of parsed degree requests
     * @throws Exception if the file cannot be read
     */
    private List<IssueDegreeRequest> parseCsvFile(MultipartFile file) throws Exception {
        List<IssueDegreeRequest> degrees = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
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
                request.setDegreeId(columns[0].trim());
                request.setStudentId(columns[1].trim());
                request.setStudentName(columns[2].trim());
                request.setFatherName(columns[3].trim());
                request.setDepartment(columns[4].trim());
                request.setCgpa(columns[5].trim());

                degrees.add(request);
            }
        }

        log.info("Parsed {} degree records from CSV", degrees.size());
        return degrees;
    }

    private Path unzipToTempDir(MultipartFile zipFile, String batchId) throws Exception {
        Path tempDir = Files.createTempDirectory("batch_" + batchId);
        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
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
                result.setDegreeId(deg.getDegreeId());
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
                result.setDegreeId(deg.getDegreeId());
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
            DegreeRecord record = new DegreeRecord(
                    request.getDegreeId(),
                    request.getStudentId(),
                    request.getStudentName(),
                    request.getFatherName(),
                    request.getDepartment(),
                    request.getCgpa(),
                    request.getDocumentHash(),
                    request.getIpfsCid(),
                    issuerAddress,
                    System.currentTimeMillis() / 1000 // Approximate issue date
            );
            degreeRecordRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to save degree metadata to DB for degree {}: {}", request.getDegreeId(), e.getMessage());
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

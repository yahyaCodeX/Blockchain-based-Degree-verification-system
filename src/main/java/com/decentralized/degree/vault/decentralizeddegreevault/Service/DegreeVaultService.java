package com.decentralized.degree.vault.decentralizeddegreevault.Service;

import com.decentralized.degree.vault.decentralizeddegreevault.dto.IssueDegreeRequest;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.TransactionResponse;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.VerifyDegreeResponse;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.DegreeRecord;
import com.decentralized.degree.vault.decentralizeddegreevault.repository.DegreeRecordRepository;
import com.example.contract.DegreeVault;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tuples.generated.Tuple4;
import org.web3j.tx.exceptions.ContractCallException;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Service layer for interacting with DegreeVault smart contract
 */
@Slf4j
@Service
public class DegreeVaultService {

    private final Web3j web3j;
    private final ContractGasProvider gasProvider;
    private final String contractAddress;
    private final String privateKey;
    private final DegreeRecordRepository degreeRecordRepository;

    public DegreeVaultService(
            Web3j web3j,
            ContractGasProvider gasProvider,
            @Value("${contract.address}") String contractAddress,
            @Value("${wallet.private-key}") String privateKey,
            DegreeRecordRepository degreeRecordRepository
    ) {
        this.web3j = web3j;
        this.gasProvider = gasProvider;
        this.contractAddress = contractAddress;
        this.privateKey = privateKey;
        this.degreeRecordRepository = degreeRecordRepository;
    }

    /**
     * Issues a new degree to the blockchain
     */
    public TransactionResponse issueDegree(IssueDegreeRequest request) {
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
     * Verifies a degree on the blockchain
     */
    public VerifyDegreeResponse verifyDegree(String degreeId) {
        try {
            // Blockchain-first verification: always query contract state first.
            Credentials credentials = Credentials.create(privateKey);
            DegreeVault contract = DegreeVault.load(
                    contractAddress,
                    web3j,
                    credentials,
                    gasProvider
            );

            log.info("Verifying degree on-chain: {}", degreeId);

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

            long issueDateLong = result.component4().longValue();
            boolean existsOnChain = issueDateLong > 0;

            if (!existsOnChain) {
                return buildUnverifiedResponse(degreeId, "Degree not found on blockchain");
            }

            String issuerAddress = resolveIssuerAddress(contract, credentials);
            Optional<DegreeRecord> metadataRecord = degreeRecordRepository.findById(degreeId);

            String studentName = metadataRecord.map(DegreeRecord::getStudentName).orElse("N/A");
            String fatherName = metadataRecord.map(DegreeRecord::getFatherName).orElse("N/A");
            String department = metadataRecord.map(DegreeRecord::getDepartment).orElse("N/A");
            String cgpa = metadataRecord.map(DegreeRecord::getCgpa).orElse("N/A");

            String message = metadataRecord.isPresent()
                    ? "Degree verified on blockchain and metadata loaded from database"
                    : "Degree verified on blockchain. Metadata is not available in database yet";

            if (metadataRecord.isEmpty()) {
                log.warn("Degree {} exists on-chain but metadata is missing in database", degreeId);
            }

            return new VerifyDegreeResponse(
                    degreeId,
                    "VERIFIED",
                    message,
                    result.component1(),
                    studentName,
                    fatherName,
                    department,
                    cgpa,
                    result.component2(),
                    result.component3(),
                    issuerAddress,
                    issueDateLong,
                    true
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
                    false
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
                false
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
}

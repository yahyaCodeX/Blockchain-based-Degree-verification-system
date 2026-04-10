package com.decentralized.degree.vault.decentralizeddegreevault.Service;

import com.decentralized.degree.vault.decentralizeddegreevault.dto.IssueDegreeRequest;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.TransactionResponse;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.VerifyDegreeResponse;
import com.example.contract.DegreeVault;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tuples.generated.Tuple4;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;

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

    public DegreeVaultService(
            Web3j web3j,
            ContractGasProvider gasProvider,
            @Value("${contract.address}") String contractAddress,
            @Value("${wallet.private-key}") String privateKey
    ) {
        this.web3j = web3j;
        this.gasProvider = gasProvider;
        this.contractAddress = contractAddress;
        this.privateKey = privateKey;
    }

    /**
     * Issues a new degree to the blockchain
     */
    public TransactionResponse issueDegree(IssueDegreeRequest request) {
        try {
            // Create credentials from private key
            Credentials credentials = Credentials.create(privateKey);

            // Load the smart contract
            DegreeVault contract = DegreeVault.load(
                    contractAddress,
                    web3j,
                    credentials,
                    gasProvider
            );

            log.info("Issuing degree: {} for student: {}", request.getDegreeId(), request.getStudentId());

            // Call the issueDegree function
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
                return new TransactionResponse(
                        transactionReceipt.getTransactionHash(),
                        "SUCCESS",
                        "Degree issued successfully"
                );
            } else {
                log.error("Transaction failed with status: {}", transactionReceipt.getStatus());
                return new TransactionResponse(
                        transactionReceipt.getTransactionHash(),
                        "FAILED",
                        "Transaction failed"
                );
            }

        } catch (Exception e) {
            log.error("Error issuing degree: {}", e.getMessage(), e);
            return new TransactionResponse(
                    null,
                    "ERROR",
                    "Error issuing degree: " + e.getMessage()
            );
        }
    }

    /**
     * Verifies a degree on the blockchain
     */
    public VerifyDegreeResponse verifyDegree(String degreeId) {
        try {
            // Create credentials from private key
            Credentials credentials = Credentials.create(privateKey);

            // Load the smart contract
            DegreeVault contract = DegreeVault.load(
                    contractAddress,
                    web3j,
                    credentials,
                    gasProvider
            );

            log.info("Verifying degree: {}", degreeId);

            // Call the verifyDegree function (read-only)
            RemoteFunctionCall<Tuple4<String, String, String, BigInteger>> txFunction =
                    contract.verifyDegree(degreeId);

            // Send the transaction
            Tuple4<String, String, String, BigInteger> result = txFunction.send();

            log.info("Degree verified successfully: {}", degreeId);

            return new VerifyDegreeResponse(
                    result.component1(),        // studentId
                    result.component2(),        // documentHash
                    result.component3(),        // ipfsCid
                    result.component4().longValue(), // issueDate
                    true
            );

        } catch (Exception e) {
            log.error("Error verifying degree {}: {}", degreeId, e.getMessage(), e);
            return new VerifyDegreeResponse(
                    null,
                    null,
                    null,
                    0,
                    false
            );
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



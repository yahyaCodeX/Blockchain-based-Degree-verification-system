package com.decentralized.degree.vault.decentralizeddegreevault.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

/**
 * Utility class for blockchain operations and validations
 */
@Slf4j
@UtilityClass
public class BlockchainUtil {

    /**
     * Validates if a string is a valid Ethereum address
     */
    public static boolean isValidEthereumAddress(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        return address.matches("^0x[a-fA-F0-9]{40}$");
    }

    /**
     * Validates if a string is a valid private key
     */
    public static boolean isValidPrivateKey(String privateKey) {
        if (privateKey == null || privateKey.isEmpty()) {
            return false;
        }
        try {
            // Try to create credentials from the private key
            Credentials.create(privateKey);
            return true;
        } catch (Exception e) {
            log.warn("Invalid private key format: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Derives the public address from a private key
     */
    public static String getAddressFromPrivateKey(String privateKey) {
        try {
            Credentials credentials = Credentials.create(privateKey);
            return credentials.getAddress();
        } catch (Exception e) {
            log.error("Failed to derive address from private key: {}", e.getMessage());
            throw new RuntimeException("Invalid private key");
        }
    }

    /**
     * Validates if a string is a valid transaction hash
     */
    public static boolean isValidTransactionHash(String hash) {
        if (hash == null || hash.isEmpty()) {
            return false;
        }
        return hash.matches("^0x[a-fA-F0-9]{64}$");
    }

    /**
     * Checks if a string is a valid SHA256 hash
     */
    public static boolean isValidSHA256Hash(String hash) {
        if (hash == null || hash.isEmpty()) {
            return false;
        }
        return hash.matches("^[a-fA-F0-9]{64}$");
    }

    /**
     * Validates IPFS CID format (basic validation)
     */
    public static boolean isValidIPFSCID(String cid) {
        if (cid == null || cid.isEmpty()) {
            return false;
        }
        // Basic IPFS CID validation - starts with Qm
        return cid.startsWith("Qm") || cid.startsWith("bafy");
    }
}


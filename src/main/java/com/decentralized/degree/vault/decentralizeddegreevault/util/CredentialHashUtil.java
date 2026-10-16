package com.decentralized.degree.vault.decentralizeddegreevault.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for generating the deterministic SHA-256 data hash that is anchored
 * on the blockchain for every degree credential.
 *
 * <h2>Hash Input Convention</h2>
 * The hash is computed over a pipe-delimited concatenation of the four core
 * credential fields, all trimmed and upper-cased for normalisation:
 * <pre>
 *   ROLLNO|STUDENTNAME|CGPA|UNIVERSITYCODE
 * </pre>
 * Example: {@code "22CS001|AHMED ALI|3.85|MUET"}
 *
 * <p>This convention must be reproduced exactly on the frontend verification
 * portal when a verifier wants to recompute the hash independently.
 */
@Slf4j
public class CredentialHashUtil {

    private static final String DELIMITER = "|";

    private CredentialHashUtil() {
        // Utility class
    }

    /**
     * Computes the deterministic SHA-256 hash for a degree credential.
     *
     * @param rollNo         student roll number (e.g. "22CS001")
     * @param studentName    full student name (e.g. "Ahmed Ali")
     * @param cgpa           CGPA string (e.g. "3.85")
     * @param universityCode university short code (e.g. "MUET")
     * @return 64-character lowercase hex SHA-256 digest
     * @throws RuntimeException if SHA-256 is not available in the JVM
     */
    public static String computeDataHash(
            String rollNo,
            String studentName,
            String cgpa,
            String universityCode
    ) {
        // Normalise inputs: trim whitespace and upper-case for determinism
        String input = normalise(rollNo)
                + DELIMITER + normalise(studentName)
                + DELIMITER + normalise(cgpa)
                + DELIMITER + normalise(universityCode);

        log.debug("Computing SHA-256 over canonical input: '{}'", input);
        return sha256Hex(input);
    }

    /**
     * Verifies that the supplied raw fields reproduce the expected hash.
     * Useful in the verification flow when no file upload is involved.
     *
     * @param rollNo         roll number
     * @param studentName    student name
     * @param cgpa           CGPA string
     * @param universityCode university code
     * @param expectedHash   the hash stored on-chain
     * @return {@code true} if the recomputed hash matches {@code expectedHash}
     */
    public static boolean verify(
            String rollNo,
            String studentName,
            String cgpa,
            String universityCode,
            String expectedHash
    ) {
        String computed = computeDataHash(rollNo, studentName, cgpa, universityCode);
        boolean matches = computed.equalsIgnoreCase(expectedHash);
        if (!matches) {
            log.warn("Hash mismatch: computed='{}' expected='{}'", computed, expectedHash);
        }
        return matches;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String normalise(String value) {
        return (value == null ? "" : value.trim().toUpperCase());
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JVM spec — this should never happen
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}

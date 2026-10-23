// SPDX-License-Identifier: MIT
pragma solidity ^0.8.19;

/**
 * @title Decentralized Degree Vault (Multi-Tenant / GUCI Edition)
 * @author University Admin
 * @notice Manages issuance and verification of academic credentials on-chain
 *         using a Globally Unique Credential Identifier (GUCI) to prevent
 *         roll-number collisions across multiple universities nationwide.
 * @dev GUCI format: "<UNIVERSITY_CODE>-<ROLL_NO>" (e.g., "MUET-22CS001")
 *      This composite key is the primary mapping key so that two universities
 *      can never collide even when they share identical roll numbers.
 */
contract DegreeVault {
    /// @notice The university admin address that deployed the contract
    address public universityAdmin;

    /// @notice Maximum number of degrees that can be issued in a single batch transaction
    uint256 public constant MAX_BATCH_SIZE = 100;

    /// @notice Counter tracking the total number of degrees issued
    uint256 private _degreeCount;

    /**
     * @notice Represents a single degree record stored on-chain.
     * @dev The `globalCredentialId` (GUCI) is the mapping key — it is also
     *      stored inside the struct for event-log traceability.
     */
    struct Degree {
        string globalCredentialId; // e.g. "MUET-22CS001"
        string universityCode;     // e.g. "MUET"
        string rollNo;             // e.g. "22CS001"
        string studentName;        // Full student name
        string cgpa;               // CGPA as string (e.g. "3.85")
        string dataHash;           // SHA-256 of (rollNo | studentName | cgpa | universityCode)
        address issuer;            // Ethereum address of the issuing admin
        uint256 issueDate;         // Unix timestamp
        bool exists;               // Guard flag
    }

    /// @notice Mapping from GUCI → Degree
    mapping(string => Degree) private degrees;

    // ============================================================
    // EVENTS
    // ============================================================

    /**
     * @notice Emitted when a single degree is issued
     * @param globalCredentialId The GUCI of the issued degree
     * @param universityCode     The issuing university's short code
     * @param rollNo             The student's roll number within that university
     * @param issuer             Ethereum address of the admin who issued it
     */
    event DegreeIssued(
        string indexed globalCredentialId,
        string universityCode,
        string rollNo,
        address indexed issuer
    );

    /**
     * @notice Emitted after a batch of degrees has been successfully issued
     * @param admin      The admin address that initiated the batch
     * @param totalCount The number of degrees issued in the batch
     * @param timestamp  The block timestamp when the batch was processed
     */
    event BatchIssued(
        address indexed admin,
        uint256 totalCount,
        uint256 timestamp
    );

    // ============================================================
    // MODIFIER
    // ============================================================

    /// @notice Restricts function access to the university admin only
    modifier onlyAdmin() {
        require(
            msg.sender == universityAdmin,
            "Only the University Admin can perform this action"
        );
        _;
    }

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    /// @notice Sets the deployer as the university admin
    constructor() {
        universityAdmin = msg.sender;
    }

    // ============================================================
    // CORE FUNCTIONS
    // ============================================================

    /**
     * @notice Issues a new degree record to the blockchain using the GUCI as key.
     * @dev Only callable by the university admin. Reverts if GUCI already exists.
     *      The GUCI is computed off-chain by the backend as:
     *      globalCredentialId = universityCode + "-" + rollNo
     * @param _globalCredentialId Composite GUCI (e.g. "MUET-22CS001")
     * @param _universityCode     Short code of the university (e.g. "MUET")
     * @param _rollNo             Student roll number within that university (e.g. "22CS001")
     * @param _studentName        Full name of the student
     * @param _cgpa               CGPA string (e.g. "3.85")
     * @param _dataHash           SHA-256 hash of core fields for integrity verification
     */
    function issueDegree(
        string memory _globalCredentialId,
        string memory _universityCode,
        string memory _rollNo,
        string memory _studentName,
        string memory _cgpa,
        string memory _dataHash
    ) public onlyAdmin {
        require(
            !degrees[_globalCredentialId].exists,
            "Credential already exists for this GUCI"
        );

        degrees[_globalCredentialId] = Degree({
            globalCredentialId: _globalCredentialId,
            universityCode: _universityCode,
            rollNo: _rollNo,
            studentName: _studentName,
            cgpa: _cgpa,
            dataHash: _dataHash,
            issuer: msg.sender,
            issueDate: block.timestamp,
            exists: true
        });

        _degreeCount++;

        emit DegreeIssued(_globalCredentialId, _universityCode, _rollNo, msg.sender);
    }

    /**
     * @notice Issues a batch of degree records in a single transaction.
     * @dev Only callable by the university admin. All input arrays must have equal length.
     *      Maximum batch size is 100 to prevent exceeding block gas limit.
     *      Emits a DegreeIssued event for each degree, and a BatchIssued event at the end.
     *      Reverts entirely if any GUCI in the batch already exists.
     * @param _globalCredentialIds Array of GUCIs
     * @param _universityCodes     Array of university codes
     * @param _rollNos             Array of roll numbers
     * @param _studentNames        Array of student names
     * @param _cgpas               Array of CGPA strings
     * @param _dataHashes          Array of SHA-256 data hashes
     */
    function issueBatch(
        string[] memory _globalCredentialIds,
        string[] memory _universityCodes,
        string[] memory _rollNos,
        string[] memory _studentNames,
        string[] memory _cgpas,
        string[] memory _dataHashes
    ) public onlyAdmin {
        uint256 batchLength = _globalCredentialIds.length;

        require(batchLength > 0, "Batch cannot be empty");
        require(batchLength <= MAX_BATCH_SIZE, "Batch size exceeds maximum allowed (100)");
        require(
            batchLength == _universityCodes.length &&
            batchLength == _rollNos.length &&
            batchLength == _studentNames.length &&
            batchLength == _cgpas.length &&
            batchLength == _dataHashes.length,
            "All input arrays must have equal length"
        );

        for (uint256 i = 0; i < batchLength; i++) {
            string memory guci = _globalCredentialIds[i];
            require(!degrees[guci].exists, "Duplicate GUCI found in batch");

            degrees[guci] = Degree({
                globalCredentialId: guci,
                universityCode: _universityCodes[i],
                rollNo: _rollNos[i],
                studentName: _studentNames[i],
                cgpa: _cgpas[i],
                dataHash: _dataHashes[i],
                issuer: msg.sender,
                issueDate: block.timestamp,
                exists: true
            });

            _degreeCount++;

            emit DegreeIssued(guci, _universityCodes[i], _rollNos[i], msg.sender);
        }

        emit BatchIssued(msg.sender, batchLength, block.timestamp);
    }

    /**
     * @notice Fetches full degree details for on-chain verification.
     * @dev Reverts if the GUCI does not exist on-chain.
     * @param _globalCredentialId The GUCI to look up
     * @return globalCredentialId The composite GUCI
     * @return universityCode     The university short code
     * @return rollNo             The roll number
     * @return studentName        The student's full name
     * @return cgpa               The CGPA string
     * @return dataHash           The SHA-256 data hash
     * @return issuer             The issuer Ethereum address
     * @return issueDate          Unix timestamp of issuance
     */
    function verifyDegree(
        string memory _globalCredentialId
    ) public view returns (
        string memory globalCredentialId,
        string memory universityCode,
        string memory rollNo,
        string memory studentName,
        string memory cgpa,
        string memory dataHash,
        address issuer,
        uint256 issueDate
    ) {
        require(degrees[_globalCredentialId].exists, "Degree record not found");

        Degree memory d = degrees[_globalCredentialId];
        return (
            d.globalCredentialId,
            d.universityCode,
            d.rollNo,
            d.studentName,
            d.cgpa,
            d.dataHash,
            d.issuer,
            d.issueDate
        );
    }

    /**
     * @notice Returns the maximum allowed batch size
     */
    function getBatchSize() public pure returns (uint256) {
        return MAX_BATCH_SIZE;
    }

    /**
     * @notice Returns the total number of degrees issued so far
     */
    function getDegreeCount() public view returns (uint256) {
        return _degreeCount;
    }

    // ============================================================
    // REVOCATION
    // ============================================================

    /// @notice Struct representing a revocation record
    struct Revocation {
        bool isRevoked;
        string reason;
        address revoker;
        uint256 revocationDate;
    }

    /// @notice Mapping from GUCI -> Revocation record
    mapping(string => Revocation) private revocations;

    /// @notice Total count of revoked degrees
    uint256 private _revokedCount;

    /**
     * @notice Emitted when a degree is officially revoked on-chain
     * @param globalCredentialId The GUCI of the revoked degree
     * @param reason The official revocation reason
     * @param revoker The admin address that executed the revocation
     * @param timestamp Unix timestamp of the revocation
     */
    event DegreeRevoked(
        string indexed globalCredentialId,
        string reason,
        address indexed revoker,
        uint256 timestamp
    );

    /**
     * @notice Revokes an issued degree on-chain.
     * @dev Only callable by the university admin. Reverts if degree does not exist or is already revoked.
     * @param _globalCredentialId The GUCI of the degree to revoke
     * @param _reason The legal / administrative reason for revocation
     */
    function revokeDegree(
        string memory _globalCredentialId,
        string memory _reason
    ) public onlyAdmin {
        require(degrees[_globalCredentialId].exists, "Degree does not exist on blockchain");
        require(!revocations[_globalCredentialId].isRevoked, "Degree is already revoked");
        require(bytes(_reason).length > 0, "Revocation reason cannot be empty");

        revocations[_globalCredentialId] = Revocation({
            isRevoked: true,
            reason: _reason,
            revoker: msg.sender,
            revocationDate: block.timestamp
        });

        _revokedCount++;

        emit DegreeRevoked(_globalCredentialId, _reason, msg.sender, block.timestamp);
    }

    /**
     * @notice Checks the revocation status of a degree.
     * @param _globalCredentialId The GUCI to look up
     * @return isRevoked True if the degree has been revoked
     * @return reason The reason for revocation (empty string if not revoked)
     * @return revoker The address of the revoking authority
     * @return revocationDate The timestamp when it was revoked
     */
    function getRevocationStatus(
        string memory _globalCredentialId
    ) public view returns (
        bool isRevoked,
        string memory reason,
        address revoker,
        uint256 revocationDate
    ) {
        Revocation memory r = revocations[_globalCredentialId];
        return (r.isRevoked, r.reason, r.revoker, r.revocationDate);
    }

    /**
     * @notice Returns the total count of revoked degrees
     */
    function getRevokedCount() public view returns (uint256) {
        return _revokedCount;
    }
}
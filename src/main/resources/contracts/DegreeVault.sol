// SPDX-License-Identifier: MIT
pragma solidity ^0.8.19;

/**
 * @title Decentralized Degree Vault
 * @author University Admin
 * @notice Manages issuance and verification of academic credentials on-chain
 * @dev Supports both single and batch degree issuance with admin-only access control
 */
contract DegreeVault {
    /// @notice The university admin address that deployed the contract
    address public universityAdmin;

    /// @notice Maximum number of degrees that can be issued in a single batch transaction
    uint256 public constant MAX_BATCH_SIZE = 100;

    /// @notice Counter tracking the total number of degrees issued
    uint256 private _degreeCount;

    /// @notice Represents a single degree record stored on-chain
    struct Degree {
        string studentId;
        string documentHash; // SHA-256 fingerprint of the degree document
        string ipfsCid;      // IPFS content identifier for decentralized storage
        uint256 issueDate;   // Unix timestamp of when the degree was issued
        bool exists;         // Flag to check if the degree record exists
    }

    /// @notice Mapping from a unique Degree ID to the Degree details
    mapping(string => Degree) private degrees;

    /// @notice Emitted when a single degree is issued
    /// @param degreeId The unique identifier of the degree
    /// @param studentId The student's identifier
    /// @param documentHash The SHA-256 hash of the degree document
    event DegreeIssued(
        string indexed degreeId,
        string studentId,
        string documentHash
    );

    /// @notice Emitted after a batch of degrees has been successfully issued
    /// @param admin The admin address that initiated the batch
    /// @param totalCount The number of degrees issued in the batch
    /// @param timestamp The block timestamp when the batch was processed
    event BatchIssued(
        address indexed admin,
        uint256 totalCount,
        uint256 timestamp
    );

    /// @notice Restricts function access to the university admin only
    modifier onlyAdmin() {
        require(
            msg.sender == universityAdmin,
            "Only the University Admin can perform this action"
        );
        _;
    }

    /// @notice Sets the deployer as the university admin
    constructor() {
        universityAdmin = msg.sender;
    }

    /**
     * @notice Issues a new degree record to the blockchain
     * @dev Only callable by the university admin. Reverts if degreeId already exists.
     * @param _degreeId Unique identifier for the degree
     * @param _studentId The student's identifier
     * @param _docHash SHA-256 hash of the degree document
     * @param _ipfsCid IPFS content identifier for the stored document
     */
    function issueDegree(
        string memory _degreeId,
        string memory _studentId,
        string memory _docHash,
        string memory _ipfsCid
    ) public onlyAdmin {
        require(!degrees[_degreeId].exists, "Degree ID already exists");

        degrees[_degreeId] = Degree({
            studentId: _studentId,
            documentHash: _docHash,
            ipfsCid: _ipfsCid,
            issueDate: block.timestamp,
            exists: true
        });

        _degreeCount++;

        emit DegreeIssued(_degreeId, _studentId, _docHash);
    }

    /**
     * @notice Issues a batch of degree records in a single transaction
     * @dev Only callable by the university admin. All input arrays must have equal length.
     *      Maximum batch size is 100 to prevent exceeding the block gas limit.
     *      Emits a DegreeIssued event for each degree, and a BatchIssued event at the end.
     *      Reverts entirely if any degreeId in the batch already exists.
     * @param _degreeIds Array of unique degree identifiers
     * @param _studentIds Array of student identifiers
     * @param _docHashes Array of SHA-256 document hashes
     * @param _ipfsCids Array of IPFS content identifiers
     */
    function issueBatch(
        string[] memory _degreeIds,
        string[] memory _studentIds,
        string[] memory _docHashes,
        string[] memory _ipfsCids
    ) public onlyAdmin {
        uint256 batchLength = _degreeIds.length;

        require(batchLength > 0, "Batch cannot be empty");
        require(batchLength <= MAX_BATCH_SIZE, "Batch size exceeds maximum allowed (100)");
        require(
            batchLength == _studentIds.length &&
            batchLength == _docHashes.length &&
            batchLength == _ipfsCids.length,
            "All input arrays must have equal length"
        );

        for (uint256 i = 0; i < batchLength; i++) {
            require(!degrees[_degreeIds[i]].exists, "Degree ID already exists in batch");

            degrees[_degreeIds[i]] = Degree({
                studentId: _studentIds[i],
                documentHash: _docHashes[i],
                ipfsCid: _ipfsCids[i],
                issueDate: block.timestamp,
                exists: true
            });

            _degreeCount++;

            emit DegreeIssued(_degreeIds[i], _studentIds[i], _docHashes[i]);
        }

        emit BatchIssued(msg.sender, batchLength, block.timestamp);
    }

    /**
     * @notice Fetches degree details for verification
     * @dev Reverts if the degree record does not exist on-chain.
     * @param _degreeId The unique identifier of the degree to verify
     * @return studentId The student's identifier
     * @return docHash The SHA-256 hash of the degree document
     * @return ipfsCid The IPFS content identifier
     * @return date The Unix timestamp of when the degree was issued
     */
    function verifyDegree(
        string memory _degreeId
    ) public view returns (
        string memory studentId,
        string memory docHash,
        string memory ipfsCid,
        uint256 date
    ) {
        require(degrees[_degreeId].exists, "Degree record not found");

        Degree memory d = degrees[_degreeId];
        return (d.studentId, d.documentHash, d.ipfsCid, d.issueDate);
    }

    /**
     * @notice Returns the maximum allowed batch size
     * @return The maximum number of degrees that can be issued in a single batch
     */
    function getBatchSize() public pure returns (uint256) {
        return MAX_BATCH_SIZE;
    }

    /**
     * @notice Returns the total number of degrees issued so far
     * @return The cumulative count of all degrees ever issued (single + batch)
     */
    function getDegreeCount() public view returns (uint256) {
        return _degreeCount;
    }
}
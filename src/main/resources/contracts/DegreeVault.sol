// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title Decentralized Degree Vault
 * @dev Manages issuance and verification of academic credentials
 */
contract DegreeVault {
    address public universityAdmin;

    struct Degree {
        string studentId;
        string documentHash; // SHA-256 fingerprint
        string ipfsCid;      // Storage link
        uint256 issueDate;
        bool exists;
    }

    // Mapping from a unique Degree ID to the Degree details
    mapping(string => Degree) private degrees;

    event DegreeIssued(string indexed degreeId, string studentId, string documentHash);

    modifier onlyAdmin() {
        require(msg.sender == universityAdmin, "Only the University Admin can perform this action");
        _;
    }

    constructor() {
        universityAdmin = msg.sender; // The person who deploys the contract becomes the Admin
    }

    /**
     * @dev Issues a new degree record to the blockchain
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

        emit DegreeIssued(_degreeId, _studentId, _docHash);
    }

    /**
     * @dev Fetches degree details for verification
     */
    function verifyDegree(string memory _degreeId) public view returns (
        string memory studentId, 
        string memory docHash, 
        string memory ipfsCid, 
        uint256 date
    ) {
        require(degrees[_degreeId].exists, "Degree record not found");
        
        Degree memory d = degrees[_degreeId];
        return (d.studentId, d.documentHash, d.ipfsCid, d.issueDate);
    }
}
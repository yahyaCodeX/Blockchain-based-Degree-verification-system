// SPDX-License-Identifier: MIT
pragma solidity ^0.8.19;

contract DegreeVault {
    address public universityAdmin;
    uint256 public constant MAX_BATCH_SIZE = 100;
    uint256 private _degreeCount;

    struct Degree {
        string globalCredentialId;
        string universityCode;
        string rollNo;
        string studentName;
        string cgpa;
        string dataHash;
        address issuer;
        uint256 issueDate;
        bool exists;
    }

    mapping(string => Degree) private degrees;

    event DegreeIssued(
        string indexed globalCredentialId,
        string universityCode,
        string rollNo,
        string dataHash
    );

    event BatchIssued(
        address indexed admin,
        uint256 totalCount,
        uint256 timestamp
    );

    modifier onlyAdmin() {
        require(msg.sender == universityAdmin, "Only the University Admin can perform this action");
        _;
    }

    constructor() {
        universityAdmin = msg.sender;
    }

    function issueDegree(
        string memory _globalCredentialId,
        string memory _universityCode,
        string memory _rollNo,
        string memory _studentName,
        string memory _cgpa,
        string memory _dataHash
    ) public onlyAdmin {
        require(!degrees[_globalCredentialId].exists, "Global credential already exists");

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

        emit DegreeIssued(_globalCredentialId, _universityCode, _rollNo, _dataHash);
        _degreeCount++;
    }

    function issueDegree(
        string memory _rollNo,
        string memory _studentId,
        string memory _dataHash,
        string memory _ipfsCid
    ) public onlyAdmin {
        string memory legacyId = string(abi.encodePacked("LEGACY-", _rollNo));
        issueDegree(legacyId, "LEGACY", _rollNo, _studentId, "0", _dataHash);
        _ipfsCid;
    }

    function issueBatch(
        string[] memory _globalCredentialIds,
        string[] memory _universityCodes,
        string[] memory _rollNos,
        string[] memory _studentNames,
        string[] memory _cgpAs,
        string[] memory _dataHashes
    ) public onlyAdmin {
        uint256 batchLength = _globalCredentialIds.length;
        require(batchLength > 0, "Batch cannot be empty");
        require(batchLength <= MAX_BATCH_SIZE, "Batch size exceeds maximum allowed (100)");
        require(
            batchLength == _universityCodes.length &&
            batchLength == _rollNos.length &&
            batchLength == _studentNames.length &&
            batchLength == _cgpAs.length &&
            batchLength == _dataHashes.length,
            "All input arrays must have equal length"
        );

        for (uint256 i = 0; i < batchLength; i++) {
            require(!degrees[_globalCredentialIds[i]].exists, "Global credential already exists in batch");

            degrees[_globalCredentialIds[i]] = Degree({
                globalCredentialId: _globalCredentialIds[i],
                universityCode: _universityCodes[i],
                rollNo: _rollNos[i],
                studentName: _studentNames[i],
                cgpa: _cgpAs[i],
                dataHash: _dataHashes[i],
                issuer: msg.sender,
                issueDate: block.timestamp,
                exists: true
            });

            emit DegreeIssued(_globalCredentialIds[i], _universityCodes[i], _rollNos[i], _dataHashes[i]);
            _degreeCount++;
        }

        emit BatchIssued(msg.sender, batchLength, block.timestamp);
    }

    function issueBatch(
        string[] memory _rollNos,
        string[] memory _studentIds,
        string[] memory _docHashes,
        string[] memory _ipfsCids
    ) public onlyAdmin {
        string[] memory globalIds = new string[](_rollNos.length);
        string[] memory universityCodes = new string[](_rollNos.length);
        string[] memory studentNames = new string[](_rollNos.length);
        string[] memory cgpAs = new string[](_rollNos.length);

        for (uint256 i = 0; i < _rollNos.length; i++) {
            globalIds[i] = string(abi.encodePacked("LEGACY-", _rollNos[i]));
            universityCodes[i] = "LEGACY";
            studentNames[i] = _studentIds[i];
            cgpAs[i] = "0";
        }

        issueBatch(globalIds, universityCodes, _rollNos, studentNames, cgpAs, _docHashes);
        _ipfsCids;
    }

    function verifyDegree(
        string memory _globalCredentialId
    ) public view returns (
        string memory universityCode,
        string memory rollNo,
        string memory studentName,
        string memory cgpa,
        string memory dataHash,
        address issuer,
        uint256 date
    ) {
        require(degrees[_globalCredentialId].exists, "Degree record not found");

        Degree memory d = degrees[_globalCredentialId];
        return (d.universityCode, d.rollNo, d.studentName, d.cgpa, d.dataHash, d.issuer, d.issueDate);
    }

    function getBatchSize() public pure returns (uint256) {
        return MAX_BATCH_SIZE;
    }

    function getDegreeCount() public view returns (uint256) {
        return _degreeCount;
    }
}

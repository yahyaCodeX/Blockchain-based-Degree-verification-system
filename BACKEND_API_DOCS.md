# Decentralized Degree Vault Backend - API Documentation

## Overview
This is a Spring Boot backend application that interacts with a smart contract deployed on the blockchain to issue and verify academic credentials (degrees).

## Setup Instructions

### Prerequisites
- Java 21
- Maven 3.9.14+
- Ganache or similar Ethereum test network running on `http://localhost:8545`
- A deployed `DegreeVault` smart contract

### Configuration

Edit `src/main/resources/application.properties`:

```properties
# Your Ethereum RPC endpoint
blockchain.rpc-url=http://localhost:8545

# Your deployed contract address
contract.address=0x1234567890123456789012345678901234567890

# Your private key (without 0x prefix) - KEEP THIS SECURE!
wallet.private-key=YOUR_PRIVATE_KEY_WITHOUT_0X
```

### Build & Run

```bash
# Build the project
./mvnw clean package

# Run the application
./mvnw spring-boot:run

# Or run the JAR file
java -jar target/Decentralizeddegreevault-0.0.1-SNAPSHOT.jar
```

The application will start on `http://localhost:8080`

## API Endpoints

### 1. Health Check
```
GET /api/v1/degrees/health
```
**Response:**
```json
"Degree Vault Backend is running"
```

### 2. Issue a Degree
```
POST /api/v1/degrees/issue
Content-Type: application/json
```

**Request Body:**
```json
{
  "degreeId": "DEGREE-001",
  "studentId": "STU-12345",
  "documentHash": "sha256hash_of_document",
  "ipfsCid": "QmXxxx...xxx"
}
```

**Response (Success):**
```json
{
  "transactionHash": "0x1234567890abcdef...",
  "status": "SUCCESS",
  "message": "Degree issued successfully"
}
```

**Response (Error):**
```json
{
  "transactionHash": null,
  "status": "ERROR",
  "message": "Error issuing degree: ..."
}
```

### 3. Verify a Degree
```
GET /api/v1/degrees/verify/{degreeId}
```

**Example:**
```
GET /api/v1/degrees/verify/DEGREE-001
```

**Response (Found):**
```json
{
  "studentId": "STU-12345",
  "documentHash": "sha256hash_of_document",
  "ipfsCid": "QmXxxx...xxx",
  "issueDate": 1712658000,
  "verified": true
}
```

**Response (Not Found):**
```json
{
  "studentId": null,
  "documentHash": null,
  "ipfsCid": null,
  "issueDate": 0,
  "verified": false
}
```

### 4. Get University Admin Address
```
GET /api/v1/degrees/admin
```

**Response:**
```json
"0x1234567890123456789012345678901234567890"
```

## Project Structure

```
src/main/java/com/decentralized/degree/vault/decentralizeddegreevault/
├── Config/
│   └── Web3jConfig.java          # Web3j Bean Configuration
├── Controller/
│   └── DegreeVaultController.java # REST API Endpoints
├── Service/
│   └── DegreeVaultService.java    # Business Logic
├── dto/
│   ├── IssueDegreeRequest.java    # Request DTO
│   ├── VerifyDegreeResponse.java  # Response DTO
│   └── TransactionResponse.java   # Transaction Response DTO
└── DecentralizedDegreeVaultBackendApplication.java
```

## Key Components

### Web3jConfig.java
Configures the Web3j connection to the blockchain and provides:
- `Web3j` bean for blockchain communication
- `ContractGasProvider` bean for transaction gas management

### DegreeVaultService.java
Core business logic layer that:
- Issues degrees by calling the smart contract's `issueDegree` function
- Verifies degrees by calling the smart contract's `verifyDegree` function
- Fetches the university admin address from the contract
- Handles all blockchain interactions and error management

### DegreeVaultController.java
REST API controller providing endpoints for:
- Issuing degrees
- Verifying degrees
- Getting admin information
- Health checks

## Smart Contract Functions

### issueDegree
```solidity
function issueDegree(
    string memory _degreeId, 
    string memory _studentId, 
    string memory _docHash, 
    string memory _ipfsCid
) public onlyAdmin
```
- **Access:** Only the university admin can call this
- **Purpose:** Records a new degree on the blockchain
- **Emits:** `DegreeIssued` event

### verifyDegree
```solidity
function verifyDegree(string memory _degreeId) public view returns (
    string memory studentId, 
    string memory docHash, 
    string memory ipfsCid, 
    uint256 date
)
```
- **Access:** Anyone can call this (read-only)
- **Purpose:** Retrieves and verifies degree information
- **Returns:** Degree details if exists, reverts if not found

## Troubleshooting

### Issue: "Bin file was not provided"
**Solution:** This was already fixed in your pom.xml. The web3j-maven-plugin now uses `soliditySourceFiles` to compile both ABI and BIN files.

### Issue: Contract address not found
**Ensure:**
1. Your contract is deployed on the network
2. The `contract.address` in application.properties is correct
3. The RPC URL points to the correct network

### Issue: "Only the University Admin can perform this action"
**Ensure:**
1. The `wallet.private-key` belongs to the same account that deployed the contract
2. The private key format is correct (without 0x prefix)

## Dependencies
- Spring Boot 3.2.4
- Web3j 4.10.3
- Lombok
- MySQL Connector

## Security Notes
⚠️ **IMPORTANT:**
- Never commit your private key to version control
- Use environment variables for sensitive configuration
- Always use HTTPS in production
- Implement proper authentication for API endpoints

## Testing with cURL

### Issue a Degree
```bash
curl -X POST http://localhost:8080/api/v1/degrees/issue \
  -H "Content-Type: application/json" \
  -d '{
    "degreeId": "DEGREE-001",
    "studentId": "STU-12345",
    "documentHash": "abc123def456",
    "ipfsCid": "QmXxxx"
  }'
```

### Verify a Degree
```bash
curl http://localhost:8080/api/v1/degrees/verify/DEGREE-001
```

### Get Admin
```bash
curl http://localhost:8080/api/v1/degrees/admin
```

## License
MIT


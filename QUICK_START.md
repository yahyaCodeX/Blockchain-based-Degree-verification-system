# Quick Start Guide for Decentralized Degree Vault Backend

## Step 1: Deploy Smart Contract

First, you need to deploy the DegreeVault smart contract to a blockchain network (e.g., Ganache, Sepolia, etc.).

### Using Hardhat (Recommended):
```bash
# Install dependencies
npm install

# Compile contract
npx hardhat compile

# Deploy to Ganache (or your network)
npx hardhat run scripts/deploy.js --network ganache
```

This will output:
```
DegreeVault deployed to: 0x1234567890...
```

## Step 2: Get Your Private Key

### From Ganache:
1. Open Ganache
2. Click on the key icon next to any account
3. Copy the private key (without 0x prefix)

### From MetaMask:
1. Click Account Details
2. Export Private Key
3. Copy (without 0x prefix)

## Step 3: Update Configuration

Edit `src/main/resources/application.properties`:

```properties
# RPC endpoint of your blockchain network
blockchain.rpc-url=http://localhost:8545

# Your deployed contract address (with 0x prefix)
contract.address=0x1234567890123456789012345678901234567890

# Your wallet private key (WITHOUT 0x prefix)
wallet.private-key=1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef
```

## Step 4: Build & Run

```bash
# Build
./mvnw clean package -DskipTests

# Run
./mvnw spring-boot:run
```

## Step 5: Test the API

### Health Check
```bash
curl http://localhost:8080/api/v1/degrees/health
```

### Get Admin Address
```bash
curl http://localhost:8080/api/v1/degrees/admin
```

### Issue a Degree
```bash
curl -X POST http://localhost:8080/api/v1/degrees/issue \
  -H "Content-Type: application/json" \
  -d '{
    "degreeId": "BSC-2024-001",
    "studentId": "STU-001",
    "documentHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "ipfsCid": "QmXwTQxDBvNYcsTvjbZPQLs5Q2V3e9JrZw1bo5aFkrtyu"
  }'
```

**Expected Response:**
```json
{
  "transactionHash": "0xabc123...",
  "status": "SUCCESS",
  "message": "Degree issued successfully"
}
```

### Verify a Degree
```bash
curl http://localhost:8080/api/v1/degrees/verify/BSC-2024-001
```

**Expected Response:**
```json
{
  "studentId": "STU-001",
  "documentHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "ipfsCid": "QmXwTQxDBvNYcsTvjbZPQLs5Q2V3e9JrZw1bo5aFkrtyu",
  "issueDate": 1712658000,
  "verified": true
}
```

## Tips for Testing

### 1. Generate a SHA-256 Hash (for documentHash)
```bash
# On Linux/Mac
echo -n "your-document-content" | sha256sum

# On Windows (PowerShell)
(Get-FileHash -Path "document.pdf" -Algorithm SHA256).Hash.ToLower()
```

### 2. Generate IPFS CID
- Upload to IPFS: https://web3.storage or https://ipfs.io
- Or use: `ipfs add <file>`

### 3. Reset Ganache
If you mess up, you can reset Ganache:
1. Click the reset icon in Ganache
2. Redeploy your contract
3. Update contract address in application.properties

## Troubleshooting

### Error: "Could not connect to RPC endpoint"
- Ensure Ganache is running on http://localhost:8545
- Check `blockchain.rpc-url` in application.properties

### Error: "Only the University Admin can perform this action"
- Your wallet private key doesn't match the contract deployer
- Use the same account that deployed the contract

### Error: "Degree record not found"
- The degree hasn't been issued yet
- Check the degree ID spelling

### Error: "Connection refused"
- Spring Boot server not running
- Make sure `./mvnw spring-boot:run` is executed

## Environment Setup (Windows)

### Install Ganache
```powershell
npm install -g ganache-cli
# Or download GUI from: https://trufflesuite.com/ganache/
```

### Start Ganache
```powershell
ganache-cli --port 8545 --host 0.0.0.0
```

### Install Java 21
```powershell
choco install openjdk21
# Or download from: https://jdk.java.net/21/
```

## Next Steps

1. ✅ Backend API is running
2. Create a frontend to consume these APIs
3. Implement authentication/authorization
4. Add database logging for audit trail
5. Deploy to mainnet or testnet


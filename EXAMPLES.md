# Example Usage - Complete Workflow

This document shows a complete example of how to use the Decentralized Degree Vault Backend.

## Prerequisites

1. **Ganache running** on `http://localhost:8545`
2. **DegreeVault smart contract deployed**
3. **Backend application running** on `http://localhost:8080`

## Complete Example Workflow

### Step 1: Check Backend Health

```bash
curl http://localhost:8080/api/v1/degrees/health
```

**Response:**
```
"Degree Vault Backend is running"
```

### Step 2: Get University Admin Address

```bash
curl http://localhost:8080/api/v1/degrees/admin
```

**Response:**
```json
"0x742d35Cc6634C0532925a3b844Bc9e7595f4bEb"
```

### Step 3: Issue Your First Degree

```bash
curl -X POST http://localhost:8080/api/v1/degrees/issue \
  -H "Content-Type: application/json" \
  -d '{
    "degreeId": "DEGREE-2024-BSC-001",
    "studentId": "STU-20240001",
    "documentHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "ipfsCid": "QmXwTQxDBvNYcsTvjbZPQLs5Q2V3e9JrZw1bo5aFkrtyu"
  }'
```

**Response:**
```json
{
  "transactionHash": "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
  "status": "SUCCESS",
  "message": "Degree issued successfully"
}
```

### Step 4: Verify the Degree

```bash
curl http://localhost:8080/api/v1/degrees/verify/DEGREE-2024-BSC-001
```

**Response:**
```json
{
  "studentId": "STU-20240001",
  "documentHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "ipfsCid": "QmXwTQxDBvNYcsTvjbZPQLs5Q2V3e9JrZw1bo5aFkrtyu",
  "issueDate": 1712658000,
  "verified": true
}
```

### Step 5: Try Verifying a Non-existent Degree

```bash
curl http://localhost:8080/api/v1/degrees/verify/DEGREE-FAKE-001
```

**Response:**
```json
{
  "studentId": null,
  "documentHash": null,
  "ipfsCid": null,
  "issueDate": 0,
  "verified": false
}
```

## Real-World Example: University Issuing Degrees

### Scenario: University of Example issues 3 degrees

```bash
# Student 1: Alice (Computer Science)
curl -X POST http://localhost:8080/api/v1/degrees/issue \
  -H "Content-Type: application/json" \
  -d '{
    "degreeId": "UE-CS-2024-001",
    "studentId": "alice@university.edu",
    "documentHash": "da39a3ee5e6b4b0d3255bfef95601890afd80709",
    "ipfsCid": "QmVaaW1pR8d7b2R3X5Z8a4B5c6D7e8F9g0H1i2J3k4L"
  }'

# Student 2: Bob (Engineering)
curl -X POST http://localhost:8080/api/v1/degrees/issue \
  -H "Content-Type: application/json" \
  -d '{
    "degreeId": "UE-ENG-2024-002",
    "studentId": "bob@university.edu",
    "documentHash": "356a192b7913b04c54574d18c28d46e6395428ab",
    "ipfsCid": "QmXxW8W9x0Y1z2A3b4C5d6E7f8G9h0I1j2K3l4M5n6O"
  }'

# Student 3: Carol (Business)
curl -X POST http://localhost:8080/api/v1/degrees/issue \
  -H "Content-Type: application/json" \
  -d '{
    "degreeId": "UE-BUS-2024-003",
    "studentId": "carol@university.edu",
    "documentHash": "77de68daecedc0647ebd8c2a0e31e89d6a01d200",
    "ipfsCid": "QmYyX9y0Z1a2B3c4D5e6F7g8H9i0J1k2L3m4N5o6P7q"
  }'
```

### Employer Verifying Degrees

```bash
# HR wants to verify Alice's degree
curl http://localhost:8080/api/v1/degrees/verify/UE-CS-2024-001

# Response indicates it's legitimate with immutable proof on blockchain
```

## Error Scenarios

### Error 1: Missing Required Fields

```bash
curl -X POST http://localhost:8080/api/v1/degrees/issue \
  -H "Content-Type: application/json" \
  -d '{
    "degreeId": "DEG-001"
    # Missing other required fields
  }'
```

**Response:**
```json
{
  "transactionHash": null,
  "status": "ERROR",
  "message": "All fields are required"
}
```

### Error 2: Invalid Degree ID

```bash
curl http://localhost:8080/api/v1/degrees/verify/INVALID-ID-@#$
```

**Response:**
```json
{
  "studentId": null,
  "documentHash": null,
  "ipfsCid": null,
  "issueDate": 0,
  "verified": false
}
```

### Error 3: Duplicate Degree ID

If you try to issue the same degree ID twice:

```bash
# First request - SUCCESS
curl -X POST http://localhost:8080/api/v1/degrees/issue \
  -H "Content-Type: application/json" \
  -d '{
    "degreeId": "DUP-001",
    "studentId": "student@uni.edu",
    "documentHash": "abc123...",
    "ipfsCid": "QmXxx..."
  }'

# Second request with same degreeId - FAILS
curl -X POST http://localhost:8080/api/v1/degrees/issue \
  -H "Content-Type: application/json" \
  -d '{
    "degreeId": "DUP-001",
    "studentId": "student@uni.edu",
    "documentHash": "def456...",
    "ipfsCid": "QmYyy..."
  }'
```

**Response (2nd request):**
```json
{
  "transactionHash": null,
  "status": "ERROR",
  "message": "Error issuing degree: Degree ID already exists"
}
```

## Postman Collection Example

You can import this into Postman:

```json
{
  "info": {
    "name": "Degree Vault API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Health Check",
      "request": {
        "method": "GET",
        "url": "http://localhost:8080/api/v1/degrees/health"
      }
    },
    {
      "name": "Get Admin",
      "request": {
        "method": "GET",
        "url": "http://localhost:8080/api/v1/degrees/admin"
      }
    },
    {
      "name": "Issue Degree",
      "request": {
        "method": "POST",
        "url": "http://localhost:8080/api/v1/degrees/issue",
        "header": [
          {
            "key": "Content-Type",
            "value": "application/json"
          }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"degreeId\": \"DEG-2024-001\",\n  \"studentId\": \"student@uni.edu\",\n  \"documentHash\": \"abc123def456\",\n  \"ipfsCid\": \"QmXxxx\"\n}"
        }
      }
    },
    {
      "name": "Verify Degree",
      "request": {
        "method": "GET",
        "url": "http://localhost:8080/api/v1/degrees/verify/DEG-2024-001"
      }
    }
  ]
}
```

## JavaScript Frontend Example

```javascript
const API_BASE = 'http://localhost:8080/api/v1/degrees';

// Issue a degree
async function issueDegree(degreeId, studentId, documentHash, ipfsCid) {
  const response = await fetch(`${API_BASE}/issue`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      degreeId,
      studentId,
      documentHash,
      ipfsCid
    })
  });
  return response.json();
}

// Verify a degree
async function verifyDegree(degreeId) {
  const response = await fetch(`${API_BASE}/verify/${degreeId}`);
  return response.json();
}

// Get admin address
async function getAdmin() {
  const response = await fetch(`${API_BASE}/admin`);
  return response.text();
}

// Usage
async function main() {
  // Issue a degree
  const issueResult = await issueDegree(
    'DEG-JS-001',
    'student@uni.edu',
    'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    'QmXwTQxDBvNYcsTvjbZPQLs5Q2V3e9JrZw1bo5aFkrtyu'
  );
  console.log('Issue Result:', issueResult);

  // Verify the degree
  const verifyResult = await verifyDegree('DEG-JS-001');
  console.log('Verify Result:', verifyResult);

  // Get admin
  const admin = await getAdmin();
  console.log('Admin Address:', admin);
}

main().catch(console.error);
```

## Python Example

```python
import requests
import json

API_BASE = 'http://localhost:8080/api/v1/degrees'

def issue_degree(degree_id, student_id, document_hash, ipfs_cid):
    response = requests.post(
        f'{API_BASE}/issue',
        headers={'Content-Type': 'application/json'},
        json={
            'degreeId': degree_id,
            'studentId': student_id,
            'documentHash': document_hash,
            'ipfsCid': ipfs_cid
        }
    )
    return response.json()

def verify_degree(degree_id):
    response = requests.get(f'{API_BASE}/verify/{degree_id}')
    return response.json()

def get_admin():
    response = requests.get(f'{API_BASE}/admin')
    return response.text

# Usage
if __name__ == '__main__':
    # Issue a degree
    result = issue_degree(
        'DEG-PY-001',
        'student@uni.edu',
        'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
        'QmXwTQxDBvNYcsTvjbZPQLs5Q2V3e9JrZw1bo5aFkrtyu'
    )
    print('Issue Result:', json.dumps(result, indent=2))

    # Verify the degree
    verify_result = verify_degree('DEG-PY-001')
    print('Verify Result:', json.dumps(verify_result, indent=2))
```

## Integration Checklist

- [ ] Backend running on port 8080
- [ ] Smart contract deployed on blockchain
- [ ] Contract address configured in `application.properties`
- [ ] Private key configured in `application.properties`
- [ ] Blockchain network (Ganache/Sepolia) running
- [ ] Health check endpoint responds
- [ ] Can issue a degree
- [ ] Can verify a degree
- [ ] Can retrieve admin address
- [ ] Frontend can connect to API

---

**Status:** All examples ready to use!


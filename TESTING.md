# Testing Guide for Decentralized Degree Vault Backend

## Manual Testing with Postman

### 1. Import Collection

Create a new Postman collection with the following requests:

#### Request 1: Health Check
- **Name:** Health Check
- **Method:** GET
- **URL:** `{{base_url}}/api/v1/degrees/health`
- **Expected Status:** 200 OK

#### Request 2: Get Admin Address
- **Name:** Get Admin
- **Method:** GET
- **URL:** `{{base_url}}/api/v1/degrees/admin`
- **Expected Status:** 200 OK
- **Expected Response:** Ethereum address

#### Request 3: Issue Degree
- **Name:** Issue Degree
- **Method:** POST
- **URL:** `{{base_url}}/api/v1/degrees/issue`
- **Headers:** `Content-Type: application/json`
- **Body (raw JSON):**
```json
{
  "degreeId": "TEST-DEGREE-001",
  "studentId": "test-student@example.com",
  "documentHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "ipfsCid": "QmXwTQxDBvNYcsTvjbZPQLs5Q2V3e9JrZw1bo5aFkrtyu"
}
```
- **Expected Status:** 200 OK
- **Expected Response:** Transaction hash with SUCCESS status

#### Request 4: Verify Degree
- **Name:** Verify Degree
- **Method:** GET
- **URL:** `{{base_url}}/api/v1/degrees/verify/TEST-DEGREE-001`
- **Expected Status:** 200 OK
- **Expected Response:** Degree details with verified=true

### Postman Variables
Create a collection variable:
- **Key:** `base_url`
- **Value:** `http://localhost:8080`

## cURL Testing Scripts

### Test Script 1: Complete Workflow

**File: `test-workflow.sh`**

```bash
#!/bin/bash

BASE_URL="http://localhost:8080"
API="/api/v1/degrees"

echo "=== Degree Vault Backend Testing ==="
echo ""

# Test 1: Health Check
echo "1. Testing Health Check..."
curl -s "$BASE_URL$API/health"
echo ""
echo ""

# Test 2: Get Admin
echo "2. Getting Admin Address..."
ADMIN=$(curl -s "$BASE_URL$API/admin")
echo "Admin: $ADMIN"
echo ""

# Test 3: Issue Degree
echo "3. Issuing a Degree..."
ISSUE_RESPONSE=$(curl -s -X POST "$BASE_URL$API/issue" \
  -H "Content-Type: application/json" \
  -d '{
    "degreeId": "CURL-TEST-'$(date +%s)'",
    "studentId": "test@university.edu",
    "documentHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "ipfsCid": "QmXwTQxDBvNYcsTvjbZPQLs5Q2V3e9JrZw1bo5aFkrtyu"
  }')
echo $ISSUE_RESPONSE | jq '.'
DEGREE_ID=$(echo $ISSUE_RESPONSE | jq -r '.degreeId')
TX_HASH=$(echo $ISSUE_RESPONSE | jq -r '.transactionHash')
echo ""

# Test 4: Verify Degree
echo "4. Verifying the Degree..."
VERIFY_RESPONSE=$(curl -s "$BASE_URL$API/verify/$DEGREE_ID")
echo $VERIFY_RESPONSE | jq '.'
echo ""

echo "=== Tests Complete ==="
```

### Test Script 2: Load Testing

**File: `load-test.sh`**

```bash
#!/bin/bash

BASE_URL="http://localhost:8080"
API="/api/v1/degrees"
NUM_REQUESTS=10

echo "=== Load Testing: Issuing $NUM_REQUESTS Degrees ==="

for i in $(seq 1 $NUM_REQUESTS); do
  echo "Request $i/$NUM_REQUESTS..."
  
  curl -s -X POST "$BASE_URL$API/issue" \
    -H "Content-Type: application/json" \
    -d "{
      \"degreeId\": \"LOAD-TEST-$i\",
      \"studentId\": \"student-$i@university.edu\",
      \"documentHash\": \"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\",
      \"ipfsCid\": \"QmXwTQxDBvNYcsTvjbZPQLs5Q2V3e9JrZw1bo5aFkrtyu\"
    }" | jq '.status'
  
  sleep 1
done

echo "=== Load Testing Complete ==="
```

### Test Script 3: Error Handling

**File: `error-test.sh`**

```bash
#!/bin/bash

BASE_URL="http://localhost:8080"
API="/api/v1/degrees"

echo "=== Error Handling Tests ==="

# Test 1: Missing Fields
echo "Test 1: Missing Required Fields"
curl -s -X POST "$BASE_URL$API/issue" \
  -H "Content-Type: application/json" \
  -d '{"degreeId": "ERROR-TEST-1"}' | jq '.'
echo ""

# Test 2: Empty String
echo "Test 2: Empty Degree ID"
curl -s -X POST "$BASE_URL$API/issue" \
  -H "Content-Type: application/json" \
  -d '{
    "degreeId": "",
    "studentId": "test@university.edu",
    "documentHash": "abc123",
    "ipfsCid": "QmXxx"
  }' | jq '.'
echo ""

# Test 3: Verify Non-existent
echo "Test 3: Verify Non-existent Degree"
curl -s "$BASE_URL$API/verify/DOES-NOT-EXIST" | jq '.'
echo ""

echo "=== Error Tests Complete ==="
```

## Automated Integration Tests

### Unit Test Template

**File: `src/test/java/com/decentralized/degree/vault/decentralizeddegreevault/Service/DegreeVaultServiceTest.java`**

```java
package com.decentralized.degree.vault.decentralizeddegreevault.Service;

import com.decentralized.degree.vault.decentralizeddegreevault.dto.IssueDegreeRequest;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.TransactionResponse;
import com.decentralized.degree.vault.decentralizeddegreevault.dto.VerifyDegreeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DegreeVaultServiceTest {

    private DegreeVaultService degreeVaultService;

    @BeforeEach
    public void setUp() {
        // Initialize service
    }

    @Test
    public void testIssueDegreeSuccess() {
        IssueDegreeRequest request = new IssueDegreeRequest(
            "TEST-001",
            "student@uni.edu",
            "abc123def456",
            "QmXwTQxDBvNYcsTvjbZPQLs5Q2V3e9JrZw1bo5aFkrtyu"
        );

        TransactionResponse response = degreeVaultService.issueDegree(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertNotNull(response.getTransactionHash());
    }

    @Test
    public void testVerifyDegreeSuccess() {
        VerifyDegreeResponse response = degreeVaultService.verifyDegree("TEST-001");

        assertNotNull(response);
        assertTrue(response.isVerified());
        assertEquals("student@uni.edu", response.getStudentId());
    }

    @Test
    public void testVerifyNonExistentDegree() {
        VerifyDegreeResponse response = degreeVaultService.verifyDegree("DOES-NOT-EXIST");

        assertNotNull(response);
        assertFalse(response.isVerified());
    }
}
```

### Controller Test Template

**File: `src/test/java/com/decentralized/degree/vault/decentralizeddegreevault/Controller/DegreeVaultControllerTest.java`**

```java
package com.decentralized.degree.vault.decentralizeddegreevault.Controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class DegreeVaultControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testHealthCheck() throws Exception {
        mockMvc.perform(get("/api/v1/degrees/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("running")));
    }

    @Test
    public void testGetAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/degrees/admin"))
                .andExpect(status().isOk());
    }

    @Test
    public void testIssueDegreeValidRequest() throws Exception {
        String request = "{"
                + "\"degreeId\": \"TEST-001\","
                + "\"studentId\": \"student@uni.edu\","
                + "\"documentHash\": \"abc123\","
                + "\"ipfsCid\": \"QmXxx\""
                + "}";

        mockMvc.perform(post("/api/v1/degrees/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isOk());
    }

    @Test
    public void testIssueDegreeInvalidRequest() throws Exception {
        String request = "{\"degreeId\": \"\"}";

        mockMvc.perform(post("/api/v1/degrees/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testVerifyDegree() throws Exception {
        mockMvc.perform(get("/api/v1/degrees/verify/TEST-001"))
                .andExpect(status().isOk());
    }
}
```

## Test Data Generation

### Generate Test SHA256 Hashes

```bash
# Linux/Mac
echo -n "document content" | sha256sum

# Windows PowerShell
(Get-FileHash -Path "C:\path\to\file" -Algorithm SHA256).Hash.ToLower()
```

## Performance Testing with JMeter

### Basic JMeter Test Plan

1. Create Thread Group (10 threads, 100 ramp-up)
2. Add HTTP Requests:
   - Health Check: GET /api/v1/degrees/health
   - Issue Degree: POST /api/v1/degrees/issue
   - Verify Degree: GET /api/v1/degrees/verify/{degreeId}
3. Add Listeners:
   - View Results Tree
   - Summary Report
   - Response Time Graph

## Monitoring & Debugging

### Enable Debug Logging

Edit `application.properties`:
```properties
logging.level.com.decentralized.degree.vault=DEBUG
logging.level.org.web3j=DEBUG
logging.level.org.springframework.web=DEBUG
```

### View Logs

```bash
# Real-time
tail -f logs/application.log

# With grep
tail -f logs/application.log | grep "ERROR"

# Count errors
grep "ERROR" logs/application.log | wc -l
```

### Check Transaction on Blockchain

```bash
# Get transaction details (using curl)
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "method":"eth_getTransactionByHash",
    "params":["0xTX_HASH"],
    "id":1
  }'
```

## Test Checklist

- [ ] Health check endpoint returns 200
- [ ] Get admin endpoint returns valid address
- [ ] Issue degree with valid data succeeds
- [ ] Issue degree with missing fields fails
- [ ] Verify issued degree returns correct data
- [ ] Verify non-existent degree returns not found
- [ ] Duplicate degree ID is rejected
- [ ] Transaction hash is returned on success
- [ ] Error messages are descriptive
- [ ] CORS headers are present
- [ ] Performance is acceptable (< 2 seconds per request)
- [ ] No unhandled exceptions in logs

## Continuous Integration Testing

### GitHub Actions Test Workflow

```yaml
name: Run Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      ganache:
        image: trufflesuite/ganache-cli
        options: >-
          --health-cmd "curl http://localhost:8545" 
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 8545:8545

    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK 21
        uses: actions/setup-java@v2
        with:
          java-version: 21
      - name: Run tests
        run: ./mvnw test
```

---

**Status:** Complete testing guide ready for use!


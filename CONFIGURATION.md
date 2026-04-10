# Environment Configuration Guide

## Overview
This guide helps you configure your backend for different environments (Development, Testing, Production).

## Configuration Files

### 1. Development Environment
**File: `src/main/resources/application-dev.properties`**

```properties
# Application name
spring.application.name=Decentralized degree vault backend

# Development Blockchain Configuration
blockchain.rpc-url=http://localhost:8545
contract.address=0x5fbdb2315678afccb333f8a9fcff40144e9b0ad1
wallet.private-key=ac0974bec39a17e36ba4a6b4d238ff944bacb476c6b8d6c65f86b73b27d3fba48

# Development Database (optional - H2 in-memory)
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop

# Logging
logging.level.root=INFO
logging.level.com.decentralized.degree.vault=DEBUG
logging.level.org.web3j=DEBUG
logging.level.org.springframework.web=DEBUG

# Server
server.port=8080
server.servlet.context-path=/

# Development settings
spring.devtools.restart.enabled=true
management.endpoints.web.exposure.include=*
```

### 2. Testing Environment
**File: `src/main/resources/application-test.properties`**

```properties
spring.application.name=Decentralized degree vault backend

# Testing Blockchain Configuration (Sepolia Testnet)
blockchain.rpc-url=https://sepolia.infura.io/v3/YOUR_INFURA_KEY
contract.address=0x742d35Cc6634C0532925a3b844Bc9e7595f4bEb
wallet.private-key=YOUR_TEST_WALLET_PRIVATE_KEY

# Testing Database
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop

# Logging
logging.level.root=WARN
logging.level.com.decentralized.degree.vault=INFO
logging.level.org.web3j=INFO

# Server
server.port=8080
server.servlet.context-path=/

# Testing settings
spring.test.randomPort=true
```

### 3. Production Environment
**File: `src/main/resources/application-prod.properties`**

```properties
spring.application.name=Decentralized degree vault backend

# Production Blockchain Configuration (Mainnet)
blockchain.rpc-url=https://mainnet.infura.io/v3/YOUR_INFURA_KEY
contract.address=${CONTRACT_ADDRESS}
wallet.private-key=${WALLET_PRIVATE_KEY}

# Production Database (MySQL)
spring.datasource.url=jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# Logging
logging.level.root=WARN
logging.level.com.decentralized.degree.vault=INFO
logging.level.org.web3j=WARN

# Server
server.port=8080
server.servlet.context-path=/

# Production settings
spring.cache.type=redis
server.ssl.enabled=true
server.ssl.key-store=${KEY_STORE_PATH}
server.ssl.key-store-password=${KEY_STORE_PASSWORD}
server.ssl.key-store-type=PKCS12
```

## Using Environment-Specific Configurations

### Running with Development Profile
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

### Running with Test Profile
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=test"
```

### Running with Production Profile
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=prod"
```

### Using JAR with Profile
```bash
java -jar application.jar --spring.profiles.active=prod
```

## Environment Variables (Production)

Create a `.env` file or set system variables:

```bash
# Blockchain Configuration
export BLOCKCHAIN_RPC_URL=https://mainnet.infura.io/v3/YOUR_KEY
export CONTRACT_ADDRESS=0xYourContractAddress
export WALLET_PRIVATE_KEY=YourPrivateKeyWithoutOx

# Database Configuration
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=degree_vault
export DB_USER=vault_user
export DB_PASSWORD=SecurePassword123!

# SSL Configuration
export KEY_STORE_PATH=/path/to/keystore.p12
export KEY_STORE_PASSWORD=KeystorePassword
```

## Docker Configuration

**File: `Dockerfile`**

```dockerfile
FROM openjdk:21-jdk-slim

WORKDIR /app

# Copy the JAR file
COPY target/Decentralizeddegreevault-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
```

**File: `docker-compose.yml`**

```yaml
version: '3.8'

services:
  backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      - BLOCKCHAIN_RPC_URL=http://ganache:8545
      - CONTRACT_ADDRESS=${CONTRACT_ADDRESS}
      - WALLET_PRIVATE_KEY=${WALLET_PRIVATE_KEY}
      - DB_HOST=mysql
      - DB_NAME=degree_vault
      - DB_USER=vault_user
      - DB_PASSWORD=${DB_PASSWORD}
    depends_on:
      - mysql
      - ganache

  mysql:
    image: mysql:8.0
    environment:
      - MYSQL_ROOT_PASSWORD=RootPassword
      - MYSQL_DATABASE=degree_vault
      - MYSQL_USER=vault_user
      - MYSQL_PASSWORD=${DB_PASSWORD}
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  ganache:
    image: trufflesuite/ganache-cli
    ports:
      - "8545:8545"
    command: ganache-cli --host 0.0.0.0 --port 8545

volumes:
  mysql_data:
```

### Run with Docker Compose

```bash
# Build and start all services
docker-compose up --build

# Stop services
docker-compose down

# View logs
docker-compose logs -f backend
```

## Blockchain Network Configuration

### Development (Ganache)
```properties
blockchain.rpc-url=http://localhost:8545
```

### Testing (Sepolia Testnet)
```properties
blockchain.rpc-url=https://sepolia.infura.io/v3/YOUR_INFURA_KEY
```

### Staging (Sepolia)
```properties
blockchain.rpc-url=https://sepolia.infura.io/v3/YOUR_INFURA_KEY
```

### Production (Ethereum Mainnet)
```properties
blockchain.rpc-url=https://mainnet.infura.io/v3/YOUR_INFURA_KEY
```

## Infura Configuration

### Getting Infura API Key

1. Go to https://infura.io
2. Create account or log in
3. Create new project
4. Select Ethereum > Sepolia or Mainnet
5. Copy the HTTPS endpoint

Example Infura URL:
```
https://sepolia.infura.io/v3/YOUR_PROJECT_ID
```

## Private Key Security

### For Development
```bash
# Generate test private key (DO NOT USE FOR REAL FUNDS)
ganache-cli --host 0.0.0.0 --port 8545
# Copy private key from output
```

### For Production
- Use a hardware wallet private key (offline)
- Never commit private keys to version control
- Use environment variables or secure vaults
- Consider using AWS KMS or similar services

## Database Configuration

### Development (H2 In-Memory)
```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
```

Access H2 Console: http://localhost:8080/h2-console

### Production (MySQL)
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/degree_vault
spring.datasource.username=vault_user
spring.datasource.password=SecurePassword123!
spring.jpa.hibernate.ddl-auto=validate
```

## Logging Levels

```properties
# OFF > FATAL > ERROR > WARN > INFO > DEBUG > TRACE

# Development (verbose)
logging.level.root=DEBUG
logging.level.com.decentralized.degree.vault=DEBUG

# Production (minimal)
logging.level.root=WARN
logging.level.com.decentralized.degree.vault=INFO
```

## CI/CD Pipeline Example (GitHub Actions)

**File: `.github/workflows/deploy.yml`**

```yaml
name: Deploy to Production

on:
  push:
    branches:
      - main

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Set up JDK 21
        uses: actions/setup-java@v2
        with:
          java-version: 21
      
      - name: Build with Maven
        run: ./mvnw clean package -DskipTests
      
      - name: Run Tests
        run: ./mvnw test
      
      - name: Build Docker image
        run: docker build -t degree-vault:latest .
      
      - name: Deploy to Production
        env:
          CONTRACT_ADDRESS: ${{ secrets.CONTRACT_ADDRESS }}
          WALLET_PRIVATE_KEY: ${{ secrets.WALLET_PRIVATE_KEY }}
          DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
        run: |
          # Deploy script here
```

## Health Check Monitoring

```bash
# Monitor backend health
watch -n 5 'curl -s http://localhost:8080/api/v1/degrees/health'

# With logging
curl -v http://localhost:8080/api/v1/degrees/health 2>&1 | tee health.log
```

## Troubleshooting

### Issue: Connection Refused
```bash
# Check if backend is running
netstat -an | grep 8080

# Check logs
tail -f logs/application.log
```

### Issue: Invalid RPC URL
```bash
# Test RPC endpoint
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"web3_clientVersion","params":[],"id":1}'
```

### Issue: Invalid Private Key
```bash
# Validate key format (should be 64 hex chars)
echo $WALLET_PRIVATE_KEY | wc -c
```

---

**Status:** All environment configurations ready!


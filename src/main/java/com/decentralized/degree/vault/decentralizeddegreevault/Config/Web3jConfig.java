package com.decentralized.degree.vault.decentralizeddegreevault.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.StaticGasProvider;
import java.math.BigInteger;

/**
 * Web3j Configuration for blockchain connection.
 *
 * <p>Provides the Web3j client and gas provider beans used by the service layer
 * to interact with the Ethereum-compatible blockchain.</p>
 *
 * // KAFKA-READY: In future, blockchain transactions can be triggered by consuming
 * // from a Kafka topic 'degree-issue-commands'. Each message triggers issueDegree().
 * // This decouples the HTTP layer from blockchain latency completely.
 * //
 * // Architecture evolution:
 * // Current:  HTTP Request → Controller → Service → Web3j → Blockchain
 * // Future:   HTTP Request → Controller → Kafka Producer → 'degree-issue-commands'
 * //           Kafka Consumer → Service → Web3j → Blockchain
 * //           Kafka Consumer → 'degree-issue-results' → Status Update Service
 * //
 * // Benefits:
 * // - HTTP requests return immediately (202 Accepted)
 * // - Blockchain latency is absorbed by the consumer
 * // - Failed transactions can be retried via Kafka dead-letter topics
 * // - Horizontal scaling by adding more consumer instances
 */
@Configuration
public class Web3jConfig {

    @Value("${blockchain.rpc-url}")
    private String rpcUrl;

    @Bean
    public Web3j web3j() {
        return Web3j.build(new HttpService(rpcUrl));
    }

    @Bean
    public ContractGasProvider gasProvider() {
        // DefaultGasProvider has a gas limit of 9,000,000 which exceeds Ganache's default.
        return new StaticGasProvider(
                BigInteger.valueOf(20000000000L), // gas price
                BigInteger.valueOf(6721975L)      // gas limit
        );
    }
}

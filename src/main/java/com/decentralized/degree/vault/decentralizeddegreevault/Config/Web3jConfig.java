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
 * Web3j Configuration for blockchain connection
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

package com.decentralized.degree.vault.decentralizeddegreevault.Config;

import io.ipfs.api.IPFS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IpfsConfig {

    @Value("${ipfs.host:127.0.0.1}")
    private String host;

    @Value("${ipfs.port:5001}")
    private int port;

    @Bean
    public IPFS ipfs() {
        return new IPFS("/ip4/" + host + "/tcp/" + port);
    }
}


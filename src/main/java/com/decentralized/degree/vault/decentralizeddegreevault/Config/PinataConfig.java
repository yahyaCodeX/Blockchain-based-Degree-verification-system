package com.decentralized.degree.vault.decentralizeddegreevault.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PinataConfig {

    @Bean(name = "pinataRestTemplate")
    public RestTemplate pinataRestTemplate(
            @Value("${pinata.jwt.key:${PINATA_JWT:}}") String pinataJwt
    ) {
        if (pinataJwt == null || pinataJwt.isBlank()) {
            throw new IllegalStateException("Pinata JWT is missing. Set pinata.jwt.key or PINATA_JWT.");
        }

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + pinataJwt);
            return execution.execute(request, body);
        });
        return restTemplate;
    }
}

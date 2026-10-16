package com.decentralized.degree.vault.decentralizeddegreevault.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class PinataService {

    private static final String PIN_FILE_PATH = "/pinning/pinFileToIPFS";

    private final RestTemplate pinataRestTemplate;
    private final ObjectMapper objectMapper;
    private final String pinataBaseUrl;

    public PinataService(
            @Qualifier("pinataRestTemplate") RestTemplate pinataRestTemplate,
            ObjectMapper objectMapper,
            @Value("${pinata.api.base-url:https://api.pinata.cloud}") String pinataBaseUrl
    ) {
        this.pinataRestTemplate = pinataRestTemplate;
        this.objectMapper = objectMapper;
        this.pinataBaseUrl = pinataBaseUrl;
    }

    public String uploadFile(MultipartFile file) throws IOException {
        return uploadBytes(file.getBytes(), file.getOriginalFilename(), file.getContentType());
    }

    public String uploadBytes(byte[] fileBytes, String fileName) {
        return uploadBytes(fileBytes, fileName, MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    public String uploadBytes(byte[] fileBytes, String fileName, String contentType) {
        String uploadName = (fileName == null || fileName.isBlank()) ? "upload.bin" : fileName;

        ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return uploadName;
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentDisposition(ContentDisposition.builder("form-data").name("file").filename(uploadName).build());
        fileHeaders.setContentType(resolveMediaType(contentType));

        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(fileResource, fileHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<String> response = pinataRestTemplate.exchange(
                pinataBaseUrl + PIN_FILE_PATH,
                HttpMethod.POST,
                new HttpEntity<>(body, requestHeaders),
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
            throw new IllegalStateException("Pinata upload failed with status " + response.getStatusCode());
        }

        try {
            JsonNode json = objectMapper.readTree(response.getBody());
            String ipfsHash = json.path("IpfsHash").asText();
            if (ipfsHash == null || ipfsHash.isBlank()) {
                throw new IllegalStateException("Pinata response did not contain IpfsHash: " + response.getBody());
            }
            return ipfsHash;
        } catch (Exception ex) {
            log.error("Failed to parse Pinata response: {}", response.getBody(), ex);
            throw new IllegalStateException("Unable to parse Pinata upload response", ex);
        }
    }

    private static MediaType resolveMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}

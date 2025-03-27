package com.reeco.bas.transport.application;

import com.reeco.bas.transport.model.*;
import com.reeco.bas.transport.utils.annotators.Service;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

import java.io.File;
import java.net.URI;

@Service
@Slf4j
public class ConfigService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String CONFIG_FILE = "config.json";
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 1000;
    private static final String API_BASE_URL = "http://smartbas-data.vnemisoft.com";
    private final RestTemplate restTemplate;

    @Value("${data.organization-id}")
    private int ORGANIZATION_ID;

    @Value("${data.berth-id}")
    private int BERTH_ID;

    public ConfigService() {
            // Configure timeout settings
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(300); // 0.5 seconds timeout for connection
        requestFactory.setReadTimeout(400);    // 0.5 seconds timeout for reading
        this.restTemplate = new RestTemplate(requestFactory);

    }

    @Scheduled(fixedDelayString = "${config.fetch.interval:1000}") // Default 60 seconds
    public void fetchAndUpdateConfig() {
        try {
            String url = API_BASE_URL + "/data-app/config/"+ "E052JI";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Parse the response to ConfigModel
                ConfigModel newConfig = objectMapper.readValue(response.getBody(), ConfigModel.class);
                // Save the new configuration
                saveConfig(newConfig);
                log.info("[HEARTBEAT] Successfully updated.");
            }  else {
                log.error("Failed to fetch configuration. Status: {}", response.getStatusCode());
            }
        } catch (RestClientException e) {
            ;
        } catch (Exception e) {
            log.error("Error processing configuration update: {}", e.getMessage());
        }
    }

    public ConfigModel loadConfig() {
        int retryCount = 0;
        while (retryCount < MAX_RETRIES) {
            try {
                File configFile = new File(CONFIG_FILE);
                if (!configFile.exists()) {
                    log.warn("Config file not found: {}", CONFIG_FILE);
                    Thread.sleep(RETRY_DELAY_MS);
                    retryCount++;
                    continue;
                }
                ConfigModel config = objectMapper.readValue(configFile, ConfigModel.class);
                return config;
            } catch (Exception e) {
                log.error("Error reading config file: {}", e.getMessage());
                retryCount++;
                if (retryCount < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting to retry config load", ie);
                    }
                }
            }
        }
        throw new RuntimeException("Failed to load config after " + MAX_RETRIES + " attempts");
    }

    public void saveConfig(ConfigModel config) {
        try {
            objectMapper.writeValue(new File(CONFIG_FILE), config);
        } catch (Exception e) {
            log.error("Error saving config file: {}", e.getMessage());
            log.error("Failed to save config", e);
        }
    }

    // Helper method to validate configuration
    private boolean isValidConfig(ConfigModel config) {
//        return config != null && config.getBerthId() != null && config.getBerthId() == 1;
        return true;
    }

    // Initialization method
    public void init() {
        // Fetch configuration immediately on startup
        fetchAndUpdateConfig();
    }
}

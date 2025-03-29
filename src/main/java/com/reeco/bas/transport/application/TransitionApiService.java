package com.reeco.bas.transport.application;

import com.reeco.bas.transport.model.VesselStateTransition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TransitionApiService {

    private static final String API_ENDPOINT_TEMPLATE = "%s/data-app/transition/%s";

    @Value("${api.base.url:http://smartbas-data.vnemisoft.com}")
    private String apiBaseUrl;

    @Value("${api.connect.timeout:1000}")
    private int connectTimeout;

    @Value("${api.read.timeout:2000}")
    private int readTimeout;

    @Value("${api.max.retries:3}")
    private int maxRetries;

    @Value("${api.retry.delay:1000}")
    private long retryDelayMillis;

    private final RestTemplate restTemplate;

    public TransitionApiService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public boolean notifyStateTransition(VesselStateTransition transition) {
        String apiUrl = String.format(API_ENDPOINT_TEMPLATE, apiBaseUrl, transition.getDataAppCode());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<VesselStateTransition> requestEntity = new HttpEntity<>(transition, headers);

        // Attempt with retries
        int attempt = 0;

        while (attempt < maxRetries) {
            attempt++;

            try {
                log.info("Sending state transition notification to {}: {} -> {} (attempt {}/{})",
                        apiUrl, transition.getFromState(), transition.getToState(), attempt, maxRetries);

                ResponseEntity<ApiResponse> response = restTemplate.exchange(
                        apiUrl,
                        HttpMethod.POST,
                        requestEntity,
                        ApiResponse.class
                );

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("Successfully notified state transition: {} -> {}",
                            transition.getFromState(), transition.getToState());
                    return true;
                } else {
                    log.warn("Failed to notify state transition: {} -> {}. Status: {}",
                            transition.getFromState(), transition.getToState(), response.getStatusCode());
                }
            } catch (RestClientException e) {
                if (attempt < maxRetries) {
                    log.warn("Error sending transition notification (attempt {}/{}): {}. Retrying in {} ms...",
                            attempt, maxRetries, e.getMessage(), retryDelayMillis);

                    try {
                        TimeUnit.MILLISECONDS.sleep(retryDelayMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Retry delay interrupted");
                    }
                } else {
                    log.error("Failed to notify state transition after {} attempts: {} -> {}. Error: {}",
                            maxRetries, transition.getFromState(), transition.getToState(), e.getMessage());
                }
            }
        }

        return false;
    }

    @lombok.Data
    private static class ApiResponse {
        private String status;
        private String message;
    }
}
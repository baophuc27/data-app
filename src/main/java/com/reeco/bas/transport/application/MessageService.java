package com.reeco.bas.transport.application;

import com.reeco.bas.transport.model.CombinedData;
import com.reeco.bas.transport.model.DataModel;
import com.reeco.bas.transport.utils.annotators.Infrastructure;
import com.reeco.bas.transport.utils.annotators.Service;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Slf4j
@RequiredArgsConstructor
@Infrastructure
@ConfigurationProperties(prefix = "gateway")
public class MessageService {
    private static final String BASE_URL = "http://smartbas-data.vnemisoft.com";
    private static final String SEND_MESSAGE_ENDPOINT = "/data-app/sensor-data";
    private static final String DATA_APP_CODE = "E052JI";
    private static final int CONNECT_TIMEOUT = 300;
    private static final int READ_TIMEOUT = 400;

    @Value("${gateway.topic.raw-data}")
    private String RAW_DATA_TOPIC_NAME;

    @Value("${gateway.topic.processed-data}")
    private String PROCESSED_DATA_TOPIC_NAME;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public MessageService() {
        this.objectMapper = new ObjectMapper();
        this.restTemplate = createRestTemplateWithTimeouts();
    }

    private RestTemplate createRestTemplateWithTimeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return new RestTemplate(factory);
    }

    public void sendDataRecord(DataModel message) {
        log.info("Sending data record to topic: {}", RAW_DATA_TOPIC_NAME);
        sendMessage(RAW_DATA_TOPIC_NAME, message);
    }

    public void sendProcessedDataRecord(CombinedData message) {
        sendMessage(PROCESSED_DATA_TOPIC_NAME, message);
    }

    private <T> void sendMessage(String topic, T message) {
        try {
            String endpoint = BASE_URL + SEND_MESSAGE_ENDPOINT;
            String messageJson = objectMapper.writeValueAsString(message);
            MessageRequest request = new MessageRequest(topic,DATA_APP_CODE, messageJson);

            HttpEntity<MessageRequest> entity = createHttpEntity(request);
            ResponseEntity<MessageResponse> response = restTemplate.postForEntity(
                    endpoint,
                    entity,
                    MessageResponse.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to send message. Status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error sending message to service: {}", e.getMessage(), e);
        }
    }

    private HttpEntity<MessageRequest> createHttpEntity(MessageRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(request, headers);
    }
}

@lombok.Data
@lombok.AllArgsConstructor
class MessageRequest {
    private String topic;
    private String code;
    private Object message;
}

@lombok.Data
class MessageResponse {
    private String status;
    private String message;
    private MessageDetails details;
}

@lombok.Data
class MessageDetails {
    private String topic;
    private int partition;
    private long offset;
}
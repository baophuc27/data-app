package com.reeco.bas.transport.infrastructure;

import com.reeco.bas.transport.application.ConfigService;
import org.codehaus.jackson.map.ObjectMapper;
import com.reeco.bas.transport.model.ConfigModel;
import com.reeco.bas.transport.utils.annotators.Infrastructure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class KafkaMessageConsumer {

    private final static ObjectMapper objectMapper = new ObjectMapper();

    private static final ConfigService configService = new ConfigService();

    private <T> T parseObject(byte[] message, Class<T> valueType){
        try {
            return objectMapper.readValue(message,valueType);
        } catch (RuntimeException | IOException e) {
            log.info("Error when parsing message object: {}",e.getMessage());
            return null;
        }
    }

    @KafkaListener(topics = "bas_config_event", containerFactory = "connectionListener")
    public void process(@Headers Map<String,byte[]> header, @Payload String message){
        try {
            ConfigModel config = objectMapper.readValue(message, ConfigModel.class);
            log.info("Got new config: {}", config.toString());
            if (config.getBerthId() != 1){
                return;
            }

//            objectMapper.writeValue(new File("config.json"), config);
            configService.saveConfig(config);

        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }
}

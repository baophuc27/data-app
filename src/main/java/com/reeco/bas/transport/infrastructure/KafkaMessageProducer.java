package com.reeco.bas.transport.infrastructure;

import com.reeco.bas.transport.application.MessageService;
import com.reeco.bas.transport.infrastructure.kafka.KafkaBaseMsg;
import com.reeco.bas.transport.infrastructure.kafka.KafkaStringMsg;
import com.reeco.bas.transport.infrastructure.kafka.KafkaMsgCallback;
import com.reeco.bas.transport.model.CombinedData;
import com.reeco.bas.transport.model.DataModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.concurrent.ListenableFuture;
import com.reeco.bas.transport.infrastructure.kafka.ByteSerializer;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import com.reeco.bas.transport.utils.annotators.Infrastructure;

@RequiredArgsConstructor
@Slf4j
public class KafkaMessageProducer {

    private KafkaTemplate<String, byte[]> kafkaTemplate;

    private ByteSerializer byteSerializer;


    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value(value = "bas_raw_data_event")
    private String sendRawDataTopicName;

    @Value(value = "bas_data_event")
    private String sendProcessedDataTopicName;

    private final MessageService messageService = new MessageService();


    public void sendRawDataRecord(DataModel message){
        byte[] data = byteSerializer.getBytes(message);
        KafkaBaseMsg kafkaMsg = new KafkaBaseMsg("1",data,null);
        send(sendRawDataTopicName,kafkaMsg,kafkaTemplate);
    }

    public void sendRawDataRecordHTTP(DataModel message){
        messageService.sendDataRecord(message);
    }

    public void sendProcessedDataRecordHTTP(CombinedData message) {
        messageService.sendProcessedDataRecord(message);

    }

    public void sendProcessedDataRecord(CombinedData message){
        byte[] data = byteSerializer.getBytes(message);
        KafkaBaseMsg kafkaMsg = new KafkaBaseMsg("1",data,null);
        send(sendProcessedDataTopicName,kafkaMsg,kafkaTemplate);
    }
    private void send(String topicName, KafkaBaseMsg message, KafkaTemplate<String,byte[]> template){
        CompletableFuture<SendResult<String,byte[]>> future = template.send(topicName, message.getKey(),message.getValue());
        future.thenAccept(result -> {
            new KafkaMsgCallback();
        });
    }
}

package com.reeco.bas.transport.infrastructure.kafka;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor

public class KafkaStringMsg {

    String key;

    String value;

    KafkaMsgHeader headers;

}

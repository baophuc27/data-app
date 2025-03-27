package com.reeco.bas.transport.infrastructure.kafka;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFutureCallback;


@Slf4j
@NoArgsConstructor
public class KafkaMsgCallback implements ListenableFutureCallback<SendResult<String,byte[]>>{

    @Override
    public void onFailure(Throwable cause){
        log.warn("Got an exception when sending message: {}",cause.getMessage());
    }


    @Override
    public void onSuccess(SendResult<String, byte[]> result) {
        if (result == null){
            return;
        }
    }
}

package com.reeco.bas.transport.infrastructure.kafka;

import com.google.gson.Gson;
import com.reeco.bas.transport.utils.annotators.Infrastructure;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Infrastructure
public class ByteSerializer {
    private static final Gson gson = new Gson();

    private static final Charset CHARSET = StandardCharsets.UTF_8;

    public byte[] getBytes(Object o){
        String line = gson.toJson(o);
        return line.getBytes(CHARSET);
    }
}

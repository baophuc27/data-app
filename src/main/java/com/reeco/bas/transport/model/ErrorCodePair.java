package com.reeco.bas.transport.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
@AllArgsConstructor
public class ErrorCodePair {
    private int error_code;

    private String error_message;
}

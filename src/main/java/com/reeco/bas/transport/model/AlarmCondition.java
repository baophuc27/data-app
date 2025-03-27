package com.reeco.bas.transport.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlarmCondition {
    private String operator;
    private Double value;
    private Integer statusId;
}

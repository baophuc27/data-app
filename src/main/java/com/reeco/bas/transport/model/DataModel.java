package com.reeco.bas.transport.model;


import lombok.Getter;
import lombok.Setter;
import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@Getter
@Setter
@AllArgsConstructor
public class DataModel {
    private Integer orgId;

    private Integer berthId;

    private SensorsType sensorsType;

    public Double speed;

    public Double distance;

    public int error_code;

    public String error_msg;
}

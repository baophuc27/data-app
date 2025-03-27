package com.reeco.bas.transport.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Data
@AllArgsConstructor
@Getter
public class SensorData {

    public Double speed;

    public Double distance;

    public Double signalStrength;


}

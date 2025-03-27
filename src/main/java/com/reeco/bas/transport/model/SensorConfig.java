package com.reeco.bas.transport.model;

import lombok.ToString;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.List;

@ToString
public class SensorConfig {

    @JsonProperty("left_sensor")
    private List<ConditionConfig> leftSensor;

    @JsonProperty("right_sensor")
    private List<ConditionConfig> rightSensor;

    public List<ConditionConfig> getLeftSensor() {
        return leftSensor;
    }

    public void setLeftSensor(List<ConditionConfig> leftSensor) {
        this.leftSensor = leftSensor;
    }

    public List<ConditionConfig> getRightSensor() {
        return rightSensor;
    }

    public void setRightSensor(List<ConditionConfig> rightSensor) {
        this.rightSensor = rightSensor;
    }
}

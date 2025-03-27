package com.reeco.bas.transport.model;

import lombok.ToString;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.List;

@ToString
public class ZoneConfig {

    @JsonProperty("distance")
    private SensorConfig distance;

    @JsonProperty("speed")
    private SensorConfig speed;

    @JsonProperty("angle")
    private List<ConditionConfig> angle;

    public SensorConfig getDistance() {
        return distance;
    }

    public void setDistance(SensorConfig distance) {
        this.distance = distance;
    }

    public SensorConfig getSpeed() {
        return speed;
    }

    public void setSpeed(SensorConfig speed) {
        this.speed = speed;
    }

    public List<ConditionConfig> getAngle() {
        return angle;
    }

    public void setAngle(List<ConditionConfig> angle) {
        this.angle = angle;
    }
}

package com.reeco.bas.transport.model;

import lombok.ToString;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
public class ConfigModel {

    @JsonProperty("orgId")
    int orgId;

    @JsonProperty("berth_id")
    int berth_id;

    @JsonProperty("session_id")
    int sessionId;

    @JsonProperty("distance_left_sensor_to_fender")
    double distanceLeftSensorToFender;

    @JsonProperty("distance_right_sensor_to_fender")
    double distanceRightSensorToFender;

    @JsonProperty("distance_between_sensors")
    double distanceBetweenFender;

    @JsonProperty("limit_zone_1")
    double limitZone1;

    @JsonProperty("limit_zone_2")
    double limitZone2;

    @JsonProperty("limit_zone_3")
    double limitZone3;

    @JsonProperty("mode")
    String mode;

    @JsonProperty("alarm")
    private AlarmConfig alarm;

    public int getOrgId() {
        return orgId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public void setOrgId(int orgId) {
        this.orgId = orgId;
    }

    public int getBerthId() {
        return berth_id;
    }

    public void setBerthId(int berthId) {
        this.berth_id = berthId;
    }

    public int getSessionId() {
        return sessionId;
    }

    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }

    public double getDistanceLeftSensorToFender() {
        return distanceLeftSensorToFender;
    }

    public void setDistanceLeftSensorToFender(double distanceLeftSensorToFender) {
        this.distanceLeftSensorToFender = distanceLeftSensorToFender;
    }

    public double getDistanceRightSensorToFender() {
        return distanceRightSensorToFender;
    }

    public void setDistanceRightSensorToFender(double distanceRightSensorToFender) {
        this.distanceRightSensorToFender = distanceRightSensorToFender;
    }

    public double getDistanceBetweenFender() {
        return distanceBetweenFender;
    }

    public void setDistanceBetweenFender(double distanceBetweenFender) {
        this.distanceBetweenFender = distanceBetweenFender;
    }

    public double getLimitZone1() {
        return limitZone1;
    }

    public void setLimitZone1(double limitZone1) {
        this.limitZone1 = limitZone1;
    }

    public double getLimitZone2() {
        return limitZone2;
    }

    public void setLimitZone2(double limitZone2) {
        this.limitZone2 = limitZone2;
    }

    public double getLimitZone3() {
        return limitZone3;
    }

    public void setLimitZone3(double limitZone3) {
        this.limitZone3 = limitZone3;
    }

    public AlarmConfig getAlarm() {
        return alarm;
    }

    public void setAlarm(AlarmConfig alarm) {
        this.alarm = alarm;
    }
}



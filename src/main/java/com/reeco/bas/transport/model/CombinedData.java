package com.reeco.bas.transport.model;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
public class CombinedData {
    private int orgid;
    private int berth_id;

    private int session_id;
    private AngleData angle;
    private SensorMetrics distance;
    private SensorMetrics speed;
    private String event_time;
    private int error_code;
    private String error_msg;

    @Data
    @Builder
    @ToString
    public static class AngleData {
        private double value;
        private int status_id;
        private int zone;
    }

    @Data
    @Builder
    @ToString
    public static class SensorMetrics {
        private SensorData ss01;
        private SensorData ss02;
    }

    @Data
    @Builder
    @ToString
    public static class SensorData {
        private double value;
        private int status_id;
        private int zone;
    }
}


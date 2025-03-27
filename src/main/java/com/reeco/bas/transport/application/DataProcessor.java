package com.reeco.bas.transport.application;

import com.reeco.bas.transport.model.*;
import com.reeco.bas.transport.utils.annotators.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

@Service
@Slf4j
@Component
public class DataProcessor {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSS");
    private static final Map<String, Integer> ZONE_MAPPING = Map.of(
            "zone_2", 2,
            "zone_3", 3,
            "zone_1", 1
    );

    private static final Map<String, BiPredicate<Double, Double>> OPERATORS = Map.of(
            ">", (value, condValue) -> value > condValue,
            "<", (value, condValue) -> value < condValue,
            ">=", (value, condValue) -> value >= condValue,
            "<=", (value, condValue) -> value <= condValue
    );

    @Value("${data.organization-id:52}")
    private int ORGANIZATION_ID;

    @Value("${data.berth-id:1}")
    private int BERTH_ID;

    public CombinedData createCombinedData(double angle, String leftZone, String rightZone,
                                           double leftDistance, double rightDistance,
                                           double leftSpeed, double rightSpeed,
                                           ConfigModel config) {
        return CombinedData.builder()
                .orgid(52)
                .berth_id(1)
                .session_id(config.getSessionId())
                .angle(buildAngleData(angle, leftZone, config))
                .distance(buildSensorMetrics(leftZone, rightZone, leftDistance, rightDistance, ParameterType.DISTANCE, config))
                .speed(buildSensorMetrics(leftZone, rightZone, leftSpeed, rightSpeed, ParameterType.SPEED, config))
                .event_time(getCurrentFormattedTime())
                .build();
    }

    private CombinedData.AngleData buildAngleData(double angle, String zone, ConfigModel config) {
        return CombinedData.AngleData.builder()
                .value(angle)
                .status_id(getStatusId(zone, Math.abs(angle), SensorType.LEFT, ParameterType.ANGLE, config))
                .zone(convertZoneStrToInt(zone))
                .build();
    }

    private CombinedData.SensorMetrics buildSensorMetrics(String leftZone, String rightZone,
                                                          double leftValue, double rightValue,
                                                          ParameterType parameterType,
                                                          ConfigModel config) {
        return CombinedData.SensorMetrics.builder()
                .ss01(buildSensorData(leftValue, leftZone, SensorType.LEFT, parameterType, config))
                .ss02(buildSensorData(rightValue, rightZone, SensorType.RIGHT, parameterType, config))
                .build();
    }

    private CombinedData.SensorData buildSensorData(double value, String zone,
                                                    SensorType sensorType,
                                                    ParameterType parameterType,
                                                    ConfigModel config) {
        return CombinedData.SensorData.builder()
                .value(value)
                .status_id(getStatusId(zone, value, sensorType, parameterType, config))
                .zone(convertZoneStrToInt(zone))
                .build();
    }

    private String getCurrentFormattedTime() {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .plusHours(7)
                .format(DATE_FORMATTER);
    }

    private int convertZoneStrToInt(String zoneStr) {
        return ZONE_MAPPING.getOrDefault(zoneStr, 1);
    }

    public SyncPayload mappingCombinedDataToSyncPayload(CombinedData combinedData) {
        try {
            SyncPayload syncPayload = new SyncPayload();
            syncPayload.setRecordId(combinedData.getSession_id());
            syncPayload.setBerthId(combinedData.getBerth_id());
            syncPayload.setTime(combinedData.getEvent_time());
            syncPayload.setOrgId(combinedData.getOrgid());

            // Set zones
            syncPayload.setAngleZone(combinedData.getAngle().getZone());
            syncPayload.setLSpeedZone(combinedData.getSpeed().getSs01().getZone());
            syncPayload.setLDistanceZone(combinedData.getDistance().getSs01().getZone());
            syncPayload.setRDistanceZone(combinedData.getDistance().getSs02().getZone());
            syncPayload.setRSpeedZone(combinedData.getSpeed().getSs02().getZone());

            // Set values
            syncPayload.setLeftSpeed(combinedData.getSpeed().getSs01().getValue());
            syncPayload.setRightSpeed(combinedData.getSpeed().getSs02().getValue());
            syncPayload.setLeftDistance(combinedData.getDistance().getSs01().getValue());
            syncPayload.setRightDistance(combinedData.getDistance().getSs02().getValue());
            syncPayload.setAngle(combinedData.getAngle().getValue());

            // Set status and alarms
            int leftStatus = combinedData.getDistance().getSs01().getStatus_id();
            int rightStatus = combinedData.getDistance().getSs02().getStatus_id();

            syncPayload.setLeftStatus(leftStatus);
            syncPayload.setRightStatus(rightStatus);
            syncPayload.setRDistanceAlarm(rightStatus);
            syncPayload.setRSpeedAlarm(combinedData.getSpeed().getSs02().getStatus_id());
            syncPayload.setLDistanceAlarm(leftStatus);
            syncPayload.setLSpeedAlarm(combinedData.getSpeed().getSs01().getStatus_id());
            syncPayload.setAngleAlarm(combinedData.getAngle().getStatus_id());

            // Set timestamps
            String eventTime = combinedData.getEvent_time();
            syncPayload.setCreatedAt(eventTime);
            syncPayload.setUpdatedAt(eventTime);
            syncPayload.setDeletedAt("");

            return syncPayload;
        } catch (Exception e) {
            log.warn("Error mapping combined data to sync payload", e);
            return new SyncPayload();
        }
    }

    private Integer getStatusId(String zone, double value, SensorType sensorType,
                                ParameterType parameterType, ConfigModel config) {
        try {
            AlarmConfig alarmConfig = Optional.ofNullable(config.getAlarm())
                    .orElse(new AlarmConfig());

            ZoneConfig zoneConfig = switch (zone) {
                case "zone_1" -> alarmConfig.getZone1();
                case "zone_2" -> alarmConfig.getZone2();
                case "zone_3" -> alarmConfig.getZone3();
                default -> null;
            };

            if (zoneConfig == null) {
                return 1;
            }

            Object parameterData = switch (parameterType) {
                case ANGLE -> zoneConfig.getAngle();
                case SPEED -> zoneConfig.getSpeed();
                case DISTANCE -> zoneConfig.getDistance();
            };

            if (parameterData == null) {
                return 1;
            }

            if (parameterType == ParameterType.DISTANCE || parameterType == ParameterType.SPEED) {
                SensorConfig sensorConfig = (SensorConfig) parameterData;
                List<ConditionConfig> conditions = (sensorType == SensorType.LEFT)
                        ? sensorConfig.getLeftSensor()
                        : sensorConfig.getRightSensor();

                return evaluateConditions(conditions, value);
            }

            return 1;
        } catch (Exception e) {
            log.error("Error evaluating status ID: zone={}, value={}, sensorType={}, parameterType={}",
                    zone, value, sensorType, parameterType, e);
            return 1;
        }
    }

    private Integer evaluateConditions(List<ConditionConfig> conditions, double value) {
        if (conditions == null) {
            return 1;
        }

        return conditions.stream()
                .filter(condition -> evaluateCondition(condition.getOperator(), condition.getValue(), value))
                .findFirst()
                .map(ConditionConfig::getStatusId)
                .orElse(1);
    }

    private boolean evaluateCondition(String operator, Double condValue, double value) {
        if (condValue == null || condValue.isInfinite()) {
            return true;
        }

        return Optional.ofNullable(OPERATORS.get(operator))
                .map(op -> op.test(value, condValue))
                .orElse(false);
    }

    public double calculateAngle(double distanceParallel1, double distanceParallel2,
                                 double distanceBetweenParallelLines) {
        double deltaDifference = Math.abs(distanceParallel1 - distanceParallel2);
        double angleRadians = Math.atan2(deltaDifference, distanceBetweenParallelLines);
        double angleDegrees = Math.toDegrees(angleRadians);

        return distanceParallel1 < distanceParallel2 ? -angleDegrees : angleDegrees;
    }

    public String getZone(double distance, ConfigModel config) {
        if (distance < config.getLimitZone1()) {
            return "zone_1";
        }
        if (distance < config.getLimitZone2()) {
            return "zone_2";
        }
        return "zone_3";
    }
}

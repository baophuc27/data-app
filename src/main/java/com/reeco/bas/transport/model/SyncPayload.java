package com.reeco.bas.transport.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class SyncPayload {
    private int recordId;

    private int berthId;

    private String time;

    private int orgId;

    private int angleZone;

    private int LSpeedZone;

    private int LDistanceZone;

    private int RDistanceZone;

    private int RSpeedZone;

    private double leftSpeed;

    private double leftDistance;

    private double rightSpeed;

    private double rightDistance;

    private double angle;

    private int leftStatus;

    private int rightStatus;

    private int RDistanceAlarm;

    private int RSpeedAlarm;

    private int LDistanceAlarm;

    private int LSpeedAlarm;

    private int angleAlarm;

    private String createdAt;

    private String updatedAt;

    private String deletedAt;
}

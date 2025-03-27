package com.reeco.bas.transport.model;

import lombok.ToString;
import org.codehaus.jackson.annotate.JsonProperty;

@ToString
public class AlarmConfig {

    @JsonProperty("zone_1")
    private ZoneConfig zone1;

    @JsonProperty("zone_2")
    private ZoneConfig zone2;

    @JsonProperty("zone_3")
    private ZoneConfig zone3;

    public ZoneConfig getZone1() {
        return zone1;
    }

    public void setZone1(ZoneConfig zone1) {
        this.zone1 = zone1;
    }

    public ZoneConfig getZone2() {
        return zone2;
    }

    public void setZone2(ZoneConfig zone2) {
        this.zone2 = zone2;
    }

    public ZoneConfig getZone3() {
        return zone3;
    }

    public void setZone3(ZoneConfig zone3) {
        this.zone3 = zone3;
    }
}

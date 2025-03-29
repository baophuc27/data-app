package com.reeco.bas.transport.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VesselStateTransition {
    private String dataAppCode;
    private VesselState fromState;
    private VesselState toState;
    private Instant timestamp = Instant.now();

    public VesselStateTransition(String dataAppCode, VesselState fromState, VesselState toState) {
        this.dataAppCode = dataAppCode;
        this.fromState = fromState;
        this.toState = toState;
        this.timestamp = Instant.now();
    }
}
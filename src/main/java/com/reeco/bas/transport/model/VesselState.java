package com.reeco.bas.transport.model;

public enum VesselState {
    AVAILABLE,    // No vessel at berth
    BERTHING,     // Vessel is approaching the berth
    MOORING,      // Vessel is docked and moored
    DEPARTING     // Vessel is leaving the berth
}
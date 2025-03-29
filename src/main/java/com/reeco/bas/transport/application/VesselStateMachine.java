package com.reeco.bas.transport.application;

import com.reeco.bas.transport.model.ConfigModel;
import com.reeco.bas.transport.model.VesselState;
import com.reeco.bas.transport.model.VesselStateTransition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class VesselStateMachine {

    @Value("${data.app.code:E052JI}")
    private String dataAppCode;

    @Value("${vessel.transition.check.interval:5000}")
    private long stateCheckInterval;

    // Transition thresholds - could be moved to ConfigModel
    @Value("${vessel.transition.berthing.complete.distance:1.0}")
    private double berthingCompleteDistance;

    @Value("${vessel.transition.berthing.complete.speed:5.0}")
    private double berthingCompleteSpeed;

    @Value("${vessel.transition.berthing.complete.time:30}")
    private int berthingCompleteTimeSeconds;

    @Value("${vessel.transition.departing.start.distance:3.0}")
    private double departingStartDistance;

    @Value("${vessel.transition.departing.start.time:60}")
    private int departingStartTimeSeconds;

    @Value("${vessel.transition.departing.complete.time:30}")
    private int departingCompleteTimeSeconds;

    private VesselState currentState = VesselState.AVAILABLE;
    private Map<String, Object> stateContext = new HashMap<>();

    @Autowired
    private TransitionApiService transitionApiService;

    @Autowired
    private ConfigService configService;

    private Instant stateChangeTime = Instant.now();
    private Instant conditionMetTime = null;

    public void initializeWithState(VesselState initialState) {
        if (currentState != initialState) {
            log.info("Initializing vessel state machine with state: {}", initialState);
            currentState = initialState;
            stateChangeTime = Instant.now();
            stateContext.clear();
        }
    }

    public VesselState getCurrentState() {
        return currentState;
    }

    @Scheduled(fixedDelayString = "${vessel.transition.check.interval:5000}")
    public void checkForStateTransition() {
        ConfigModel config = configService.loadConfig();
        if (config == null || "stop".equals(config.getMode())) {
            return;
        }

        switch (currentState) {
            case AVAILABLE:
                // No automatic transition from AVAILABLE
                // This is triggered by user input
                break;

            case BERTHING:
                checkBerthingToMooringTransition();
                break;

            case MOORING:
                checkMooringToDepartingTransition();
                break;

            case DEPARTING:
                checkDepartingToAvailableTransition();
                break;
        }
    }

    private void checkBerthingToMooringTransition() {
        // Get latest sensor data from context
        Double leftDistance = (Double) stateContext.getOrDefault("leftDistance", Double.MAX_VALUE);
        Double rightDistance = (Double) stateContext.getOrDefault("rightDistance", Double.MAX_VALUE);
        Double leftSpeed = (Double) stateContext.getOrDefault("leftSpeed", Double.MAX_VALUE);
        Double rightSpeed = (Double) stateContext.getOrDefault("rightSpeed", Double.MAX_VALUE);

        double minDistance = Math.min(leftDistance, rightDistance);
        double maxSpeed = Math.max(Math.abs(leftSpeed), Math.abs(rightSpeed));

        if (minDistance < berthingCompleteDistance && maxSpeed < berthingCompleteSpeed) {
            if (conditionMetTime == null) {
                conditionMetTime = Instant.now();
                log.debug("Berthing completion condition first met at {}", conditionMetTime);
            } else {
                long elapsedSeconds = Instant.now().getEpochSecond() - conditionMetTime.getEpochSecond();
                if (elapsedSeconds >= berthingCompleteTimeSeconds) {
                    log.info("Transition condition met: BERTHING -> MOORING (distance < {}, speed < {}, time >= {}s)",
                            berthingCompleteDistance, berthingCompleteSpeed, berthingCompleteTimeSeconds);
                    transitionState(VesselState.MOORING);
                }
            }
        } else {
            // Reset the timer if conditions are no longer met
            if (conditionMetTime != null) {
                log.debug("Berthing completion conditions no longer met. Resetting timer.");
                conditionMetTime = null;
            }
        }
    }

    private void checkMooringToDepartingTransition() {
        // Get latest sensor data from context
        Double initialLeftDistance = (Double) stateContext.getOrDefault("initialLeftDistance", null);
        Double initialRightDistance = (Double) stateContext.getOrDefault("initialRightDistance", null);
        Double currentLeftDistance = (Double) stateContext.getOrDefault("leftDistance", Double.MAX_VALUE);
        Double currentRightDistance = (Double) stateContext.getOrDefault("rightDistance", Double.MAX_VALUE);

        // Initialize reference distances if not set
        if (initialLeftDistance == null || initialRightDistance == null) {
            stateContext.put("initialLeftDistance", currentLeftDistance);
            stateContext.put("initialRightDistance", currentRightDistance);
            stateContext.put("distanceCheckStartTime", Instant.now());
            return;
        }

        // Check if either sensor shows significant movement
        boolean leftMoving = currentLeftDistance - initialLeftDistance >= departingStartDistance;
        boolean rightMoving = currentRightDistance - initialRightDistance >= departingStartDistance;

        if (leftMoving || rightMoving) {
            Instant checkStartTime = (Instant) stateContext.get("distanceCheckStartTime");
            long elapsedSeconds = Instant.now().getEpochSecond() - checkStartTime.getEpochSecond();

            if (elapsedSeconds >= departingStartTimeSeconds) {
                log.info("Transition condition met: MOORING -> DEPARTING (distance change >= {}, time >= {}s)",
                        departingStartDistance, departingStartTimeSeconds);
                transitionState(VesselState.DEPARTING);
            }
        } else {
            // Update reference values periodically to handle small movements
            if (Instant.now().getEpochSecond() - ((Instant) stateContext.get("distanceCheckStartTime")).getEpochSecond() > 300) {
                stateContext.put("initialLeftDistance", currentLeftDistance);
                stateContext.put("initialRightDistance", currentRightDistance);
                stateContext.put("distanceCheckStartTime", Instant.now());
                log.debug("Updated reference distances for mooring state");
            }
        }
    }

    private void checkDepartingToAvailableTransition() {
        // Check if both sensors lost target
        Boolean leftTargetLost = (Boolean) stateContext.getOrDefault("leftTargetLost", false);
        Boolean rightTargetLost = (Boolean) stateContext.getOrDefault("rightTargetLost", false);

        if (leftTargetLost && rightTargetLost) {
            if (conditionMetTime == null) {
                conditionMetTime = Instant.now();
                log.debug("Departing completion condition first met at {}", conditionMetTime);
            } else {
                long elapsedSeconds = Instant.now().getEpochSecond() - conditionMetTime.getEpochSecond();
                if (elapsedSeconds >= departingCompleteTimeSeconds) {
                    log.info("Transition condition met: DEPARTING -> AVAILABLE (both targets lost for >= {}s)",
                            departingCompleteTimeSeconds);
                    transitionState(VesselState.AVAILABLE);
                }
            }
        } else {
            // Reset the timer if conditions are no longer met
            if (conditionMetTime != null) {
                log.debug("Departing completion conditions no longer met. Resetting timer.");
                conditionMetTime = null;
            }
        }
    }

    public void updateSensorData(double leftDistance, double rightDistance, double leftSpeed, double rightSpeed,
                                 boolean leftTargetLost, boolean rightTargetLost) {
        stateContext.put("leftDistance", leftDistance);
        stateContext.put("rightDistance", rightDistance);
        stateContext.put("leftSpeed", leftSpeed);
        stateContext.put("rightSpeed", rightSpeed);
        stateContext.put("leftTargetLost", leftTargetLost);
        stateContext.put("rightTargetLost", rightTargetLost);
        stateContext.put("lastUpdate", Instant.now());
    }

    public void manualTransition(VesselState newState) {
        if (isValidTransition(currentState, newState)) {
            log.info("Manual transition triggered: {} -> {}", currentState, newState);
            transitionState(newState);
        } else {
            log.warn("Invalid manual state transition attempted: {} -> {}", currentState, newState);
        }
    }

    private boolean isValidTransition(VesselState from, VesselState to) {
        return switch (from) {
            case AVAILABLE -> to == VesselState.BERTHING;
            case BERTHING -> to == VesselState.MOORING;
            case MOORING -> to == VesselState.DEPARTING;
            case DEPARTING -> to == VesselState.AVAILABLE;
        };
    }

    private void transitionState(VesselState newState) {
        VesselState oldState = currentState;
        currentState = newState;
        stateChangeTime = Instant.now();
        conditionMetTime = null;

        // Clear context but keep sensor data
        Map<String, Object> sensorData = new HashMap<>();
        sensorData.put("leftDistance", stateContext.getOrDefault("leftDistance", null));
        sensorData.put("rightDistance", stateContext.getOrDefault("rightDistance", null));
        sensorData.put("leftSpeed", stateContext.getOrDefault("leftSpeed", null));
        sensorData.put("rightSpeed", stateContext.getOrDefault("rightSpeed", null));
        sensorData.put("leftTargetLost", stateContext.getOrDefault("leftTargetLost", null));
        sensorData.put("rightTargetLost", stateContext.getOrDefault("rightTargetLost", null));
        sensorData.put("lastUpdate", stateContext.getOrDefault("lastUpdate", null));

        stateContext.clear();
        stateContext.putAll(sensorData);

        // Notify about state transition
        VesselStateTransition transition = new VesselStateTransition(dataAppCode, oldState, newState);
        boolean success = transitionApiService.notifyStateTransition(transition);

        if (success) {
            log.info("Successfully notified state transition: {} -> {}", oldState, newState);
        } else {
            log.error("Failed to notify state transition: {} -> {}", oldState, newState);
        }
    }
}
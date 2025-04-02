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
import jakarta.annotation.PostConstruct;

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

    // Track the last known mode to detect changes
    private String lastKnownMode = null;

    @PostConstruct
    public void initialize() {
        try {
            ConfigModel config = configService.loadConfig();
            if (config != null) {
                VesselState initialState = determineStateFromMode(config.getMode());
                initializeWithState(initialState);
                log.info("Initialized vessel state to {} based on config mode '{}'", initialState, config.getMode());
                lastKnownMode = config.getMode();
            } else {
                log.warn("Could not load configuration during initialization, defaulting to AVAILABLE state");
            }
        } catch (Exception e) {
            log.error("Error during vessel state initialization", e);
        }
    }

    private VesselState determineStateFromMode(String mode) {
        if (mode == null) {
            return VesselState.AVAILABLE;
        }

        return switch (mode) {
            case "start" -> VesselState.BERTHING;
            case "start-mooring" -> VesselState.MOORING;
            case "departing" -> VesselState.DEPARTING;
            default -> VesselState.AVAILABLE;
        };
    }

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
        if (config == null) {
            return;
        }

        String currentMode = config.getMode();
        boolean modeChanged = !currentMode.equals(lastKnownMode);

        // Handle mode-based state transitions
        if (modeChanged) {
            handleModeBasedTransition(currentMode);
        }

        // Update the last known mode
        lastKnownMode = currentMode;

        // Skip further processing if mode is "stop"
        if ("stop".equals(currentMode)) {
            return;
        }

        switch (currentState) {
            case AVAILABLE:
                // Automatic transition from AVAILABLE to BERTHING handled in handleModeBasedTransition
                break;

            case BERTHING:
                checkBerthingToMooringTransition(config);
                break;

            case MOORING:
                checkMooringToDepartingTransition(config);
                break;

            case DEPARTING:
                checkDepartingToAvailableTransition();
                break;
        }
    }

    private void handleModeBasedTransition(String newMode) {
        log.info("Mode changed to '{}'. Current state: {}", newMode, currentState);

        VesselState targetState = determineStateFromMode(newMode);
        if (targetState != currentState) {
            log.info("Mode '{}' indicates state should be {}. Transitioning from {}",
                    newMode, targetState, currentState);
            transitionState(targetState);
        }
    }

    private void checkBerthingToMooringTransition(ConfigModel config) {
        // Get latest sensor data from context including target lost status
        Double leftDistance = (Double) stateContext.getOrDefault("leftDistance", null);
        Double rightDistance = (Double) stateContext.getOrDefault("rightDistance", null);
        Double leftSpeed = (Double) stateContext.getOrDefault("leftSpeed", null);
        Double rightSpeed = (Double) stateContext.getOrDefault("rightSpeed", null);
        Boolean leftTargetLost = (Boolean) stateContext.getOrDefault("leftTargetLost", false);
        Boolean rightTargetLost = (Boolean) stateContext.getOrDefault("rightTargetLost", false);

        // Skip if we don't have valid data yet
        if (leftDistance == null || rightDistance == null || leftSpeed == null || rightSpeed == null) {
            return;
        }

        // Skip transition check if both sensors have target loss (error codes 1011/1012)
        if (leftTargetLost && rightTargetLost) {
            return;
        }

        // Calculate distances relative to fender
        double leftDistanceToFender = leftDistance - config.getDistanceLeftSensorToFender();
        double rightDistanceToFender = rightDistance - config.getDistanceRightSensorToFender();

        // Calculate using valid sensor data, using 0 for sensors with target loss
        double minDistance;
        double maxSpeed;
        
        if (leftTargetLost && !rightTargetLost) {
            // Only right sensor is valid
            minDistance = rightDistanceToFender;
            maxSpeed = Math.abs(rightSpeed);
        } else if (!leftTargetLost && rightTargetLost) {
            // Only left sensor is valid
            minDistance = leftDistanceToFender;
            maxSpeed = Math.abs(leftSpeed);
        } else {
            // Both sensors are valid
            minDistance = Math.min(leftDistanceToFender, rightDistanceToFender);
            maxSpeed = Math.max(Math.abs(leftSpeed), Math.abs(rightSpeed));
        }

        if (minDistance < berthingCompleteDistance && maxSpeed < berthingCompleteSpeed) {
            if (conditionMetTime == null) {
                conditionMetTime = Instant.now();
                if (log.isDebugEnabled()) {
                    log.debug("Berthing completion condition first met at {}", conditionMetTime);
                }
            } else {
                long elapsedSeconds = Instant.now().getEpochSecond() - conditionMetTime.getEpochSecond();
                if (elapsedSeconds >= berthingCompleteTimeSeconds) {
                    log.info("Transition: BERTHING -> MOORING (distance < {}, speed < {}, time >= {}s)",
                            berthingCompleteDistance, berthingCompleteSpeed, berthingCompleteTimeSeconds);
                    transitionState(VesselState.MOORING);
                }
            }
        } else if (conditionMetTime != null) {
            // Reset the timer if conditions are no longer met
            conditionMetTime = null;
        }
    }

    private void checkMooringToDepartingTransition(ConfigModel config) {
        // Get latest sensor data from context
        Double leftDistance = (Double) stateContext.getOrDefault("leftDistance", null);
        Double rightDistance = (Double) stateContext.getOrDefault("rightDistance", null);
        Boolean leftTargetLost = (Boolean) stateContext.getOrDefault("leftTargetLost", false);
        Boolean rightTargetLost = (Boolean) stateContext.getOrDefault("rightTargetLost", false);

        // Skip if we don't have valid sensor data yet
        if (leftDistance == null || rightDistance == null) {
            return;
        }

        // Calculate distances relative to fender
        double leftDistanceToFender = leftDistance - config.getDistanceLeftSensorToFender();
        double rightDistanceToFender = rightDistance - config.getDistanceRightSensorToFender();

        // Get reference distances, might be null if not set yet
        Double initialLeftFenderDistance = (Double) stateContext.getOrDefault("initialLeftFenderDistance", null);
        Double initialRightFenderDistance = (Double) stateContext.getOrDefault("initialRightFenderDistance", null);
        Instant movementDetectionStartTime = (Instant) stateContext.getOrDefault("movementDetectionStartTime", null);

        // Initialize reference distances if not set
        if (initialLeftFenderDistance == null || initialRightFenderDistance == null) {
            stateContext.put("initialLeftFenderDistance", leftDistanceToFender);
            stateContext.put("initialRightFenderDistance", rightDistanceToFender);
            stateContext.put("distanceCheckStartTime", Instant.now());
            stateContext.put("movementDetectionStartTime", null); // No movement detected yet
            return;
        }

        // Check if either sensor shows significant movement
        boolean leftMoving = !leftTargetLost && (leftDistanceToFender - initialLeftFenderDistance >= departingStartDistance);
        boolean rightMoving = !rightTargetLost && (rightDistanceToFender - initialRightFenderDistance >= departingStartDistance);

        if (leftMoving || rightMoving) {
            if (movementDetectionStartTime == null) {
                // Start tracking continuous movement
                movementDetectionStartTime = Instant.now();
                stateContext.put("movementDetectionStartTime", movementDetectionStartTime);
                if (log.isDebugEnabled()) {
                    log.debug("MOORING->DEPARTING: Movement detection started");
                }
            }

            long elapsedSeconds = Instant.now().getEpochSecond() - movementDetectionStartTime.getEpochSecond();

            if (elapsedSeconds >= departingStartTimeSeconds) {
                log.info("Transition: MOORING -> DEPARTING (fender distance change >= {}, time >= {}s)",
                        departingStartDistance, departingStartTimeSeconds);
                transitionState(VesselState.DEPARTING);
            }
        } else if (movementDetectionStartTime != null) {
            // If neither sensor is showing sufficient movement, reset the movement detection timer
            stateContext.put("movementDetectionStartTime", null);
        } else {
            // Check if we should update reference values due to small movements or elapsed time
            Instant checkStartTime = (Instant) stateContext.get("distanceCheckStartTime");
            if (checkStartTime == null) {
                checkStartTime = Instant.now();
                stateContext.put("distanceCheckStartTime", checkStartTime);
            }

            long timeSinceLastUpdate = Instant.now().getEpochSecond() - checkStartTime.getEpochSecond();

            // Only update reference distances if significant time has passed
            if (timeSinceLastUpdate > 300) {
                stateContext.put("initialLeftFenderDistance", leftDistanceToFender);
                stateContext.put("initialRightFenderDistance", rightDistanceToFender);
                stateContext.put("distanceCheckStartTime", Instant.now());
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
                if (log.isDebugEnabled()) {
                    log.debug("Departing completion condition first met at {}", conditionMetTime);
                }
            } else {
                long elapsedSeconds = Instant.now().getEpochSecond() - conditionMetTime.getEpochSecond();
                if (elapsedSeconds >= departingCompleteTimeSeconds) {
                    log.info("Transition: DEPARTING -> AVAILABLE (both targets lost for >= {}s)",
                            departingCompleteTimeSeconds);
                    transitionState(VesselState.AVAILABLE);
                }
            }
        } else if (conditionMetTime != null) {
            // Reset the timer if conditions are no longer met
            conditionMetTime = null;
        }
    }

    public void updateSensorData(double leftDistance, double rightDistance, double leftSpeed, double rightSpeed,
                                 boolean leftTargetLost, boolean rightTargetLost) {
        // Log when initial sensor data is received in MOORING state
        if (currentState == VesselState.MOORING &&
                !stateContext.containsKey("leftDistance") &&
                !stateContext.containsKey("rightDistance")) {
            log.info("MOORING: First sensor data received: Left: {}m, Right: {}m", leftDistance, rightDistance);
        }

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

        if (!success) {
            log.error("Failed to notify state transition: {} -> {}", oldState, newState);
        }
    }
}
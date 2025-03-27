package com.reeco.bas.transport.application;


import com.reeco.bas.transport.infrastructure.KafkaMessageProducer;
import com.reeco.bas.transport.model.*;
import com.reeco.bas.transport.utils.annotators.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

@Service
@Slf4j
public class DataService {
    private DataModel leftSensorData;
    private DataModel rightSensorData;
    private double lastValidLeftDistance = 0;
    private double lastValidRightDistance = 0;
    private boolean isLeftTimeout = false;
    private boolean isRightTimeout = false;
    private long leftSensorTimestamp = System.currentTimeMillis();
    private long rightSensorTimestamp = System.currentTimeMillis();
    private static final long SENSOR_TIMEOUT_MS = 10000;

    private static final DataProcessor dataProcessor = new DataProcessor();

    private static final CacheStorageService cacheStorageService = new CacheStorageService();

    @Autowired
    private final ConfigService configService = new ConfigService();

    @Autowired
    private final MessageService messageService = new MessageService();



        public void processData(DataModel dataModel) {
        ConfigModel config = configService.loadConfig();
	long currentTime = System.currentTimeMillis();

        if (dataModel.getSensorsType() == SensorsType.LEFT) {
            leftSensorData = dataModel;
	    leftSensorTimestamp = currentTime;
	    isLeftTimeout = false;
        } else if (dataModel.getSensorsType() == SensorsType.RIGHT) {
            rightSensorData = dataModel;
	    rightSensorTimestamp = currentTime;
	    isRightTimeout = false;
        }
	try {
            if (shouldProcessData(currentTime) && config.getBerthId() == 1) {
                // Handle timeout cases by copying data
                handleSensorTimeout(currentTime);
                processAndSendCombinedData(config);
            }
        } finally {
            // Only clear the data after successful processing or if both sensors have timed out
            if (shouldClearData(currentTime)) {
                leftSensorData = null;
                rightSensorData = null;
            }
        }
	
        log.info(String.valueOf(rightSensorTimestamp - currentTime));
	}
            private boolean shouldProcessData(long currentTime) {
        // Process if both sensors have data and at least one is recent
        return (leftSensorData != null && rightSensorData != null) ||
               (hasTimedOut(currentTime, leftSensorTimestamp) || 
                hasTimedOut(currentTime, rightSensorTimestamp));
    }

    private boolean shouldClearData(long currentTime) {
        // Clear data if both sensors are present and processed, or both have timed out
        return (leftSensorData != null && rightSensorData != null) ||
               (hasTimedOut(currentTime, leftSensorTimestamp) && 
                hasTimedOut(currentTime, rightSensorTimestamp));
    }

    private void handleSensorTimeout(long currentTime) {
        // If left sensor timed out but right is recent, copy right to left
        if (hasTimedOut(currentTime, leftSensorTimestamp) && 
            isDataRecent(currentTime, rightSensorTimestamp)) {
            leftSensorData = rightSensorData;
            //leftSensorTimestamp = rightSensorTimestamp;
	    isLeftTimeout = true;
        }
        // If right sensor timed out but left is recent, copy left to right
        else if (hasTimedOut(currentTime, rightSensorTimestamp) && 
                 isDataRecent(currentTime, leftSensorTimestamp)) {
            rightSensorData = leftSensorData;
            // rightSensorTimestamp = leftSensorTimestamp;
	    isRightTimeout = true;
        }
    }

    private boolean hasTimedOut(long currentTime, long timestamp) {
        return (currentTime - timestamp) > SENSOR_TIMEOUT_MS;
    }

    private boolean isDataRecent(long currentTime, long timestamp) {
        return (currentTime - timestamp) <= SENSOR_TIMEOUT_MS;
    }

    private void processAndSendCombinedData(ConfigModel config) {
        if (config.getMode().equals("stop")){
            cacheStorageService.exportAndClear(config.getOrgId(),config.getBerthId(),config.getSessionId());
        }
        else{
            double leftSpeed = leftSensorData.getSpeed() * 100;
            double rightSpeed = rightSensorData.getSpeed() * 100;

            double leftDistance = leftSensorData.getDistance();
            double rightDistance = rightSensorData.getDistance();

            if (leftDistance < 300) {
                lastValidLeftDistance = leftDistance;
            }
            if (rightDistance < 300) {
                lastValidRightDistance = rightDistance;
            }

            // Calculate distances relative to fender
            double leftDistanceToFender = lastValidLeftDistance - config.getDistanceLeftSensorToFender();
            double rightDistanceToFender = lastValidRightDistance - config.getDistanceRightSensorToFender();

            // Calculate angle
            double angle = dataProcessor.calculateAngle(leftDistanceToFender,rightDistanceToFender,config.getDistanceBetweenFender());

            String leftZone = dataProcessor.getZone(leftDistanceToFender,config);
            String rightZone = dataProcessor.getZone(rightDistanceToFender,config);

            CombinedData combinedData = dataProcessor.createCombinedData(angle, leftZone, rightZone,
                    leftDistanceToFender, rightDistanceToFender, leftSpeed, rightSpeed,config);

            ErrorCodePair errorPair = mergeErrorCode(
                    leftSensorData.getError_code(),
                    rightSensorData.getError_code()
            );
            combinedData.setError_code(errorPair.getError_code());
            combinedData.setError_msg(errorPair.getError_message());

	    if (isLeftTimeout){
	    combinedData = deleteSSData(combinedData,true,false);
	    combinedData.setError_code(1031);
	    }
	    if (isRightTimeout){
	    combinedData = deleteSSData(combinedData,false,true);
	    combinedData.setError_code(1032);
	    }

        messageService.sendProcessedDataRecord(combinedData);
        log.info("[PROCESSED DATA]: "+combinedData.toString());
	    try{
            SyncPayload syncPayload = dataProcessor.mappingCombinedDataToSyncPayload(combinedData);
            cacheStorageService.addItem(syncPayload);
	    }
	    catch (Exception e){
	    log.error("Failed to process and cache sync payload. combinedData: {}", combinedData, e);
	    }
        }


    }


    private ErrorCodePair mergeErrorCode(int errorCode1, int errorCode2) {
        if (errorCode1 == 1011 && errorCode2 == 0) {
            return new ErrorCodePair(1011, "Left sensor out of target");
        }
        if (errorCode1 == 0 && errorCode2 == 1012) {
            return new ErrorCodePair(1012, "Right sensor out of target");
        }
        if (errorCode1 == 1011 && errorCode2 == 1012) {
            return new ErrorCodePair(1013, "Both sensor out of target");
        }

        if (errorCode1 == 1021 && errorCode2 == 0) {
            return new ErrorCodePair(1021, "Left sensor weak signal");
        }
        if (errorCode1 == 0 && errorCode2 == 1022) {
            return new ErrorCodePair(1022, "Right sensor weak signal");
        }
        if (errorCode1 == 1021 && errorCode2 == 1022) {
            return new ErrorCodePair(1023, "Both sensor weak signal");
        }

        return new ErrorCodePair(0, "");
    }
    private CombinedData deleteSSData(CombinedData data, boolean deleteSS01, boolean deleteSS02) {

        if (deleteSS01) {
            data.getDistance().setSs01(null);
            data.getSpeed().setSs01(null);
        }
        if (deleteSS02) {
            data.getDistance().setSs02(null);
            data.getSpeed().setSs02(null);
       }
        data.setAngle(null);
        return data;
    }
//
//    private List<Integer> extractStatusIds(CombinedData data) {
//        List<Integer> statusIds = new ArrayList<>();
//        statusIds.add(data.getAngle().getStatusId());
//        statusIds.add(data.getDistance().getSs01().getStatusId());
//        statusIds.add(data.getDistance().getSs02().getStatusId());
//        statusIds.add(data.getSpeed().getSs01().getStatusId());
//        statusIds.add(data.getSpeed().getSs02().getStatusId());
//        return statusIds;
//    }
}

package com.reeco.bas.transport.infrastructure;

import com.reeco.bas.transport.application.DataService;
import com.reeco.bas.transport.application.MessageService;
import com.reeco.bas.transport.model.SensorData;
import com.reeco.bas.transport.receiver.SerialPortReader;
import com.reeco.bas.transport.utils.annotators.Infrastructure;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import com.fazecast.jSerialComm.SerialPort;
import org.springframework.beans.factory.annotation.Autowired;
import com.reeco.bas.transport.model.SensorType;
import com.reeco.bas.transport.model.SensorsType;
import com.reeco.bas.transport.model.DataModel;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

@Infrastructure
@RequiredArgsConstructor
@Slf4j
public class DeviceHandler {

    @Value("ttyS0")
    private String LEFT_SENSOR_PORT;

    @Value("ttyS1")
    private String RIGHT_SENSOR_PORT;

    @Value("${data.organization-id}")
    private Integer ORGANIZATION_ID;

    @Value("${data.berth-id}")
    private Integer BERTH_ID;

    @Value("${data.threshold.weak-signal}")
    private Integer WEAK_SIGNAL_THRESHOLD;

    private Double lastValidLeftSpeed;

    private Double lastValidLeftDistance;

    private Double lastValidRightSpeed;

    private Double lastValidRightDistance;

    @Autowired
    private DataService dataService;

    @Autowired
    private MessageService messageService;

    @Scheduled(cron="0 * * * * *")
    public void processCsvFiles() {
        log.info("[SERVICE] Starting CSV processing at {}", LocalDateTime.now());
    }
    public SensorData parseData(String input){
        try {
        String cleanedInput = input.substring(1).trim();

        String[] parts = cleanedInput.split("\\s+");

        String speed = parts[0];          
        String distance = parts[1];       
        String signalStrength = parts[2]; 

        Double speedValue = Double.parseDouble(speed);
        Double distanceValue = Double.parseDouble(distance);
        Double signalStrengthValue = Double.parseDouble(signalStrength);

        SensorData sensorData = new SensorData(speedValue, distanceValue, signalStrengthValue);
        return sensorData;
        }
        catch (Exception ex) {
            System.out.println(input);
            return new SensorData(0d,-1d,0d);
        }
    }

    @PostConstruct
    public void init(){

        SerialPort[] ports = SerialPort.getCommPorts();
        
        for (SerialPort port : ports) {
            System.out.println("Available Port: " + port.getSystemPortName());
        }

        System.out.println("[TRANSPORT] Receiving data from: "+LEFT_SENSOR_PORT);
        SerialPortReader leftReader = new SerialPortReader(LEFT_SENSOR_PORT);
        
        leftReader.setDataListener(data -> {
             System.out.println("[LEFT SENSOR] Data received: " + data);
            SensorData sensorData = parseData(data);
            
            DataModel dataRecord = new DataModel(ORGANIZATION_ID,BERTH_ID,SensorsType.LEFT,sensorData.speed,sensorData.distance,0,"");
            // System.out.println("TTYS0 sensor " + dataRecord.toString());
            // sensorData.distance = -1.0;
            if (sensorData.distance < 0){
                dataRecord.error_code = 1011;
                dataRecord.speed = 0.0;
                dataRecord.distance = 0.0;
                dataRecord.error_msg = "Left sensor out of target";
            }
            else if (sensorData.signalStrength < WEAK_SIGNAL_THRESHOLD){
                dataRecord.error_code = 1021;
                dataRecord.error_msg = "Left sensor weak signal";
                dataRecord.speed = lastValidLeftSpeed;
                dataRecord.distance = lastValidLeftDistance;
                
            }
            else {
                lastValidLeftDistance = sensorData.distance;
                lastValidLeftSpeed = sensorData.speed;
            }
            log.info("[RAW DATA] TTYS0 sensor: "+ dataRecord.toString()+" Signal: "+ sensorData.signalStrength);

            try{
                messageService.sendDataRecord(dataRecord);
                dataService.processData(dataRecord);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        leftReader.startListening();

        System.out.println("[TRANSPORT] Receiving data from: "+RIGHT_SENSOR_PORT);
        SerialPortReader rightReader = new SerialPortReader(RIGHT_SENSOR_PORT);
        
        rightReader.startListening();
        rightReader.setDataListener(data -> {
             System.out.println("[RIGHT SENSOR] Data received: " + data);
            SensorData sensorData = parseData(data);
            // System.out.println("TTYS1 sensor: " + sensorData.toString());
            DataModel dataRecord = new DataModel(ORGANIZATION_ID,BERTH_ID,SensorsType.RIGHT,sensorData.speed,sensorData.distance,0,"");

            if (sensorData.distance < 0){
                dataRecord.error_code = 1012;
                dataRecord.error_msg = "Right sensor out of target";
                dataRecord.speed = 0.0;
                dataRecord.distance = 0.0;
            }
            else if (sensorData.signalStrength < WEAK_SIGNAL_THRESHOLD){
                dataRecord.error_code = 1022;
                dataRecord.error_msg = "Right sensor weak signal";
                dataRecord.speed = lastValidRightSpeed;
                dataRecord.distance = lastValidRightDistance;
            }
            else {
                lastValidRightDistance = sensorData.distance;
                lastValidRightSpeed = sensorData.speed;
            }
            log.info("[RAW DATA] TTYS1 sensor: "+ dataRecord.toString() +" Signal: "+ sensorData.signalStrength);

            try{
                messageService.sendDataRecord(dataRecord);
                dataService.processData(dataRecord);
            } catch (Exception e) {
                e.printStackTrace();
            }

        });

    }

}

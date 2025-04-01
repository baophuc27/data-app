package com.reeco.bas.transport.application;

// CacheStorageService.java
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.reeco.bas.transport.model.SyncPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.FileWriter;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.springframework.http.ResponseEntity;
import java.io.File;
import java.nio.file.Files;
import org.springframework.scheduling.annotation.Scheduled;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
@Slf4j
public class CacheStorageService {
    private final ConcurrentLinkedQueue<SyncPayload> storage;
    private static final String CSV_DIRECTORY = "export/";
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private RestTemplate restTemplate = new RestTemplate();

    private static final String API_URL = "https://smartbas-api.vnemisoft.com/api/sync";

    public CacheStorageService() {
        this.storage = new ConcurrentLinkedQueue<>();
        createExportDirectory();
    }

    @Scheduled(fixedRate = 60000) // Runs every 1 minute
    public void processCsvFiles() {
        log.info("Starting CSV processing at {}", LocalDateTime.now());

        try {
            // Create directories if they don't exist

            // Get all CSV files from export directory
            File exportDir = new File(CSV_DIRECTORY);
            File[] csvFiles = exportDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));

            if (csvFiles == null || csvFiles.length == 0) {
                log.info("No CSV files found in export directory");
                return;
            }

            for (File csvFile : csvFiles) {
                syncFromCsv(csvFile.getAbsolutePath());
            }

        } catch (Exception e) {
            log.error("Error in CSV processing scheduler: ", e);
        }
    }
    private void createExportDirectory() {
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(CSV_DIRECTORY));
        } catch (IOException e) {
            log.error("Failed to create export directory: {}", e.getMessage());
        }
    }

    /**
     * Add a new item to the cache storage
     */
    public void addItem(SyncPayload payload) {
        storage.offer(payload);
        log.debug("Added item to cache. Current size: {}", storage.size());
    }

    public void syncFromCsv(String csvFilePath) {
        List<SyncPayload> payloads = readCsvFile(csvFilePath);
        sendToApi(payloads);
    }
    /**
     * Add multiple items to the cache storage
     */
    public void addItems(List<SyncPayload> payloads) {
        payloads.forEach(storage::offer);
        log.debug("Added {} items to cache. Current size: {}", payloads.size(), storage.size());
    }

    /**
     * Get current size of the cache
     */
    public int getSize() {
        return storage.size();
    }

    /**
     * Export all items to CSV and clear the storage
     * @return The path to the exported CSV file
     */
    public String exportAndClear(int org_id, int berth_id, int session_id) {
        if (storage.isEmpty()) {
            return null;
        }

        String encoded_id = org_id + "_" + berth_id + "_" + session_id;

        String fileName = String.format("%s%s-%s.csv",
                CSV_DIRECTORY,
                encoded_id,
                LocalDateTime.now().format(FILE_DATE_FORMAT));

        try (CSVWriter writer = new CSVWriter(new FileWriter(fileName))) {
            // Write header
            writer.writeNext(new String[]{
                    "record_id",
                    "berth_id",
                    "time",
                    "org_id",
                    "angle_zone",
                    "lspeed_zone",
                    "ldistance_zone",
                    "rdistance_zone",
                    "rspeed_zone",
                    "left_speed",
                    "left_distance",
                    "right_speed",
                    "right_distance",
                    "angle",
                    "left_status",
                    "right_status",
                    "rdistance_alarm",
                    "rspeed_alarm",
                    "ldistance_alarm",
                    "lspeed_alarm",
                    "angle_alarm",
                    "created_at",
                    "updated_at",
                    "deleted_at",

            });

            // Write data
            List<SyncPayload> dataToExport = new ArrayList<>();
            SyncPayload item;
            int count = 0;
            while ((item = storage.poll()) != null) {
                dataToExport.add(item);
                count += 1;
                if (count == 50){
                    storage.clear();
                }
                writer.writeNext(new String[]{
                        String.valueOf(item.getRecordId()),
                        String.valueOf(item.getBerthId()),
                        item.getTime(),
                        String.valueOf(item.getOrgId()),
                        String.valueOf(item.getAngleZone()),
                        String.valueOf(item.getLSpeedZone()),
                        String.valueOf(item.getLDistanceZone()),
                        String.valueOf(item.getRDistanceZone()),
                        String.valueOf(item.getRSpeedZone()),
                        String.valueOf(item.getLeftSpeed()),
                        String.valueOf(item.getLeftDistance()),
                        String.valueOf(item.getRightSpeed()),
                        String.valueOf(item.getRightDistance()),
                        String.valueOf(item.getAngle()),
                        String.valueOf(item.getLeftStatus()),
                        String.valueOf(item.getRightStatus()),
                        String.valueOf(item.getRDistanceAlarm()),
                        String.valueOf(item.getRSpeedAlarm()),
                        String.valueOf(item.getLDistanceAlarm()),
                        String.valueOf(item.getLSpeedAlarm()),
                        String.valueOf(item.getAngleAlarm()),
                        item.getCreatedAt(),
                        item.getUpdatedAt(),
                        item.getDeletedAt() != null ? item.getDeletedAt() : ""
                });
            }



            log.info("Exported {} records to {}", dataToExport.size(), fileName);

            syncFromCsv(fileName);

            return fileName;

        } catch (IOException e) {
            log.error("Failed to export data to CSV: {}", e.getMessage());
            // If export fails, put the items back in the queue
            storage.addAll(new ArrayList<>(storage));
            throw new RuntimeException("Failed to export data to CSV", e);
        }
    }

    private List<SyncPayload> readCsvFile(String csvFilePath) {
        List<SyncPayload> payloads = new ArrayList<>();

        try (CSVReader reader = new CSVReaderBuilder(new FileReader(csvFilePath))
                .withSkipLines(1) // Skip header row
                .build()) {

            String[] line;
            int count = 0;
            while ((line = reader.readNext()) != null) {
                SyncPayload payload = new SyncPayload();
                count += 1;
                if (count == 30){
                    break;
                }
                // Parse and set fields from CSV
                payload.setRecordId(Integer.parseInt(line[0].replace("\"", "")));
                payload.setBerthId(Integer.parseInt(line[1].replace("\"", "")));
                payload.setTime(line[2].replace("\"", ""));
                payload.setOrgId(Integer.parseInt(line[3].replace("\"", "")));
                payload.setAngleZone(Integer.parseInt(line[4].replace("\"", "")));
                payload.setLSpeedZone(Integer.parseInt(line[5].replace("\"", "")));
                payload.setLDistanceZone(Integer.parseInt(line[6].replace("\"", "")));
                payload.setRDistanceZone(Integer.parseInt(line[7].replace("\"", "")));
                payload.setRSpeedZone(Integer.parseInt(line[8].replace("\"", "")));
                payload.setLeftSpeed(Double.parseDouble(line[9].replace("\"", "")));
                payload.setLeftDistance(Double.parseDouble(line[10].replace("\"", "")));
                payload.setRightSpeed(Double.parseDouble(line[11].replace("\"", "")));
                payload.setRightDistance(Double.parseDouble(line[12].replace("\"", "")));
                payload.setAngle(Double.parseDouble(line[13].replace("\"", "")));
                payload.setLeftStatus(Integer.parseInt(line[14].replace("\"", "")));
                payload.setRightStatus(Integer.parseInt(line[15].replace("\"", "")));
                payload.setRDistanceAlarm(Integer.parseInt(line[16].replace("\"", "")));
                payload.setRSpeedAlarm(Integer.parseInt(line[17].replace("\"", "")));
                payload.setLDistanceAlarm(Integer.parseInt(line[18].replace("\"", "")));
                payload.setLSpeedAlarm(Integer.parseInt(line[19].replace("\"", "")));
                payload.setAngleAlarm(Integer.parseInt(line[20].replace("\"", "")));
                payload.setCreatedAt(line[21].replace("\"", ""));
                payload.setUpdatedAt(line[22].replace("\"", ""));
                payload.setDeletedAt(line[23].replace("\"", ""));

                payloads.add(payload);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to read CSV file: " + e.getMessage());
        }

        return payloads;
    }
    /**
     * Clear the storage without exporting
     */
    public void clear() {
        int size = storage.size();
        storage.clear();
        log.info("Cleared {} items from cache", size);
    }
    private void sendToApi(List<SyncPayload> payloads) {
        return;
//        try {
//            ResponseEntity<String> response = restTemplate.postForEntity(
//                    API_URL,
//                    payloads,
//                    String.class
//            );
//
//            if (response.getStatusCode().is2xxSuccessful()) {
//                System.out.println("Successfully synced " + payloads.size() + " records");
//            } else {
//                System.err.println("Failed to sync data. Status code: " + response.getStatusCode());
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            log.warn("Failed to send data to API: " + e.getMessage());
//        }
    }
    /**
     * Get all items without removing them
     */
    public List<SyncPayload> getAll() {
        return new ArrayList<>(storage);
    }
}

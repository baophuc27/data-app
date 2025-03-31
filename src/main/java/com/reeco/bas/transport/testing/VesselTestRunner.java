package com.reeco.bas.transport.testing;

import com.reeco.bas.transport.application.DataService;
import com.reeco.bas.transport.application.VesselStateMachine;
import com.reeco.bas.transport.model.DataModel;
import com.reeco.bas.transport.model.SensorsType;
import com.reeco.bas.transport.model.VesselState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Component
@Profile("test")
@Slf4j
public class VesselTestRunner implements ApplicationRunner {

    private static final String TEST_DIR = "test-data";
    private static final String RESULTS_DIR = "test-results";
    private static final DateTimeFormatter LOG_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private DataService dataService;

    @Autowired
    private VesselStateMachine vesselStateMachine;

    @Value("${data.organization-id:52}")
    private int ORGANIZATION_ID;

    @Value("${data.berth-id:1}")
    private int BERTH_ID;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean testRunning = new AtomicBoolean(false);
    private List<Map<String, String>> currentTestData = new ArrayList<>();
    private int currentIndex = 0;
    private String currentTestName = "";
    private File currentResultFile;
    private PrintWriter resultWriter;
    private VesselState lastState = null;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Vessel Test Runner initialized. Looking for test files in {}", TEST_DIR);

        // Create test and results directories if they don't exist
        createDirectories();

        // List available test files
        listAvailableTestFiles();

        // Check if we should run a specific test file
        List<String> testFileArgs = args.getOptionValues("test-file");
        if (testFileArgs != null && !testFileArgs.isEmpty()) {
            String testFile = testFileArgs.get(0);
            log.info("Running specified test file: {}", testFile);
            runTestFile(testFile);
        } else {
            // Set up a scheduler to check for test files periodically
            scheduler.scheduleAtFixedRate(this::checkForTestFiles, 5, 10, TimeUnit.SECONDS);
        }
    }

    private void createDirectories() {
        try {
            Files.createDirectories(Paths.get(TEST_DIR));
            Files.createDirectories(Paths.get(RESULTS_DIR));
        } catch (IOException e) {
            log.error("Failed to create test directories", e);
        }
    }

    private void listAvailableTestFiles() {
        File directory = new File(TEST_DIR);
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));

        if (files != null && files.length > 0) {
            log.info("Available test files:");
            for (File file : files) {
                log.info("  - {}", file.getName());
            }
            log.info("To run a specific test, use: --test-file=filename.csv");
        } else {
            log.info("No test files found in {} directory", TEST_DIR);
        }
    }

    private void checkForTestFiles() {
        if (testRunning.get()) {
            return;
        }

        File directory = new File(TEST_DIR);
        File[] files = directory.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".csv") &&
                        !name.startsWith("processed_"));

        if (files != null && files.length > 0) {
            // Sort files - run numbered files in order
            Arrays.sort(files, Comparator.comparing(File::getName));
            runTestFile(files[0].getName());
        }
    }

    private void runTestFile(String fileName) {
        File testFile = new File(TEST_DIR, fileName);
        if (!testFile.exists()) {
            log.error("Test file not found: {}", fileName);
            return;
        }

        try {
            testRunning.set(true);
            currentTestName = fileName.replace(".csv", "");

            // Initialize result file
            initializeResultFile();

            // Load test data
            loadTestData(testFile);

            log.info("Starting test: {} with {} data points", currentTestName, currentTestData.size());
            writeToResult("===== TEST STARTED: " + currentTestName + " =====");
            writeToResult("Initial state: " + vesselStateMachine.getCurrentState());
            lastState = vesselStateMachine.getCurrentState();

            // Start processing data
            scheduler.scheduleAtFixedRate(this::processNextDataPoint, 0, 1, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("Error starting test {}: {}", fileName, e.getMessage(), e);
            testRunning.set(false);
        }
    }

    private void initializeResultFile() throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        currentResultFile = new File(RESULTS_DIR, currentTestName + "_" + timestamp + ".log");
        resultWriter = new PrintWriter(new FileWriter(currentResultFile));
    }

    private void loadTestData(File testFile) throws IOException {
        currentTestData.clear();
        currentIndex = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(testFile))) {
            // Read header
            String header = reader.readLine();
            if (header == null) {
                throw new IOException("Empty test file");
            }

            // Parse headers (tab-separated)
            String[] headers = header.split("\t");

            // Read data lines
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split("\t");
                Map<String, String> dataPoint = new HashMap<>();

                for (int i = 0; i < headers.length && i < values.length; i++) {
                    dataPoint.put(headers[i], values[i]);
                }

                currentTestData.add(dataPoint);
            }
        }
    }

    private void processNextDataPoint() {
        if (currentIndex >= currentTestData.size()) {
            // Test complete
            finishTest();
            return;
        }

        Map<String, String> dataPoint = currentTestData.get(currentIndex++);

        try {
            String sensorType = dataPoint.get("sensor_type");
            double speed = Double.parseDouble(dataPoint.getOrDefault("speed", "0.0"));
            double distance = Double.parseDouble(dataPoint.getOrDefault("distance", "0.0"));
            int errorCode = Integer.parseInt(dataPoint.getOrDefault("error_code", "0"));

            log.debug("Processing: {} speed={}, distance={}, error_code={}",
                    sensorType, speed, distance, errorCode);

            // Create data model based on sensor type
            SensorsType type = sensorType.contains("TTYS0") ? SensorsType.LEFT : SensorsType.RIGHT;

            DataModel model = new DataModel(
                    ORGANIZATION_ID,
                    BERTH_ID,
                    type,
                    speed,
                    distance,
                    errorCode,
                    errorCode > 0 ? (type == SensorsType.LEFT ? "Left sensor out of target" : "Right sensor out of target") : ""
            );

            // Process the data
            dataService.processData(model);

            // Check for state change
            VesselState currentState = vesselStateMachine.getCurrentState();
            if (currentState != lastState) {
                String transition = lastState + " -> " + currentState;
                log.info("State transition detected: {}", transition);
                writeToResult("STATE TRANSITION: " + transition);
                lastState = currentState;
            }

            // Log progress periodically
            if (currentIndex % 100 == 0 || currentIndex == currentTestData.size()) {
                int percentage = (int) ((double) currentIndex / currentTestData.size() * 100);
                log.info("Test progress: {}% ({}/{})", percentage, currentIndex, currentTestData.size());
            }

        } catch (Exception e) {
            log.error("Error processing data point: {}", e.getMessage());
        }
    }

    private void finishTest() {
        testRunning.set(false);

        writeToResult("===== TEST COMPLETED =====");
        writeToResult("Final state: " + vesselStateMachine.getCurrentState());

        if (resultWriter != null) {
            resultWriter.close();
        }

        log.info("Test {} completed. Results written to {}", currentTestName, currentResultFile.getAbsolutePath());

        // Rename the test file to mark as processed
        try {
            File testFile = new File(TEST_DIR, currentTestName + ".csv");
            File processedFile = new File(TEST_DIR, "processed_" + currentTestName + ".csv");
            if (testFile.renameTo(processedFile)) {
                log.info("Renamed test file to: {}", processedFile.getName());
            }
        } catch (Exception e) {
            log.error("Error renaming test file: {}", e.getMessage());
        }

        // Reset state for next test
        currentTestData.clear();
        currentIndex = 0;
    }

    private void writeToResult(String message) {
        String timestamp = LocalDateTime.now().format(LOG_FORMATTER);
        String logEntry = timestamp + " - " + message;

        if (resultWriter != null) {
            resultWriter.println(logEntry);
            resultWriter.flush();
        }

        log.info(logEntry);
    }
}
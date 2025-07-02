package com.example.awsrdsdashboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DataController {

    @Autowired
    private DataService dataService;

    @GetMapping("/query/{queryId}")
    public ResponseEntity<List<Map<String, Object>>> executeQuery(@PathVariable String queryId) {
        try {
            List<Map<String, Object>> result = dataService.executeQuery(queryId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(List.of(Map.of("error", e.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(List.of(Map.of("error", "An internal error occurred.")));
        }
    }

    @GetMapping("/telemetry")
    public ResponseEntity<TelemetryData> getTelemetry() {
        try {
            TelemetryData telemetryData = dataService.getTelemetryData();
            return ResponseEntity.ok(telemetryData);
        } catch (Exception e) {
            List<Map<String, Object>> dbLoadError = Collections.singletonList(Map.of("error", e.getMessage()));
            List<Map<String, Object>> osProcessesError = Collections.singletonList(Map.of("error", "Could not fetch OS processes."));
            Map<String, String> osMetricsError = Map.of("error", "Could not fetch OS metrics.");
            Map<String, String> memoryStatsError = Map.of("error", "Could not fetch memory stats.");
            String prettyIoJsonError = "Error fetching I/O JSON.";
            String networkJsonError = "Error fetching Network JSON.";

            TelemetryData errorData = new TelemetryData(
                dbLoadError,
                osProcessesError,
                osMetricsError,
                memoryStatsError,
                prettyIoJsonError,
                networkJsonError,
                "Error fetching other telemetry"
            );
            return ResponseEntity.internalServerError().body(errorData);
        }
    }

    @GetMapping("/proctab")
    public ResponseEntity<List<Map<String, Object>>> getProctabActivity() {
        try {
            List<Map<String, Object>> result = dataService.getProctabActivity();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            System.err.println("CRASH IN PROCTAB ENDPOINT: " + sw.toString());
            return ResponseEntity.internalServerError().body(Collections.singletonList(Map.of("error", e.getMessage(), "stackTrace", sw.toString())));
        }
    }

    @GetMapping("/events")
    public ResponseEntity<List<Map<String, Object>>> getRdsEvents() {
        try {
            List<Map<String, Object>> result = dataService.getRdsEvents();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
             StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            System.err.println("CRASH IN EVENTS ENDPOINT: " + sw.toString());
            return ResponseEntity.internalServerError().body(Collections.singletonList(Map.of("error", e.getMessage(), "stackTrace", sw.toString())));
        }
    }

    @GetMapping("/postgres-logs")
    public ResponseEntity<String> getPostgresLogs() {
        try {
            String result = dataService.getPostgresLogs();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
    
    @GetMapping("/debug/pi-dimensions")
    public ResponseEntity<List<Map<String, Object>>> getPiDimensions() {
        try {
            List<Map<String, Object>> result = dataService.getPerformanceInsightsDimensions();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            System.err.println("CRASH IN DEBUG ENDPOINT: " + sw.toString());
            return ResponseEntity.internalServerError().body(Collections.singletonList(Map.of("error", e.getMessage(), "stackTrace", sw.toString())));
        }
    }
}
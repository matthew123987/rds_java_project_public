package com.example.awsrdsdashboard;

import java.util.List;
import java.util.Map;

public class TelemetryData {
    private List<Map<String, Object>> databaseLoad;
    private List<Map<String, Object>> osProcesses;
    private Map<String, String> osMetrics;      // Holds CPU stats
    private Map<String, String> memoryStats;
    private String prettyIoJson;   // For Disk JSON
    private String networkJson;    // For Network JSON
    private String otherTelemetry;

    public TelemetryData(List<Map<String, Object>> databaseLoad, List<Map<String, Object>> osProcesses, Map<String, String> osMetrics, Map<String, String> memoryStats, String prettyIoJson, String networkJson, String otherTelemetry) {
        this.databaseLoad = databaseLoad;
        this.osProcesses = osProcesses;
        this.osMetrics = osMetrics;
        this.memoryStats = memoryStats;
        this.prettyIoJson = prettyIoJson;
        this.networkJson = networkJson;
        this.otherTelemetry = otherTelemetry;
    }

    public List<Map<String, Object>> getDatabaseLoad() { return databaseLoad; }
    public void setDatabaseLoad(List<Map<String, Object>> databaseLoad) { this.databaseLoad = databaseLoad; }
    public List<Map<String, Object>> getOsProcesses() { return osProcesses; }
    public void setOsProcesses(List<Map<String, Object>> osProcesses) { this.osProcesses = osProcesses; }
    public Map<String, String> getOsMetrics() { return osMetrics; }
    public void setOsMetrics(Map<String, String> osMetrics) { this.osMetrics = osMetrics; }
    public Map<String, String> getMemoryStats() { return memoryStats; }
    public void setMemoryStats(Map<String, String> memoryStats) { this.memoryStats = memoryStats; }
    public String getPrettyIoJson() { return prettyIoJson; }
    public void setPrettyIoJson(String prettyIoJson) { this.prettyIoJson = prettyIoJson; }
    public String getNetworkJson() { return networkJson; }
    public void setNetworkJson(String networkJson) { this.networkJson = networkJson; }
    public String getOtherTelemetry() { return otherTelemetry; }
    public void setOtherTelemetry(String otherTelemetry) { this.otherTelemetry = otherTelemetry; }
}
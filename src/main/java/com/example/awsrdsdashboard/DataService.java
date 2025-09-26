package com.example.awsrdsdashboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent;
import software.amazon.awssdk.services.pi.PiClient;
import software.amazon.awssdk.services.pi.model.*;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DescribeEventsRequest;
import software.amazon.awssdk.services.rds.model.DescribeEventsResponse;
import software.amazon.awssdk.services.rds.model.Event;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DataService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final PiClient piClient;
    private final RdsClient rdsClient;
    private final CloudWatchLogsClient cloudWatchLogsClient;
    private final ObjectMapper objectMapper;

    private static final String RDS_RESOURCE_ID = "db-5CZOGBDK4QMID5GYSD5KG6JGYU";
    private static final String DB_INSTANCE_IDENTIFIER = "database-2";
    //private static final String EC2_INSTANCE_ID = "i-0c990538f1035b4cf";

    public DataService(@Value("${aws.region}") String awsRegion) {
        Region region = Region.of(awsRegion);
        this.piClient = PiClient.builder().region(region).build();
        this.rdsClient = RdsClient.builder().region(region).build();
        this.cloudWatchLogsClient = CloudWatchLogsClient.builder().region(region).build();
        this.objectMapper = new ObjectMapper();
    }

    public String getPostgresLogs() {
        try {
            String command = "aws logs get-log-events --log-group-name /aws/rds/instance/database-2/postgresql --log-stream-name \"$(aws logs describe-log-streams --log-group-name /aws/rds/instance/database-2/postgresql --order-by LastEventTime --descending --limit 1 --query \"logStreams[0].logStreamName\" --output text)\" --limit 25 --query \"events[].[timestamp, message]\" --output text --region eu-west-2";
            
            ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-c", command);
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");1
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                StringBuilder error = new StringBuilder();
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                while ((line = errorReader.readLine()) != null) {
                    error.append(line).append("\n");
                }
                return "Error executing command:\n" + error.toString();
            }
            return output.toString();

        } catch (Exception e) {
            return "Java Exception: " + e.getMessage();
        }
    }

    public List<Map<String, Object>> executeQuery(String queryId) {
        String sql;
        switch (queryId) {
            case "1":
                sql = "DROP TABLE IF EXISTS sales_partitioned CASCADE; CREATE temporary TABLE sales_partitioned (id SERIAL primary key, sale_date DATE NOT NULL, amount NUMERIC(10, 2) NOT NULL) PARTITION BY RANGE (id); CREATE temporary TABLE sales_partitioned_default PARTITION OF sales_partitioned DEFAULT; DO $$ DECLARE i INT; start_id INT; end_id INT; BEGIN FOR i IN 1..5000 LOOP start_id := (i - 1) * 1000 + 1; end_id := i * 1000 + 1; EXECUTE format('CREATE temporary TABLE sales_partitioned_part%s PARTITION OF sales_partitioned FOR VALUES FROM (%s) TO (%s);', i, start_id, end_id); END LOOP; END $$";
                break;
            case "2":
                sql = "begin; set local work_mem= '40 GB'; SELECT g % 2000 AS group_id, count(*) FROM generate_series(1, 2000000000) g GROUP BY 1; commit;";
                break;
            case "3":
                sql = "select pg_terminate_backend(pid) from pg_stat_activity where state='idle' and not usename= 'rdsadmin';";
                break;
            default:
                throw new IllegalArgumentException("Invalid query ID: " + queryId);
        }
        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> getRdsEvents() {
        DescribeEventsRequest request = DescribeEventsRequest.builder()
            .sourceIdentifier(DB_INSTANCE_IDENTIFIER)
            .sourceType("db-instance")
            .duration(20160) // 14 days in minutes
            .build();

        DescribeEventsResponse response = rdsClient.describeEvents(request);
        List<Event> allEvents = response.events();

        // Get the last 5 events
        int listSize = allEvents.size();
        List<Event> last5Events = allEvents.subList(Math.max(listSize - 5, 0), listSize);
        
        // Convert to a simpler map for the frontend, reversing the order to show newest first
        List<Map<String, Object>> resultList = last5Events.stream()
            .map(event -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("Date", event.date().toString());
                map.put("Message", event.message());
                map.put("Categories", event.eventCategories());
                return map;
            })
            .collect(Collectors.toList());
        
        Collections.reverse(resultList);
        return resultList;
    }

    public List<Map<String, Object>> getProctabActivity() {
        String sql = "SELECT sa.pid, sa.usename, sa.datname, sa.client_addr, sa.state AS pg_state, sa.backend_start, sa.xact_start, sa.query_start, sa.state_change, now(), sa.wait_event_type, sa.wait_event, sa.backend_type, left(sa.query,20) as query, pt.utime, pt.stime, (pt.rss * 4) / 1024 AS rss_mb FROM pg_stat_activity AS sa JOIN pg_proctab() AS pt ON sa.pid = pt.pid WHERE sa.pid IS NOT NULL;";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        // Post-process the results
        for (Map<String, Object> row : results) {
            // Fix for client_addr being an object
            Object clientAddr = row.get("client_addr");
            if (clientAddr != null) {
                row.put("client_addr", clientAddr.toString());
            }
        }
        return results;
    }

    public TelemetryData getTelemetryData() {
        List<Map<String, Object>> dbLoad = getPerformanceInsightsLoad();
        List<Map<String, Object>> osProcesses = getOsProcesses();
        
        Map<String, String> cpuStats = new LinkedHashMap<>();
        Map<String, String> memoryStats = new LinkedHashMap<>();
        String prettyIoJson = "";
        String networkJson = "";
        
        try {
            Map<String, Object> osMetricsJson = getLatestOsLogForStats();
            populateCpuStats(osMetricsJson, cpuStats);
            populateMemoryStats(osMetricsJson, memoryStats);
            prettyIoJson = buildPrettyIoJson(osMetricsJson);
            networkJson = buildNetworkJson(osMetricsJson);
        } catch (Exception e) {
            System.err.println("Error processing RDS Metrics from Logs: " + e.getMessage());
            e.printStackTrace();
            cpuStats.put("error", e.getMessage());
            memoryStats.put("error", e.getMessage());
            prettyIoJson = "Error building I/O JSON: " + e.getMessage();
            networkJson = "Error building Network JSON: " + e.getMessage();
        }

        String otherTelemetry = "Other real-time data...";
        return new TelemetryData(dbLoad, osProcesses, cpuStats, memoryStats, prettyIoJson, networkJson, otherTelemetry);
    }
    
    public List<Map<String, Object>> getPerformanceInsightsDimensions() {
        try {
            DescribeDimensionKeysRequest request = DescribeDimensionKeysRequest.builder()
                .serviceType(ServiceType.RDS).identifier(RDS_RESOURCE_ID)
                .startTime(Instant.now().minusSeconds(900)).endTime(Instant.now())
                .metric("os.cpuUtilization").build();
            DescribeDimensionKeysResponse response = piClient.describeDimensionKeys(request);
            return response.keys().stream()
                    .map(key -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("dimensions", key.dimensions());
                        map.put("total", key.total());
                        return map;
                    }).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("An unexpected error occurred in DescribeDimensionKeys: " + e.getMessage(), e);
        }
    }
    
    private List<Map<String, Object>> getPerformanceInsightsLoad() {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            GetResourceMetricsRequest request = GetResourceMetricsRequest.builder()
                .serviceType(ServiceType.RDS).identifier(RDS_RESOURCE_ID)
                .startTime(Instant.now().minusSeconds(300)).endTime(Instant.now()).periodInSeconds(60)
                .metricQueries(MetricQuery.builder().metric("db.load.avg").groupBy(DimensionGroup.builder().group("db.sql").build()).build())
                .build();
            GetResourceMetricsResponse response = piClient.getResourceMetrics(request);
            if (response.metricList() != null && !response.metricList().isEmpty()) {
                for (MetricKeyDataPoints point : response.metricList()) {
                    if (point.key() != null && point.key().dimensions() != null && !point.dataPoints().isEmpty()) {
                        Map<String, Object> metric = new HashMap<>();
                        metric.put("sql", point.key().dimensions().get("db.sql.statementText"));
                        metric.put("value", point.dataPoints().get(point.dataPoints().size() - 1).value());
                        results.add(metric);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching Performance Insights Load: " + e.getMessage());
            results.add(Map.of("error", e.getMessage()));
        }
        return results;
    }

    private List<Map<String, Object>> getOsProcesses() {
        List<Map<String, Object>> osProcessList = new ArrayList<>();
        try {
            // Request the single most recent log event
            GetLogEventsRequest request = GetLogEventsRequest.builder()
                .logGroupName("RDSOSMetrics")
                .logStreamName(RDS_RESOURCE_ID)
                .limit(1)
                .startFromHead(false) // false means start from tail (newest)
                .build();

            GetLogEventsResponse response = cloudWatchLogsClient.getLogEvents(request);

            if (response.events() != null && !response.events().isEmpty()) {
                // There will be at most one event because of the limit(1)
                OutputLogEvent event = response.events().get(0);
                String message = event.message();
                Map<String, Object> osMetricsJson = objectMapper.readValue(message, new TypeReference<>() {});

                if (osMetricsJson.containsKey("processList")) {
                    List<Map<String, Object>> rawProcessList = (List<Map<String, Object>>) osMetricsJson.get("processList");
                    for (Map<String, Object> process : rawProcessList) {
                        Map<String, Object> procEntry = new HashMap<>();
                        procEntry.put("id", String.valueOf(process.get("id")));
                        procEntry.put("name", process.get("name"));
                        procEntry.put("cpu", process.get("cpuUsedPc"));
                        Number rssUsedKb = (Number) process.get("rss");
                        if (rssUsedKb != null) {
                            procEntry.put("memory", rssUsedKb.doubleValue() / 1024.0);
                        }
                        osProcessList.add(procEntry);
                    }
                }
            }
            // Sorting is still useful
            osProcessList.sort(Comparator.comparingDouble(p -> -((Number)p.getOrDefault("cpu", 0.0)).doubleValue()));
        } catch (Exception e) {
            System.err.println("Error in getOsProcesses: " + e.getMessage());
            e.printStackTrace();
            return Collections.singletonList(Map.of("error", "Could not parse OS processes: " + e.getMessage()));
        }
        return osProcessList;
    }

    private Map<String, Object> getLatestOsLogForStats() throws Exception {
        long endTimeMillis = Instant.now().toEpochMilli();
        long startTimeMillis = Instant.now().minusSeconds(300).toEpochMilli();

        GetLogEventsRequest request = GetLogEventsRequest.builder()
            .logGroupName("RDSOSMetrics").logStreamName(RDS_RESOURCE_ID)
            .startTime(startTimeMillis).endTime(endTimeMillis)
            .limit(1).startFromHead(false).build();
        GetLogEventsResponse response = cloudWatchLogsClient.getLogEvents(request);

        if (response.events() != null && !response.events().isEmpty()) {
            String message = response.events().get(response.events().size() - 1).message();
            return objectMapper.readValue(message, new TypeReference<>() {});
        }
        return null;
    }
    
    private void populateCpuStats(Map<String, Object> osMetricsJson, Map<String, String> cpuStats) {
        if (osMetricsJson == null) {
            cpuStats.put("Metrics", "No log events found");
            return;
        }
        Number numVcpus = (Number) osMetricsJson.get("numVCPUs");
        if (numVcpus != null) {
            cpuStats.put("num vCPUs", String.valueOf(numVcpus.intValue()));
        }
        if (osMetricsJson.containsKey("cpuUtilization")) {
            Map<String, Object> cpuData = (Map<String, Object>) osMetricsJson.get("cpuUtilization");
            DecimalFormat df = new DecimalFormat("#.##");
            String indent = "&nbsp;&nbsp;&nbsp;&nbsp;";
            Number idle = (Number) cpuData.get("idle");
            if (idle != null) cpuStats.put("Idle", df.format(idle.doubleValue()) + "%");
            Number total = (Number) cpuData.get("total");
            if (total != null) cpuStats.put("Utilization", df.format(total.doubleValue()) + "%");
            Number system = (Number) cpuData.get("system");
            if (system != null) cpuStats.put(indent + "System", df.format(system.doubleValue()) + "%");
            Number user = (Number) cpuData.get("user");
            if (user != null) cpuStats.put(indent + "User", df.format(user.doubleValue()) + "%");
            Number wait = (Number) cpuData.get("wait");
            if (wait != null) cpuStats.put(indent + "Wait", df.format(wait.doubleValue()) + "%");
            Number irq = (Number) cpuData.get("irq");
            if (irq != null) cpuStats.put(indent + "IRQ", df.format(irq.doubleValue()) + "%");
            Number guest = (Number) cpuData.get("guest");
            if (guest != null) cpuStats.put(indent + "Guest", df.format(guest.doubleValue()) + "%");
            Number steal = (Number) cpuData.get("steal");
            if (steal != null) cpuStats.put(indent + "Steal", df.format(steal.doubleValue()) + "%");
            Number nice = (Number) cpuData.get("nice");
            if (nice != null) cpuStats.put(indent + "Nice", df.format(nice.doubleValue()) + "%");
        }
    }
    
    private void populateMemoryStats(Map<String, Object> osMetricsJson, Map<String, String> memoryStats) {
        if (osMetricsJson == null) {
            memoryStats.put("Metrics", "No log events found");
            return;
        }
        DecimalFormat df = new DecimalFormat("#,###.##");
        String indent = "&nbsp;&nbsp;&nbsp;&nbsp;";
        if (osMetricsJson.containsKey("memory")) {
            Map<String, Object> memData = (Map<String, Object>) osMetricsJson.get("memory");
            Number total = (Number) memData.get("total");
            if (total != null) memoryStats.put("Total memory (MB)", df.format(total.doubleValue() / 1024.0));
            Number free = (Number) memData.get("free");
            if (free != null) memoryStats.put(indent + "Free memory (MB)", df.format(free.doubleValue() / 1024.0));
            Number active = (Number) memData.get("active");
            if (active != null) memoryStats.put(indent + "Active memory (MB)", df.format(active.doubleValue() / 1024.0));
            Number inactive = (Number) memData.get("inactive");
            if (inactive != null) memoryStats.put(indent + "Inactive memory (MB)", df.format(inactive.doubleValue() / 1024.0));
        }
        if (osMetricsJson.containsKey("swap")) {
            Map<String, Object> swapData = (Map<String, Object>) osMetricsJson.get("swap");
            Number total = (Number) swapData.get("total");
            if (total != null) memoryStats.put("Swap total (MB)", df.format(total.doubleValue() / 1024.0));
            Number free = (Number) swapData.get("free");
            if (free != null) memoryStats.put(indent + "Swap free (MB)", df.format(free.doubleValue() / 1024.0));
            Number cached = (Number) swapData.get("cached");
            if (cached != null) memoryStats.put(indent + "Swap cached (MB)", df.format(cached.doubleValue() / 1024.0));
        }
    }

    private String buildPrettyIoJson(Map<String, Object> osMetricsJson) {
        if (osMetricsJson == null) {
            return "No log data found to generate I/O JSON.";
        }
        try {
            StringBuilder sb = new StringBuilder();
            
            // Filter and append diskIO
            if (osMetricsJson.get("diskIO") instanceof List) {
                List<Map<String, Object>> originalList = (List<Map<String, Object>>) osMetricsJson.get("diskIO");
                List<Map<String, Object>> filteredList = filterDeviceIoList(originalList);
                if (!filteredList.isEmpty()) {
                    sb.append("diskIO:\n");
                    sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(filteredList));
                    sb.append("\n\n");
                }
            }

            // Filter and append physicalDeviceIO
            if (osMetricsJson.get("physicalDeviceIO") instanceof List) {
                List<Map<String, Object>> originalList = (List<Map<String, Object>>) osMetricsJson.get("physicalDeviceIO");
                List<Map<String, Object>> filteredList = filterDeviceIoList(originalList);
                if (!filteredList.isEmpty()) {
                    sb.append("physicalDeviceIO:\n");
                    sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(filteredList));
                    sb.append("\n\n");
                }
            }

            // Filter and append fileSys
            if (osMetricsJson.get("fileSys") instanceof List) {
                List<Map<String, Object>> originalList = (List<Map<String, Object>>) osMetricsJson.get("fileSys");
                List<Map<String, Object>> filteredList = new ArrayList<>();
                for(Map<String, Object> originalMap : originalList) {
                    Map<String, Object> filteredMap = new LinkedHashMap<>();
                    filteredMap.put("name", originalMap.get("name"));
                    filteredMap.put("mountPoint", originalMap.get("mountPoint"));
                    filteredMap.put("total", originalMap.get("total"));
                    filteredMap.put("usedPercent", originalMap.get("usedPercent"));
                    filteredMap.put("usedFilePercent", originalMap.get("usedFilePercent"));
                    filteredList.add(filteredMap);
                }
                if (!filteredList.isEmpty()) {
                    sb.append("fileSys:\n");
                    sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(filteredList));
                }
            }
            
            return sb.toString();
        } catch (Exception e) {
            System.err.println("Error formatting IO JSON: " + e.getMessage());
            return "Error formatting IO JSON.";
        }
    }

    // Helper method to filter diskIO and physicalDeviceIO lists
    private List<Map<String, Object>> filterDeviceIoList(List<Map<String, Object>> originalList) {
        List<Map<String, Object>> filteredList = new ArrayList<>();
        // These are the fields we want to KEEP
        List<String> fieldsToKeep = Arrays.asList("device", "writeKbPS", "readKbPS", "tps", "await");

        for (Map<String, Object> originalMap : originalList) {
            Map<String, Object> filteredMap = new LinkedHashMap<>();
            for (String field : fieldsToKeep) {
                if (originalMap.containsKey(field)) {
                    filteredMap.put(field, originalMap.get(field));
                }
            }
            filteredList.add(filteredMap);
        }
        return filteredList;
    }

    private String buildNetworkJson(Map<String, Object> osMetricsJson) {
        if (osMetricsJson == null) {
            return "No log data found to generate Network JSON.";
        }
        try {
            Object network = osMetricsJson.get("network");
            if (network != null) {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(network);
            }
            return "No 'network' data in log.";
        } catch (Exception e) {
            System.err.println("Error formatting Network JSON: " + e.getMessage());
            return "Error formatting Network JSON.";
        }
    }
}
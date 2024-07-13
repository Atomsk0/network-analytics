package com.atomsk.analytics.service;

import com.atomsk.analytics.model.AnomalyData;
import com.atomsk.analytics.model.NetworkTrafficData;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxTable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class NetworkTrafficService {

    @Autowired
    private InfluxDBClient influxDBClient;

    @Value("${influxdb.bucket}")
    private String bucket;

    @Value("${influxdb.org}")
    private String orgz;

    public List<NetworkTrafficData> getRecentTraffic() {
        QueryApi queryApi = influxDBClient.getQueryApi();

        String flux = String.format("from(bucket:\"%s\")" + " |> range(start: -1h)"
                + " |> filter(fn: (r) => r._measurement == \"network_traffic\")"
                + " |> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")"
                + " |> sort(columns: [\"_time\"], desc: false)" + " |> limit(n:100)", bucket);

        List<FluxTable> tables = queryApi.query(flux, orgz);
        List<NetworkTrafficData> trafficDataList = new ArrayList<>();

        tables.stream().flatMap(table -> table.getRecords().stream()).forEach(fluxRecord -> {
            NetworkTrafficData data = NetworkTrafficData.builder().timestamp(fluxRecord.getTime())
                    .sourceIp(fluxRecord.getValueByKey("sourceIp").toString())
                    .destinationIp(fluxRecord.getValueByKey("destinationIp").toString())
                    .protocol(fluxRecord.getValueByKey("protocol").toString())
                    .port(((Number) fluxRecord.getValueByKey("port")).intValue())
                    .bytes(((Number) fluxRecord.getValueByKey("bytes")).longValue())
                    .packets(((Number) fluxRecord.getValueByKey("packets")).longValue()).build();
            trafficDataList.add(data);
        });
        trafficDataList.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
        return trafficDataList;

    }

    public List<AnomalyData> getRecentAnomalies() {
        QueryApi queryApi = influxDBClient.getQueryApi();

        String flux = String.format("from(bucket:\"%s\")" + " |> range(start: -24h)"
                + " |> filter(fn: (r) => r._measurement == \"anomalies\")"
                + " |> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")"
                + " |> sort(columns: [\"_time\"], desc: true)" + " |> limit(n:50)", bucket);

        List<FluxTable> tables = queryApi.query(flux, orgz);
        List<AnomalyData> anomalyDataList = new ArrayList<>();

        tables.stream().flatMap(table -> table.getRecords().stream()).forEach(fluxRecord -> {
            AnomalyData data = AnomalyData.builder().timestamp(fluxRecord.getTime())
                    .type(AnomalyData.AnomalyType.valueOf(fluxRecord.getValueByKey("type").toString()))
                    .severity(AnomalyData.AnomalySeverity.valueOf(fluxRecord.getValueByKey("severity").toString()))
                    .description(fluxRecord.getValueByKey("description").toString())
                    .affectedIp(fluxRecord.getValueByKey("affectedIp").toString()).build();
            anomalyDataList.add(data);
        });
        anomalyDataList.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
        return anomalyDataList;
    }

    @Scheduled(fixedRate = 5000)
    public void simulateTrafficData() {
        log.info("Simulating traffic now...");
        NetworkTrafficData data = generateRandomTrafficData();
        saveTrafficData(data);
        detectAnomalies(data);
        log.info("Done simulating!");
    }

    private Map<String, MovingAverage> ipTrafficAverages = new HashMap<>();

    private NetworkTrafficData generateRandomTrafficData() {
        String[] protocols = { "TCP", "UDP", "HTTP", "HTTPS" };
        String[] ipPrefixes = { "192.168.", "10.0.", "172.16." };

        String sourceIp = generateRandomIp(ipPrefixes);
        String destIp = generateRandomIp(ipPrefixes);
        String protocol = protocols[ThreadLocalRandom.current().nextInt(protocols.length)];
        int port = ThreadLocalRandom.current().nextInt(1, 65536);
        long bytes = ThreadLocalRandom.current().nextLong(100, 10000000);
        long packets = bytes / ThreadLocalRandom.current().nextLong(100, 1500);

        return NetworkTrafficData.builder().timestamp(Instant.now()).sourceIp(sourceIp).destinationIp(destIp)
                .protocol(protocol).port(port).bytes(bytes).packets(packets).build();
    }

    private String generateRandomIp(String[] ipPrefixes) {
        String prefix = ipPrefixes[ThreadLocalRandom.current().nextInt(ipPrefixes.length)];
        return prefix + ThreadLocalRandom.current().nextInt(0, 256) + "." + ThreadLocalRandom.current().nextInt(0, 256);
    }

    private void saveTrafficData(NetworkTrafficData data) {
        try (WriteApi writeApi = influxDBClient.getWriteApi()) {
            Point point = Point.measurement("network_traffic").addTag("sourceIp", data.getSourceIp())
                    .addTag("destinationIp", data.getDestinationIp()).addTag("protocol", data.getProtocol())
                    .addField("port", data.getPort()).addField("bytes", data.getBytes())
                    .addField("packets", data.getPackets()).time(data.getTimestamp(), WritePrecision.NS);

            writeApi.writePoint(point);
        }
    }

    private boolean isAnomaly(NetworkTrafficData data) {
        String ip = data.getSourceIp();
        long trafficVolume = data.getBytes();

        // Get or create moving average for this IP

        MovingAverage avgTraffic = ipTrafficAverages.computeIfAbsent(ip, k -> new MovingAverage(10));
        double average = avgTraffic.next(trafficVolume);

        log.info("Traffic volume: {}, average: {}", trafficVolume, average);
        // Check if current traffic is significantly higher than the moving average
        if (trafficVolume > average * 3 && trafficVolume > 1000000) { // 1MB threshold
            log.info("Anomaly detected: Traffic spike for IP {} - Current: {}, Average: {}", ip, trafficVolume,
                    average);
            return true;
        }

        // Check for unusual port usage
        if (data.getPort() < 1024 && !isCommonPort(data.getPort())) {
            return true;
        }

        // Add more anomaly detection rules as needed

        return false;
    }

    private boolean isCommonPort(int port) {
        // List of common ports (this is a simplified example)
        int[] commonPorts = { 80, 443, 22, 21, 25, 110, 143, 993, 995 };
        for (int commonPort : commonPorts) {
            if (port == commonPort)
                return true;
        }
        return false;
    }

    private void saveAnomaly(AnomalyData anomaly) {
        try (WriteApi writeApi = influxDBClient.getWriteApi()) {
            Point point = Point.measurement("anomalies").addTag("type", anomaly.getType().toString())
                    .addTag("severity", anomaly.getSeverity().toString())
                    .addField("description", anomaly.getDescription()).addField("affectedIp", anomaly.getAffectedIp())
                    .time(anomaly.getTimestamp(), WritePrecision.NS);

            // Add additional info fields
            for (Map.Entry<String, Object> entry : anomaly.getAdditionalInfo().entrySet()) {
                if (entry.getValue() instanceof Number) {
                    point.addField(entry.getKey(), (Number) entry.getValue());
                } else if (entry.getValue() instanceof Boolean) {
                    point.addField(entry.getKey(), (Boolean) entry.getValue());
                } else {
                    point.addField(entry.getKey(), entry.getValue().toString());
                }
            }

            writeApi.writePoint(point);
        }
    }

    // Helper class for calculating moving average
    private static class MovingAverage {
        private final int size;
        private final Queue<Long> window;
        private long sum;

        public MovingAverage(int size) {
            this.size = size;
            this.window = new LinkedList<>();
            this.sum = 0;
        }

        public double next(long val) {
            sum += val;
            window.offer(val);

            if (window.size() > size) {
                sum -= window.poll();
            }

            return (double) sum / window.size();
        }
    }

    private void detectAnomalies(NetworkTrafficData data) {
        // Anomaly detection logic
        if (isAnomaly(data)) {
            AnomalyData anomaly = AnomalyData.builder().timestamp(Instant.now())
                    .type(AnomalyData.AnomalyType.TRAFFIC_SPIKE).severity(AnomalyData.AnomalySeverity.HIGH)
                    .description("Unusual traffic spike detected").affectedIp(data.getSourceIp())
                    .additionalInfo(Map.of("trafficVolume", data.getBytes())).build();

            saveAnomaly(anomaly);
        }
    }
}

package com.atomsk.analytics.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class AnomalyData {
    private Instant timestamp;
    private AnomalyType type;
    private AnomalySeverity severity;
    private String description;
    private String affectedIp;
    private Map<String, Object> additionalInfo;

    // Enum for anomaly types
    public enum AnomalyType {
        TRAFFIC_SPIKE, UNUSUAL_PORT, UNUSUAL_PROTOCOL, POTENTIAL_PORT_SCAN, POTENTIAL_DDoS
    }

    // Enum for anomaly severity
    public enum AnomalySeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}

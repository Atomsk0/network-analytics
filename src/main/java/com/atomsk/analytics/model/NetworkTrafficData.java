package com.atomsk.analytics.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class NetworkTrafficData {
    private Instant timestamp;
    private String sourceIp;
    private String destinationIp;
    private String protocol;
    private int port;
    private long bytes;
    private long packets;
}

package com.atomsk.analytics.controller;

import com.atomsk.analytics.model.AnomalyData;
import com.atomsk.analytics.model.NetworkTrafficData;
import com.atomsk.analytics.service.NetworkTrafficService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class NetworkTrafficController {

    @Autowired
    private NetworkTrafficService trafficService;

    @GetMapping("/traffic")
    public List<NetworkTrafficData> getRecentTraffic() {
        return trafficService.getRecentTraffic();
    }

    @GetMapping("/anomalies")
    public List<AnomalyData> getRecentAnomalies() {
        return trafficService.getRecentAnomalies();
    }
}

package com.example.job.api.controller;

import com.example.job.api.config.ApiPathProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UiConfigController {

    private final ApiPathProperties apiPathProperties;

    public UiConfigController(ApiPathProperties apiPathProperties) {
        this.apiPathProperties = apiPathProperties;
    }

    @GetMapping("${app.api.ui-config-path:/api/ui-config}")
    public Map<String, String> uiConfig() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("jobsBasePath", apiPathProperties.getJobsBasePath());
        config.put("jobsStreamPath", apiPathProperties.getJobsStreamFullPath());
        return config;
    }
}

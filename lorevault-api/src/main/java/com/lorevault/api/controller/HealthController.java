package com.lorevault.api.controller;

import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final Optional<BuildProperties> buildProperties;

    public HealthController(Optional<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping("/status")
    public Map<String, String> getStatus() {
        return Map.of(
            "status", "UP",
            "service", buildProperties.map(BuildProperties::getName).orElse("LoreVault API"),
            "version", buildProperties.map(BuildProperties::getVersion).orElse("unknown"),
            "artifact", buildProperties.map(BuildProperties::getArtifact).orElse("lorevault-api"),
            "group", buildProperties.map(BuildProperties::getGroup).orElse("com.lorevault")
        );
    }
}

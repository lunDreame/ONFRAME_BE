package com.onframe.be.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "service", "onframe-be");
    }
}
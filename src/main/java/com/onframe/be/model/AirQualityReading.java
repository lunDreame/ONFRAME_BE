package com.onframe.be.model;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AirQualityReading {
    private String sensorId;
    private Double pm25;
    private Double pm10;
    private Double co2;
    private Double tvoc;
    private Double temperature;
    private Double humidity;
    private Double aqi;
    private OffsetDateTime timestamp;
    private Map<String,Object> raw; // 원본 필드 보존
}
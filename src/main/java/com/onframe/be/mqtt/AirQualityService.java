package com.onframe.be.mqtt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onframe.be.config.FrontendProperties;
import com.onframe.be.model.AirQualityReading;
import com.onframe.be.util.BoundedDeque;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AirQualityService {

    private final ObjectMapper om = new ObjectMapper();
    private final FrontendProperties props;

    private final ConcurrentHashMap<String, AirQualityReading> latest = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BoundedDeque<AirQualityReading>> history = new ConcurrentHashMap<>();

    private final Sinks.Many<AirQualityReading> sink = Sinks.many().multicast().onBackpressureBuffer();

    public Map<String, AirQualityReading> getAllLatest() { return latest; }
    public AirQualityReading getLatest(String sensorId) { return latest.get(sensorId); }
    public BoundedDeque<AirQualityReading> getHistory(String sensorId) {
        return history.computeIfAbsent(sensorId, k -> new BoundedDeque<>(props.getAir().getHistorySize()));
    }
    public Sinks.Many<AirQualityReading> sink() { return sink; }

    /** MQTT inbound handler */
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMqtt(Message<?> msg) {
        try {
            String topic = (String) msg.getHeaders().getOrDefault("mqtt_receivedTopic", "");
            String sensorId = extractSensorId(topic); // onframe/air/<sensorId>
            if (sensorId == null) {
                log.debug("Skip MQTT (no sensorId): {}", topic);
                return;
            }
            String payload = payloadAsString(msg);
            Map<String, Object> map = om.readValue(payload, new TypeReference<>() {});

            AirQualityReading reading = AirQualityReading.builder()
                    .sensorId(sensorId)
                    .pm25(toDouble(map.get("pm25")))
                    .pm10(toDouble(map.get("pm10")))
                    .co2(toDouble(map.get("co2")))
                    .tvoc(toDouble(map.get("tvoc")))
                    .temperature(toDouble(map.get("temp"), toDouble(map.get("temperature"))))
                    .humidity(toDouble(map.get("humidity")))
                    .aqi(toDouble(map.get("aqi"), toDouble(map.get("aqIndex"))))
                    .timestamp(parseTimestamp(map.get("ts")))
                    .raw(map)
                    .build();

            if (reading.getTimestamp() == null) reading.setTimestamp(OffsetDateTime.now());

            latest.put(sensorId, reading);
            getHistory(sensorId).add(reading);

            var emit = sink.tryEmitNext(reading);
            if (emit.isFailure()) log.debug("SSE emit dropped: {}", emit);
        } catch (Exception e) {
            log.warn("MQTT parse error: {}", e.getMessage());
        }
    }

    private String payloadAsString(Message<?> msg) {
        Object payload = msg.getPayload();
        if (payload instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return String.valueOf(payload);
    }

    private String extractSensorId(String topic) {
        // 기대 topic: onframe/air/<sensorId> or onframe/air/<room>/<sensorId>
        if (topic == null) return null;
        String[] parts = topic.split("/");
        if (parts.length >= 3 && "onframe".equals(parts[0]) && "air".equals(parts[1])) {
            return parts[parts.length - 1]; // 마지막 토큰을 sensorId로
        }
        return null;
    }

    private Double toDouble(Object... vals) {
        for (Object v : vals) {
            if (v == null) continue;
            if (v instanceof Number n) return n.doubleValue();
            try { return Double.parseDouble(v.toString()); } catch (Exception ignore) {}
        }
        return null;
    }

    private OffsetDateTime parseTimestamp(Object v) {
        try {
            if (v == null) return null;
            return OffsetDateTime.parse(v.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
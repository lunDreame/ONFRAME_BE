package com.neovision.onframe.mqtt;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.neovision.onframe.model.AirQualityReading;
import com.neovision.onframe.util.BoundedDeque;

import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/air")
@RequiredArgsConstructor
public class AirQualityController {

    private final AirQualityService svc;

    @GetMapping("/latest")
    public Map<String, AirQualityReading> latestAll() {
        return svc.getAllLatest();
    }

    @GetMapping("/latest/{sensorId}")
    public AirQualityReading latest(@PathVariable String sensorId) {
        return svc.getLatest(sensorId);
    }

    @GetMapping("/history/{sensorId}")
    public Object history(@PathVariable String sensorId) {
        BoundedDeque<AirQualityReading> dq = svc.getHistory(sensorId);
        return dq.snapshot(); // ArrayDeque 반환 → JSON 배열로 직렬화
    }

    /** 실시간 SSE 스트림 (프론트: EventSource('/api/air/sse')) */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AirQualityReading> stream() {
        // 연결 유지 heartbeat
        Flux<AirQualityReading> heartbeat = Flux.interval(Duration.ofSeconds(30))
                .map(t -> AirQualityReading.builder().sensorId("_heartbeat").timestamp(java.time.OffsetDateTime.now()).build());
        return Flux.merge(svc.sink().asFlux(), heartbeat);
    }
}
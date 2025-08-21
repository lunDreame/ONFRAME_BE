package com.onframe.be.ha;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import io.swagger.v3.oas.annotations.Hidden;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/ha")
@RequiredArgsConstructor
public class HaController {

    private final HomeAssistantClient ha;

    @GetMapping("/states")
    public Mono<Object> states() {
        return ha.getStates();
    }

    @GetMapping("/states/{entityId}")
    public Mono<Object> state(@PathVariable String entityId) {
        return ha.getState(entityId);
    }

    @PostMapping("/services/{domain}/{service}")
    public Mono<Object> callService(@PathVariable String domain,
                                    @PathVariable String service,
                                    @RequestBody(required = false) Map<String, Object> body) {
        return ha.callService(domain, service, body);
    }

    @GetMapping("/history")
    public Mono<Object> history(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end,
                                @RequestParam(required = false) String entityId) {
        return ha.getHistory(start, end, entityId);
    }

    /** (선택) 화이트리스트 프록시 */
    @Hidden
    @RequestMapping(value = "/proxy/{*path}", consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Object> proxy(@PathVariable("path") String path,
                              @RequestBody(required = false) String body,
                              @RequestHeader(value = "x-ha-method", required = false, defaultValue = "GET") String method) {
        return ha.proxy(method.toUpperCase(), path, body);
    }
}
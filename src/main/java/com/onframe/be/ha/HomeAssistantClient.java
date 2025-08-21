package com.onframe.be.ha;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HomeAssistantClient {

    private final WebClient haWebClient;

    public Mono<Object> getStates() {
        return haWebClient.get().uri("/api/states")
                .retrieve().bodyToMono(Object.class);
    }

    public Mono<Object> getState(String entityId) {
        return haWebClient.get().uri("/api/states/{id}", entityId)
                .retrieve().bodyToMono(Object.class);
    }

    public Mono<Object> callService(String domain, String service, Map<String, Object> body) {
        return haWebClient.post().uri("/api/services/{d}/{s}", domain, service)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body == null ? Map.of() : body)
                .retrieve().bodyToMono(Object.class);
    }

    public Mono<Object> getHistory(OffsetDateTime start, OffsetDateTime end, String entityId) {
        return haWebClient.get().uri(uriBuilder -> {
                    var b = uriBuilder.path("/api/history/period/{start}")
                            .queryParam("end_time", end.toString());
                    if (entityId != null && !entityId.isBlank()) {
                        b.queryParam("filter_entity_id", entityId);
                    }
                    return b.build(start.toString());
                })
                .retrieve().bodyToMono(Object.class);
    }

    /** (선택) 프록시: /api/... 경로를 화이트리스트로 제한 */
    public Mono<Object> proxy(String method, String pathAfterApi, String rawBody) {
        if (!pathAfterApi.startsWith("states")
                && !pathAfterApi.startsWith("services")
                && !pathAfterApi.startsWith("history")) {
            return Mono.error(new IllegalArgumentException("Proxy path not allowed: /api/" + pathAfterApi));
        }

        WebClient.RequestBodySpec req = haWebClient
                .method(org.springframework.http.HttpMethod.valueOf(method))
                .uri("/api/" + pathAfterApi);

        boolean canHaveBody = !("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method));
        boolean hasBody = rawBody != null && !rawBody.isBlank();

        if (canHaveBody && hasBody) {
            return req
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(rawBody)
                    .retrieve()
                    .bodyToMono(Object.class);
        } else {
            return req
                    .retrieve()
                    .bodyToMono(Object.class);
        }
    }
}
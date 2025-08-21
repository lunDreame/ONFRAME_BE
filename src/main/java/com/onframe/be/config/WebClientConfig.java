package com.onframe.be.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final HomeAssistantProperties haProps;

    @Bean("haWebClient")
    public WebClient haWebClient() {
        return WebClient.builder()
                .baseUrl(haProps.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + haProps.getToken())
                .defaultHeader("Content-Type", "application/json")
                // 대용량 페이로드 대응
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                        .build())
                .build();
    }
}
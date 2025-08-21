package com.onframe.be.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        HomeAssistantProperties.class,
        MqttProperties.class,
        FrontendProperties.class
})
public class AppProperties {}
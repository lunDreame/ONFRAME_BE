package com.onframe.be.config;

import com.onframe.be.mqtt.AirQualityService;
import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.*;

@Configuration
@RequiredArgsConstructor
public class MqttConfig {

    private final MqttProperties mqttProps;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        var opts = new MqttConnectOptions();
        opts.setServerURIs(new String[]{mqttProps.getBrokerUrl()});
        if (mqttProps.getUsername() != null && !mqttProps.getUsername().isBlank()) {
            opts.setUserName(mqttProps.getUsername());
        }
        if (mqttProps.getPassword() != null && !mqttProps.getPassword().isBlank()) {
            opts.setPassword(mqttProps.getPassword().toCharArray());
        }
        opts.setAutomaticReconnect(true);
        opts.setCleanSession(true);
        opts.setConnectionTimeout(10);

        var factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(opts);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer inbound(MqttPahoClientFactory factory) {
        var adapter = new MqttPahoMessageDrivenChannelAdapter(
                mqttProps.getClientId(), factory, mqttProps.getTopics().toArray(String[]::new));
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter()); // payload = String/byte[]
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    // 핸들러는 AirQualityService 내부 @ServiceActivator 메서드로 등록
}
package com.bitbox.gateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    /**
     * Non-blocking HTTP client towards inventory-service. Network-level timeouts prevent a slow or dead
     * upstream from holding connections (and SSE streams) forever.
     */
    @Bean
    public WebClient inventoryWebClient(WebClient.Builder builder, GatewayProperties properties) {
        GatewayProperties.Inventory inventory = properties.inventory();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) inventory.connectTimeout().toMillis())
                .responseTimeout(inventory.timeout());
        return builder
                .baseUrl(inventory.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}

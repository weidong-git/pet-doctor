package com.petdoctor.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebClient 与跨域配置
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient difyWebClient(DifyProperties difyProperties) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(difyProperties.getReadTimeoutMs()))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, difyProperties.getConnectTimeoutMs());

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}

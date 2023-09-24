package com.zaynjarvis.hscloud.config;

import nova.traffic.server.ServerChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    @Bean
    public ServerChannel serverChannel() {
        return new ServerChannel();
    }
}

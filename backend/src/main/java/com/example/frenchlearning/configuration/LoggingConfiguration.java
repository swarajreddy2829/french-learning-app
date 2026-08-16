package com.example.frenchlearning.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LoggingConfiguration {

    public static final String REQUEST_LOGGER_NAME = "com.example.frenchlearning.request";

    @Bean
    CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}

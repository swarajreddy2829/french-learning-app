package com.example.frenchlearning.configuration;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
public class PersistenceConfiguration {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    DateTimeProvider utcDateTimeProvider(@Qualifier("utcClock") Clock utcClock) {
        return () -> Optional.of(Instant.now(utcClock));
    }
}

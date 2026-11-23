package com.bitbox.inventory.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** Single source of time (UTC) so timestamps are consistent and testable. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

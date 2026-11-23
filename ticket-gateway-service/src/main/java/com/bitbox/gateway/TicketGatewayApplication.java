package com.bitbox.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TicketGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketGatewayApplication.class, args);
    }
}

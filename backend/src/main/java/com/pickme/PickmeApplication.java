package com.pickme;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PickmeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PickmeApplication.class, args);
    }
}

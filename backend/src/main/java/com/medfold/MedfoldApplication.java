package com.medfold;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MedfoldApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedfoldApplication.class, args);
    }
}

package com.fintap.digikadai;

import com.fintap.digikadai.config.IntegrationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties(IntegrationProperties.class)
@EnableAsync
public class DigiKadaiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DigiKadaiApplication.class, args);
    }
}

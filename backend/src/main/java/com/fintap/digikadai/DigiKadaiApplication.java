package com.fintap.digikadai;

import com.fintap.digikadai.config.IntegrationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(IntegrationProperties.class)
public class DigiKadaiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DigiKadaiApplication.class, args);
    }
}

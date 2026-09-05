package com.example.auth;

import org.springframework.boot.SpringApplication;
import com.example.auth.config.EnvironmentProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Точка входа сервиса `auth`. */
@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties(EnvironmentProperties.class)
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}

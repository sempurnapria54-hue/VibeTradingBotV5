package com.example.auth;

import com.example.auth.config.EnvironmentProperties;
import com.example.auth.config.IdentityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Точка входа сервиса `auth`. */
@SpringBootApplication
@EnableConfigurationProperties({EnvironmentProperties.class, IdentityProperties.class})
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}

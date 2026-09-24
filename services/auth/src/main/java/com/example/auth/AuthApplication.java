package com.example.auth;

import com.example.auth.config.EnvironmentProperties;
import com.example.auth.config.IdentityProperties;
import com.example.platform.exception.handler.AccessDenialHandler;
import com.example.platform.security.ActorProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

/**
 * Точка входа сервиса `auth`.
 *
 * <p><b>Область отображаемых классов названа явно.</b> Базовый тип
 * audit-полей лежит в общем артефакте, то есть вне пакета сервиса, и
 * умолчание сканирования его не видит. Свой пакет перечисляется рядом:
 * {@code @EntityScan} умолчание ЗАМЕЩАЕТ, а не дополняет.
 *
 * <p><b>Взятое из общего артефакта периметра названо ИМЕНОВАННО</b>, а не
 * взято сканированием его пакета: перечень взятого читается в одном
 * месте. Отсюда же резолвер актора, которого у сервиса прежде не было
 * вовсе ({@code JpaAuditConfig}).
 */
@SpringBootApplication
@EntityScan({"com.example.auth", "com.example.tradingbot.persistence.model"})
@Import({AccessDenialHandler.class, ActorProvider.class})
@EnableConfigurationProperties({EnvironmentProperties.class, IdentityProperties.class})
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}

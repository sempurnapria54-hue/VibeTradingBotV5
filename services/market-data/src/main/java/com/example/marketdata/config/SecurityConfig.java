package com.example.marketdata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Контур доступа к поверхности рыночных данных.
 *
 * <p>Форма та же, что у остальных сервисов: bearer с локальной проверкой
 * подписи, умолчание закрыто, открыта только проба живости
 * (docs/rules/api-access-policy.md). Вызывающие — {@code trading-core},
 * {@code strategies} и {@code bff}
 * (docs/architecture/contracts.md §«Синхронные вызовы»).
 *
 * <p><b>Резолва членства здесь нет, и это не упущение:</b> листинг,
 * свечи и производные — платформенное знание, у них нет тенанта-владельца
 * (docs/architecture/tenant-and-exchange.md §Инструменты). Различать
 * тенантов на этой границе нечем и незачем.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        // Проба живости открыта: она отвечает на вопрос
                        // «процесс жив», а не отдаёт данные.
                        .requestMatchers("/actuator/health/**").permitAll()
                        // Умолчание закрыто.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(server -> server.jwt(jwt -> { }))
                .build();
    }
}

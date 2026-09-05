package com.example.tradingcore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Контур доступа к поверхности торгового ядра.
 *
 * <p>Форма та же, что у остальных сервисов: bearer с локальной проверкой
 * подписи, умолчание закрыто, открыта только проба живости
 * (docs/rules/api-access-policy.md). Вызывающие — {@code bff} и
 * {@code strategies} (docs/architecture/contracts.md §«Синхронные
 * вызовы»).
 *
 * <p><b>Пер-операционных проверок права здесь нет намеренно.</b> При
 * одном субъекте различать некого; проверка права приезжает со вторым
 * субъектом (фаза 5), и заводить её раньше значило бы держать механизм,
 * которому нечего различать.
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

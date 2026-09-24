package com.example.connector.okx.config;

import com.example.platform.exception.handler.AccessDenialHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Контур доступа к поверхности коннектора.
 *
 * <p><b>Вызывающий здесь — сервис, а не человек.</b> Коннектор стои́т
 * ярусом ниже домена, и в него ходят {@code trading-core} и
 * {@code market-data} под сервисной идентичностью кластера
 * ({@code docs/architecture/contracts.md} §«Контекст тенанта в вызове»);
 * пользовательского токена на этой границе не бывает. Отсюда та же форма,
 * что у остальных сервисов, — bearer с локальной проверкой подписи, — но
 * без резолва членства: различать тенантов на этой границе нечем и незачем,
 * тенант приезжает операндом вызова.
 *
 * <p><b>Сеть — вторая половина этого контура, и она не здесь.</b> Пара
 * «кто к кому ходит» объявлена таблицей синхронных вызовов и становится
 * {@code NetworkPolicy} при запрете по умолчанию
 * ({@code docs/architecture/platform.md} §Безопасность). Токен отвечает
 * «кто это был», политика — «кому вообще можно дойти».
 *
 * <p><b>Отказ отвечает тем же error-DTO, что и всякая ошибка
 * поверхности</b> — обе точки входа цепочки делегируют в
 * {@link AccessDenialHandler} (docs/rules/error-handling-policy.md
 * §«Отказ доступа — тот же контракт, что и прочие ошибки»). Умолчание
 * ресурс-сервера отвечало ПУСТЫМ телом, то есть вторым форматом.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AccessDenialHandler accessDenialHandler) throws Exception {
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
                .oauth2ResourceServer(server -> server
                        .jwt(jwt -> {})
                        .authenticationEntryPoint(accessDenialHandler)
                        .accessDeniedHandler(accessDenialHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(accessDenialHandler)
                        .accessDeniedHandler(accessDenialHandler))
                .build();
    }
}

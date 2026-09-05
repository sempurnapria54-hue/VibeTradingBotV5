package com.example.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Контур доступа целевой поверхности: bearer-токен провайдера
 * идентичности, подпись проверяется локально по его ключам
 * (docs/rules/api-access-policy.md §«Два контура доступа сосуществуют,
 * пока жив донор»).
 *
 * <p><b>Пер-операционных проверок права здесь нет намеренно.</b> При
 * одном субъекте различать некого, и требование к контуру — уметь
 * ответить «кто это был», а не «что ему можно». Проверка права приезжает
 * со вторым субъектом (фаза 5), и заводить её раньше значило бы держать
 * механизм, который нечему различать.
 *
 * <p>CSRF выключен и это не унаследованная оговорка Basic-контура:
 * поверхность stateless, сессии нет, тропа — заголовок Authorization,
 * который браузер сам не переотправляет.
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
                        // Умолчание закрыто: всё прочее требует
                        // предъявленного и принятого принципала.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(server -> server.jwt(jwt -> {}))
                .build();
    }
}

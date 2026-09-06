package com.example.bff.config;

import com.example.bff.api.AccessDenialHandler;
import com.example.bff.util.Constants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Контур доступа периметра: bearer-токен провайдера идентичности,
 * подпись проверяется локально по его ключам, без вызова {@code auth} на
 * каждом запросе (docs/architecture/contracts.md §«Контекст тенанта в
 * вызове»).
 *
 * <p><b>Умолчание закрыто; открытая точка одна — проба живости</b>
 * (docs/rules/api-access-policy.md §«Вся поверхность закрыта; открытое —
 * перечислено»). Она отвечает на вопрос «процесс жив», а не отдаёт
 * данные.
 *
 * <p><b>Тропа подписки исключена из bearer-цепочки НАМЕРЕННО, и открытой
 * она от этого не становится.</b> Браузерный {@code EventSource}
 * заголовка {@code Authorization} не ставит — интерфейс его не
 * принимает, — поэтому подписку открывает краткоживущий БИЛЕТ, выданный
 * этим же периметром по обычному вызову под токеном
 * (docs/architecture/contracts.md §«Подписку открывает билет, а не сам
 * токен»). Проверка билета безусловна и живёт в самой точке подписки;
 * отказ отвечает тем же error-DTO, что и отказ фильтр-цепочки, — иначе у
 * поверхности появился бы второй формат отказа.
 *
 * <p><b>Пер-операционных проверок права здесь нет намеренно.</b> Право
 * проверяет сервис-владелец операции, а не периметр: отказ по праву есть
 * решение, а периметр решений не принимает
 * (docs/rules/api-access-policy.md §«Где проверяется право операции: у
 * домена, а не у периметра»).
 *
 * <p>CSRF выключен и это не унаследованная оговорка: поверхность
 * stateless, сессии нет, тропа — заголовок {@code Authorization},
 * который браузер сам не переотправляет.
 */
@Configuration
public class SecurityConfig {

    /** Точка подписки: единственная, чей предъявитель — билет, а не токен. */
    private static final String STREAM_PATH = Constants.Paths.PERIMETER_ROOT + "/stream";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AccessDenialHandler denialHandler) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(Constants.Paths.HEALTH).permitAll()
                        // Билет вместо токена — проверка в самой точке подписки.
                        .requestMatchers(STREAM_PATH).permitAll()
                        // Умолчание закрыто: всё прочее требует
                        // предъявленного и принятого принципала.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(server -> server.jwt(jwt -> {})
                        .authenticationEntryPoint(denialHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(denialHandler)
                        .accessDeniedHandler(denialHandler))
                .build();
    }
}

package com.example.bff.config;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.bff.util.Constants;
import com.example.platform.exception.handler.AccessDenialHandler;
import com.example.platform.exception.handler.BearerTokenFailureInstaller;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Контур доступа периметра: bearer-токен провайдера идентичности,
 * подпись проверяется локально по его ключам, без вызова {@code auth} на
 * каждом запросе (docs/architecture/contracts.md §«Контекст тенанта в
 * вызове»).
 *
 * <p><b>Умолчание закрыто; открытых точек две — проба живости и съём
 * метрик</b> (docs/rules/api-access-policy.md §«Вся поверхность закрыта;
 * открытое — перечислено»). Первая отвечает на вопрос «процесс жив»,
 * вторая отдаёт ряд частоты отказов доступа и ряды среды исполнения; ни
 * одна не отдаёт данных. Съём снаружи кластера недостижим: ингресс ведёт
 * только префикс внешней поверхности.
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
 * <p><b>Токен без субъекта — непринятый принципал</b>
 * (docs/rules/api-access-policy.md §«Два контура доступа сосуществуют, пока
 * жив донор»): на вопрос «кто это был» он не отвечает, а субъект — ключ
 * кэша членств. Отвергает его звено проверки токена, то есть точка входа
 * отказа отвечает {@code 401} до резолва контекста; пропущенный дальше, он
 * доходил бы до кэша пустым ключом.
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
                        // Съём метрик наблюдателем окружения — поимённо.
                        .requestMatchers(Constants.Paths.PROMETHEUS).permitAll()
                        // Билет вместо токена — проверка в самой точке подписки.
                        .requestMatchers(STREAM_PATH).permitAll()
                        // Умолчание закрыто: всё прочее требует
                        // предъявленного и принятого принципала.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(server -> server.jwt(jwt -> {})
                        .authenticationEntryPoint(denialHandler)
                        .accessDeniedHandler(denialHandler)
                        // Сбой самого звена — 500 тем же DTO, а не 401 поверх.
                        .withObjectPostProcessor(new BearerTokenFailureInstaller(denialHandler)))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(denialHandler)
                        .accessDeniedHandler(denialHandler))
                .build();
    }

    /**
     * Валидатор присутствия субъекта: автоконфигурация декодера добавляет
     * его к умолчаниям (подпись, издатель, срок), а не заменяет их.
     * Субъект, не являющийся непустой строкой, — тот же отказ, что и
     * отсутствующий.
     */
    @Bean
    public OAuth2TokenValidator<Jwt> subjectPresenceValidator() {
        return new JwtClaimValidator<Object>(JwtClaimNames.SUB,
                subject -> subject instanceof String value && isNotBlank(value));
    }
}

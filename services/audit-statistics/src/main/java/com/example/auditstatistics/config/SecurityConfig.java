package com.example.auditstatistics.config;

import com.example.auditstatistics.api.AccessDenialHandler;
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
 * <p><b>Умолчание закрыто, открытое перечислено</b>
 * (docs/rules/api-access-policy.md §«Вся поверхность закрыта; открытое —
 * перечислено»): открытых точек две — проба живости и съём метрик.
 * Описание поверхности (`/v3/api-docs`, `/swagger-ui/**`) в исключения не
 * входит и закрыто умолчанием.
 *
 * <p><b>Съём метрик открыт по тому же признаку, что и проба живости:</b>
 * его спрашивает развёртывание, а не человек, и предмета контура он не
 * называет — ни позиций, ни ступеней, ни денег, ни строк журнала. Что он
 * отдаёт, названо там же: имена тем производителей, возраст последнего
 * принятого события, остаток непринятого и порог алерта — то есть
 * состояние ПРИЁМА, а не содержимое журнала. Разбор и границы —
 * docs/rules/api-access-policy.md §«Съём метрик — второе исключение».
 *
 * <p><b>Пер-операционных проверок права здесь нет намеренно.</b> При
 * одном субъекте различать некого, и требование к контуру — уметь
 * ответить «кто это был», а не «что ему можно». Проверка права приезжает
 * со вторым субъектом (фаза 5), и заводить её раньше значило бы держать
 * механизм, который нечему различать.
 *
 * <p><b>Читающая поверхность контур не смягчает.</b> Команд у сервиса нет
 * по инвентарю (docs/architecture/services.md), но журнал отвечает на
 * вопрос «что происходило у тенанта», а тенант едет операндом вызова, а
 * не токеном (docs/architecture/contracts.md §«Контекст тенанта в
 * вызове»): открытое чтение отдало бы историю любого тенанта кому угодно.
 *
 * <p>CSRF выключен и это не унаследованная оговорка Basic-контура:
 * поверхность stateless, сессии нет, тропа — заголовок Authorization,
 * который браузер сам не переотправляет.
 *
 * <p><b>Отказ отвечает тем же error-DTO, что и всякая ошибка
 * поверхности</b> — обе точки входа цепочки делегируют в
 * {@link AccessDenialHandler} (docs/rules/error-handling-policy.md
 * §«Отказ доступа — тот же контракт, что и прочие ошибки»). Умолчание
 * ресурс-сервера отвечает ПУСТЫМ телом, то есть вторым форматом.
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
                        // Съём метрик открыт: его спрашивает наблюдатель
                        // окружения, токена он не носит, а тропа к нему
                        // ограничена сетью — пространство имён закрыто по
                        // умолчанию (deploy/base/network-default-deny.yaml).
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // Умолчание закрыто: всё прочее требует
                        // предъявленного и принятого принципала.
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

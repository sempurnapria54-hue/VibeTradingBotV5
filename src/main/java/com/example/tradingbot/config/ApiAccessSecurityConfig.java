package com.example.tradingbot.config;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.api.security.AccessDenialHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Контур доступа к поверхности нашего API — дом правила
 * docs/rules/api-access-policy.md, здесь его исполнение.
 *
 * <p><b>Умолчание — закрыто.</b> Публично только то, что названо в цепочке
 * ниже; всё остальное требует аутентифицированного принципала. Обратный
 * дефолт («закрываем перечисленное») запрещён П1: незакрытым оказывается всё,
 * что забыли, и это ошибка в разрешающую сторону.
 *
 * <p><b>Открыта ровно одна точка — проба живости.</b> Её спрашивает
 * развёртывание, а не человек, и она не отвечает ни на один вопрос о состоянии
 * контура: ни о позициях, ни о ступенях, ни о деньгах. Всякое расширение
 * исключения проходит тот же вопрос — что о контуре узнает тот, кто не
 * предъявил себя.
 *
 * <p><b>Ролей нет, и это не упущение:</b> при одном субъекте различать некого
 * («что ему можно» тождественно «всё»). Требование к контуру — уметь ответить
 * «кто это был», и его несёт актор записи, а не модель прав.
 *
 * <p><b>Сессии не заводятся</b> ({@code STATELESS}): каждый вызов предъявляет
 * себя сам. Сессионный контур добавил бы второй носитель факта «кто вызывает»
 * — и первый, кто разошёлся бы с актором записи.
 *
 * <p><b>CSRF выключен осознанно:</b> защита от него нужна там, где браузер
 * шлёт куки-сессию автоматически; при stateless-предъявлении креда на каждом
 * вызове подделывать нечего.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class ApiAccessSecurityConfig {

    /** Единственная открытая точка: жив ли процесс. */
    private static final String LIVENESS_PROBE = "/actuator/health";

    private final ApiAccessProperties properties;
    private final AccessDenialHandler accessDenialHandler;

    @Bean
    public SecurityFilterChain apiAccessFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, LIVENESS_PROBE).permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(accessDenialHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(accessDenialHandler)
                        .accessDeniedHandler(accessDenialHandler))
                .build();
    }

    /**
     * Реестр принципалов контура. <b>Арность выражена здесь и только здесь:</b>
     * один субъект — посылка фазы 1 (docs/rules/api-access-policy.md), и её
     * параметр снятия — появление второго субъекта поверхности. Остальной код
     * работает с «текущим принципалом» и имени его не знает, поэтому снятие
     * посылки не растекается по коду.
     *
     * <p>Имя и секрет приходят из конфигурации (Vault per-profile), константой
     * в коде не лежат. Пустой секрет — <b>отказ подъёма</b>, а не открытая
     * поверхность: контур, поднявшийся с поверхностью, закрытой ничем, — это
     * ошибка в разрешающую сторону.
     */
    @Bean
    public UserDetailsService apiAccessPrincipal(PasswordEncoder passwordEncoder) {
        if (isBlank(properties.getPrincipal()) || isBlank(properties.getSecret())) {
            throw new IllegalStateException(
                    "api-access.principal/secret не заданы: поверхность контура закрывать нечем");
        }
        return new InMemoryUserDetailsManager(User
                .withUsername(properties.getPrincipal())
                .password(passwordEncoder.encode(properties.getSecret()))
                .authorities("PRINCIPAL")
                .build());
    }

    /** Сырой секрет в памяти не удерживается — только его хэш. */
    @Bean
    public PasswordEncoder apiAccessPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

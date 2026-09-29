package com.example.platform.exception.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

/**
 * Ставит звену bearer-токена обработчик отказа поверхности вместо умолчания.
 *
 * <p><b>Почему постобработка, а не настройка ресурс-сервера.</b> Настройщик
 * ресурс-сервера открывает точку входа и обработчик отказа авторизации, но
 * не обработчик отказа самого звена: умолчание звена
 * ({@code AuthenticationEntryPointFailureHandler}) собирается внутри него и
 * сбой звена перебрасывает мимо точки входа. Заменить его можно только на
 * собранном звене — постобработкой настройщика.
 *
 * <p><b>Что и почему ставится — называет {@link AccessDenialHandler}</b>;
 * здесь только проводка.
 */
@RequiredArgsConstructor
public class BearerTokenFailureInstaller implements ObjectPostProcessor<BearerTokenAuthenticationFilter> {

    private final AccessDenialHandler accessDenialHandler;

    @Override
    public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
        filter.setAuthenticationFailureHandler(accessDenialHandler);
        return filter;
    }
}

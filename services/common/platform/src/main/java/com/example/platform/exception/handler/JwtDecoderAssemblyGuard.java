package com.example.platform.exception.handler;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Переводит отказ ленивой сборки декодера токена в отказ проверки токена.
 *
 * <p><b>Зачем.</b> Декодер, собираемый по адресу провайдера, собирается на
 * первом токене контекста; недоступен диспетчер провайдера — сборка бросает
 * {@link JwtDecoderInitializationException}. Это наследник
 * {@code RuntimeException}, а не {@link JwtException}: провайдер
 * аутентификации его не переводит, звено bearer-токена не ловит, и сбой
 * уходит контейнеру мимо обработчика отказа звена — наружу тот же {@code 401}
 * без тела поверх {@code 500}, что у сбоя добычи ключей до
 * {@link BearerTokenFailureInstaller}. Переведённый в {@link JwtException},
 * он идёт той же тропой: провайдер делает из него сбой сервиса, и обработчик
 * отказа звена отвечает {@code 500} единым error-DTO
 * (docs/rules/error-handling-policy.md §«Отказ, произведённый контейнером, —
 * тот же контракт»).
 *
 * <p><b>Прочие отказы декодера не трогаются:</b> отвержение токена и сбой
 * добычи ключей уже приходят своими классами, и их исходы различает
 * провайдер, а не эта охрана.
 */
public class JwtDecoderAssemblyGuard implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof JwtDecoder decoder) {
            return guarded(decoder);
        }
        return bean;
    }

    private JwtDecoder guarded(JwtDecoder decoder) {
        return token -> {
            try {
                return decoder.decode(token);
            } catch (JwtDecoderInitializationException failure) {
                throw new JwtException("Декодер токена не собран", failure);
            }
        };
    }
}

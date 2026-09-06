package com.example.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Оси провайдера идентичности, которые сервис читает при резолве
 * контекста.
 *
 * <p><b>Зачем нужна отдельная ось «клиент браузера».</b> Заведение
 * тенанта идёт только по тропе периметра и только для токена ЧЕЛОВЕКА:
 * служебная идентичность кластера предъявляется на межсервисных
 * вызовах, где пользователя нет вовсе, и заведение по ней создало бы
 * тенанта на каждый сервис (docs/architecture/contracts.md §«Контекст
 * тенанта в вызове»). Различает их клиент, которым токен выдан, —
 * стандартный claim {@code azp}.
 *
 * <p>Пустое значение означает, что браузерная тропа не настроена: тогда
 * резолв не заводит ничего и отвечает отказом — это отказ, а не
 * открытая тропа.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "platform.identity")
public class IdentityProperties {

    /**
     * Идентификатор публичного клиента браузера у провайдера. Токен,
     * выданный не им, тропу заведения не проходит.
     */
    private String browserClientId;
}

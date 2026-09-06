package com.example.strategies.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Адреса соседей по контракту и регистрация исходящей идентичности
 * (docs/architecture/contracts.md §«Синхронные вызовы»).
 *
 * <p><b>Незаданный адрес означает отказ, а не умолчание.</b> Адрес
 * приезжает из манифеста окружения; пустое значение уводило бы вызов в
 * никуда либо — что хуже — в чужое окружение, если умолчание угадало бы
 * имя сервиса.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "neighbours")
public class NeighbourProperties {

    /**
     * Торговое ядро: числа риск-аппетита тенанта и проверка ссылок
     * определения. Второй сосед — {@code market-data} (история для
     * бэктеста) — приезжает со своим предметом, фазой 4.
     */
    private Neighbour tradingCore = new Neighbour();

    /** Сосед: куда ходить и под какой регистрацией клиента. */
    @Getter
    @Setter
    public static class Neighbour {

        /** Базовый адрес поверхности соседа внутри кластера. */
        private String baseUrl;

        /** Регистрация клиента, под которой добывается сервисный токен. */
        private String clientRegistrationId;
    }
}

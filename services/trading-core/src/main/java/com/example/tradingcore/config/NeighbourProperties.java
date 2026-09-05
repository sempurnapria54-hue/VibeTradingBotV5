package com.example.tradingcore.config;

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

    /** Коннектор площадки: команды и добыча фактов. */
    private Neighbour connector = new Neighbour();

    /** Сервис идентичности и реестра счетов. */
    private Neighbour auth = new Neighbour();

    /** Сервис рыночных данных: каталог и фичи. */
    private Neighbour marketData = new Neighbour();

    /** Сосед: куда ходить и под какой регистрацией клиента. */
    @Getter
    @Setter
    public static class Neighbour {

        /** Базовый адрес поверхности соседа внутри кластера. */
        private String baseUrl;

        /** Регистрация клиента, под которой добывается сервисный токен. */
        private String clientRegistrationId;

        /**
         * Код площадки соседа-коннектора: у коннектора он есть, у
         * остальных пуст. Поле объявлено потому, что ключ уже объявлен
         * манифестом окружения; без него привязка молча его теряла бы.
         */
        private String exchangeCode;
    }
}

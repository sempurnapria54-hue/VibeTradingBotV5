package com.example.marketdata.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Привязка к коннектору площадки: адрес его поверхности, код площадки,
 * которую он обслуживает, и регистрация клиента, под которой market-data
 * к нему ходит.
 *
 * <p><b>Коннектор один на площадку</b> (docs/architecture/services.md), и
 * связь «код площадки ↔ адрес коннектора» есть конфигурация окружения, а
 * не знание сервиса: вторая площадка приезжает вторым коннектором и
 * вторым блоком настроек, а не правкой кода.
 *
 * <p>Умолчаний у адреса и регистрации нет намеренно: незаданное означает
 * ОТКАЗ, а не поход в неизвестно чей коннектор.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "connector")
public class ConnectorProperties {

    /** Код площадки, которую обслуживает этот коннектор (OKX, ...). */
    private String exchangeCode;

    /** Базовый адрес поверхности коннектора. */
    private String baseUrl;

    /** Идентификатор регистрации OAuth2-клиента, под которой идёт исходящий вызов. */
    private String clientRegistrationId;

    /**
     * Типы инструментов площадки, чей листинг зеркалится в каталог.
     * Каталог полон всегда: заказать сбор по инструменту, которого в нём
     * нет, невозможно. Что по этим инструментам собирается — решает
     * требование потребителя, а не этот перечень
     * (docs/architecture/market-data-collection.md §«Как потребность
     * доходит до сбора»).
     */
    private List<String> instrumentTypes = List.of();

    /**
     * Котировочные валюты, по которым читаются цены индексов. Чтение
     * индексов у площадки идёт по валюте, а не по инструменту
     * (docs/integrations/okx/contracts/index-data.md).
     */
    private List<String> indexQuoteCurrencies = List.of();
}

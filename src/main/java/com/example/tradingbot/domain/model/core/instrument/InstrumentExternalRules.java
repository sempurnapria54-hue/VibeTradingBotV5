package com.example.tradingbot.domain.model.core.instrument;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.math.BigDecimal;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Актуальные внешние правила инструмента (спецификация биржи): шаги
 * цены/размера, стоимость контракта, per-order лимиты размера, биржевой
 * максимум плеча, торгуемость. Источник ограничений для риск-преконтроля
 * (округление цены/размера, расчёт размера в контрактах, проверки
 * min/max limits, биржевого max leverage, торгуемости). Хранится
 * JSONB-навесом на строке владельца {@link Instrument}; собственной
 * таблицы/{@code id} нет, audit-поля наследуются от строки-владельца,
 * обновляется только InstrumentExternalRulesSyncJob. Справочные валюты
 * инструмента (base/quote/settle) эта модель не держит — они в
 * транзиентном InstrumentExternalSnapshot. См.
 * docs/models/domain/other/InstrumentExternalRules.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentExternalRules {

    /** Внутренний ID инструмента-владельца в системе (ключ навеса). */
    private Long instrumentId;

    /** Нормализованный тип инструмента. */
    private InstrumentType instrumentType;

    /** Нормализованный тип контракта (линейный/инверсный). */
    private ContractType contractType;

    /** Нормализованный статус торгуемости инструмента. */
    private Status status;

    /** Сырой тип инструмента биржи (OKX instType). */
    private String externalInstrumentType;

    /** Имя инструмента на бирже (OKX instId). */
    private String externalInstrumentId;

    /** Сырой тип контракта (OKX ctType). */
    private String externalContractType;

    /** Стоимость контракта (OKX ctVal, decimal-строка). */
    private String externalContractValue;

    /** Валюта стоимости контракта (OKX ctValCcy). */
    private String externalContractValueCurrency;

    /** Шаг цены (OKX tickSz, decimal-строка). */
    private String externalTickSize;

    /** Шаг размера (OKX lotSz, decimal-строка). */
    private String externalLotSize;

    /** Минимальный размер ордера (OKX minSz, decimal-строка). */
    private String externalMinSize;

    /** Максимальный размер limit-ордера (OKX maxLmtSz, decimal-строка). */
    private String externalMaxLimitSize;

    /** Максимальный размер market-ордера (OKX maxMktSz, decimal-строка). */
    private String externalMaxMarketSize;

    /** Максимальный размер trigger-ордера (OKX maxTriggerSz, decimal-строка). */
    private String externalMaxTriggerSize;

    /** Максимальный размер stop-ордера (OKX maxStopSz, decimal-строка). */
    private String externalMaxStopSize;

    /** Биржевой максимум плеча инструмента (OKX lever, decimal-строка). */
    private String externalMaxLeverage;

    /** Сырой статус инструмента биржи (OKX state). */
    private String externalState;

    /** Инструмент торгуем (статус LIVE). */
    public Boolean isLive() {
        return Objects.equals(status, Status.LIVE);
    }

    /** Есть ли валидный набор полей для расчёта размера в контрактах (ctVal/lotSz/minSz положительны). */
    public Boolean hasSizingSpecs() {
        return isPositive(contractValue()) && isPositive(lotSize()) && isPositive(minSize());
    }

    /** Стоимость контракта (ctVal) числом; {@code null}, если значения нет. */
    public BigDecimal contractValue() {
        return toDecimal(externalContractValue);
    }

    /** Шаг цены (tickSz) числом; {@code null}, если значения нет. */
    public BigDecimal tickSize() {
        return toDecimal(externalTickSize);
    }

    /** Шаг размера (lotSz) числом; {@code null}, если значения нет. */
    public BigDecimal lotSize() {
        return toDecimal(externalLotSize);
    }

    /** Минимальный размер (minSz) числом; {@code null}, если значения нет. */
    public BigDecimal minSize() {
        return toDecimal(externalMinSize);
    }

    /** Максимальный размер limit-ордера (maxLmtSz) числом; {@code null}, если значения нет. */
    public BigDecimal maxLimitSize() {
        return toDecimal(externalMaxLimitSize);
    }

    /** Максимальный размер market-ордера (maxMktSz) числом; {@code null}, если значения нет. */
    public BigDecimal maxMarketSize() {
        return toDecimal(externalMaxMarketSize);
    }

    /** Биржевой максимум плеча (lever) числом; {@code null}, если значения нет. */
    public BigDecimal maxLeverage() {
        return toDecimal(externalMaxLeverage);
    }

    private static boolean isPositive(BigDecimal value) {
        return nonNull(value) && value.signum() > 0;
    }

    /** Парсит сырое биржевое значение; пусто/нечисловое → {@code null} (трактуется как отсутствие спецификации). */
    private static BigDecimal toDecimal(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Нормализованный статус торгуемости инструмента; источник — OKX state. */
    public enum Status {

        /** Торгуется в обычном режиме. */
        LIVE,

        /** Торговля приостановлена. */
        SUSPEND,

        /** Пре-открытие (приём заявок до старта торгов). */
        PREOPEN,

        /** Инструмент экспирирован. */
        EXPIRED,

        /** Тестовый инструмент. */
        TEST,

        /** Статус не удалось нормализовать. */
        UNKNOWN
    }

    /** Нормализованный тип инструмента. */
    public enum InstrumentType {

        /** Бессрочный своп (perpetual). */
        SWAP,

        /** Фьючерс с датой экспирации. */
        FUTURES,

        /** Спот. */
        SPOT,

        /** Маржинальная торговля (для текущего бота не используется). */
        MARGIN,

        /** Опцион (для текущего бота не используется). */
        OPTION,

        /** Тип не удалось нормализовать. */
        UNKNOWN
    }

    /** Нормализованный тип контракта. */
    public enum ContractType {

        /** Линейный контракт (расчёт в quote-валюте, ctValCcy = baseCcy). */
        LINEAR,

        /** Инверсный контракт (расчёт в base-валюте). */
        INVERSE,

        /** Тип не удалось нормализовать. */
        UNKNOWN
    }
}

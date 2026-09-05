package com.example.tradingbot.domain.model.core.exchange_account;

import com.example.tradingbot.domain.model.Auditable;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Биржевой счёт тенанта: тенант × площадка × метка
 * (docs/models/domain/core/ExchangeAccount.md).
 *
 * <p><b>Писателей два, и они пишут разные наборы полей:</b> реестровую
 * часть (идентичность, контур, статус) — {@code auth}; торговое состояние
 * (база риска, серия убытков, счётчик слепых проходов) —
 * {@code trading-core}. Физически это две таблицы в двух базах с общим
 * {@link #internalId}, поэтому правило «у таблицы один писатель»
 * соблюдено. Класс один, потому что одна и та же сущность: разведение по
 * писателям — свойство хранения, не формы.
 *
 * <p>Ключей счёта здесь нет ни в каком виде: они в Vault по пути,
 * выводимому из {@link #internalId}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeAccount extends Auditable {

    /** Внутренний идентификатор. Границу сервиса не пересекает. */
    private Long id;

    /**
     * Идентичность счёта наружу и между сервисами. Из неё выводится путь
     * ключей в Vault; присваивается при регистрации, дальше неизменяема.
     */
    private String internalId;

    /** {@code internalId} тенанта-владельца. */
    private String tenantId;

    /** Код площадки. */
    private String exchangeCode;

    /**
     * Метка счёта, видимая человеку: у тенанта на одной площадке счетов
     * может быть несколько.
     */
    private String label;

    /**
     * Контур площадки. Лежит рядом с ключами, потому что ключ и контур
     * суть одна истина: демо-ключ на боевой площадке отвергается, боевой
     * на демо — тоже.
     */
    private Contour contour;

    /** Состояние счёта. */
    private Status status;

    /**
     * База риска счёта — операнд всех четырёх потолков через снимок на
     * сделке. Пусто ⇒ risk-creating действие отвергается: пустое место
     * означает отказ, а не ноль.
     */
    private BigDecimal riskBase;

    /** Валюта базы; источник — расчётная валюта инструмента. */
    private String riskBaseCurrency;

    /** Длина текущей серии подряд убыточных закрытых сделок. */
    private Integer consecutiveLossCount;

    /** Подряд идущие ненаблюдённые проходы проактивной детекции. */
    private Integer blindPassCount;

    /**
     * Счёт двигает капитал владельца.
     *
     * <p>Предикат на модели: вопрос задают и окружение при регистрации, и
     * ядро перед набором риска, и ответ обязан быть один.
     */
    public Boolean isCapitalMoving() {
        return Contour.LIVE.equals(contour);
    }

    /** База риска назначена, значит risk-creating действие имеет делитель. */
    public Boolean hasRiskBase() {
        return Objects.nonNull(riskBase);
    }

    /** Контур площадки, к которому принадлежат ключи счёта. */
    public enum Contour {

        /** Боевая площадка: сделки двигают капитал владельца. */
        LIVE,

        /** Демо-контур площадки: сделки капитала не двигают. */
        DEMO
    }

    /**
     * Состояние счёта. {@code HOLD} и {@code TRADE_BLOCKED} — ступени
     * лестницы реакций; их условия входа и снятия живут в
     * docs/rules/exchange-hold.md и здесь не пересказываются.
     */
    public enum Status {

        /** Счёт торгует. */
        ACTIVE,

        /**
         * Мягкий холд: счёт выпадает из выборки сканера входа, новые
         * сделки не создаются; живые сопровождаются полностью.
         */
        HOLD,

        /**
         * Сворачивание: снятие живого риска по счёту, каскад активных
         * сделок в ошибку, блок торговых команд.
         */
        TRADE_BLOCKED,

        /** Счёт отключён владельцем; ключи отозваны. */
        CLOSED
    }
}

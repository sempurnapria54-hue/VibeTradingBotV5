package com.example.tradingbot.domain.model.core.exchange;

import com.example.tradingbot.domain.model.Auditable;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Биржа, с которой работает бот: идентичность, точка подключения и
 * статус использования. Инструменты ссылаются на биржу через
 * {@code Instrument.exchangeId} (docs/models/domain/core/Exchange.md).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Exchange extends Auditable {

    /** Внутренний идентификатор биржи. */
    private Long id;

    /** Межсервисный идентификатор биржи. */
    private String internalId;

    /** Уникальное имя биржи (например, OKX). */
    private String name;

    /** Базовый URL API биржи. */
    private String baseUrl;

    /** Текущий статус подключения/использования биржи. */
    private Status status;

    /**
     * <b>Живая база риска счёта</b> — делитель всех четырёх потолков у
     * сделки, чей снимок ещё не заморожен. Следует за свободным остатком
     * расчётной валюты В ОБЕ СТОРОНЫ и автоматически: ни пола, ни потолка
     * на ходу нет — защиту от отыгрыша держат холды, предел серии убытков
     * и катастрофический потолок (docs/rules/risk-policy.md).
     *
     * <p>Пусто — база ни разу не наблюдалась, и преконтроль отказывает:
     * базы риска не существует, а подставить её нечем.
     */
    private BigDecimal riskBase;

    /** Валюта базы риска — расчётная валюта контура. */
    private String riskBaseCurrency;

    /**
     * Серия убыточных исходов подряд — операнд остановки по серии
     * (docs/rules/loss-streak-halt.md).
     */
    private Integer consecutiveLossCount;

    /** Биржа заморожена реактивным safety-холдом (уровень 4, docs/rules/exchange-hold.md). */
    public Boolean isTradeBlocked() {
        return Objects.equals(status, Status.TRADE_BLOCKED);
    }

    /** Статус подключения/использования биржи. */
    public enum Status {

        /** Биржа заведена, подключение ещё не настраивалось. */
        CREATED,

        /** Идёт настройка/проверка подключения. */
        PENDING,

        /** Биржа активна, доступна для работы. */
        ACTIVE,

        /**
         * Торговля по бирже заморожена реактивным safety-холдом (уровень 4
         * error-градации, docs/rules/exchange-hold.md): каскадно блокирует
         * входы по всем инструментам биржи, safety/read разрешены. Вход —
         * только из {@code ACTIVE} по аварии; снятие — вручную в {@code ACTIVE}.
         */
        TRADE_BLOCKED,

        /** Биржа выведена из использования. */
        CLOSED,

        /** Ошибка подключения/использования. */
        ERROR
    }
}

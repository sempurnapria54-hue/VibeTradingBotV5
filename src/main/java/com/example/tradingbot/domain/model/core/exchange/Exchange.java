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

    /** Биржа стои́т в мягкой ступени: новых сделок нет, живые ведутся полностью. */
    public Boolean isSoftHeld() {
        return Objects.equals(status, Status.HOLD);
    }

    /**
     * Ступень биржи гасит НОВЫЕ входы — мягкая и жёсткая одинаково.
     * Различаются они судьбой ПРИНЯТОГО риска, а не правом набирать новый:
     * его отменяют обе (docs/rules/exchange-hold.md).
     */
    public Boolean blocksEntry() {
        return isSoftHeld() || isTradeBlocked();
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
         * Мягкая биржевая ступень (ступень 1, docs/rules/exchange-hold.md):
         * биржа выпадает из выборки входа — и всё. Командного блок-сета у
         * неё нет, живые сделки ведутся штатным FSM в полном объёме
         * (ремодел защиты, управление, закрытие). Принятый риск покрыт и
         * исполняется, под сомнением только право набирать новый.
         *
         * <p>Вход — из {@code ACTIVE} по своим триггерам (расхождение
         * сверки, серия убытков, слепота safety-сети, ручной вызов) либо
         * СПУСКОМ из {@code TRADE_BLOCKED} при снятии сворачивания. Снятие
         * — вручную в {@code ACTIVE}.
         */
        HOLD,

        /**
         * Торговля по бирже заморожена реактивным safety-холдом (уровень 4
         * error-градации, docs/rules/exchange-hold.md): каскадно блокирует
         * входы по всем инструментам биржи, safety/read разрешены. Вход —
         * из любого статуса (авария застаёт биржу в любом состоянии);
         * снятие — вручную и только в {@code HOLD}: два условия снятия
         * лестница проверяет по одному, прыжка сразу в рабочее состояние нет.
         */
        TRADE_BLOCKED,

        /** Биржа выведена из использования. */
        CLOSED,

        /** Ошибка подключения/использования. */
        ERROR
    }
}

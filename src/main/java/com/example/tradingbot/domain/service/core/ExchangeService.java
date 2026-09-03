package com.example.tradingbot.domain.service.core;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.safety.HoldClearanceGate;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Прикладная логика {@link Exchange}: заведение биржи и чтение.
 * Fetch-or-throw живёт в {@link ExchangeDataService} (codestyle:
 * getRequiredBy* — в DataService), сюда делегируется.
 */
@Service
@RequiredArgsConstructor
public class ExchangeService {

    private final ExchangeDataService exchangeDataService;
    private final HoldClearanceGate holdClearanceGate;

    /**
     * Заведение биржи. Счётчики серии убытков и слепоты стартуют нулём
     * здесь: обе колонки объявлены {@code NOT NULL}, а api-запрос их не
     * несёт — без явного писателя вставка уходила бы в источник с
     * {@code null}. Правило «таблицы пусты» снимает бэкфилл, но не
     * назначение писателя у объектов, заводимых ПОСЛЕ ввода колонки
     * (.claude/rules/pre-launch-schema-changes.md §«Чего правило НЕ
     * снимает»).
     */
    public Exchange create(Exchange exchange) {
        exchange.setStatus(Exchange.Status.CREATED);
        exchange.setConsecutiveLossCount(0);
        exchange.setBlindPassCount(0);
        return exchangeDataService.save(exchange);
    }

    public Exchange getRequiredByInternalId(String internalId) {
        return exchangeDataService.getRequiredByInternalId(internalId);
    }

    /**
     * Снятие биржевого сворачивания — <b>первый из двух ходов</b>:
     * TRADE_BLOCKED → HOLD, а не сразу в рабочее состояние. Условий снятия
     * два, и лестница проверяет их по одному: этот ход закрывает машинно
     * проверяемое («риска не осталось»), второй — суждение держателя
     * («причина понята»), см. {@link #clearHold}. Гардирована статусом; не в
     * TRADE_BLOCKED — переход не применяется (IllegalStateException → 409).
     *
     * <p><b>Предусловие «живого риска не осталось» проверяется машинно, до
     * записи статуса</b> (docs/rules/manual-halt.md): реакция сворачивания
     * best-effort по составу, и снятие поверх непогашенного риска было бы
     * разрешающей ошибкой. Выход при неподтверждённом kill-switch — повторный
     * полный вызов держателя, не этот метод.
     *
     * <p>Каскад L4 этим ходом ещё не отпускается: входы по инструментам биржи
     * остаются закрытыми мягкой ступенью, и это намеренно — торговля
     * возобновляется вторым осознанным действием, а не первым.
     */
    public Exchange unblockTrade(String internalId) {
        Exchange exchange = exchangeDataService.getRequiredByInternalId(internalId);
        if (isFalse(holdClearanceGate.riskClearedOnExchange(exchange.getId()))) {
            throw new IllegalStateException("Live risk is not proven absent on exchange: " + internalId);
        }
        if (isFalse(exchangeDataService.unblockTrade(exchange.getId()))) {
            throw new IllegalStateException("Exchange is not trade-blocked: " + internalId);
        }
        exchange.setStatus(Exchange.Status.HOLD);
        return exchange;
    }

    /**
     * Снятие мягкой биржевой ступени — <b>второй ход</b>: HOLD → ACTIVE.
     * Отпускает выборку входа по всем инструментам биржи разом.
     *
     * <p><b>Гейта живого риска здесь нет, и это не пропуск.</b> Мягкая
     * ступень принятый риск не снимала — живые сделки под ней ведутся в
     * полном объёме, — поэтому требовать «живого риска не осталось» значило
     * бы сделать ступень неснимаемой ровно в её штатном состоянии.
     * Машинно проверяемое условие энфорсится на первом ходе, у своей ступени;
     * дублировать его здесь — применить предусловие жёсткой ступени к мягкой
     * (docs/rules/exchange-hold.md §«Снятие — вручную и только в `HOLD`»,
     * docs/rules/manual-halt.md §«Предусловие «риска не осталось» — машинное,
     * а не заявляемое»).
     *
     * <p>Гард отвергает прыжок через ступень: биржа под сворачиванием этим
     * вызовом не разблокируется — сначала первый ход.
     */
    public Exchange clearHold(String internalId) {
        Exchange exchange = exchangeDataService.getRequiredByInternalId(internalId);
        if (isFalse(exchangeDataService.clearHold(exchange.getId()))) {
            throw new IllegalStateException("Exchange is not soft-held: " + internalId);
        }
        exchange.setStatus(Exchange.Status.ACTIVE);
        return exchange;
    }

    /** Резолв internalId биржи по id — проекция одного поля, не вся сущность. */
    public String getRequiredInternalIdById(Long id) {
        return exchangeDataService.getRequiredInternalIdById(id);
    }
}

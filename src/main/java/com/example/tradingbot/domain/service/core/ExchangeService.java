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

    public Exchange create(Exchange exchange) {
        exchange.setStatus(Exchange.Status.CREATED);
        return exchangeDataService.save(exchange);
    }

    public Exchange getRequiredByInternalId(String internalId) {
        return exchangeDataService.getRequiredByInternalId(internalId);
    }

    /**
     * Ручная разморозка safety-холда биржи (L4): TRADE_BLOCKED → ACTIVE. Одно
     * действие отпускает весь каскад — enforcement читает статус биржи живьём,
     * поэтому входы по всем её инструментам разблокируются разом (per-instrument
     * снятие для L4 не вводится; собственные L3-холды инструментов остаются и
     * снимаются отдельно). Гардирована статусом; не в TRADE_BLOCKED — переход не
     * применяется (IllegalStateException → 409).
     *
     * <p><b>Предусловие «живого риска не осталось» проверяется машинно, до
     * записи статуса</b> (docs/rules/manual-halt.md): реакция сворачивания
     * best-effort по составу, и снятие поверх непогашенного риска было бы
     * разрешающей ошибкой.
     *
     * <p><b>Названное ограничение:</b> снятие ведёт в рабочее состояние, а
     * не в мягкую биржевую ступень, как объявляет таблица снятия —
     * отдельного статуса мягкой ступени в модели биржи не заведено, и
     * заводить ветвь без него значило бы писать переход в несуществующее
     * состояние.
     */
    public Exchange unblockTrade(String internalId) {
        Exchange exchange = exchangeDataService.getRequiredByInternalId(internalId);
        if (isFalse(holdClearanceGate.riskClearedOnExchange(exchange.getId()))) {
            throw new IllegalStateException("Live risk is not proven absent on exchange: " + internalId);
        }
        if (isFalse(exchangeDataService.unblockTrade(exchange.getId()))) {
            throw new IllegalStateException("Exchange is not trade-blocked: " + internalId);
        }
        exchange.setStatus(Exchange.Status.ACTIVE);
        return exchange;
    }

    /** Резолв internalId биржи по id — проекция одного поля, не вся сущность. */
    public String getRequiredInternalIdById(Long id) {
        return exchangeDataService.getRequiredInternalIdById(id);
    }
}

package com.example.tradingbot.domain.command;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;

/**
 * Процессный runtime-context одного прохода FSM: runtime-картина для
 * обработки сделки (deal с graph, exchange, instrument, pinned detail,
 * последний balance snapshot, action-states). RVO, не persisted; не
 * часть доменной модели Deal. Свежие рыночные/расчётные данные сюда не
 * входят (собираются в CalculationContext). См.
 * docs/components/models/DealContext.md.
 */
@Value
@Builder
public class DealContext {

    /** Сделка с runtime graph (orders + algoOrders + position). */
    Deal deal;

    /** Биржа / exchange account (HOLD / safety / adapter context). */
    Exchange exchange;

    /** Торговый инструмент сделки. */
    Instrument instrument;

    /** Pinned-конфигурация сделки. */
    StrategyDetail strategyDetail;

    /** Последний persisted snapshot баланса (свежесть не гарантирована). */
    BalanceContainer balanceContainer;

    /** Runtime-состояние выполнения actions (recovery/retry/idempotency/target). */
    List<DealActionState> actionStates;

    /** Runtime-состояние финализационных команд (FINALIZE_* / MARK_*) сделки — recovery/retry/idempotency финализации. */
    List<DealFinalizationState> finalizationStates;

    /**
     * Строка исполнения потраншевого действия на ТЕКУЩЕМ эпизоде транша.
     * Отбор идёт тройкой «действие + транш + номер эпизода»:
     * переоткрытие ведётся тем же траншем, поэтому без номера эпизода
     * строки прошлого эпизода неотличимы от строк текущего.
     */
    public Optional<DealActionState> actionState(Long strategyActionId, DealTranche tranche) {
        if (isEmpty(actionStates) || isNull(tranche)) {
            return Optional.empty();
        }
        return actionStates.stream()
                .filter(state -> Objects.equals(strategyActionId, state.getStrategyActionId()))
                .filter(state -> Objects.equals(tranche.getId(), state.getDealTrancheId()))
                .filter(state -> Objects.equals(tranche.getEpisodeSeq(), state.getTrancheEpisodeSeq()))
                .findFirst();
    }
}

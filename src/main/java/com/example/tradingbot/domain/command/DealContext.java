package com.example.tradingbot.domain.command;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
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
     * <b>Граф сделки предъявлен целиком</b> — транши, эпизоды, ноги,
     * встроенные защиты и отдельные условные заявки. Признак кладёт сюда
     * фабрика контекста; читатели берут его ГОТОВЫМ и не пересобирают —
     * иначе каждый обязан был бы знать объём загрузки
     * (docs/spec/deal-context-load.json §graphComplete).
     *
     * <p>Ложен — писатели четвёрки чисел риска не пишут и своё звено не
     * завершают, а преконтроль отказывает fail-fast: на неполном графе
     * операнды потолков занижены, то есть ошибка направлена в
     * разрешающую сторону (docs/models/domain/aggregate/Deal.md).
     */
    Boolean graphComplete;

    /**
     * Объявление, по которому материализован транш; пусто у
     * восстановленного (объявления у него нет) и у сделки без
     * закреплённой детали.
     */
    public StrategyTranche declarationOf(DealTranche tranche) {
        if (isNull(strategyDetail) || isNull(tranche)) {
            return null;
        }
        return strategyDetail.declarationById(tranche.getStrategyTrancheId());
    }

    /**
     * Допускает ли объявление транша переоткрытие его эпизода. Признак
     * живёт на ОБЪЯВЛЕНИИ, а не на детали: сетка и одиночный вход в
     * одной фазе вправе решать это по-разному. Пусто читается как «не
     * допускает» — разрешение объявляется явно.
     */
    public Boolean reopenAllowed(DealTranche tranche) {
        StrategyTranche declaration = declarationOf(tranche);
        return nonNull(declaration) && isTrue(declaration.getPositionReopenAllowed());
    }

    /**
     * Строка исполнения действия. Отбор дискриминируется УРОВНЕМ
     * объявления, а не разными вызовами:
     *
     * <ul>
     *   <li>потраншевое — тройка «действие + транш + номер эпизода»:
     *       переоткрытие ведётся тем же траншем, поэтому без номера
     *       эпизода строки прошлого эпизода неотличимы от строк
     *       текущего;</li>
     *   <li>агрегатное (шаг узкой поверхности уровня сделки) — пара
     *       «действие + сделка»: транша у него нет ни одного, и пустой
     *       транш участвует в отборе как пустой, а не как «любой».</li>
     * </ul>
     *
     * <p>Общий вход, а не два: вызывающий держит уровень в одном
     * операнде — самом транше, — и ветвление здесь не даёт ему выбрать
     * не тот отбор.
     */
    public Optional<DealActionState> actionState(Long strategyActionId, DealTranche tranche) {
        if (isEmpty(actionStates)) {
            return Optional.empty();
        }
        return actionStates.stream()
                .filter(state -> Objects.equals(strategyActionId, state.getStrategyActionId()))
                .filter(state -> matchesLevel(state, tranche))
                .findFirst();
    }

    private boolean matchesLevel(DealActionState state, DealTranche tranche) {
        if (isNull(tranche)) {
            return isNull(state.getDealTrancheId());
        }
        return Objects.equals(tranche.getId(), state.getDealTrancheId())
                && Objects.equals(tranche.getEpisodeSeq(), state.getTrancheEpisodeSeq());
    }
}

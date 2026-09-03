package com.example.tradingbot.domain.command;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;

/**
 * Процессный runtime-context одного прохода FSM: runtime-картина для
 * обработки сделки (deal с graph, exchange, instrument, pinned detail,
 * последний balance snapshot, строки исполнения, разбивка движений). RVO,
 * не persisted; не часть доменной модели Deal. Свежие рыночные/расчётные
 * данные сюда не входят (собираются в CalculationContext). См.
 * docs/components/models/DealContext.md.
 */
@Value
@Builder
public class DealContext {

    /** Сделка с runtime graph (транши + ноги + эпизоды позиции). */
    Deal deal;

    /** Биржа / exchange account (HOLD / safety / adapter context). */
    Exchange exchange;

    /** Торговый инструмент сделки. */
    Instrument instrument;

    /** Pinned-конфигурация сделки. */
    StrategyDetail strategyDetail;

    /** Последний persisted snapshot баланса (свежесть не гарантирована). */
    BalanceContainer balanceContainer;

    /**
     * Строки исполнения сделки — стратегийные и системные вместе.
     * Список <b>изменяемый по построению</b>: строка, заведённая этим же
     * проходом, обязана быть видна анкеру команды, а контекст собран до
     * неё ({@link #register(DealActionState)}).
     */
    List<DealActionState> actionStates;

    /**
     * Строки разбивки движений средств сделки — левые стороны пар сверки
     * и слагаемое чужой валюты в итоговом числе
     * (docs/spec/deal-result.json).
     */
    List<DealCashFlow> cashFlows;

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
     * <b>Разбивка движений добыта И предъявлена целиком</b>
     * (docs/spec/deal-context-load.json §flowsComplete). Конъюнкта
     * полноты графа не содержит: строки разбивки в операнды четвёрки
     * чисел риска не входят, и упёршаяся в потолок выборка пересчёт
     * четвёрки не запрещает.
     */
    Boolean flowsComplete;

    /**
     * Явный конструктор — ради изменяемости списка строк исполнения:
     * строку, заведённую этим проходом, регистрирует
     * {@link #register(DealActionState)}, и вызывающая сторона не обязана
     * подавать сюда именно изменяемый список.
     */
    private DealContext(Deal deal, Exchange exchange, Instrument instrument, StrategyDetail strategyDetail,
                        BalanceContainer balanceContainer, List<DealActionState> actionStates,
                        List<DealCashFlow> cashFlows, Boolean graphComplete, Boolean flowsComplete) {
        this.deal = deal;
        this.exchange = exchange;
        this.instrument = instrument;
        this.strategyDetail = strategyDetail;
        this.balanceContainer = balanceContainer;
        this.actionStates = new ArrayList<>(emptyIfNull(actionStates));
        this.cashFlows = new ArrayList<>(emptyIfNull(cashFlows));
        this.graphComplete = graphComplete;
        this.flowsComplete = flowsComplete;
    }

    /**
     * Зарегистрировать строку исполнения, заведённую этим проходом.
     * Контекст собирается до неё, а анкер команды обязан резолвиться
     * сразу: без регистрации два запроса одного системного действия за
     * проход завели бы две живые строки и столкнулись бы на частичном
     * ключе.
     */
    public void register(DealActionState state) {
        if (nonNull(state) && isFalse(actionStates.contains(state))) {
            actionStates.add(state);
        }
    }

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
     * Строка исполнения СТРАТЕГИЙНОГО действия. Отбор дискриминируется
     * УРОВНЕМ объявления, а не разными вызовами:
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
                .filter(state -> isFalse(state.isSystem()))
                .filter(state -> Objects.equals(strategyActionId, state.getStrategyActionId()))
                .filter(state -> matchesLevel(state, tranche))
                .findFirst();
    }

    /**
     * ЖИВАЯ строка исполнения системного действия названного типа и
     * уровня. Завершённые и отказавшие строки живыми не считаются: новая
     * надобность заводит новую строку
     * (docs/components/SystemActionExecutor.md).
     */
    public Optional<DealActionState> liveSystemActionState(SystemActionType type, DealTranche tranche) {
        return actionStates.stream()
                .filter(state -> isTrue(state.isSystem()))
                .filter(state -> Objects.equals(type, state.getSystemActionType()))
                .filter(state -> matchesLevel(state, tranche))
                .filter(state -> isTrue(state.isLive()))
                .findFirst();
    }

    /** Строки исполнения системного действия названного типа — любых статусов и уровней. */
    public List<DealActionState> systemActionStates(SystemActionType type) {
        return actionStates.stream()
                .filter(state -> isTrue(state.isSystem()))
                .filter(state -> Objects.equals(type, state.getSystemActionType()))
                .toList();
    }

    private boolean matchesLevel(DealActionState state, DealTranche tranche) {
        if (isNull(tranche)) {
            return isNull(state.getDealTrancheId());
        }
        return Objects.equals(tranche.getId(), state.getDealTrancheId())
                && Objects.equals(tranche.getEpisodeSeq(), state.getTrancheEpisodeSeq());
    }
}

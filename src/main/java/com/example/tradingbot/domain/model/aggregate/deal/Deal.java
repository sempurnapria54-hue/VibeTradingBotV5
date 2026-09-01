package com.example.tradingbot.domain.model.aggregate.deal;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Сделка — lifecycle root и runtime graph торговой сделки: что система
 * сопровождает сценарий по конкретному Instrument, по pinned
 * StrategyDetail, в ожидаемом направлении, с FSM-статусом, причинами и
 * итоговым PnL. Не биржевая сущность (нет external id/status). Логика
 * финализации PnL (resultProfit через REFRESH_FILLS) — шаг 7; здесь —
 * структура. Связь с DealActionState — не поле Deal (через
 * Deal.id → DealActionState.dealId). См.
 * docs/models/domain/aggregate/Deal.md, docs/lifecycles/Deal.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class Deal extends Auditable {

    /** Внутренний идентификатор в БД. */
    private Long id;

    /** Безопасный внешний/межсервисный id (API, логи, timeline). */
    private String internalId;

    /** Инструмент (полный Instrument — в DealContext). */
    private Long instrumentId;

    /** Pinned StrategyDetail: открытая сделка ведётся по этой версии даже при изменении Strategy. */
    private Long strategyDetailId;

    /** FSM-статус сделки. */
    private Status status;

    /** Expected direction, фиксируется при создании; Position.direction должен ему соответствовать. */
    private StrategyTradeDirection direction;

    /** Короткая причина создания (не управляет FSM). */
    private EntryReason entryReason;

    /** Тип entry-step (не управляет FSM). */
    private EntryStepType entryStepType;

    /**
     * Фаза рынка при входе сделки. Write-once, пишет DealOpeningService
     * на тропе входа по объявлению той же транзакцией, что заводит
     * сделку. Пусто ровно у восстановленной сделки — входа по
     * объявлению у неё не было.
     */
    private MarketPhase.Type entryMarketPhase;

    /** Причина graceful shutdown / controlled close (если запущен). Не заменяет closeReason. */
    private ShutdownReason shutdownReason;

    /** Итоговая бизнес-причина завершения. */
    private CloseReason closeReason;

    /** Итоговый PnL (для terminal CLOSED/EMERGENCY_CLOSED обязателен; считается через REFRESH_FILLS). */
    private BigDecimal resultProfit;

    /** Валюта результата (для ETH-USDT-SWAP обычно USDT). */
    private String resultProfitCurrency;

    /**
     * Порог доказанного покрытия — до какого момента наблюдался факт
     * закрытия эпизода. В предикате линковки движений НЕ участвует:
     * сверху окно открыто до времени источника прохода. Пишет
     * наблюдатель факта закрытия эпизода, монотонно вперёд; пусто —
     * закрытие не добыто ни у одного эпизода. Потребитель —
     * обязанность сверки (docs/models/domain/aggregate/Deal.md).
     */
    private OffsetDateTime coverageProvenThrough;

    /** Ordinary orders сделки (attached protection — внутри Order). */
    private List<Order> orders;

    /** Standalone algo-orders сделки. */
    private List<AlgoOrder> algoOrders;

    /**
     * Эпизоды позиции: одна биржевая позиция — одна строка. Живой
     * эпизод не более одного, закрытые остаются.
     */
    private List<Position> positions;

    /**
     * Транши сделки — единицы принятия и сопровождения риска. Стадии
     * входа и сопровождения принадлежат им, а не сделке.
     */
    private List<DealTranche> tranches;

    /** Все транши сделки терминальны — предусловие терминала сделки. */
    public Boolean allTranchesTerminal() {
        return emptyIfNull(tranches).stream()
                .allMatch(tranche -> isTrue(tranche.isTerminal()));
    }

    /** Хоть один транш сделки несёт живой риск. */
    public Boolean anyTrancheRiskBearing() {
        return emptyIfNull(tranches).stream()
                .anyMatch(tranche -> isTrue(tranche.isRiskBearing()));
    }

    /**
     * Риск ВСЕХ траншей сделки покрыт защитой (docs/spec/protection-coverage.json,
     * величина {@code allTranchesCovered}) — агрегатный аналог покрытия транша.
     * Терминальный транш экспозиции не несёт и покрыт тривиально, поэтому
     * перечень не сужается до живых: сужение меняло бы ответ только там, где
     * покрывать нечего.
     */
    public Boolean allTranchesCovered() {
        return emptyIfNull(tranches).stream()
                .allMatch(tranche -> isTrue(tranche.isCovered()));
    }

    /**
     * Уровень защиты на всю позицию не резолвится: хоть один транш с
     * экспозицией не несёт своего уровня (docs/spec/protection-coverage.json,
     * величина {@code dealStopUnresolved}).
     */
    public Boolean stopUnresolved() {
        return emptyIfNull(tranches).stream()
                .anyMatch(tranche -> isTrue(tranche.stopUnresolved()));
    }

    /** Живые транши сделки: те, что ещё занимают место в проходе. */
    public List<DealTranche> liveTranches() {
        return emptyIfNull(tranches).stream()
                .filter(tranche -> isTrue(tranche.isActive()))
                .collect(Collectors.toList());
    }

    /** Live ordinary orders сделки (остаточный live-risk для teardown); пусто — нет. */
    public List<Order> liveOrders() {
        return emptyIfNull(orders).stream()
                .filter(order -> isTrue(order.isLive()))
                .collect(Collectors.toList());
    }

    /** Live standalone algo-orders сделки (остаточный live-risk для teardown); пусто — нет. */
    public List<AlgoOrder> liveAlgoOrders() {
        return emptyIfNull(algoOrders).stream()
                .filter(algoOrder -> isTrue(algoOrder.isLive()))
                .collect(Collectors.toList());
    }

    /** Живой эпизод позиции сделки либо пусто. */
    public Position livePosition() {
        return emptyIfNull(positions).stream()
                .filter(episode -> isTrue(episode.hasLiveRisk()) || Position.Status.ACTIVE == episode.getStatus())
                .findFirst()
                .orElse(null);
    }

    /** Позиция сделки несёт live market risk (есть и в статусе с живым риском). */
    public Boolean hasLivePositionRisk() {
        Position live = livePosition();
        return nonNull(live) && isTrue(live.hasLiveRisk());
    }

    /** Эпизоды, ждущие положения закрытия: строка закрыта и записи закрытия не несёт. */
    public List<Position> episodesAwaitingCloseRecord() {
        return emptyIfNull(positions).stream()
                .filter(episode -> isTrue(episode.awaitsCloseRecord()))
                .collect(Collectors.toList());
    }

    /**
     * Позиция по сделке наблюдалась: вход исполнялся либо сделка
     * заведена восстановлением уже существующего живого риска. Два
     * дизъюнкта несущие по отдельности — восстановленная сделка своих
     * исполненных ног не имеет, а исполнившаяся стратегийная не имеет
     * RECOVERY (docs/spec/deal-context-load.json, positionObserved).
     */
    public Boolean positionObserved() {
        return EntryReason.RECOVERY == entryReason
                || emptyIfNull(tranches).stream().anyMatch(tranche -> isTrue(tranche.hasEntryFill()));
    }

    /**
     * Порог доказанного покрытия двигается ТОЛЬКО вперёд: число
     * наблюдений равно числу закрывшихся эпизодов, и порог обязан
     * накрывать движения всех.
     */
    public void advanceCoverageProvenThrough(OffsetDateTime observedAt) {
        if (nonNull(observedAt) && (isNull(coverageProvenThrough) || coverageProvenThrough.isBefore(observedAt))) {
            coverageProvenThrough = observedAt;
        }
    }

    /**
     * FSM-статус сделки: бизнес-этап, не статус Order/AlgoOrder/Position
     * и не exchange ACK. Значения, группы и переходы —
     * docs/lifecycles/Deal.md.
     */
    public enum Status {

        /**
         * Сделка ведётся: транши живут своими жизненными циклами внутри
         * неё. Стадии входа и сопровождения принадлежат ТРАНШУ, не сделке
         * ({@link DealTranche.Status}) — сделка их не повторяет.
         */
        ACTIVE,

        /** Запущено сворачивание — ждём завершения закрывающих действий. */
        EXIT_PENDING,

        /** Ошибка цикла сделки. */
        ERROR,

        /** Сделка завершена штатно. */
        CLOSED,

        /** Сделка завершена аварийно (emergency close). */
        EMERGENCY_CLOSED
    }

    /** Причина создания сделки (не управляет FSM). */
    public enum EntryReason {

        /** Создана EntryScannerJob по условиям. */
        STRATEGY,

        /** Создана вручную. */
        MANUAL,

        /** Восстановление существующего runtime risk. */
        RECOVERY,

        /** Fallback, не для normal flow. */
        UNKNOWN
    }

    /** Тип entry-step сделки (или null, если создана не через strategy entry-step). */
    public enum EntryStepType {

        /** Обычный вход. */
        ENTRY,

        /** Вход уровнем grid-сетки. */
        GRID_ENTRY
    }

    /** Причина запуска graceful shutdown / controlled close. Не заменяет closeReason. */
    public enum ShutdownReason {

        /** Стратегия удалена. */
        STRATEGY_DELETED,

        /** Устаревание рыночных данных (только если policy решила завершать сделку). */
        MARKET_DATA_EXPIRED,

        /** Ручная остановка. */
        MANUAL_STOP,

        /** Risk-policy. */
        RISK_POLICY,

        /** Exchange hold. */
        EXCHANGE_HOLD,

        /** Fallback. */
        UNKNOWN
    }

    /** Итоговая бизнес-причина завершения сделки (не технический механизм закрытия). */
    public enum CloseReason {

        /** Candidate закрыт в PRECHECK до live risk. */
        ENTRY_CONDITION_EXPIRED,

        /** Штатный выход по стратегии. */
        STRATEGY_EXIT,

        /** Take-profit. */
        TAKE_PROFIT,

        /** Stop-loss (включая fixed и trailing; механизм — в Order/AlgoOrder/DealActionState/audit). */
        STOP_LOSS,

        /** Time stop. */
        TIME_STOP,

        /** Штатное risk-control завершение (включая risk-block в PRECHECK). */
        RISK_CONTROL,

        /** Ручное закрытие. */
        MANUAL_CLOSE,

        /** Аварийное закрытие (только для EMERGENCY_CLOSED). */
        EMERGENCY_CLOSE,

        /** Fallback. */
        UNKNOWN
    }
}

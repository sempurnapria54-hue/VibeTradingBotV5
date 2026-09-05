package com.example.tradingbot.domain.model.aggregate.deal;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Сделка — lifecycle root и runtime graph торговой сделки: что система
 * сопровождает сценарий по конкретному Instrument, по pinned
 * StrategyDetail, в ожидаемом направлении, с FSM-статусом, причинами и
 * итоговым PnL. Не биржевая сущность (нет external id/status). Итоговый результат
 * считается разбивкой движений средств (docs/rules/pnl-reconciliation.md). Связь с DealActionState — не поле Deal (через
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

    /** Итоговый PnL (для terminal CLOSED/EMERGENCY_CLOSED обязателен; считается по движениям средств). */
    private BigDecimal resultProfit;

    /** Валюта результата (для ETH-USDT-SWAP обычно USDT). */
    private String resultProfitCurrency;

    /**
     * <b>Риск, принятый сделкой на входах ({@code R})</b> — заявленный
     * риск живых и исполнившихся входных ног всех траншей плюс налитая
     * доля выбывших. Знаменатель R-мультипликатора и операнд
     * кумулятивного потолка. Производная проекция: пересчитывается
     * ЦЕЛИКОМ (docs/spec/deal-risk-numbers.json §dealPlannedRisk).
     */
    private BigDecimal plannedRiskAmount;

    /**
     * <b>Взятый на входе риск</b> — сколько из заявленного встало под
     * удар. Измеритель разрыва «заявлено ↔ взято»; частичным выходом не
     * уменьшается. Производная проекция.
     */
    private BigDecimal incurredRiskAmount;

    /**
     * <b>Неотработанная доля взятого на входе риска.</b> Уменьшается
     * частичным выходом, при полном — ноль. Риском «под ударом сейчас»
     * не является: уровня стопа формула не содержит. Производная
     * проекция.
     */
    private BigDecimal currentRiskAmount;

    /**
     * <b>Риск, снятый защитой</b> — насколько действующий стоп уменьшил
     * взятый риск. Наблюдение, не операнд потолков; знак не клэмпится.
     * Производная проекция.
     */
    private BigDecimal protectionRelievedRiskAmount;

    /**
     * Валюта всех чисел риска сделки. Источник — расчётная валюта
     * инструмента; у всех траншей она одна.
     */
    private String plannedRiskCurrency;

    /**
     * <b>База риска на момент сайзинга. Write-once</b>: фиксируется
     * ПЕРВЫМ сайзингом сделки, каким бы траншем он ни делался.
     * Потребители — знаменатель фактического процента риска в
     * отчётности и делитель всех четырёх потолков живой сделки. Пусто =
     * сайзинга не было (позиция создана вне приложения).
     */
    private BigDecimal plannedRiskEquityBase;

    /**
     * Порог доказанного покрытия — до какого момента наблюдался факт
     * закрытия эпизода. В предикате линковки движений НЕ участвует:
     * сверху окно открыто до времени источника прохода. Пишет
     * наблюдатель факта закрытия эпизода, монотонно вперёд; пусто —
     * закрытие не добыто ни у одного эпизода. Потребитель —
     * обязанность сверки (docs/models/domain/aggregate/Deal.md).
     */
    private OffsetDateTime coverageProvenThrough;

    /**
     * Нижняя граница окна линковки движений. Единственный писатель —
     * SubmitOrderExecutor, по биржевому времени создания первой
     * отправленной входной заявки сделки, каким бы траншем она ни
     * ставилась; write-once. Пусто — граница не добыта, предикат
     * линковки берёт суррогат из externalCreatedAt
     * (docs/spec/cash-flow-linkage.json §lowerBound).
     */
    private OffsetDateTime billsWindowBegin;

    /**
     * До какого момента движения средств добыты. Пишет
     * RefreshBillsExecutor, монотонно вперёд. Единственный durable-факт
     * «добыча выполнялась»: пусто = не добывали, а не «добыли, движений
     * нет» (docs/models/domain/aggregate/Deal.md).
     */
    private OffsetDateTime billsFetchedThrough;

    /**
     * <b>Торговый исход закрытия</b> — признак отбора для отчёта.
     * Отличается от {@link #closeReason} намеренно: та — бизнес-причина
     * завершения сделки, этот — рыночный факт того, кто и почему закрыл
     * позицию (docs/spec/position-close-outcome.json).
     */
    private CloseOutcome closeOutcome;

    /**
     * <b>Исход сверки разбивки</b> — признак отбора. Свойство числа, а
     * не членства: сделка остаётся в популяции при любом значении
     * (docs/spec/pnl-reconciliation.json).
     */
    private ReconciliationStatus reconciliationStatus;

    /**
     * <b>Полнота разбивки</b> — признак отбора: накрыло ли окно добычи
     * движений всю жизнь сделки (docs/components/RefreshBillsExecutor.md).
     */
    private BreakdownCompleteness breakdownIncomplete;

    /**
     * <b>Почему знаменатель {@code R} пуст или непуст</b> — признак
     * отбора. {@code MISSING} есть аномалия с отчётом: вход был, а
     * знаменателя нет (docs/spec/deal-lifecycle.json
     * §benchmarkAvailabilityOnTerminal).
     */
    private RiskBenchmarkAvailability riskBenchmarkAvailability;

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

    /**
     * Сделка в терминальном статусе: слот инструмента не держит и
     * движений счёта больше не принимает (операнд предиката линковки —
     * docs/spec/cash-flow-linkage.json §linksToDeal). ERROR терминалом
     * не является — обработка аварийной тропы ещё идёт.
     */
    public Boolean isTerminal() {
        return Objects.equals(Status.CLOSED, status) || Objects.equals(Status.EMERGENCY_CLOSED, status);
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

    /**
     * Живые ВСТРОЕННЫЕ защиты сделки — остаточный live-risk для teardown
     * наравне с отдельными условными заявками.
     *
     * <p>Перечень идёт по <b>всем</b> заявкам, а не по живым: встроенная
     * защита материализуется самостоятельной заявкой на бирже при
     * непустом наливе родителя (`docs/models/domain/core/Order.md`
     * §«Встроенная защита»), то есть переживает терминал родителя. Обход
     * по живым родителям пропустил бы ровно тот случай, ради которого
     * перечень заведён.
     */
    public List<AttachedAlgoOrder> liveAttachedProtections() {
        return emptyIfNull(orders).stream()
                .flatMap(order -> emptyIfNull(order.getAttachedAlgoOrders()).stream())
                .filter(protection -> isTrue(protection.isActiveLike()))
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
     * Причина закрытия сделки по СТАРШИНСТВУ причин её траншей: берётся
     * старшая (наименьший ранг) среди закрывшихся
     * (docs/spec/deal-lifecycle.json §trancheCloseReasonRank,
     * §dealCloseReasonBySeniority). Пусто — ни один транш причины не
     * назвал, и подставлять благоприятную нечем.
     *
     * <p>Порядок — не порядок закрытия: транш, закрывшийся первым, не
     * получает этим старшинства. Ранг у причин один на модель, и здесь он
     * читается, а не переизобретается.
     */
    public CloseReason closeReasonBySeniority() {
        return emptyIfNull(tranches).stream()
                .map(DealTranche::getCloseReason)
                .filter(Objects::nonNull)
                .min(java.util.Comparator.comparingInt(Deal::trancheReasonRank))
                .map(Deal::toDealReason)
                .orElse(null);
    }

    /** Ранг причины транша: 1 — старшая. Значение вне перечня старшинства рангу не подлежит. */
    private static int trancheReasonRank(DealTranche.CloseReason reason) {
        return switch (reason) {
            case EXTERNAL_CLOSE -> 1;
            case RISK_CONTROL -> 2;
            case STOP_LOSS -> 3;
            case TIME_STOP -> 4;
            case STRATEGY_EXIT -> 5;
            case TAKE_PROFIT -> 6;
            case ENTRY_CONDITION_EXPIRED -> 7;
            default -> 99;
        };
    }

    /** Причина транша в перечне сделки; перечни пересекаются по этим семи значениям. */
    private static CloseReason toDealReason(DealTranche.CloseReason reason) {
        return switch (reason) {
            case EXTERNAL_CLOSE -> CloseReason.EXTERNAL_CLOSE;
            case RISK_CONTROL -> CloseReason.RISK_CONTROL;
            case STOP_LOSS -> CloseReason.STOP_LOSS;
            case TIME_STOP -> CloseReason.TIME_STOP;
            case STRATEGY_EXIT -> CloseReason.STRATEGY_EXIT;
            case TAKE_PROFIT -> CloseReason.TAKE_PROFIT;
            case ENTRY_CONDITION_EXPIRED -> CloseReason.ENTRY_CONDITION_EXPIRED;
            default -> null;
        };
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

        /** Восстановление существующего runtime risk. */
        RECOVERY
    }

    /** Причина запуска graceful shutdown / controlled close. Не заменяет closeReason. */
    public enum ShutdownReason {

        /** Стратегия удалена. */
        STRATEGY_DELETED,

        /** Устаревание рыночных данных (только если policy решила завершать сделку). */
        MARKET_DATA_EXPIRED,

        /** Risk-policy. */
        RISK_POLICY,

        /** Exchange hold. */
        EXCHANGE_HOLD
    }

    /**
     * Торговый исход закрытия позиции сделки — рыночный факт, а не
     * бизнес-причина. Резолв и старшинство по эпизодам —
     * docs/models/mapping/PositionCloseResult.md.
     */
    public enum CloseOutcome {

        /** Позицию закрыли мы либо биржевое событие вне ликвидационного контура. */
        NORMAL_EXIT,

        /** Принудительное закрытие по марже. */
        LIQUIDATION,

        /** Принудительное сокращение. */
        FORCED_REDUCTION,

        /**
         * Торговый исход <b>не установлен</b> — значение, а не пустота:
         * сделка остаётся в популяции и счётна отдельной корзиной.
         */
        UNDETERMINED
    }

    /**
     * Исход сверки разбивки движений с записями закрытия эпизодов.
     * Третьего значения нет: различение «были обязаны и не посчитали»
     * несёт терминальный статус, а не признак.
     */
    public enum ReconciliationStatus {

        /** Сверка не была обязана: хотя бы один конъюнкт обязанности не выполнен. */
        NOT_RUN,

        /** Общее расхождение по четырём парам в пределах допуска. */
        MATCHED,

        /** Общее расхождение сверх допуска. */
        MISMATCHED
    }

    /** Полнота разбивки движений: накрыло ли окно добычи всю жизнь сделки. */
    public enum BreakdownCompleteness {

        /** Возраст нижней границы окна на момент добычи не превышает глубины доступности движений. */
        COMPLETE,

        /** Превышает: часть движений старше доступной глубины и в разбивку не попала. */
        INCOMPLETE_BY_WINDOW,

        /** Сравнение не выполнялось — добыча движений не выполнялась (единственный триггер). */
        NOT_ASSESSED
    }

    /** Почему знаменатель {@code R} пуст или непуст. */
    public enum RiskBenchmarkAvailability {

        /** Знаменатель определён. */
        AVAILABLE,

        /** Входа не было, поэтому знаменателя нет ПО ПОСТРОЕНИЮ — нормальная популяция. */
        NOT_APPLICABLE,

        /** Вход был, а знаменателя нет — аномалия с отчётом. */
        MISSING
    }

    /** Итоговая бизнес-причина завершения сделки (не технический механизм закрытия). */
    public enum CloseReason {

        /**
         * Позицию закрыл кто-то извне системы. Клетка ребра
         * {@code EXIT_PENDING → CLOSED} без инициатора выхода закрывается
         * им; значение СТАРШЕЕ в порядке старшинства причин траншей
         * (docs/spec/deal-lifecycle.json §trancheCloseReasonRank).
         */
        EXTERNAL_CLOSE,

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

        /** Аварийное закрытие (только для EMERGENCY_CLOSED). */
        EMERGENCY_CLOSE
    }
}

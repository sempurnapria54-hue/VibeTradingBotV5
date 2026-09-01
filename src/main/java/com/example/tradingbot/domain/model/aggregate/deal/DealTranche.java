package com.example.tradingbot.domain.model.aggregate.deal;

import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Транш сделки — единица принятия и сопровождения риска внутри одной
 * сделки: собственный вход, собственная защита, собственный жизненный
 * цикл. Сделка агрегирует транши; экспозиция сделки есть сумма экспозиций
 * траншей. Не биржевая сущность.
 *
 * <p>Транш переоткрывается тем же объектом: эпизод различается номером
 * {@code episodeSeq}, а не новой строкой. Поэтому всё, что «однократно на
 * эпизоде», отбирается парой «транш + номер эпизода», а не одним траншем.
 *
 * <p>См. docs/models/domain/aggregate/DealTranche.md,
 * docs/lifecycles/DealTranche.md, docs/spec/deal-tranche-lifecycle.json.
 */
@Getter
@Setter
@NoArgsConstructor
public class DealTranche extends Auditable {

    /** Внутренний идентификатор в БД. */
    private Long id;

    /** Безопасный внешний/межсервисный id (API, логи, timeline). */
    private String internalId;

    /** Сделка-агрегат, которой принадлежит транш. */
    private Long dealId;

    /** FSM-статус транша. */
    private Status status;

    /**
     * Номер эпизода транша: растёт при переоткрытии. Операнд области
     * «эпизод» — без него строки прошлого эпизода неотличимы от строк
     * текущего, потому что переоткрытие идёт тем же траншем.
     */
    private Integer episodeSeq;

    /** Ordinary orders транша (attached protection — внутри Order). */
    private List<Order> orders;

    /** Standalone algo-orders транша. */
    private List<AlgoOrder> algoOrders;

    /** Суммарный объём входных исполнений транша. */
    private BigDecimal entryFilled;

    /** Суммарный объём reduce-only исполнений транша. */
    private BigDecimal reduceOnlyFilled;

    /** Суммарный объём, закрытый сработавшей защитой транша. */
    private BigDecimal protectionClosed;

    /**
     * Объём закрывающего исполнения уровня сделки, приписанный этому
     * траншу правилом сопоставления. Четвёртое слагаемое экспозиции;
     * отдельным полем не хранится, приходит расчётом контекста прохода.
     */
    private BigDecimal closeAttributed;

    /**
     * По траншу состоялось входное исполнение. Дизъюнкт признака
     * «позиция по сделке наблюдалась» (docs/spec/deal-context-load.json,
     * positionObserved): его вторая половина — ярлык восстановительной
     * тропы, и порознь они популяцию не исчерпывают.
     */
    public Boolean hasEntryFill() {
        return zeroIfNull(entryFilled).signum() > 0;
    }

    /** Транш терминален: дальше по жизненному циклу он не идёт. */
    public Boolean isTerminal() {
        return Status.CLOSED.equals(status);
    }

    /** Транш активен: занимает место в проходе и может нести риск. */
    public Boolean isActive() {
        return nonNull(status) && !isTrue(isTerminal());
    }

    /**
     * Экспозиция транша ДО атрибуции закрывающих исполнений уровня сделки
     * — первые три слагаемых формулы дома.
     */
    public BigDecimal grossExposure() {
        return zeroIfNull(entryFilled)
                .subtract(zeroIfNull(reduceOnlyFilled))
                .subtract(zeroIfNull(protectionClosed));
    }

    /**
     * Экспозиция транша — производная его собственных заявок и
     * приписанного ему закрывающего исполнения уровня сделки, а не
     * накопитель: порядок событий на неё не влияет.
     */
    public BigDecimal exposure() {
        return grossExposure().subtract(zeroIfNull(closeAttributed));
    }

    /** Live ordinary orders транша; пусто — нет. */
    public List<Order> liveOrders() {
        return emptyIfNull(orders).stream()
                .filter(order -> isTrue(order.isLive()))
                .collect(Collectors.toList());
    }

    /** Live standalone algo-orders транша; пусто — нет. */
    public List<AlgoOrder> liveAlgoOrders() {
        return emptyIfNull(algoOrders).stream()
                .filter(algoOrder -> isTrue(algoOrder.isLive()))
                .collect(Collectors.toList());
    }

    /**
     * У транша есть живая входная нога — живая заявка, которая НЕ только
     * уменьшает позицию. Признак входа берётся у доменного намерения
     * заявки ({@code positionReducingOnly}), а не у её типа: reduce-only
     * ногу от входной отличает именно оно.
     */
    public Boolean hasLiveEntryOrder() {
        return emptyIfNull(orders).stream()
                .anyMatch(order -> isTrue(order.isLive())
                        && isFalse(order.getPositionReducingOnly()));
    }

    /**
     * У транша есть живая защита — встроенная либо отдельная
     * (docs/spec/protection-coverage.json, величина
     * {@code trancheHasLiveProtection}). Живость читается по носителю, и
     * уровень остановки убытка здесь не спрашивается: предикат отвечает
     * «защита существует», а не «защита покрывает».
     */
    public Boolean hasLiveProtection() {
        boolean attached = emptyIfNull(orders).stream()
                .flatMap(order -> emptyIfNull(order.getAttachedAlgoOrders()).stream())
                .anyMatch(protection -> isTrue(protection.isActiveLike()));
        return attached || emptyIfNull(algoOrders).stream().anyMatch(algo -> isTrue(algo.isExchangeLive()));
    }

    /**
     * Покрытие транша ПОСЛЕ того, как снимаемая отдельная защита
     * исчезнет (docs/spec/protection-coverage.json, величина
     * {@code coverageAfterRemoval}) — операнд преконтроля снятия. Сумма
     * идёт по живым защитам транша, несущим действующий уровень
     * остановки убытка; защиты соседних траншей в неё не входят по
     * построению.
     */
    public BigDecimal coverageWithoutAlgoOrder(Long algoOrderId) {
        return coverageWithout(algoOrderId);
    }

    /**
     * Снятие отдельной защиты законно: остающееся покрытие не ниже
     * экспозиции транша (величина {@code removalAllowed}).
     *
     * <p>{@code null} — предикат ОТКАЗЫВАЕТ ВЫЧИСЛЕНИЕМ: живая защита
     * при нуле предъявленных заявок означает «предъявлено не всё», а не
     * «заявок нет». Экспозиция считается по заявкам транша и в этом
     * состоянии схлопывается в ноль, а сравнение с нулём разрешило бы
     * снятие последней защиты над живой экспозицией.
     */
    public Boolean removalAllowed(Long algoOrderId) {
        if (isTrue(hasLiveProtection()) && isEmpty(orders)) {
            return null;
        }
        return coverageWithoutAlgoOrder(algoOrderId).compareTo(exposure()) >= 0;
    }

    /** Сумма покрытий защит транша, кроме отдельной защиты с указанным id. */
    private BigDecimal coverageWithout(Long excludedAlgoOrderId) {
        BigDecimal attached = emptyIfNull(orders).stream()
                .map(this::attachedCoverageOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal standalone = emptyIfNull(algoOrders).stream()
                .filter(algo -> isFalse(Objects.equals(excludedAlgoOrderId, algo.getId())))
                .filter(algo -> isTrue(algo.isExchangeLive()) && isTrue(algo.carriesActiveStopLevel()))
                .map(AlgoOrder::coveredSize)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return attached.add(standalone);
    }

    /** Покрытие встроенными защитами одной заявки: каждая — не больше её налива. */
    private BigDecimal attachedCoverageOf(Order order) {
        return emptyIfNull(order.getAttachedAlgoOrders()).stream()
                .filter(protection -> isTrue(protection.isActiveLike()))
                .map(protection -> protection.coveredSize(order.getAccumulatedFillSize()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Транш несёт живой риск: положительная экспозиция либо живая заявка
     * любого рода. Предикат читает только собственные данные транша.
     */
    public Boolean isRiskBearing() {
        return exposure().signum() > 0
                || !liveOrders().isEmpty()
                || !liveAlgoOrders().isEmpty();
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return nonNull(value) ? value : BigDecimal.ZERO;
    }

    /**
     * FSM-статус транша: бизнес-этап принятия и сопровождения риска.
     * Значения и переходы — docs/lifecycles/DealTranche.md, исполнимая
     * форма матрицы рёбер — docs/spec/deal-tranche-lifecycle.json.
     */
    public enum Status {

        /** Кандидат: повторная проверка условий входа до живого риска. */
        PRECHECK,

        /** Входная заявка транша отправлена на биржу. */
        ENTRY_SUBMITTED,

        /** Входная заявка финализирована — позиция транша есть. */
        ENTRY_FINALIZED,

        /** Защита транша переключена с attached на standalone. */
        PROTECTION_SWITCHED,

        /** Сопровождение открытой экспозиции транша. */
        MANAGING,

        /** Запущен выход транша — ждём завершения закрывающих действий. */
        EXIT_PENDING,

        /** Транш завершён: экспозиции нет, риска не несёт. */
        CLOSED
    }

    /**
     * Итоговая бизнес-причина завершения транша. Старшинство причин при
     * сведе́нии к причине сделки — docs/spec/deal-lifecycle.json
     * §trancheCloseReasonRank.
     */
    public enum CloseReason {

        /** Позицию транша закрыл кто-то извне системы. */
        EXTERNAL_CLOSE,

        /** Штатное risk-control завершение. */
        RISK_CONTROL,

        /** Сработал стоп-лосс транша. */
        STOP_LOSS,

        /** Сработал временной стоп. */
        TIME_STOP,

        /** Штатный выход по стратегии. */
        STRATEGY_EXIT,

        /** Сработал тейк-профит. */
        TAKE_PROFIT,

        /** Кандидат закрыт до живого риска: условие входа истекло. */
        ENTRY_CONDITION_EXPIRED,

        /** Fallback. */
        UNKNOWN
    }
}

package com.example.tradingbot.domain.command.risk;

import static java.math.BigDecimal.ZERO;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.util.DomainMath;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.util.Constants;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * Четвёрка чисел риска сделки — исполнимая форма
 * docs/spec/deal-risk-numbers.json. Чистый расчёт: ничего не читает из
 * базы и ничего не пишет — писатели зовут его и кладут результат сами.
 *
 * <p><b>Все четыре — производные проекций заявок ВСЕХ траншей</b>, а не
 * накопители: группировка по траншам их не сдвигает, область отбора —
 * входные ноги сделки, чьи бы транши их ни держали. Отсюда и пересчёт
 * ЦЕЛИКОМ: частичный оставлял бы соседние числа посчитанными по прежнему
 * графу.
 *
 * <p><b>Отбор идёт по бизнес-типу ноги, а доля — по её состоянию.</b>
 * Живая и исполнившаяся входят заявленным риском целиком, выбывшая
 * (снятая, {@code ERROR}) — только налитой долей: довод «снятая заявка не
 * стояла» верен для её НЕИСПОЛНЕННОЙ доли и неверен для налитой — за
 * налитую сделка рисковала и продолжает рисковать.
 */
public final class DealRiskNumbers {

    /** Живая либо исполнившаяся: всякая снятая выпадает независимо от причины снятия. */
    private static final Set<Order.Status> LIVE_OR_COMPLETED = EnumSet.of(
            Order.Status.CREATED, Order.Status.PENDING, Order.Status.ACTIVE,
            Order.Status.PARTIALLY_COMPLETED, Order.Status.COMPLETED);

    private DealRiskNumbers() {
    }

    /**
     * Посчитать четвёрку по графу сделки. Уровень действующей защиты
     * приходит резолвом покрытия транша: у каждой ноги он свой —
     * наименее благоприятный среди защит ЕЁ транша.
     */
    public static Numbers compute(Deal deal) {
        List<Order> entryLegs = entryLegs(deal);
        Position live = deal.livePosition();
        Long liveEpisodeId = nonNull(live) ? live.getId() : null;

        BigDecimal plannedRisk = sum(entryLegs, leg -> legPlannedRiskShare(leg));
        BigDecimal incurredRisk = sum(entryLegs, leg -> incurredShare(leg));
        BigDecimal episodeIncurred = sum(onEpisode(entryLegs, liveEpisodeId), leg -> incurredShare(leg));
        BigDecimal episodeAtCurrentStop = sum(onEpisode(entryLegs, liveEpisodeId),
                leg -> legRiskAtCurrentStop(leg, deal));
        BigDecimal episodeFilled = sum(onEpisode(entryLegs, liveEpisodeId),
                leg -> zeroIfNull(leg.getAccumulatedFillSize()));

        return new Numbers(plannedRisk, incurredRisk,
                currentRisk(episodeIncurred, episodeFilled, live),
                episodeIncurred.subtract(episodeAtCurrentStop));
    }

    /**
     * Неотработанная доля взятого на входе риска. Числитель и знаменатель
     * отобраны ОДНИМ предикатом (ноги живого эпизода): пожизненный
     * числитель при знаменателе живого эпизода завышал бы величину кратно
     * числу закрытых эпизодов.
     *
     * <p>Вырожденный знаменатель даёт ноль, а не деление: филла на живом
     * эпизоде нет — отрабатывать нечего.
     */
    private static BigDecimal currentRisk(BigDecimal episodeIncurred, BigDecimal episodeFilled, Position live) {
        if (episodeFilled.signum() == 0 || isNull(live) || isNull(live.getExternalSize())) {
            return ZERO;
        }
        return episodeIncurred.multiply(live.getExternalSize())
                .divide(episodeFilled, DomainMath.CONTEXT);
    }

    /**
     * Доля заявленного риска, которой нога входит в число сделки: живая
     * либо исполнившаяся — целиком, выбывшая — налитой долей.
     */
    private static BigDecimal legPlannedRiskShare(Order leg) {
        BigDecimal planned = zeroIfNull(leg.getPlannedRiskAmount());
        return LIVE_OR_COMPLETED.contains(leg.getStatus())
                ? planned
                : planned.multiply(filledShare(leg));
    }

    /**
     * Взятая доля: у ЛЮБОЙ ноги — налитая. Различие с заявленным
     * проходит по ДОЛЕ, а не по множеству ног; собственный конъюнкт
     * «филл больше нуля» говорит то же прямо — нет филла, нет и взятого.
     */
    private static BigDecimal incurredShare(Order leg) {
        if (zeroIfNull(leg.getAccumulatedFillSize()).signum() <= 0) {
            return ZERO;
        }
        return zeroIfNull(leg.getPlannedRiskAmount()).multiply(filledShare(leg));
    }

    /** Вырожденный знаменатель даёт ноль, а не деление. */
    public static BigDecimal filledShare(Order leg) {
        BigDecimal planned = zeroIfNull(leg.getPlannedSizeContracts());
        if (planned.signum() == 0) {
            return ZERO;
        }
        return zeroIfNull(leg.getAccumulatedFillSize()).divide(planned, DomainMath.CONTEXT);
    }

    /**
     * Риск ноги при ДЕЙСТВУЮЩЕМ стопе: та же закрытая форма убытка на
     * стопе с подстановкой действующего уровня и фактического филла.
     * Действующий уровень — наименее благоприятный среди живых защит
     * транша этой ноги; его нет — риск ноги равен взятому целиком:
     * ограничивать его нечем.
     */
    private static BigDecimal legRiskAtCurrentStop(Order leg, Deal deal) {
        BigDecimal currentStop = trancheStopCurrent(leg, deal);
        if (isNull(currentStop) || isNull(leg.getPlannedEntryPrice()) || isNull(leg.getPlannedContractValue())) {
            return incurredShare(leg);
        }
        BigDecimal lossPerUnit = lossAtStopPerUnit(deal.getDirection(), leg.getPlannedEntryPrice(), currentStop,
                feeRate(leg));
        return lossPerUnit.multiply(zeroIfNull(leg.getAccumulatedFillSize()))
                .multiply(leg.getPlannedContractValue());
    }

    /**
     * Плановый риск ноги — убыток на её стопе при постановке, с
     * round-trip комиссией. Та же закрытая форма, что у риска при
     * действующем стопе: разведены не формулы, а операнды — здесь
     * плановые, там наблюдённые. {@code null} — стопа у ноги нет, и
     * риск нечем посчитать.
     */
    public static BigDecimal plannedRisk(StrategyTradeDirection direction, BigDecimal entryPrice,
                                         BigDecimal stopPrice, BigDecimal feeRate,
                                         BigDecimal sizeContracts, BigDecimal contractValue) {
        if (isNull(entryPrice) || isNull(stopPrice) || isNull(sizeContracts) || isNull(contractValue)) {
            return null;
        }
        return lossAtStopPerUnit(direction, entryPrice, stopPrice, zeroIfNull(feeRate))
                .multiply(sizeContracts).multiply(contractValue);
    }

    /**
     * Убыток на стопе на единицу — закрытая форма
     * docs/spec/risk-at-stop.json: знаковая дистанция плюс round-trip
     * комиссия. Знак не клэмпится: стоп за безубытком даёт отрицательный
     * убыток, и это факт, а не ошибка.
     */
    private static BigDecimal lossAtStopPerUnit(StrategyTradeDirection direction, BigDecimal entryAnchor,
                                                BigDecimal stopPrice, BigDecimal feeRate) {
        BigDecimal signedDistance = StrategyTradeDirection.LONG.equals(direction)
                ? entryAnchor.subtract(stopPrice)
                : stopPrice.subtract(entryAnchor);
        return signedDistance.add(feeRate.multiply(entryAnchor.add(stopPrice)));
    }

    /**
     * Ставка, под которую нога сайзилась, восстановленная ОБРАЩЕНИЕМ
     * закрытой формы сайзинга: нового поля она не требует.
     *
     * <p>Вырожденная база обращения даёт НОЛЬ, а не деление: через
     * коллекцию ног эта граница недостижима — читатель отбирает только
     * ноги с филлом, а филл не бывает больше планового размера.
     */
    public static BigDecimal feeRate(Order leg) {
        if (isNull(leg.getPlannedStopPrice()) || isNull(leg.getPlannedRiskAmount())) {
            return ZERO;
        }
        BigDecimal notional = zeroIfNull(leg.getPlannedContractValue())
                .multiply(zeroIfNull(leg.getPlannedSizeContracts()));
        BigDecimal base = notional.multiply(leg.getPlannedEntryPrice().add(leg.getPlannedStopPrice()));
        if (base.signum() == 0) {
            return ZERO;
        }
        BigDecimal bareDistance = leg.getPlannedEntryPrice().subtract(leg.getPlannedStopPrice()).abs();
        return leg.getPlannedRiskAmount().subtract(bareDistance.multiply(notional))
                .divide(base, DomainMath.CONTEXT);
    }

    /**
     * Действующий уровень остановки убытка, покрывающий эту ногу, —
     * наименее благоприятный среди живых защит ЕЁ транша
     * (docs/spec/protection-coverage.json, {@code trancheStopCurrent}).
     * Пусто — живой защиты с уровнем у транша нет.
     */
    private static BigDecimal trancheStopCurrent(Order leg, Deal deal) {
        return emptyIfNull(deal.getTranches()).stream()
                .filter(tranche -> Objects.equals(leg.getDealTrancheId(), tranche.getId()))
                .map(tranche -> tranche.worstActiveStopLevel(deal.getDirection()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** Входные ноги сделки — по всем траншам: числа риска агрегатные. */
    public static List<Order> entryLegs(Deal deal) {
        return emptyIfNull(deal.getOrders()).stream()
                .filter(order -> Order.Type.ENTRY.equals(order.getType())
                        || Order.Type.ENTRY_ATTACHED_STOP_LOSS.equals(order.getType()))
                .filter(order -> nonNull(order.getPlannedRiskAmount()))
                .collect(Collectors.toList());
    }

    /** Ноги живого эпизода; пустой идентификатор эпизода в множество не входит. */
    private static List<Order> onEpisode(List<Order> legs, Long liveEpisodeId) {
        if (isNull(liveEpisodeId)) {
            return List.of();
        }
        return legs.stream()
                .filter(leg -> Objects.equals(liveEpisodeId, leg.getPositionId()))
                .collect(Collectors.toList());
    }

    private static BigDecimal sum(List<Order> legs, java.util.function.Function<Order, BigDecimal> of) {
        return legs.stream().map(of).reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return nonNull(value) ? value : ZERO;
    }

    /** Признак «пересчёт разрешён»: граф предъявлен целиком. */
    public static Boolean recomputeAllowed(Boolean graphComplete) {
        return isTrue(graphComplete);
    }

    /** Четвёрка как одно значение: писатель кладёт её целиком либо не кладёт вовсе. */
    @Value
    public static class Numbers {

        /** Риск, принятый сделкой на входах. */
        BigDecimal plannedRiskAmount;

        /** Взятый на входе риск. */
        BigDecimal incurredRiskAmount;

        /** Неотработанная доля взятого. */
        BigDecimal currentRiskAmount;

        /** Риск, снятый защитой; знак не клэмпится. */
        BigDecimal protectionRelievedRiskAmount;
    }
}

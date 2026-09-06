package com.example.tradingcore.domain.command.risk;

import static java.math.BigDecimal.ZERO;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.util.DomainMath;
import com.example.tradingbot.domain.util.RiskMath;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.persistence.service.DealDataService;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Четвёрка чисел риска сделки: исполнимая форма
 * docs/spec/deal-risk-numbers.json плюс её запись.
 *
 * <p><b>Общий носитель обязанности, а не копия в каждом исполнителе:</b>
 * перечень писателей закрыт (docs/models/domain/aggregate/Deal.md
 * §«Писатели четвёрки и их триггеры»), и каждый из них пересчитывает ВСЕ
 * ЧЕТЫРЕ целиком — частичный пересчёт оставлял бы соседние числа
 * посчитанными по прежнему графу.
 *
 * <p><b>Все четыре — производные проекций заявок ВСЕХ траншей</b>, а не
 * накопители: группировка по траншам их не сдвигает, область отбора —
 * входные ноги сделки, чьи бы транши их ни держали.
 *
 * <p><b>Отбор идёт по бизнес-типу ноги, а доля — по её состоянию.</b>
 * Живая и исполнившаяся входят заявленным риском целиком, выбывшая
 * (снятая, {@code ERROR}) — только налитой долей: довод «снятая заявка не
 * стояла» верен для её НЕИСПОЛНЕННОЙ доли и неверен для налитой — за
 * налитую сделка рисковала и продолжает рисковать.
 *
 * <p><b>Пересчёт запрещён на неполном графе, и это обязанность каждого
 * писателя.</b> На неполном графе заявленный риск вышел бы заниженным, то
 * есть ослабил бы кумулятивный потолок — ошибка в разрешающую сторону.
 * Признак читается ГОТОВЫМ с контекста прохода и не пересобирается: иначе
 * каждый писатель обязан был бы знать объём загрузки.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DealRiskNumbersService {

    /** Живая либо исполнившаяся: всякая снятая выпадает независимо от причины снятия. */
    private static final Set<Order.Status> LIVE_OR_COMPLETED = EnumSet.of(
            Order.Status.CREATED, Order.Status.PENDING, Order.Status.ACTIVE,
            Order.Status.PARTIALLY_COMPLETED, Order.Status.COMPLETED);

    private final DealDataService dealDataService;

    /**
     * Пересчитать и записать четвёрку той же транзакцией, что и правку
     * операнда.
     *
     * <p>{@code false} — граф предъявлен не целиком: числа не тронуты, и
     * <b>звено писателя не завершается</b>. Исход возвращается
     * вызывающему, прежние значения остаются нетронутыми, а повтор идёт по
     * бюджету попыток строки исполнения.
     */
    public Boolean recompute(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isNotTrue(dealContext.getGraphComplete())) {
            log.debug("Risk numbers not recomputed: deal graph incomplete dealId={}", deal.getId());
            return false;
        }
        DealRiskNumbers numbers = compute(deal);
        deal.setPlannedRiskAmount(numbers.getPlannedRiskAmount());
        deal.setIncurredRiskAmount(numbers.getIncurredRiskAmount());
        deal.setCurrentRiskAmount(numbers.getCurrentRiskAmount());
        deal.setProtectionRelievedRiskAmount(numbers.getProtectionRelievedRiskAmount());
        dealDataService.save(deal);
        return true;
    }

    /**
     * Посчитать четвёрку по графу сделки. Уровень действующей защиты
     * приходит резолвом покрытия транша: у каждой ноги он свой —
     * наименее благоприятный среди защит ЕЁ транша.
     */
    public DealRiskNumbers compute(Deal deal) {
        List<Order> entryLegs = entryLegs(deal);
        Position live = deal.livePosition();
        Long liveEpisodeId = nonNull(live) ? live.getId() : null;
        List<Order> episodeLegs = onEpisode(entryLegs, liveEpisodeId);

        BigDecimal episodeIncurred = sum(episodeLegs, DealRiskNumbersService::incurredShare);
        BigDecimal episodeAtCurrentStop = sum(episodeLegs, leg -> legRiskAtCurrentStop(leg, deal));
        BigDecimal episodeFilled = sum(episodeLegs, leg -> zeroIfNull(leg.getAccumulatedFillSize()));

        return new DealRiskNumbers(
                sum(entryLegs, DealRiskNumbersService::legPlannedRiskShare),
                sum(entryLegs, DealRiskNumbersService::incurredShare),
                currentRisk(episodeIncurred, episodeFilled, live),
                episodeIncurred.subtract(episodeAtCurrentStop));
    }

    /**
     * Входные ноги сделки — по всем траншам: числа риска агрегатные.
     *
     * <p><b>Ноги собираются обходом траншей, а не полем агрегата.</b>
     * Поля {@code Deal.orders} в целевой модели нет: нога висит на транше
     * (docs/models/domain/aggregate/Deal.md §Структура), а одноимённое
     * поле общей библиотеки — донорское и ядром не читается
     * (.claude/work/backlog.md §«Донорские поля агрегата сделки в общей
     * библиотеке»).
     *
     * <p><b>Читателей у трёх статических величин здесь два</b>, и второй —
     * сверка P&amp;L: её омиссионный член взвешен ожидаемой комиссией тех
     * же ног, той же ставкой и той же долей филла
     * (docs/spec/pnl-reconciliation.json подключает
     * docs/spec/deal-risk-numbers.json через {@code includes}). Вторая их
     * копия разошлась бы с первой при первой же правке формы.
     */
    public static List<Order> entryLegs(Deal deal) {
        return emptyIfNull(deal.getTranches()).stream()
                .flatMap(tranche -> emptyIfNull(tranche.getOrders()).stream())
                .filter(order -> Order.Type.ENTRY.equals(order.getType())
                        || Order.Type.ENTRY_ATTACHED_STOP_LOSS.equals(order.getType()))
                .filter(order -> nonNull(order.getPlannedRiskAmount()))
                .collect(Collectors.toList());
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
     * Взятая доля: у ЛЮБОЙ ноги — налитая. Различие с заявленным проходит
     * по ДОЛЕ, а не по множеству ног; собственный конъюнкт «филл больше
     * нуля» говорит то же прямо — нет филла, нет и взятого.
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
        BigDecimal lossPerUnit = RiskMath.lossAtStopPerUnit(deal.getDirection(), leg.getPlannedEntryPrice(),
                currentStop, feeRate(leg));
        return lossPerUnit.multiply(zeroIfNull(leg.getAccumulatedFillSize()))
                .multiply(leg.getPlannedContractValue());
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

    /** Ноги живого эпизода; пустой идентификатор эпизода в множество не входит. */
    private static List<Order> onEpisode(List<Order> legs, Long liveEpisodeId) {
        if (isNull(liveEpisodeId)) {
            return List.of();
        }
        return legs.stream()
                .filter(leg -> Objects.equals(liveEpisodeId, leg.getPositionId()))
                .collect(Collectors.toList());
    }

    private static BigDecimal sum(List<Order> legs, Function<Order, BigDecimal> of) {
        return legs.stream().map(of).reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return nonNull(value) ? value : ZERO;
    }
}

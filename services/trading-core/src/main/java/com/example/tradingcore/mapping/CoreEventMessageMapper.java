package com.example.tradingcore.mapping;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.message.AnomalyReportedMessage;
import com.example.tradingbot.message.DealClosedMessage;
import com.example.tradingbot.message.DealOpenedMessage;
import com.example.tradingbot.message.DealShutdownInitiatedMessage;
import com.example.tradingbot.message.HoldRaisedMessage;
import com.example.tradingbot.message.OrderDecidedMessage;
import com.example.tradingcore.domain.safety.AnomalyReport;
import com.example.tradingcore.domain.safety.HoldSignal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг domain → message для классов событий торгового ядра
 * (docs/architecture/contracts.md §События).
 *
 * <p><b>Форму провода строит граница, а не домен.</b> Прежде каждая форма
 * несла фабрику {@code of(доменная модель)}, и доменный код собирал то,
 * что читает чужой сервис. Перевод между слоями принадлежит мапперу
 * (.claude/rules/codestyle.md §«Слой сообщения: внутренняя шина»), и
 * обратной стороны у этого маппера нет: события ядра читают соседи, а не
 * оно само.
 *
 * <p><b>Непокрытое целевое поле здесь ОШИБКА, а не умолчание.</b> Общий
 * дефолт мапперов ({@code unmappedTargetPolicy = IGNORE}) заведён под
 * слои, где часть полей цели законно пуста; у формы сообщения пустое поле
 * есть потерянный факт у читателя на другой стороне провода, и заметит он
 * его не при сборке, а в проде. Поэтому политика ужесточена до
 * {@code ERROR}: поле, забытое при расширении содержимого, роняет
 * компиляцию.
 *
 * <p><b>Перечни едут именами.</b> MapStruct переводит значение перечня в
 * строку через {@code name()} и держит проверку на пустоту — та же
 * семантика, что была у снятых фабрик: пустая фаза входа у
 * восстановленной сделки остаётся пустой, а не литералом {@code "null"}
 * (docs/rules/absent-value-semantics.md).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CoreEventMessageMapper {

    /**
     * Решение о заявке. Идентичности сделки, транша, счёта и инструмента
     * приходят операндами: заявка несёт числовые ключи, а провод
     * пересекает только {@code internalId}.
     */
    @Mapping(target = "orderInternalId", source = "order.internalId")
    @Mapping(target = "orderType", source = "order.type")
    @Mapping(target = "direction", source = "order.side")
    @Mapping(target = "plannedSizeContracts", source = "order.size")
    @Mapping(target = "plannedEntryPrice", source = "order.price")
    OrderDecidedMessage domainToOrderDecidedMessage(Order order,
                                                    String dealInternalId,
                                                    String dealTrancheInternalId,
                                                    String exchangeAccountInternalId,
                                                    String instrumentInternalId);

    /** Создание сделки: идентичности радиуса плюс контекст входа. */
    @Mapping(target = "dealInternalId", source = "deal.internalId")
    @Mapping(target = "entryReason", source = "deal.entryReason")
    @Mapping(target = "direction", source = "deal.direction")
    @Mapping(target = "entryMarketPhase", source = "deal.entryMarketPhase")
    DealOpenedMessage domainToDealOpenedMessage(Deal deal,
                                                String exchangeAccountInternalId,
                                                String instrumentInternalId,
                                                String strategyInternalId);

    /** Выход сделки из штатного ведения; актор различает тропы класса. */
    @Mapping(target = "dealInternalId", source = "deal.internalId")
    @Mapping(target = "status", source = "deal.status")
    @Mapping(target = "shutdownReason", source = "deal.shutdownReason")
    DealShutdownInitiatedMessage domainToDealShutdownInitiatedMessage(Deal deal,
                                                                      String exchangeAccountInternalId,
                                                                      String instrumentInternalId,
                                                                      String strategyInternalId,
                                                                      String actor);

    /**
     * Закрытие сделки.
     *
     * <p>Четыре величины разбивки — вопросы к агрегату, а не его поля
     * ({@code positionObserved()}, накопленные издержки), и переносятся
     * выражением: предикат остаётся на модели
     * (.claude/rules/codestyle.md §«Вложенность и rich-модели»).
     */
    @Mapping(target = "dealInternalId", source = "deal.internalId")
    @Mapping(target = "status", source = "deal.status")
    @Mapping(target = "closeReason", source = "deal.closeReason")
    @Mapping(target = "tookRisk", expression = "java(deal.positionObserved())")
    @Mapping(target = "result", source = "deal.resultProfit")
    @Mapping(target = "resultCurrency", source = "deal.resultProfitCurrency")
    @Mapping(target = "fee", expression = "java(deal.accumulatedFeeCost())")
    @Mapping(target = "funding", expression = "java(deal.accumulatedFundingCost())")
    @Mapping(target = "liquidationPenalty", expression = "java(deal.accumulatedLiquidationPenaltyCost())")
    @Mapping(target = "plannedRisk", source = "deal.plannedRiskAmount")
    @Mapping(target = "closeOutcome", source = "deal.closeOutcome")
    @Mapping(target = "reconciliationStatus", source = "deal.reconciliationStatus")
    @Mapping(target = "breakdownIncomplete", source = "deal.breakdownIncomplete")
    @Mapping(target = "riskBenchmarkAvailability", source = "deal.riskBenchmarkAvailability")
    DealClosedMessage domainToDealClosedMessage(Deal deal,
                                                String exchangeAccountInternalId,
                                                String instrumentInternalId,
                                                String strategyInternalId,
                                                Boolean graphComplete);

    /**
     * Подъём ступени радиуса. Инструмент приходит операндом и пуст у
     * счётного сигнала — по построению радиуса, а не по пропуску.
     */
    @Mapping(target = "scope", source = "signal.scope")
    @Mapping(target = "rung", source = "signal.rung")
    @Mapping(target = "code", source = "signal.code")
    HoldRaisedMessage domainToHoldRaisedMessage(HoldSignal signal,
                                                String exchangeAccountInternalId,
                                                String instrumentInternalId,
                                                String actor);

    /** Отчёт о происшествии; актор различает детекцию и ручную тропу. */
    @Mapping(target = "anomalyReportInternalId", source = "report.internalId")
    @Mapping(target = "scope", source = "report.scope")
    @Mapping(target = "severity", source = "report.severity")
    @Mapping(target = "code", source = "report.code")
    AnomalyReportedMessage domainToAnomalyReportedMessage(AnomalyReport report,
                                                          String exchangeAccountInternalId,
                                                          String instrumentInternalId,
                                                          String actor);
}

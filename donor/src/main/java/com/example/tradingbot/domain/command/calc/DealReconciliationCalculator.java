package com.example.tradingbot.domain.command.calc;

import static java.math.BigDecimal.ZERO;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.config.ExchangeContourProperties;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.risk.DealRiskNumbers;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Считает исход сверки P&L: сходятся ли четыре пары «разбивка движений ↔
 * записи закрытия эпизодов» в пределах допуска. Исполнимая форма —
 * docs/spec/pnl-reconciliation.json; политика реакции —
 * docs/rules/pnl-reconciliation.md.
 *
 * <p><b>Сверка независима от гранулярности записи источника.</b>
 * Комбинированная запись (сумма и комиссия одной строкой) и раздельная
 * пара строк дают один результат: комиссионная компонента вычитается из
 * суммы строки, а различитель гранулярности берётся из самих строк сделки
 * — наличием комиссионной строки расчётной валюты.
 *
 * <p><b>Расхождения разных знаков друг друга не гасят:</b> общее
 * расхождение — сумма модулей четырёх пар.
 */
@Service
@RequiredArgsConstructor
public class DealReconciliationCalculator {

    private final ExchangeContourProperties exchangeContourProperties;

    /**
     * Исход сверки. Обязанность — конъюнкция трёх условий: записи
     * закрытия добыты у всех эпизодов, порог доказанного покрытия непуст,
     * добыча движений выполнялась. Не наступила — {@code NOT_RUN}: это
     * «не были обязаны», а не «посчитали и сошлось».
     */
    public Deal.ReconciliationStatus reconcile(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isFalse(dutyArisen(dealContext))) {
            return Deal.ReconciliationStatus.NOT_RUN;
        }
        String settleCurrency = dealContext.getInstrument().getExternalSettlementCurrency();
        ExchangeContourProperties.Contour contour =
                exchangeContourProperties.forExchange(dealContext.getExchange().getName());
        List<DealCashFlow> scope = inScope(dealContext.getCashFlows(), settleCurrency, contour);
        boolean separateFeeGranularity = separateFeeGranularity(scope);
        BigDecimal discrepancy = totalDiscrepancy(deal, scope, separateFeeGranularity);
        return discrepancy.compareTo(epsilon(deal, scope, contour)) <= 0
                ? Deal.ReconciliationStatus.MATCHED
                : Deal.ReconciliationStatus.MISMATCHED;
    }

    /**
     * Расхождение сверх допуска триггерит биржевую ступень 1 только в
     * боевом режиме: до калибровки расхождение неотличимо от «допуск не
     * тот», и вешать на него блокировку нельзя
     * (docs/rules/pnl-reconciliation.md §«Реакция на расхождение»).
     */
    public Boolean rungRequested(DealContext dealContext, Deal.ReconciliationStatus status) {
        ExchangeContourProperties.Contour contour =
                exchangeContourProperties.forExchange(dealContext.getExchange().getName());
        return Deal.ReconciliationStatus.MISMATCHED.equals(status)
                && isFalse(contour.getReconciliationExploratory());
    }

    /** Обязанность сверки — конъюнкция трёх условий; ни одно не подставляется. */
    private Boolean dutyArisen(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        boolean closeRecordsFetched = emptyIfNull(deal.getPositions()).stream()
                .allMatch(episode -> nonNull(episode.getExternalRealizedProfit()));
        return closeRecordsFetched
                && nonNull(deal.getCoverageProvenThrough())
                && nonNull(deal.getBillsFetchedThrough());
    }

    /**
     * Область сверки: расчётная валюта, вне списка исключений биржи,
     * экономическая категория. Конъюнкция целиком — принимающая корзина
     * нераспознанного в область не входит.
     */
    private List<DealCashFlow> inScope(List<DealCashFlow> cashFlows, String settleCurrency,
                                       ExchangeContourProperties.Contour contour) {
        if (isBlank(settleCurrency)) {
            return List.of();
        }
        return cashFlows.stream()
                .filter(flow -> Objects.equals(settleCurrency, flow.getCcy()))
                .filter(flow -> isFalse(contour.excludesFromReconciliation(flow.getExternalType(),
                        flow.getExternalSubType())))
                .filter(flow -> isFalse(DealCashFlow.CashFlowCategory.OTHER.equals(flow.getCategory())))
                .toList();
    }

    /**
     * Источник эмитит комиссию отдельной записью. Различитель берётся из
     * самих строк сделки — не из настройки и не из рантайм-факта: он
     * снимает построчную неразличимость комбинированной записи и
     * информационного эха комиссии на торговой записи.
     *
     * <p><b>Область различителя уже области сверки на один конъюнкт</b>
     * — список исключений биржи сюда не входит: исключение комиссионного
     * типа списком сбило бы различитель в «комбинированная», после чего
     * эхо вычиталось бы из торговой строки. Здесь область уже сужена
     * вызывающей стороной, и это названное упрощение: комиссионные типы
     * контура в списке исключений не стоя́т.
     */
    private boolean separateFeeGranularity(List<DealCashFlow> scope) {
        return scope.stream().anyMatch(flow -> isFeeCategory(flow.getCategory()));
    }

    private boolean isFeeCategory(DealCashFlow.CashFlowCategory category) {
        return DealCashFlow.CashFlowCategory.TRADE_FEE.equals(category)
                || DealCashFlow.CashFlowCategory.REBATE.equals(category);
    }

    /**
     * Комиссионная компонента одной строки. У строки комиссионной
     * категории ею служит само движение — это определение категории, а не
     * подстановка по умолчанию. У прочих категорий компонента берётся
     * явным полем, но только при комбинированной гранулярности: при
     * раздельной то же поле на торговой записи есть эхо уже посчитанной
     * комиссионной строки, и его учёт задваивал бы комиссию.
     */
    private BigDecimal flowFeeComponent(DealCashFlow flow, boolean separateFeeGranularity) {
        if (isFeeCategory(flow.getCategory())) {
            return nullSafe(flow.getAmount());
        }
        return separateFeeGranularity ? ZERO : nullSafe(flow.getExternalFee());
    }

    /** Сумма строки за вычетом её комиссионной компоненты — делает композицию независимой от гранулярности. */
    private BigDecimal flowAmountNetOfFee(DealCashFlow flow, boolean separateFeeGranularity) {
        return nullSafe(flow.getAmount()).subtract(flowFeeComponent(flow, separateFeeGranularity));
    }

    /** Общее расхождение по четырём парам: сумма модулей — разные знаки друг друга не гасят. */
    private BigDecimal totalDiscrepancy(Deal deal, List<DealCashFlow> scope, boolean separateFeeGranularity) {
        BigDecimal realizedPnl = leftByCategory(scope, DealCashFlow.CashFlowCategory.REALIZED_PNL,
                separateFeeGranularity).subtract(rightRealizedPnl(deal));
        BigDecimal tradeFee = leftTradeFee(scope, separateFeeGranularity).subtract(rightTradeFee(deal));
        BigDecimal funding = leftByCategory(scope, DealCashFlow.CashFlowCategory.FUNDING, separateFeeGranularity)
                .subtract(rightFunding(deal));
        BigDecimal penalty = leftByCategory(scope, DealCashFlow.CashFlowCategory.LIQ_PENALTY, separateFeeGranularity)
                .subtract(rightLiquidationPenalty(deal));
        return realizedPnl.abs().add(tradeFee.abs()).add(funding.abs()).add(penalty.abs());
    }

    private BigDecimal leftByCategory(List<DealCashFlow> scope, DealCashFlow.CashFlowCategory category,
                                      boolean separateFeeGranularity) {
        return scope.stream()
                .filter(flow -> Objects.equals(category, flow.getCategory()))
                .map(flow -> flowAmountNetOfFee(flow, separateFeeGranularity))
                .reduce(ZERO, BigDecimal::add);
    }

    /**
     * Комиссия собирается по ВСЕЙ области сверки, а не по комиссионным
     * категориям: у комбинированной записи она сидит внутри торговой
     * строки и по категории не находится.
     */
    private BigDecimal leftTradeFee(List<DealCashFlow> scope, boolean separateFeeGranularity) {
        return scope.stream()
                .map(flow -> flowFeeComponent(flow, separateFeeGranularity))
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal rightRealizedPnl(Deal deal) {
        return sumEpisodes(deal, Position::getExternalRealizedProfitGross);
    }

    private BigDecimal rightTradeFee(Deal deal) {
        return sumEpisodes(deal, Position::getExternalFee);
    }

    /**
     * Знак снимается обратно: домен хранит финансирование издержкой
     * (положительной), а разбивка — сырой знаковой суммой. Сравнение идёт
     * в СЫРОЙ конвенции, потому что у комиссии и штрафа знак сырой;
     * смешение конвенций дало бы расхождение на всей популяции.
     */
    private BigDecimal rightFunding(Deal deal) {
        return ZERO.subtract(sumEpisodes(deal, Position::getExternalFundingCost));
    }

    private BigDecimal rightLiquidationPenalty(Deal deal) {
        return sumEpisodes(deal, Position::getExternalLiquidationPenalty);
    }

    private BigDecimal sumEpisodes(Deal deal, java.util.function.Function<Position, BigDecimal> field) {
        return emptyIfNull(deal.getPositions()).stream()
                .map(field)
                .map(this::nullSafe)
                .reduce(ZERO, BigDecimal::add);
    }

    /**
     * Допуск один на сделку: больший из пола и меньшего из двух членов.
     * На сделке без входных ног омиссионный член равен нулю, минимум
     * схлопывается, и допуск вырождается в пол при любом обороте — тест
     * одночастный, охрану композиции держит только пол.
     */
    private BigDecimal epsilon(Deal deal, List<DealCashFlow> scope, ExchangeContourProperties.Contour contour) {
        ExchangeContourProperties.ReconciliationTolerance tolerance = contour.getReconciliationTolerance();
        BigDecimal relative = nullSafe(tolerance.getRelativeShare()).multiply(grossTurnover(scope));
        BigDecimal omission = nullSafe(tolerance.getOmissionMultiplier()).multiply(expectedDealFee(deal));
        return nullSafe(tolerance.getFloor()).max(relative.min(omission));
    }

    /** Валовой оборот — якорь относительного члена; обе стороны теста однородны по покрытию. */
    private BigDecimal grossTurnover(List<DealCashFlow> scope) {
        return scope.stream()
                .map(flow -> nullSafe(flow.getAmount()).abs())
                .reduce(ZERO, BigDecimal::add);
    }

    /**
     * Ожидаемая комиссия сделки: round-trip комиссия входных ног,
     * взвешенная филлом. Множество ног — предикат ВЗЯТОГО риска, а не
     * заявленного: нога, стоящая неисполненной, комиссии не создаёт.
     */
    private BigDecimal expectedDealFee(Deal deal) {
        return DealRiskNumbers.entryLegs(deal).stream()
                .filter(leg -> nonNull(leg.getAccumulatedFillSize()) && leg.getAccumulatedFillSize().signum() > 0)
                .map(this::legExpectedFee)
                .reduce(ZERO, BigDecimal::add);
    }

    /**
     * Ожидаемая round-trip комиссия ноги: ставка × (цена входа + плановый
     * стоп) × размер × стоимость контракта × доля филла. Ни ставка, ни
     * доля филла здесь не переизобретаются — обе живут у дома чисел риска.
     */
    private BigDecimal legExpectedFee(Order leg) {
        BigDecimal anchors = nullSafe(leg.getPlannedEntryPrice()).add(nullSafe(leg.getPlannedStopPrice()));
        return DealRiskNumbers.feeRate(leg).multiply(anchors)
                .multiply(nullSafe(leg.getPlannedSizeContracts()))
                .multiply(nullSafe(leg.getPlannedContractValue()))
                .multiply(DealRiskNumbers.filledShare(leg));
    }

    /** Пустое слагаемое вносит ноль — «вносить нечего», не «величина равна нулю». */
    private BigDecimal nullSafe(BigDecimal value) {
        return isNull(value) ? ZERO : value;
    }
}

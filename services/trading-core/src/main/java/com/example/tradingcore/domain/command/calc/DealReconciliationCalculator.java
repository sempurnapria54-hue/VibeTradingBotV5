package com.example.tradingcore.domain.command.calc;

import static java.math.BigDecimal.ZERO;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingcore.config.ExchangeContourProperties;
import com.example.tradingcore.config.PnlReconciliationProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.DealRiskNumbersService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Считает исход сверки P&amp;L: сходятся ли четыре пары «разбивка движений
 * ↔ записи закрытия эпизодов» в пределах допуска. Исполнимая форма —
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
    private final PnlReconciliationProperties toleranceProperties;

    /**
     * Исход сверки. Обязанность — конъюнкция трёх условий: записи закрытия
     * добыты у всех эпизодов, порог доказанного покрытия непуст, добыча
     * движений выполнялась. Не наступила — {@code NOT_RUN}: это «не были
     * обязаны», а не «посчитали и сошлось».
     */
    public Deal.ReconciliationStatus reconcile(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isFalse(dutyArisen(deal))) {
            return Deal.ReconciliationStatus.NOT_RUN;
        }
        String settleCurrency = dealContext.getInstrument().getExternalSettlementCurrency();
        ExchangeContourProperties.Contour contour = contourOf(dealContext);
        List<DealCashFlow> scope = inScope(dealContext.getCashFlows(), settleCurrency, contour);
        boolean separateFeeGranularity = separateFeeGranularity(scope);
        BigDecimal discrepancy = totalDiscrepancy(deal, scope, separateFeeGranularity);
        return discrepancy.compareTo(epsilon(deal, scope)) <= 0
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
        return Deal.ReconciliationStatus.MISMATCHED.equals(status)
                && isFalse(contourOf(dealContext).getReconciliationExploratory());
    }

    private ExchangeContourProperties.Contour contourOf(DealContext dealContext) {
        return exchangeContourProperties.forExchange(dealContext.getExchangeAccount().getExchangeCode());
    }

    /** Обязанность сверки — конъюнкция трёх условий; ни одно не подставляется. */
    private Boolean dutyArisen(Deal deal) {
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
        return emptyIfNull(cashFlows).stream()
                .filter(flow -> Objects.equals(settleCurrency, flow.getCcy()))
                .filter(flow -> isFalse(contour.excludesFromReconciliation(flow.getExternalType(),
                        flow.getExternalSubType())))
                .filter(flow -> isFalse(DealCashFlow.CashFlowCategory.OTHER.equals(flow.getCategory())))
                .collect(Collectors.toList());
    }

    /**
     * Источник эмитит комиссию отдельной записью. Различитель берётся из
     * самих строк сделки — не из настройки и не из рантайм-факта: он
     * снимает построчную неразличимость комбинированной записи и
     * информационного эха комиссии на торговой записи.
     *
     * <p><b>Область различителя уже области сверки на один конъюнкт</b> —
     * список исключений биржи сюда не входит: исключение комиссионного
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
            return zeroIfNull(flow.getAmount());
        }
        return separateFeeGranularity ? ZERO : zeroIfNull(flow.getExternalFee());
    }

    /**
     * Сумма строки за вычетом её комиссионной компоненты — делает
     * композицию независимой от гранулярности записи источника.
     */
    private BigDecimal flowAmountNetOfFee(DealCashFlow flow, boolean separateFeeGranularity) {
        return zeroIfNull(flow.getAmount()).subtract(flowFeeComponent(flow, separateFeeGranularity));
    }

    /**
     * Общее расхождение по четырём парам: сумма модулей — разные знаки
     * друг друга не гасят.
     */
    private BigDecimal totalDiscrepancy(Deal deal, List<DealCashFlow> scope, boolean separateFeeGranularity) {
        BigDecimal realizedPnl = leftByCategory(scope, DealCashFlow.CashFlowCategory.REALIZED_PNL,
                separateFeeGranularity).subtract(sumEpisodes(deal, Position::getExternalRealizedProfitGross));
        BigDecimal tradeFee = leftTradeFee(scope, separateFeeGranularity)
                .subtract(sumEpisodes(deal, Position::getExternalFee));
        BigDecimal funding = leftByCategory(scope, DealCashFlow.CashFlowCategory.FUNDING, separateFeeGranularity)
                .subtract(rightFunding(deal));
        BigDecimal penalty = leftByCategory(scope, DealCashFlow.CashFlowCategory.LIQ_PENALTY,
                separateFeeGranularity).subtract(sumEpisodes(deal, Position::getExternalLiquidationPenalty));
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

    /**
     * Знак снимается обратно: домен хранит финансирование издержкой
     * (положительной), а разбивка — сырой знаковой суммой. Сравнение идёт
     * в СЫРОЙ конвенции, потому что у комиссии и штрафа знак сырой;
     * смешение конвенций дало бы расхождение на всей популяции.
     */
    private BigDecimal rightFunding(Deal deal) {
        return ZERO.subtract(sumEpisodes(deal, Position::getExternalFundingCost));
    }

    private BigDecimal sumEpisodes(Deal deal, Function<Position, BigDecimal> field) {
        return emptyIfNull(deal.getPositions()).stream()
                .map(field)
                .map(this::zeroIfNull)
                .reduce(ZERO, BigDecimal::add);
    }

    /**
     * Допуск один на сделку: больший из пола и меньшего из двух членов.
     * На сделке без входных ног омиссионный член равен нулю, минимум
     * схлопывается, и допуск вырождается в пол при любом обороте — тест
     * одночастный, охрану композиции держит только пол.
     */
    private BigDecimal epsilon(Deal deal, List<DealCashFlow> scope) {
        BigDecimal relative = zeroIfNull(toleranceProperties.getRelativeShare()).multiply(grossTurnover(scope));
        BigDecimal omission = zeroIfNull(toleranceProperties.getOmissionMultiplier())
                .multiply(expectedDealFee(deal));
        return zeroIfNull(toleranceProperties.getFloor()).max(relative.min(omission));
    }

    /** Валовой оборот — якорь относительного члена; обе стороны теста однородны по покрытию. */
    private BigDecimal grossTurnover(List<DealCashFlow> scope) {
        return scope.stream()
                .map(flow -> zeroIfNull(flow.getAmount()).abs())
                .reduce(ZERO, BigDecimal::add);
    }

    /**
     * Ожидаемая комиссия сделки: round-trip комиссия входных ног,
     * взвешенная филлом. Множество ног — предикат ВЗЯТОГО риска, а не
     * заявленного: нога, стоящая неисполненной, комиссии не создаёт.
     */
    private BigDecimal expectedDealFee(Deal deal) {
        return DealRiskNumbersService.entryLegs(deal).stream()
                .filter(leg -> nonNull(leg.getAccumulatedFillSize())
                        && leg.getAccumulatedFillSize().signum() > 0)
                .map(this::legExpectedFee)
                .reduce(ZERO, BigDecimal::add);
    }

    /**
     * Ожидаемая round-trip комиссия ноги: ставка × (цена входа + плановый
     * стоп) × размер × стоимость контракта × доля филла. Ни ставка, ни
     * доля филла здесь не переизобретаются — обе живут у дома чисел риска.
     */
    private BigDecimal legExpectedFee(Order leg) {
        BigDecimal anchors = zeroIfNull(leg.getPlannedEntryPrice()).add(zeroIfNull(leg.getPlannedStopPrice()));
        return DealRiskNumbersService.feeRate(leg).multiply(anchors)
                .multiply(zeroIfNull(leg.getPlannedSizeContracts()))
                .multiply(zeroIfNull(leg.getPlannedContractValue()))
                .multiply(DealRiskNumbersService.filledShare(leg));
    }

    /** Пустое слагаемое вносит ноль — «вносить нечего», не «величина равна нулю». */
    private BigDecimal zeroIfNull(BigDecimal value) {
        return isNull(value) ? ZERO : value;
    }
}

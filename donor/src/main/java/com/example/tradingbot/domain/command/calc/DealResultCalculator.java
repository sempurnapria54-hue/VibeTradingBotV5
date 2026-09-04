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
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Считает итоговый результат сделки — сумму net по эпизодам плюс
 * слагаемое чужой валюты. Исполнимая форма — docs/spec/deal-result.json;
 * здесь она реализована, а не переписана.
 *
 * <p><b>Число недоступно, а не занижено</b>, когда не добыта запись
 * закрытия любого эпизода, либо разбивка движений не добыта или
 * предъявлена не целиком, либо движение вне расчётной валюты ждёт курса.
 * Пропуск слагаемого и публикация итога без него запрещены — это было бы
 * благоприятное умолчание.
 *
 * <p><b>Область слагаемого и область блокировки не совпадают</b>, и это
 * не описка: блокировка шире — на принимающую корзину нераспознанного и
 * на все неисключённые строки при нерезолвимой расчётной валюте. Дом
 * политики асимметрии — docs/rules/pnl-reconciliation.md.
 */
@Service
@RequiredArgsConstructor
public class DealResultCalculator {

    private final ExchangeContourProperties exchangeContourProperties;

    /**
     * Итог сделки по фактам прохода. Тропа закрытия без входа доступна
     * всегда: ни эпизодов, ни движений там нет по построению, и ноль —
     * результат расчёта, а не умолчание.
     */
    public DealResult calculate(DealContext dealContext) {
        String settleCurrency = dealContext.getInstrument().getExternalSettlementCurrency();
        Deal deal = dealContext.getDeal();
        if (isFalse(deal.positionObserved())) {
            return new DealResult(true, ZERO, settleCurrency);
        }
        ExchangeContourProperties.Contour contour =
                exchangeContourProperties.forExchange(dealContext.getExchange().getName());
        List<DealCashFlow> cashFlows = dealContext.getCashFlows();
        boolean available = isTrue(dealContext.getGraphComplete())
                && isTrue(allCloseRecordsFetched(deal))
                && isTrue(dealContext.getFlowsComplete())
                && isFalse(rateBlocking(cashFlows, settleCurrency, contour));
        if (isFalse(available)) {
            return new DealResult(false, null, settleCurrency);
        }
        BigDecimal profit = netSum(deal).add(crossCurrencyTerm(cashFlows, settleCurrency, contour));
        return new DealResult(true, profit, settleCurrency);
    }

    /**
     * Записи закрытия добыты у ВСЕХ эпизодов. Пустое слагаемое нулём не
     * подставляется: эпизод без записи закрытия делает сумму
     * недоступной, а не заниженной.
     */
    private Boolean allCloseRecordsFetched(Deal deal) {
        return emptyIfNull(deal.getPositions()).stream()
                .allMatch(episode -> nonNull(episode.getExternalRealizedProfit()));
    }

    /** Сумма net по эпизодам, а не число последнего: числитель обязан покрывать те же заявки, что и знаменатель R. */
    private BigDecimal netSum(Deal deal) {
        return emptyIfNull(deal.getPositions()).stream()
                .map(Position::getExternalRealizedProfit)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
    }

    /**
     * Строка с неполученным курсом блокирует итог: пропуск такой строки
     * был бы благоприятным умолчанием. Область блокировки шире области
     * слагаемого — попадание в неё необходимо, но не достаточно.
     */
    private Boolean rateBlocking(List<DealCashFlow> cashFlows, String settleCurrency,
                                 ExchangeContourProperties.Contour contour) {
        return cashFlows.stream()
                .filter(flow -> isTrue(inBlockingScope(flow, settleCurrency, contour)))
                .anyMatch(flow -> DealCashFlow.RateStatus.RATE_UNAVAILABLE.equals(flow.getRateStatus())
                        || DealCashFlow.RateStatus.SETTLE_CURRENCY_UNAVAILABLE.equals(flow.getRateStatus()));
    }

    /**
     * Слагаемое чужой валюты: движения, оплаченные вне расчётной валюты,
     * переведённые по применённому курсу. Без него число завышалось бы
     * молча.
     */
    private BigDecimal crossCurrencyTerm(List<DealCashFlow> cashFlows, String settleCurrency,
                                         ExchangeContourProperties.Contour contour) {
        return cashFlows.stream()
                .filter(flow -> isTrue(inTermScope(flow, settleCurrency, contour)))
                .filter(flow -> DealCashFlow.RateStatus.APPLIED.equals(flow.getRateStatus()))
                .map(flow -> flow.getAmount().multiply(flow.getAppliedRate()))
                .reduce(ZERO, BigDecimal::add);
    }

    /**
     * Область СЛАГАЕМОГО: движение вне расчётной валюты, вне списка
     * исключений биржи, экономической категории; при нерезолвимой
     * расчётной валюте область пуста.
     */
    private Boolean inTermScope(DealCashFlow flow, String settleCurrency,
                                ExchangeContourProperties.Contour contour) {
        return isFalse(isBlank(settleCurrency))
                && isFalse(Objects.equals(flow.getCcy(), settleCurrency))
                && isFalse(excluded(flow, contour))
                && isFalse(DealCashFlow.CashFlowCategory.OTHER.equals(flow.getCategory()))
                && nonNull(flow.getAmount())
                && nonNull(flow.getAppliedRate());
    }

    /**
     * Область БЛОКИРОВКИ: шире области слагаемого — на принимающую
     * корзину, а при нерезолвимой расчётной валюте на все неисключённые
     * строки.
     */
    private Boolean inBlockingScope(DealCashFlow flow, String settleCurrency,
                                    ExchangeContourProperties.Contour contour) {
        return isFalse(excluded(flow, contour))
                && (isBlank(settleCurrency) || isFalse(Objects.equals(flow.getCcy(), settleCurrency)));
    }

    private Boolean excluded(DealCashFlow flow, ExchangeContourProperties.Contour contour) {
        if (isNull(contour)) {
            return false;
        }
        return contour.excludesFromReconciliation(flow.getExternalType(), flow.getExternalSubType());
    }
}

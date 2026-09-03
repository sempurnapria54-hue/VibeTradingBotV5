package com.example.tradingbot.domain.safety;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.risk.RiskValidator;
import com.example.tradingbot.domain.deal.DealContextService;
import com.example.tradingbot.domain.deal.DealTerminalGate;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Детекторы инвариантов живой сделки: `A4` (живой риск без покрытия),
 * `A11` (расхождение суммы экспозиций с нетто-размером эпизода), `A12`
 * (нарушение риск-политики при стоящей защите).
 *
 * <p><b>Своих величин детекторы не заводят.</b> `A4` читает предикат
 * покрытия транша, `A11` — сверку экспозиции, которой гейтится терминал
 * сделки, `A12` — те же неравенства потолков при нулевом акте. Второй дом
 * у любой из этих форм был бы копией, расходящейся первой же правкой.
 *
 * <p><b>Гейт полноты графа обязателен у всех трёх.</b> На неполном графе
 * операнды занижены, и детектор МОЛЧИТ, а не рапортует: ложный триггер
 * `A4` и `A11` сносит всю биржу, `A12` — останавливает входы по
 * инструменту (docs/rules/instrument-hold.md §«Форма реакции на нарушение
 * риск-политики при живой защите»).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DealInvariantDetectors {

    /** Признак сравнивает БД с биржей: подтверждается следующим тиком. */
    private static final Integer CONFIRMED_NEXT_TICK = 2;

    private final DealDataService dealDataService;
    private final DealContextService dealContextService;
    private final DealTerminalGate dealTerminalGate;
    private final RiskValidator riskValidator;
    private final AnomalyReaction reaction;

    public void detect(Exchange exchange) {
        for (Deal deal : dealDataService.findActiveByExchangeId(exchange.getId())) {
            try {
                DealContext context = dealContextService.build(deal);
                if (isFalse(context.getGraphComplete())) {
                    continue;
                }
                uncoveredLiveRisk(context, exchange);
                exposureMismatch(context, exchange);
                riskPolicyBreach(context, exchange);
            } catch (RuntimeException e) {
                log.error("[anomaly] инварианты сделки не проверены dealId={}", deal.getId(), e);
            }
        }
    }

    /**
     * `A4`: общий детектор инварианта покрытия. Прежде воплощены были две
     * тропы его области — подтверждение входа без резолвимой защиты и
     * потеря основной защиты в переключении; здесь предикат проверяется
     * по КАЖДОМУ живому траншу каждым проходом.
     *
     * <p><b>Недопокрытие под живым обязательством нарушением не
     * является</b> — это наш собственный незавершённый ход, и его
     * различает сам предикат транша. Гистерезис к структурному гейту
     * добавляется, а не подменяет его.
     */
    private void uncoveredLiveRisk(DealContext context, Exchange exchange) {
        for (DealTranche tranche : context.getDeal().getTranches()) {
            if (isFalse(tranche.isRiskBearing()) || isTrue(tranche.isCovered())) {
                continue;
            }
            log.warn("[anomaly] живой риск без покрытия dealId={} trancheId={}",
                    context.getDeal().getId(), tranche.getId());
            reaction.apply(AnomalyFinding.builder()
                    .scope(HoldScope.EXCHANGE)
                    .rung(HoldRung.HARD)
                    .code(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED)
                    .instrument(context.getInstrument())
                    .hysteresisTicks(CONFIRMED_NEXT_TICK)
                    .journalOnly(false)
                    .build(), exchange);
            return;
        }
    }

    /**
     * `A11`: сумма gross-экспозиций траншей разошлась с нетто-размером
     * живого эпизода. Меньше — экспозиция, которую модель не приписывает
     * ни одному траншу; больше — часть нашей закрыта не нами. Оба
     * направления одинаково опасны: наш счёт экспозиции разошёлся с
     * биржей.
     */
    private void exposureMismatch(DealContext context, Exchange exchange) {
        Deal deal = context.getDeal();
        if (isEmpty(deal.getTranches())) {
            return;
        }
        if (isTrue(dealTerminalGate.exposureReconciled(deal.livePosition(), deal.getTranches()))) {
            return;
        }
        log.warn("[anomaly] сумма экспозиций разошлась с нетто-размером dealId={}", deal.getId());
        reaction.apply(AnomalyFinding.builder()
                .scope(HoldScope.EXCHANGE)
                .rung(HoldRung.HARD)
                .code(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED)
                .instrument(context.getInstrument())
                .hysteresisTicks(CONFIRMED_NEXT_TICK)
                .journalOnly(false)
                .build(), exchange);
    }

    /**
     * `A12`: живая сделка перестала укладываться в потолки, хотя её
     * защита стои́т и подтверждается. Форма реакции мягкая — принятый риск
     * покрыт, и рвать его нечем; жёсткая была бы платой рыночной цены без
     * основания. Когда покрытие нарушено, работает `A4`, а не этот.
     */
    private void riskPolicyBreach(DealContext context, Exchange exchange) {
        if (isEmpty(riskValidator.ceilingsBreachedWithoutAct(context))) {
            return;
        }
        log.warn("[anomaly] нарушение риск-политики при живой защите dealId={}", context.getDeal().getId());
        reaction.apply(AnomalyFinding.builder()
                .scope(HoldScope.INSTRUMENT)
                .rung(HoldRung.SOFT)
                .code(Constants.Hold.RISK_POLICY_BREACH_UNDER_PROTECTION)
                .instrument(context.getInstrument())
                .hysteresisTicks(CONFIRMED_NEXT_TICK)
                .journalOnly(false)
                .build(), exchange);
    }
}

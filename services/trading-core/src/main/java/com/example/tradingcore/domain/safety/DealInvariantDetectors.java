package com.example.tradingcore.domain.safety;

import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskValidator;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.deal.ProtectionCoverageGate;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Детекторы инвариантов живой сделки: живой риск без покрытия,
 * расхождение суммы экспозиций с нетто-размером эпизода, нарушение
 * риск-политики при стоящей защите (docs/components/AnomalyJob.md §«Что
 * ищет»).
 *
 * <p><b>Своих величин детекторы не заводят.</b> Первый читает предикат
 * покрытия транша, второй — сверку экспозиции, которой гейтится терминал
 * сделки, третий — те же неравенства потолков при нулевом акте. Второй
 * дом у любой из этих форм был бы копией, расходящейся первой же
 * правкой.
 *
 * <p><b>Гейт полноты графа обязателен у всех трёх.</b> На неполном графе
 * операнды занижены, и детектор МОЛЧИТ, а не рапортует: ложный триггер
 * первых двух сносит весь счёт, третьего — останавливает входы по
 * инструменту.
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
    private final ProtectionCoverageGate protectionCoverageGate;
    private final RiskValidator riskValidator;
    private final AnomalyReaction reaction;

    /** Обход нетерминальных сделок счёта. */
    public void detect(ExchangeAccount account) {
        for (Deal deal : dealDataService.findNonTerminalByExchangeAccountId(account.getId())) {
            try {
                DealContext context = dealContextService.build(deal);
                if (isFalse(context.getGraphComplete())) {
                    continue;
                }
                uncoveredLiveRisk(context, account);
                exposureMismatch(context, account);
                riskPolicyBreach(context, account);
            } catch (RuntimeException e) {
                log.error("Deal invariants are not checked dealId={}", deal.getId(), e);
            }
        }
    }

    /**
     * Общий детектор инварианта покрытия: предикат проверяется по КАЖДОМУ
     * траншу каждым проходом.
     *
     * <p><b>Форма читается по дому, а не пересобирается</b>
     * (docs/spec/protection-coverage.json, величина
     * {@code trancheViolated}): третья конъюнкта — не смягчение, а
     * граница области, и без неё детектор снимал бы риск в окне, где
     * защита ещё ставится.
     *
     * <p>Реакция поднимается ОДИН раз на сделку: радиус у неё счётный, и
     * второй транш с тем же нарушением добавил бы вторую строку по тому
     * же ключу.
     */
    private void uncoveredLiveRisk(DealContext context, ExchangeAccount account) {
        for (DealTranche tranche : emptyIfNull(context.getDeal().getTranches())) {
            if (isFalse(protectionCoverageGate.trancheViolated(context, tranche))) {
                continue;
            }
            log.warn("Live risk without coverage dealId={} trancheId={}",
                    context.getDeal().getId(), tranche.getId());
            reaction.apply(AnomalyFinding.builder()
                    .scope(HoldScope.EXCHANGE_ACCOUNT)
                    .rung(HoldRung.HARD)
                    .code(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED)
                    .instrument(context.getInstrument())
                    .hysteresisTicks(CONFIRMED_NEXT_TICK)
                    .journalOnly(false)
                    .build(), account);
            return;
        }
    }

    /**
     * Сумма gross-экспозиций траншей разошлась с нетто-размером живого
     * эпизода. Меньше — экспозиция, которую модель не приписывает ни
     * одному траншу; больше — часть нашей закрыта не нами. Оба
     * направления одинаково опасны: наш счёт экспозиции разошёлся с
     * биржей.
     */
    private void exposureMismatch(DealContext context, ExchangeAccount account) {
        Deal deal = context.getDeal();
        if (isEmpty(deal.getTranches())) {
            return;
        }
        if (isTrue(dealTerminalGate.exposureReconciled(deal.livePosition(), deal.getTranches()))) {
            return;
        }
        log.warn("Tranche exposure does not reconcile with the net size dealId={}", deal.getId());
        reaction.apply(AnomalyFinding.builder()
                .scope(HoldScope.EXCHANGE_ACCOUNT)
                .rung(HoldRung.HARD)
                .code(Constants.Hold.EXCHANGE_EXPOSURE_MISMATCH)
                .instrument(context.getInstrument())
                .hysteresisTicks(CONFIRMED_NEXT_TICK)
                .journalOnly(false)
                .build(), account);
    }

    /**
     * Живая сделка перестала укладываться в потолки, хотя её защита стои́т
     * и подтверждается. Форма реакции мягкая — принятый риск покрыт, и
     * рвать его нечем; жёсткая была бы платой рыночной цены без
     * основания. Когда покрытие нарушено, работает детектор выше, а не
     * этот.
     *
     * <p>Операнд — <b>вторая точка входа преконтроля</b>: те же
     * неравенства при нулевом акте
     * (docs/components/RiskValidator.md §«Что делает»). Собственных
     * величин детектор не заводит.
     */
    private void riskPolicyBreach(DealContext context, ExchangeAccount account) {
        if (isEmpty(riskValidator.ceilingsBreachedWithoutAct(context))) {
            return;
        }
        log.warn("Risk policy is breached under a standing protection dealId={}",
                context.getDeal().getId());
        reaction.apply(AnomalyFinding.builder()
                .scope(HoldScope.INSTRUMENT)
                .rung(HoldRung.SOFT)
                .code(Constants.Hold.RISK_POLICY_BREACH_UNDER_PROTECTION)
                .instrument(context.getInstrument())
                .hysteresisTicks(CONFIRMED_NEXT_TICK)
                .journalOnly(false)
                .build(), account);
    }
}

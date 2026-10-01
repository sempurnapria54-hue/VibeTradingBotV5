package com.example.tradingcore.domain.safety;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isNotFalse;
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskValidator;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.deal.ProtectionCoverageGate;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.util.Constants;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Детекторы инвариантов живой сделки: живой риск без покрытия ({@code A4}),
 * расхождение суммы экспозиций с нетто-размером эпизода ({@code A11}),
 * нарушение риск-политики при стоящей защите ({@code A12}) и ликвидация за
 * стопом у ведомой позиции ({@code A13}) (docs/components/AnomalyJob.md
 * §«Что ищет»).
 *
 * <p><b>Своих величин детекторы не заводят.</b> Первый читает предикат
 * покрытия транша, второй — сверку экспозиции, которой гейтится терминал
 * сделки, третий — те же неравенства потолков при нулевом акте, четвёртый —
 * предикат инварианта ликвидации на модели сделки. Второй дом у любой из
 * этих форм был бы копией, расходящейся первой же правкой.
 *
 * <p><b>Гейт полноты графа обязателен у всех четырёх.</b> На неполном
 * графе операнды занижены, и детектор МОЛЧИТ, а не рапортует: ложный
 * триггер первых двух сносит весь счёт, третьего — останавливает входы по
 * инструменту, четвёртого — снимает риск пары.
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

    /**
     * Обход нетерминальных сделок счёта.
     *
     * @param scan срез прохода: его строки по инструменту сделки едут во
     *             внешний снимок отчёта, и площадку второй раз не читают
     */
    public void detect(AnomalyScan scan, ExchangeAccount account) {
        for (Deal deal : dealDataService.findNonTerminalByExchangeAccountId(account.getId())) {
            try {
                DealContext context = dealContextService.build(deal);
                if (isNotTrue(context.getGraphComplete())) {
                    continue;
                }
                Map<String, Object> observed = observedRows(scan, context);
                uncoveredLiveRisk(context, account, observed);
                exposureMismatch(context, account, observed);
                riskPolicyBreach(context, account, observed);
                liquidationBeforeStop(context, account, observed);
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
    private void uncoveredLiveRisk(DealContext context, ExchangeAccount account,
                                   Map<String, Object> observed) {
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
                    .externalObservation(observed)
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
    private void exposureMismatch(DealContext context, ExchangeAccount account,
                                  Map<String, Object> observed) {
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
                .externalObservation(observed)
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
    private void riskPolicyBreach(DealContext context, ExchangeAccount account,
                                  Map<String, Object> observed) {
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
                .externalObservation(observed)
                .hysteresisTicks(CONFIRMED_NEXT_TICK)
                .journalOnly(false)
                .build(), account);
    }

    /**
     * {@code A13}: действующий уровень остановки убытка удерживаемой позиции
     * не лежит между ценой и ценой ликвидации, которую площадка называет у
     * живого эпизода. Преконтроль сверяет эту границу только на актах,
     * создающих риск, а ликвидация едет по мере удержания — маржа убывает на
     * финансировании, ставку тира меняет площадка; переоценку держит этот
     * детектор (docs/components/AnomalyJob.md §«Переоценка инварианта
     * ликвидации»).
     *
     * <p><b>Признак читается готовым с модели сделки</b> — оба операнда
     * лежат в её графе (docs/spec/risk-limits.json, величина
     * {@code heldStopBeforeLiquidation}). Пусто — не измерено, и детектор
     * МОЛЧИТ: у каждой пустой ветви свой хозяин, а ложный триггер снял бы
     * покрытый риск по рынку без факта.
     *
     * <p><b>Третья конъюнкта гейта — живое обязательство покрытия у
     * траншей сделки</b> (docs/spec/protection-coverage.json, величина
     * {@code hasLiveCommitment}): в окне замены защиты уровень на всю
     * позицию читается по худшей из двух, и прежняя, ещё не снятая, давала
     * бы признак, которого после замены не будет. Та же конъюнкта, что у
     * {@code A4}, — граница области, а не смягчение.
     *
     * <p><b>Реакция — снятие риска пары</b>: принятый риск стопом больше не
     * ограничен, а наш учёт цел — ликвидацию сдвинули операнды площадки.
     * Гистерезис два тика: налив и замена защиты производят признак нашим
     * же незавершённым ходом (docs/rules/instrument-hold.md §Триггеры).
     */
    private void liquidationBeforeStop(DealContext context, ExchangeAccount account,
                                       Map<String, Object> observed) {
        Deal deal = context.getDeal();
        if (isNotFalse(deal.heldStopBeforeLiquidation())) {
            return;
        }
        if (isTrue(anyLiveCommitment(context))) {
            return;
        }
        log.warn("Held stop is not ahead of the liquidation price dealId={} stop={} liquidation={}",
                deal.getId(), deal.currentStopLevel(), deal.livePosition().getExternalLiquidationPrice());
        reaction.apply(AnomalyFinding.builder()
                .scope(HoldScope.INSTRUMENT)
                .rung(HoldRung.HARD)
                .code(Constants.Hold.INSTRUMENT_LIQUIDATION_BEFORE_STOP)
                .instrument(context.getInstrument())
                .externalObservation(observed)
                .hysteresisTicks(CONFIRMED_NEXT_TICK)
                .journalOnly(false)
                .build(), account);
    }

    /** Хоть у одного транша сделки есть живое обязательство покрытия. */
    private Boolean anyLiveCommitment(DealContext context) {
        return emptyIfNull(context.getDeal().getTranches()).stream()
                .anyMatch(tranche -> isTrue(protectionCoverageGate.hasLiveCommitment(context, tranche)));
    }

    /**
     * Строки среза по инструменту сделки. Инструмента в контексте нет —
     * адресовать срез нечем, и снимок отчёта добывает площадку сам.
     */
    private Map<String, Object> observedRows(AnomalyScan scan, DealContext context) {
        Instrument instrument = context.getInstrument();
        return isNull(instrument) ? null : scan.observedRowsOf(instrument.getExternalId());
    }
}

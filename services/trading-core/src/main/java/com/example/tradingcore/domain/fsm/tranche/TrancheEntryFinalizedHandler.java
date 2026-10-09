package com.example.tradingcore.domain.fsm.tranche;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.deal.ProtectionCoverageGate;
import com.example.tradingcore.domain.fsm.DealTrancheHandler;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import com.example.tradingcore.domain.fsm.TrancheWorkPass;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Подтверждает, что вход транша завершён и его экспозиция открыта, и
 * определяет следующий безопасный путь
 * (docs/components/TrancheEntryFinalizedHandler.md).
 *
 * <p><b>Переход в статус переключения защиты не обязателен.</b> Стратегия
 * может требовать основную защиту, но не всякая заменяет ею встроенную:
 * переключение объявляет себя само — тем, что основная защита уже стои́т,
 * а встроенная ещё жива.
 *
 * <p><b>Встроенная защита уходит на биржу вместе с родительской заявкой,
 * но ставится площадкой на терминале родителя</b> — полном наливе либо
 * снятии остатка, — и на налитый объём. Окно, пока живая нога частично
 * налита, держится отложенным покрытием, а не стопом на площадке; к
 * подтверждённому входу оно закрыто: вход финализирован, и момент
 * постановки наступил (docs/rules/live-risk-protection.md, отложенное
 * покрытие встроенной защиты). Налив родителя доказательством постановки
 * не является — её доказывает найденная самостоятельная запись
 * (docs/lifecycles/Order.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrancheEntryFinalizedHandler implements DealTrancheHandler {

    private final TrancheWorkPass workPass;
    private final ProtectionCoverageGate coverageGate;

    @Override
    public DealTranche.Status handledStatus() {
        return DealTranche.Status.ENTRY_FINALIZED;
    }

    @Override
    public TrancheTransition handle(DealContext dealContext, DealTranche tranche) {
        Deal deal = dealContext.getDeal();
        if (isNull(dealContext.declarationOf(tranche)) || isTrue(deal.moreThanOneLiveEpisode())) {
            log.warn("Entry-finalized tranche in an impossible state dealId={} trancheId={}",
                    deal.getId(), tranche.getId());
            return TrancheTransition.escalate();
        }
        if (isFalse(deal.hasLivePositionRisk()) || isFalse(tranche.hasEntryFill())) {
            log.warn("Entry-finalized tranche has no live exposure dealId={} trancheId={}",
                    deal.getId(), tranche.getId());
            return TrancheTransition.escalate();
        }
        // Наблюдение покрытия: недопокрытие с живым обязательством — ход
        // продолжается, без обязательства — нарушение инварианта и биржевая
        // ступень 2 (docs/rules/live-risk-protection.md §«Реакция на непокрытый
        // риск»). Предикат читается по дому, а не пересобирается здесь.
        if (isTrue(coverageGate.trancheViolated(dealContext, tranche))) {
            log.error("Coverage invariant violated on a finalized entry dealId={} trancheId={}",
                    deal.getId(), tranche.getId());
            return TrancheTransition.escalate(
                    HoldSignal.exchangeAccount(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED));
        }
        TrancheTransition work = workPass.run(dealContext, tranche);
        if (isTrue(workPass.spoke(work))) {
            return work;
        }
        return exitCheck(dealContext, tranche);
    }

    /**
     * Выходная проверка: покрытие транша сошлось, работа исчерпана —
     * дальше либо сценарий переключения, либо прямо сопровождение.
     *
     * <p><b>Переключение объявляет себя составом защит:</b> основная
     * защита уже стои́т и встроенная ещё жива — значит замена состоялась и
     * встроенную предстоит снять. Иного носителя у вопроса «нужно ли
     * переключение» нет: объявление называет действия, а не сценарий.
     */
    private TrancheTransition exitCheck(DealContext dealContext, DealTranche tranche) {
        if (isFalse(tranche.isCovered())) {
            return TrancheTransition.stay();
        }
        boolean switching = isTrue(tranche.hasStandaloneProtection())
                && isFalse(tranche.liveAttachedProtections().isEmpty());
        return TrancheTransition.moveTo(switching
                ? DealTranche.Status.PROTECTION_SWITCHED
                : DealTranche.Status.MANAGING);
    }
}

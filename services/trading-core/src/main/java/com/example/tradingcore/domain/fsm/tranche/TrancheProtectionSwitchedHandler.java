package com.example.tradingcore.domain.fsm.tranche;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.fsm.DealTrancheHandler;
import com.example.tradingcore.domain.fsm.TrancheActionDisposition;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import com.example.tradingcore.domain.fsm.TrancheWorkPass;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Подтверждает сценарий переключения в транше: его встроенная защита
 * заменена подтверждённой основной
 * (docs/components/TrancheProtectionSwitchedHandler.md).
 *
 * <p><b>Основной защиты не осталось при живом риске — защита
 * потеряна.</b> Действующего обязательства здесь нет по построению: в
 * этот статус приводит только подтверждённая отправка защитного действия,
 * поэтому наблюдение попадает ровно во вторую строку реакции —
 * биржевая ступень 2 плюс ошибочная тропа сделки
 * (docs/rules/live-risk-protection.md §«Реакция на непокрытый риск»).
 *
 * <p><b>Названное ограничение: точечной отмены встроенной защиты этот
 * обработчик не эмитит.</b> Эмитента у команды
 * {@code CANCEL_ATTACHED_PROTECTION} сегодня нет ни одного, и это граница
 * кодирования, а не пробел концепции: перекрытие встроенной и основной
 * защиты безопасно — обе {@code reduce-only}, окна без защиты не
 * возникает (docs/rules/replace-not-amend.md). Следствие названо явно:
 * объявленный преконтроль снятия вызывающего не имеет и на живой тропе не
 * срабатывает. Условие снятия — ход, вводящий эмитент точечной отмены;
 * задача и владелец — .claude/work/backlog.md §«Названные ограничения
 * кодирования шага 7 — возврат по появлению носителя».
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrancheProtectionSwitchedHandler implements DealTrancheHandler {

    private final TrancheWorkPass workPass;
    private final TrancheActionDisposition disposition;

    @Override
    public DealTranche.Status handledStatus() {
        return DealTranche.Status.PROTECTION_SWITCHED;
    }

    @Override
    public TrancheTransition handle(DealContext dealContext, DealTranche tranche) {
        Deal deal = dealContext.getDeal();
        if (isTrue(deal.moreThanOneLiveEpisode())) {
            log.warn("More than one live episode on a switching tranche dealId={} trancheId={}",
                    deal.getId(), tranche.getId());
            return TrancheTransition.escalate();
        }
        if (isTrue(tranche.isRiskBearing()) && isFalse(tranche.hasStandaloneProtection())) {
            log.error("Main protection is gone while risk is live dealId={} trancheId={}",
                    deal.getId(), tranche.getId());
            return TrancheTransition.escalate(
                    HoldSignal.exchangeAccount(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED));
        }
        // Безопасный переход вперёд после рестарта: переключать нечего, а
        // позиция под контролем — транш идёт в сопровождение, не дожидаясь
        // сценария, которого не будет (docs/components/TrancheProtectionSwitchedHandler.md).
        if (isFalse(deal.hasLivePositionRisk()) && isFalse(tranche.isRiskBearing())) {
            return TrancheTransition.moveTo(DealTranche.Status.MANAGING);
        }
        TrancheTransition work = workPass.run(dealContext, tranche);
        if (isTrue(workPass.spoke(work))) {
            return work;
        }
        return isTrue(tranche.isCovered())
                ? TrancheTransition.moveTo(DealTranche.Status.MANAGING)
                : TrancheTransition.stay();
    }

}

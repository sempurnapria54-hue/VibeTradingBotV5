package com.example.tradingcore.domain.fsm.tranche;

import static java.math.BigDecimal.ZERO;
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
 * Сопровождает экспозицию транша по стратегии — основной рабочий статус
 * после его входа и постановки его защиты
 * (docs/components/TrancheManagingHandler.md).
 *
 * <p><b>У восстановленного транша нет ни объявления, ни детали, и обе
 * пустоты проверку не заваливают.</b> Он материализован вокруг
 * найденного живого риска, а не вокруг объявленного входа, и
 * сопровождение — единственный статус, в котором он живёт. Проверки,
 * стоящие на самих фактах, действуют на нём без послаблений.
 *
 * <p><b>Наблюдение нулевой экспозиции ветвится ОБЪЯВЛЕНИЕМ.</b>
 * Дискриминатор траншевый: нетто-размер позиции на сетке в ноль почти не
 * приходит, и наблюдение по нему было бы слепым. Пустое объявление
 * читается как ЗАПРЕТ переоткрытия — разрешающее прочтение отправило бы
 * транш открывать риск, которого никто не объявлял.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrancheManagingHandler implements DealTrancheHandler {

    private final TrancheWorkPass workPass;
    private final ProtectionCoverageGate coverageGate;

    @Override
    public DealTranche.Status handledStatus() {
        return DealTranche.Status.MANAGING;
    }

    @Override
    public TrancheTransition handle(DealContext dealContext, DealTranche tranche) {
        Deal deal = dealContext.getDeal();
        if (isTrue(deal.unattributedLiveRisk()) || isTrue(deal.moreThanOneLiveEpisode())) {
            log.warn("Foreign live risk on a managed deal dealId={} trancheId={}",
                    deal.getId(), tranche.getId());
            return TrancheTransition.escalate();
        }
        // Третий триггер выхода, и он разрывает круг: закрытие нетто-экспозиции
        // обязано идти ПОСЛЕ снятия живых входных заявок, снимает их только
        // обработчик выхода, а сам статус выхода прежде наступал лишь от
        // схлопнувшейся экспозиции — то есть от того самого закрытия
        // (docs/rules/exit-teardown-order.md).
        if (isTrue(deal.isCollapsing())) {
            return TrancheTransition.moveTo(DealTranche.Status.EXIT_PENDING);
        }
        if (tranche.exposure().compareTo(ZERO) == 0) {
            return collapsedExposure(dealContext, tranche);
        }
        // Наблюдение покрытия стои́т на НЕНУЛЕВОЙ экспозиции: сопровождение —
        // единственный статус, где она растёт добором, а основная защита
        // пересчитывается шагом стратегии (docs/rules/live-risk-protection.md).
        if (isTrue(coverageGate.trancheViolated(dealContext, tranche))) {
            log.error("Coverage invariant violated in managing dealId={} trancheId={}",
                    deal.getId(), tranche.getId());
            return TrancheTransition.escalate(
                    HoldSignal.exchangeAccount(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED));
        }
        TrancheTransition work = workPass.run(dealContext, tranche);
        return isTrue(workPass.spoke(work)) ? work : TrancheTransition.stay();
    }

    /**
     * Экспозиция транша схлопнулась: объявление разрешает переоткрытие —
     * транш проходит свою объявленную дорожку заново; запрещает — уходит в
     * свой выход и сворачивается штатно.
     *
     * <p><b>У восстановленного транша это не третья ветка, а вторая</b> и
     * не исход наблюдения, а постоянное состояние: заявок у него нет,
     * экспозиция нулевая всегда, переоткрывать нечего, и выход —
     * единственное, куда он из сопровождения уходит.
     *
     * <p><b>Инкремент номера эпизода делает не обработчик</b>, а машина —
     * той же транзакцией, которой применяется одобренное ребро
     * переоткрытия: признаки, чья область — эпизод, обязаны сбрасываться
     * вместе с ним (docs/rules/strategy-step-once-per-episode.md).
     */
    private TrancheTransition collapsedExposure(DealContext dealContext, DealTranche tranche) {
        if (isFalse(dealContext.reopenAllowed(tranche)) || isFalse(tranche.hasLiveEntryOrder())) {
            return TrancheTransition.moveTo(DealTranche.Status.EXIT_PENDING);
        }
        return TrancheTransition.moveTo(DealTranche.Status.ENTRY_SUBMITTED);
    }
}

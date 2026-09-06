package com.example.tradingcore.domain.deal;

import static java.math.BigDecimal.ZERO;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealContext;
import org.springframework.stereotype.Service;

/**
 * Нарушен ли инвариант покрытия транша. Исполнимая форма —
 * docs/spec/protection-coverage.json §{@code trancheViolated}; при
 * расхождении верна спека.
 *
 * <p><b>Третья конъюнкта — граница области, а не смягчение.</b> Без неё
 * предикат срабатывал бы на состоянии, которое спека называет законным и
 * достижимым: защита в постановке — экспозиция есть, уровня ещё нет,
 * обязательство живо (docs/rules/live-risk-protection.md §«Реакция на
 * непокрытый риск»).
 *
 * <p><b>Носитель один на всех читателей.</b> Наблюдают предикат три
 * тропы — подтверждение входа, переключение защиты и общий детектор
 * нарушений инвариантов, — и упрощённая копия у любой из них разошлась бы
 * с этой в разрешающую сторону.
 *
 * <p><b>Обязательство живёт на СТРОКЕ ИСПОЛНЕНИЯ, а не на модели
 * транша.</b> Поэтому предикат стои́т здесь, а не на доменной модели:
 * строки исполнения принадлежат ядру и в общий артефакт не уезжают.
 */
@Service
public class ProtectionCoverageGate {

    /**
     * У транша есть живое обязательство покрытия: нетерминальная строка
     * исполнения защитного действия с неисчерпанным бюджетом.
     *
     * <p>Отказавшая строка обязательством не является: попытки исчерпаны,
     * и доиграть надобность больше некому.
     */
    public Boolean hasLiveCommitment(DealContext dealContext, DealTranche tranche) {
        StrategyDetail detail = dealContext.getStrategyDetail();
        if (isNull(detail)) {
            return false;
        }
        for (DealActionState state : dealContext.liveStrategyActionStates(tranche)) {
            StrategyAction action = detail.actionById(state.getStrategyActionId());
            if (isTrue(protective(action))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Нарушение инварианта: экспозиция транша есть, покрытия нет и
     * обязательства покрытия нет тоже.
     */
    public Boolean trancheViolated(DealContext dealContext, DealTranche tranche) {
        return tranche.exposure().compareTo(ZERO) > 0
                && isFalse(tranche.isCovered())
                && isFalse(hasLiveCommitment(dealContext, tranche));
    }

    /** Действие ставит защиту: защитное объявление условной заявки. */
    private Boolean protective(StrategyAction action) {
        return action instanceof StrategyAlgoOrderAction algoAction && isTrue(algoAction.isProtective());
    }
}

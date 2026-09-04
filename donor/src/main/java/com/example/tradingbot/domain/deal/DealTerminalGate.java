package com.example.tradingbot.domain.deal;

import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.position.Position;
import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Гейт терминала сделки: доказано ли, что живого риска не осталось.
 * Исполнимая форма — docs/spec/deal-lifecycle.json §riskProvenAbsent;
 * этот класс её выражает, при расхождении верна спека.
 *
 * <p>Гейт вычисляется ВСЕГДА на состоянии «живого эпизода нет» — он и
 * стои́т в конъюнкции с ним, — поэтому каждый его операнд обязан быть
 * тотальным на этом состоянии. Живого эпизода не бывает двумя штатными
 * способами, и оба сюда доезжают: строки позиции нет вовсе (вход отвергнут
 * либо не дал движения — самая частая тропа) либо строка закрыта и доживает
 * с НЕОБНУЛЁННЫМ размером (externalSize пишется только из снимка, при
 * закрытии снимка нет, обнулителя поля в системе нет ни одного).
 *
 * <p>Контракты результата (cleanTerminalContract /
 * emergencyTerminalContract) сюда пока не входят: их операнды
 * ({@code hadEntry}, {@code resultProfitFinalized}, доступность итога) —
 * поля финализации P&L, которых модель сделки ещё не несёт. Гейт живого
 * риска от них независим и работает без них.
 *
 * <p>См. docs/models/domain/aggregate/Deal.md §«Экспозиция сделки и сверка
 * с биржей», docs/components/DealStateMachine.md.
 */
@Slf4j
@Service
public class DealTerminalGate {

    /**
     * Живой эпизод: позиция сделки несёт рыночный риск. Ровно этот
     * предикат удостоверяет ноль в правой стороне сверки экспозиции —
     * подстановка опирается на него, а не на пустоту операнда.
     */
    public Boolean hasLiveEpisode(Position position) {
        return nonNull(position) && isTrue(position.hasLiveRisk());
    }

    /** Сумма экспозиций траншей сделки. */
    public BigDecimal dealExposure(List<DealTranche> tranches) {
        return emptyIfNull(tranches).stream()
                .map(DealTranche::exposure)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Сверка модели с биржевым фактом: сумма экспозиций траншей равна
     * нетто-размеру ЖИВОГО эпизода, а при его отсутствии — нулю. Ноль
     * здесь ФАКТ «живой экспозиции на бирже нет», а не умолчание: его
     * удостоверяет {@link #hasLiveEpisode}. Расхождение означает живой
     * риск, который модель не приписывает ни одному траншу.
     */
    public Boolean exposureReconciled(Position position, List<DealTranche> tranches) {
        BigDecimal netSize = isTrue(hasLiveEpisode(position))
                ? position.getExternalSize()
                : BigDecimal.ZERO;
        return dealExposure(tranches).compareTo(netSize) == 0;
    }

    /** Все транши сделки терминальны. */
    public Boolean allTranchesTerminal(List<DealTranche> tranches) {
        return emptyIfNull(tranches).stream()
                .allMatch(tranche -> isTrue(tranche.isTerminal()));
    }

    /** Хоть один транш сделки несёт живой риск. */
    public Boolean dealRiskBearing(List<DealTranche> tranches) {
        return emptyIfNull(tranches).stream()
                .anyMatch(tranche -> isTrue(tranche.isRiskBearing()));
    }

    /** У сделки есть живая заявка любого рода — в любом её транше. */
    public Boolean anyLiveOrder(List<DealTranche> tranches) {
        return emptyIfNull(tranches).stream()
                .anyMatch(tranche -> !tranche.liveOrders().isEmpty()
                        || !tranche.liveAlgoOrders().isEmpty());
    }

    /**
     * Живой риск доказанно отсутствует: граф исполнения полон, живого
     * эпизода нет, риска не несёт ни один транш, живых заявок нет и
     * экспозиция сходится с биржей. Ложь любого конъюнкта означает, что
     * терминал ставить рано.
     */
    public Boolean riskProvenAbsent(Deal deal, List<DealTranche> tranches, Boolean graphComplete) {
        if (isFalse(graphComplete)) {
            return false;
        }
        Position position = deal.livePosition();
        if (isTrue(hasLiveEpisode(position))) {
            return false;
        }
        if (isTrue(dealRiskBearing(tranches)) || isTrue(anyLiveOrder(tranches))) {
            return false;
        }
        boolean reconciled = isTrue(exposureReconciled(position, tranches));
        if (isFalse(reconciled)) {
            log.debug("Exposure is not reconciled dealId={} dealExposure={}",
                    deal.getId(), dealExposure(tranches));
        }
        return reconciled;
    }
}

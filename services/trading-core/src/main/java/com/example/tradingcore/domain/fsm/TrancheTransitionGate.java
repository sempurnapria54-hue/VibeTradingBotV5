package com.example.tradingcore.domain.fsm;

import static java.math.BigDecimal.ZERO;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Гейт статусного ребра транша: объявлено ли ребро, разрешено ли им взять
 * новый риск и доказано ли отсутствие риска у терминала. Исполнимая форма
 * — docs/spec/deal-tranche-lifecycle.json; при расхождении верна спека.
 *
 * <p><b>Носитель один на всех читателей.</b> Матрицу читают машина транша
 * и каждый обработчик, предлагающий ребро; второй, «упрощённый» перечень
 * рёбер у обработчика разошёлся бы с этим в разрешающую сторону.
 *
 * <p><b>Энфорсер запрета нового риска стои́т на ЦЕЛИ перехода, а не на
 * паре.</b> В отправленный вход приходят два разных ребра — штатный вход
 * из предвходовой проверки и переоткрытие из сопровождения, — и гейт по
 * паре пришлось бы дублировать (docs/rules/exit-teardown-order.md).
 */
@Slf4j
@Service
public class TrancheTransitionGate {

    /**
     * Объявленные рёбра: сегодняшняя матрица сделки минус ошибочные
     * статусы плюс ребро переоткрытия
     * (docs/lifecycles/DealTranche.md §«Матрица переходов»).
     */
    private static final Map<DealTranche.Status, Set<DealTranche.Status>> DECLARED_EDGES =
            new EnumMap<>(DealTranche.Status.class);

    static {
        DECLARED_EDGES.put(DealTranche.Status.PRECHECK,
                Set.of(DealTranche.Status.ENTRY_SUBMITTED, DealTranche.Status.CLOSED));
        DECLARED_EDGES.put(DealTranche.Status.ENTRY_SUBMITTED,
                Set.of(DealTranche.Status.ENTRY_FINALIZED, DealTranche.Status.EXIT_PENDING,
                        DealTranche.Status.CLOSED));
        DECLARED_EDGES.put(DealTranche.Status.ENTRY_FINALIZED,
                Set.of(DealTranche.Status.PROTECTION_SWITCHED, DealTranche.Status.MANAGING));
        DECLARED_EDGES.put(DealTranche.Status.PROTECTION_SWITCHED,
                Set.of(DealTranche.Status.MANAGING));
        DECLARED_EDGES.put(DealTranche.Status.MANAGING,
                Set.of(DealTranche.Status.ENTRY_SUBMITTED, DealTranche.Status.EXIT_PENDING));
        DECLARED_EDGES.put(DealTranche.Status.EXIT_PENDING,
                Set.of(DealTranche.Status.CLOSED));
        DECLARED_EDGES.put(DealTranche.Status.CLOSED, Set.of());
    }

    /** Ребро объявлено матрицей жизненного цикла транша. */
    public Boolean edgeDeclared(DealTranche.Status from, DealTranche.Status to) {
        if (isNull(from) || isNull(to)) {
            return false;
        }
        return DECLARED_EDGES.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Ребро переоткрытия эпизода: сопровождение → отправленный вход.
     * Отдельная величина, потому что охрана у него своя.
     */
    public Boolean reopenEdge(DealTranche.Status from, DealTranche.Status to) {
        return DealTranche.Status.MANAGING.equals(from) && DealTranche.Status.ENTRY_SUBMITTED.equals(to);
    }

    /**
     * Переоткрытие разрешено: объявление допускает, экспозиция транша
     * схлопнулась, живая входная нога осталась.
     *
     * <p>Дискриминатор <b>траншевый</b>: нетто-размер позиции на сетке в
     * ноль почти не приходит, и охрана по нему была бы слепой.
     */
    public Boolean reopenPermitted(DealContext dealContext, DealTranche tranche) {
        return isTrue(dealContext.reopenAllowed(tranche))
                && tranche.exposure().compareTo(ZERO) == 0
                && isTrue(tranche.hasLiveEntryOrder());
    }

    /**
     * Ребро набирает новый риск посреди сворачивания сделки.
     *
     * <p><b>Окно читается одним статусом.</b> Вторая его половина —
     * ошибочное состояние — закрыта НЕДОСТИЖИМОСТЬЮ акта: FSM траншей там
     * не прогоняется вовсе (docs/processes/fsm-execution-layering.md),
     * поэтому энфорсер её не мерит и мерить не обязан.
     */
    public Boolean riskCreatingUnderCollapse(Deal deal, DealTranche.Status to) {
        return DealTranche.Status.ENTRY_SUBMITTED.equals(to) && isTrue(deal.isCollapsing());
    }

    /**
     * Контракт терминала транша: граф предъявлен целиком И живого риска у
     * транша нет.
     *
     * <p><b>Первый конъюнкт — охрана полнотой графа</b>, симметричная
     * охране терминала сделки. Без неё недогруженная живая заявка или
     * защита делает «риска нет» ложным <b>молча</b>: транш уходит в
     * терминал, обработчика у него больше нет, и ошибка разрешающая и
     * необратимая (docs/lifecycles/DealTranche.md §«Терминал транша»).
     */
    public Boolean terminalContract(DealTranche tranche, Boolean graphComplete) {
        return isTrue(graphComplete) && isFalse(tranche.isRiskBearing());
    }

    /**
     * Переход разрешён целиком: ребро объявлено, нового риска под
     * сворачиванием не берёт, переоткрытие охранено своими тремя
     * условиями, терминал — своим контрактом.
     */
    public Boolean transitionAllowed(DealContext dealContext, DealTranche tranche, DealTranche.Status to) {
        DealTranche.Status from = tranche.getStatus();
        if (isFalse(edgeDeclared(from, to))) {
            log.warn("Tranche edge is not declared trancheId={} from={} to={}", tranche.getId(), from, to);
            return false;
        }
        if (isTrue(riskCreatingUnderCollapse(dealContext.getDeal(), to))) {
            log.warn("Tranche takes new risk under deal collapse trancheId={} to={}", tranche.getId(), to);
            return false;
        }
        if (isTrue(reopenEdge(from, to)) && isFalse(reopenPermitted(dealContext, tranche))) {
            return false;
        }
        return isFalse(DealTranche.Status.CLOSED.equals(to))
                || isTrue(terminalContract(tranche, dealContext.getGraphComplete()));
    }
}

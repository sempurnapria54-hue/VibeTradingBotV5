package com.example.tradingcore.domain.fsm.tranche;

import static java.math.BigDecimal.ZERO;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandPayload;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.payload.CancelAlgoOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.CancelAttachedProtectionCommandPayload;
import com.example.tradingcore.domain.command.payload.CancelOrderCommandPayload;
import com.example.tradingcore.domain.fsm.DealTrancheHandler;
import com.example.tradingcore.domain.fsm.TrancheActionDisposition;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import com.example.tradingcore.domain.fsm.TrancheWorkPass;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Сворачивает транш после инициированного выхода: снимает его живую
 * входную заявку, закрывает его экспозицию собственным reduce-only
 * размером, снимает его живые защиты — в порядке инварианта
 * (docs/components/TrancheExitPendingHandler.md).
 *
 * <p><b>Полное закрытие нетто-экспозиции этот обработчик не эмитит</b> —
 * оно уровня сделки и законно только при выходе всех траншей.
 *
 * <p><b>Своей reduce-only ноги под каскадом сворачивания сделки транш не
 * выпускает.</b> Экспозицию в этом случае закрывает одно закрытие уровня
 * сделки, а траншу она гасится приписанным объёмом; иначе на общем выходе
 * экспозицию гасили бы дважды — N ног плюс одна команда.
 *
 * <p><b>Защиты транша снимаются только при нулевой экспозиции.</b> Пока
 * экспозиция есть, проход её наблюдает, а под сворачиванием сделки при
 * живом риске позиции молчит вовсе: закрытие ведёт обработчик сделки, и
 * снятый раньше стоп оставил бы позицию голой до налива закрытия
 * (docs/rules/exit-teardown-order.md §«Защиты снимаются последними»).
 *
 * <p><b>Снятие едет с добычей снимаемой сущности.</b> Приём снятия
 * фактом не является, а живость сущности резолвит только добыча; поэтому
 * к каждому снятию проход добавляет добычу этой сущности — у заявки вместе
 * с позицией: её налив меняет экспозицию, и наблюдённый без позиции он
 * развёл бы сверку. Снятие, чьё намерение уже стоит, не повторяется —
 * сущность только наблюдается. Иначе проход возвращал бы снятие заново и
 * до наблюдения не доходил никогда.
 *
 * <p><b>Дочистка идёт напрямую, без анкера:</b> исполнения-действия у неё
 * нет, а значит нет и бюджета отказов; неснятую сущность подберёт
 * следующий проход. Преконтроль риска дочистка не проходит
 * (docs/rules/risk-validator-scope.md).
 *
 * <p><b>Восстановленный транш сюда доходит, и обе пустоты — объявления и
 * детали — проверку не заваливают:</b> и порядок снятия, и подтверждение
 * отсутствия живого риска стоя́т на собственных фактах транша.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrancheExitPendingHandler implements DealTrancheHandler {

    private final TrancheWorkPass workPass;
    private final TrancheActionDisposition disposition;

    @Override
    public DealTranche.Status handledStatus() {
        return DealTranche.Status.EXIT_PENDING;
    }

    @Override
    public TrancheTransition handle(DealContext dealContext, DealTranche tranche) {
        Deal deal = dealContext.getDeal();
        if (isTrue(deal.moreThanOneLiveEpisode())) {
            log.warn("More than one live episode on an exiting tranche dealId={} trancheId={}",
                    deal.getId(), tranche.getId());
            return TrancheTransition.escalate();
        }
        // Порядок инварианта: сперва живая ВХОДНАЯ нога транша, затем его
        // экспозиция, затем остальные его живые сущности
        // (docs/rules/exit-teardown-order.md).
        Order entryLeg = liveEntryLeg(tranche);
        if (nonNull(entryLeg)) {
            return cancelOrder(dealContext, tranche, entryLeg);
        }
        TrancheTransition exposure = closeExposure(dealContext, tranche);
        if (isTrue(workPass.spoke(exposure))) {
            return exposure;
        }
        if (tranche.exposure().signum() > 0) {
            return observeExposure(dealContext, tranche);
        }
        TrancheTransition remaining = cancelRemaining(dealContext, tranche);
        if (nonNull(remaining)) {
            return remaining;
        }
        return terminalCheck(dealContext, tranche);
    }

    /**
     * Живая входная нога — первый ход инварианта: пока она жива, закрытие
     * экспозиции гонялось бы за наливом.
     */
    private Order liveEntryLeg(DealTranche tranche) {
        return tranche.liveOrders().stream()
                .filter(order -> isFalse(order.getPositionReducingOnly()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Закрытие собственной экспозиции транша — его объявленным
     * reduce-only выходом.
     *
     * <p><b>Под каскадом сворачивания сделки ветвь не работает:</b>
     * экспозицию там гасит закрытие уровня сделки, а траншу она
     * приписывается правилом сопоставления
     * (docs/models/domain/aggregate/DealTranche.md). Собственный
     * reduce-only остаётся формой САМОСТОЯТЕЛЬНОГО выхода транша.
     */
    private TrancheTransition closeExposure(DealContext dealContext, DealTranche tranche) {
        if (isTrue(dealContext.getDeal().isCollapsing()) || tranche.exposure().compareTo(ZERO) == 0) {
            return TrancheTransition.stay();
        }
        return workPass.run(dealContext, tranche);
    }

    /**
     * Экспозиция есть, а работы нет: защиты остаются, проход наблюдает
     * закрытие.
     *
     * <p><b>Под сворачиванием сделки при живом риске позиции — пустой
     * исход.</b> Закрытие ведёт обработчик сделки одним закрытием
     * нетто-экспозиции и сам же его наблюдает; просьба транша заняла бы
     * каскад, и закрытие уровня сделки не эмитилось бы никогда.
     *
     * <p>Иначе наблюдаются живые заявки транша — после снятия входной ноги
     * это его reduce-only ноги, чей налив и гасит экспозицию, — вместе с
     * позицией, а без них одна позиция.
     */
    private TrancheTransition observeExposure(DealContext dealContext, DealTranche tranche) {
        Deal deal = dealContext.getDeal();
        if (isTrue(deal.isCollapsing()) && isTrue(deal.hasLivePositionRisk())) {
            return TrancheTransition.stay();
        }
        TrancheTransition observation = TrancheTransition.stay();
        for (Order live : tranche.liveOrders()) {
            observation = observation.withObservation(
                    disposition.orderFetch(dealContext, live.getId()).orElse(null));
        }
        return observation.withObservation(disposition.positionFetch(dealContext).orElse(null));
    }

    /**
     * Снятие остальных живых сущностей транша — последний ход инварианта, и
     * идёт он только при нулевой экспозиции: условная заявка переживает
     * собственную экспозицию, и оставленная на бирже, она откроет позицию в
     * обратную сторону. Пусто — снимать нечего.
     *
     * <p>Ход берёт <b>остаточные живые заявки</b> — reduce-only ноги, не
     * дошедшие до налива: снятие входной ноги их не адресует по построению,
     * а терминал транша их и не гейтит (docs/spec/protection-coverage.json,
     * величина {@code trancheRiskBearing}); затем отдельные условные
     * заявки; затем <b>встроенные защиты</b>. Встроенная при непустом
     * наливе родителя живёт на бирже самостоятельной заявкой и переживает
     * его терминал (docs/models/domain/core/Order.md §«Встроенная защита»):
     * без этого хода она осталась бы за закрытым траншем.
     */
    private TrancheTransition cancelRemaining(DealContext dealContext, DealTranche tranche) {
        Order liveOrder = tranche.liveOrders().stream().findFirst().orElse(null);
        if (nonNull(liveOrder)) {
            return cancelOrder(dealContext, tranche, liveOrder);
        }
        AlgoOrder liveAlgo = tranche.liveAlgoOrders().stream().findFirst().orElse(null);
        if (nonNull(liveAlgo)) {
            return cancelAlgoOrder(dealContext, tranche, liveAlgo);
        }
        AttachedAlgoOrder liveAttached = tranche.liveAttachedProtections().stream().findFirst().orElse(null);
        if (nonNull(liveAttached)) {
            return cancelAttached(dealContext, tranche, liveAttached);
        }
        return null;
    }

    /** Снятие заявки, если намерения ещё нет, и её добыча вместе с позицией. */
    private TrancheTransition cancelOrder(DealContext dealContext, DealTranche tranche, Order order) {
        TrancheTransition cancel = isNull(order.getCloseReason())
                ? TrancheTransition.command(command(ServiceCommandType.CANCEL_ORDER_COMMAND, tranche,
                        new CancelOrderCommandPayload(order.getId(), Order.CloseReason.CANCELED_BY_STRATEGY)))
                : TrancheTransition.stay();
        return cancel.withObservation(disposition.orderFetch(dealContext, order.getId()).orElse(null))
                .withObservation(disposition.positionFetch(dealContext).orElse(null));
    }

    /** Снятие отдельной условной заявки, если намерения ещё нет, и её добыча. */
    private TrancheTransition cancelAlgoOrder(DealContext dealContext, DealTranche tranche, AlgoOrder algo) {
        TrancheTransition cancel = isNull(algo.getCloseReason())
                ? TrancheTransition.command(command(ServiceCommandType.CANCEL_ALGO_ORDER_COMMAND, tranche,
                        new CancelAlgoOrderCommandPayload(algo.getId(), AlgoOrder.CloseReason.CANCELED_BY_STRATEGY)))
                : TrancheTransition.stay();
        return cancel.withObservation(disposition.algoOrderFetch(dealContext, algo.getId()).orElse(null));
    }

    /**
     * Снятие встроенной защиты, если намерения ещё нет, и добыча её
     * РОДИТЕЛЯ: судьбу встроенной резолвит добыча родительской заявки
     * (docs/components/KillSwitchExecutor.md §Подтверждение).
     */
    private TrancheTransition cancelAttached(DealContext dealContext, DealTranche tranche,
                                             AttachedAlgoOrder attached) {
        TrancheTransition cancel = isNull(attached.getCloseReason())
                ? TrancheTransition.command(command(ServiceCommandType.CANCEL_ATTACHED_PROTECTION_COMMAND,
                        tranche, new CancelAttachedProtectionCommandPayload(attached.getId(),
                                AttachedAlgoOrder.CloseReason.CANCELED_BY_STRATEGY)))
                : TrancheTransition.stay();
        return cancel.withObservation(disposition.orderFetch(dealContext, attached.getOrderId()).orElse(null));
    }

    /**
     * Терминал транша: живого риска нет и это подтверждено, а граф
     * предъявлен целиком. Причину пишет тот же ход, что ставит статус.
     *
     * <p>Полноту графа охраняет матрица переходов
     * (docs/spec/deal-tranche-lifecycle.json §{@code terminalContract});
     * здесь спрашивается только собственный риск транша — второй носитель
     * охраны разошёлся бы с первым.
     */
    private TrancheTransition terminalCheck(DealContext dealContext, DealTranche tranche) {
        if (isTrue(tranche.isRiskBearing())) {
            return disposition.contextFetch(dealContext);
        }
        return TrancheTransition.close(closeReason(dealContext, tranche));
    }

    /**
     * Причина закрытия транша — по инициатору выхода.
     *
     * <p><b>Инициатора нет вовсе — {@code EXTERNAL_CLOSE}:</b> экспозиция
     * обнулилась вне нашего ведения, и ни одно из наличных значений этого
     * не описывает. Так у восстановленного транша, чей штатный конец — тот
     * же выход, и у обычного, чью позицию закрыли на бирже руками
     * (docs/lifecycles/DealTranche.md).
     */
    private DealTranche.CloseReason closeReason(DealContext dealContext, DealTranche tranche) {
        if (isTrue(dealContext.getDeal().isCollapsing())) {
            return disposition.inheritedCloseReason(dealContext.getDeal());
        }
        DealTranche.CloseReason initiated = tranche.exitInitiatedReason();
        return isNull(initiated) ? DealTranche.CloseReason.EXTERNAL_CLOSE : initiated;
    }

    /**
     * Дочистка адресует сделку и цель, но анкера не несёт: строки
     * исполнения у неё нет, а значит нет и учёта повторов.
     */
    private ServiceCommand command(ServiceCommandType type, DealTranche tranche,
                                   ServiceCommandPayload payload) {
        return ServiceCommand.builder()
                .type(type)
                .dealId(tranche.getDealId())
                .payload(payload)
                .build();
    }
}

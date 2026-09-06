package com.example.tradingcore.domain.fsm.tranche;

import static java.math.BigDecimal.ZERO;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandPayload;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.payload.CancelAlgoOrderCommandPayload;
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
        ServiceCommand entryCancel = cancelLiveEntryLeg(tranche);
        if (nonNull(entryCancel)) {
            return TrancheTransition.command(entryCancel);
        }
        TrancheTransition exposure = closeExposure(dealContext, tranche);
        if (isTrue(workPass.spoke(exposure))) {
            return exposure;
        }
        ServiceCommand protectionCancel = cancelRemaining(tranche);
        if (nonNull(protectionCancel)) {
            return TrancheTransition.command(protectionCancel);
        }
        return terminalCheck(dealContext, tranche);
    }

    /**
     * Снятие живой входной ноги — первый ход инварианта: пока она жива,
     * закрытие экспозиции гонялось бы за наливом.
     */
    private ServiceCommand cancelLiveEntryLeg(DealTranche tranche) {
        return tranche.liveOrders().stream()
                .filter(order -> isFalse(order.getPositionReducingOnly()))
                .findFirst()
                .map(order -> command(ServiceCommandType.CANCEL_ORDER_COMMAND, tranche,
                        new CancelOrderCommandPayload(order.getId(), Order.CloseReason.CANCELED_BY_STRATEGY)))
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
     * Снятие остальных живых сущностей транша — последний ход инварианта:
     * условная заявка переживает собственную экспозицию, и оставленная на
     * бирже, она откроет позицию в обратную сторону.
     *
     * <p>Ход берёт и <b>остаточные живые заявки</b> — reduce-only ноги,
     * не дошедшие до налива: снятие входной ноги их не адресует по
     * построению, а терминал транша их и не гейтит
     * (docs/spec/protection-coverage.json, величина
     * {@code trancheRiskBearing}) — то есть без этого хода они остались бы
     * на бирже за закрытым траншем.
     */
    private ServiceCommand cancelRemaining(DealTranche tranche) {
        Order liveOrder = tranche.liveOrders().stream().findFirst().orElse(null);
        if (nonNull(liveOrder)) {
            return command(ServiceCommandType.CANCEL_ORDER_COMMAND, tranche,
                    new CancelOrderCommandPayload(liveOrder.getId(), Order.CloseReason.CANCELED_BY_STRATEGY));
        }
        return tranche.liveAlgoOrders().stream()
                .findFirst()
                .map(algo -> command(ServiceCommandType.CANCEL_ALGO_ORDER_COMMAND, tranche,
                        new CancelAlgoOrderCommandPayload(algo.getId(), AlgoOrder.CloseReason.CANCELED_BY_STRATEGY)))
                .orElse(null);
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

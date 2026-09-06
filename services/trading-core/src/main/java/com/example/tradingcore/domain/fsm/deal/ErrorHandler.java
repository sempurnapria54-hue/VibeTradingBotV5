package com.example.tradingcore.domain.fsm.deal;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.action.SystemActionExecutor;
import com.example.tradingcore.domain.fsm.DealHandler;
import com.example.tradingcore.domain.fsm.DealTransition;
import com.example.tradingcore.domain.fsm.DealTransitionGate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ведёт сделку в ошибочном состоянии: авария обнаружена, обычная логика
 * заблокирована, риск может быть ещё живым. Разрешены только safety,
 * восстановление и проверка фактов (docs/components/ErrorHandler.md).
 *
 * <p><b>FSM траншей в этом статусе не гоняется</b> — единственный из трёх
 * обработчиков сделки, который её не гоняет: набор риска остановлен по
 * всем траншам сразу, и терминальность траншей на этой тропе не требуется
 * (docs/processes/fsm-execution-layering.md §«Кто прогоняет FSM транша»).
 *
 * <p><b>Снятие риска ведёт координатор холда, а не этот обработчик.</b>
 * Килл-свич стои́т вне слоёв и петле не подчинён: то, что снимает риск, не
 * должно быть остановимо тем, что ограничивает его набор
 * (docs/rules/execution-hierarchy.md). Здесь — добыча фактов и гейт
 * аварийного терминала.
 *
 * <p><b>Есть тропа, на которой подтверждение не придёт по построению:</b>
 * когда источник отверг наши креды, ни добыть факты, ни дождаться снятия
 * нельзя, и сделка остаётся в ошибочном состоянии до восстановления
 * доступа. Это названное ограничение реакции биржевой ступени
 * (docs/rules/exchange-hold.md), а не зацикливание обработчика.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorHandler implements DealHandler {

    private final DealTransitionGate transitionGate;
    private final SystemActionExecutor systemActionExecutor;

    @Override
    public Deal.Status handledStatus() {
        return Deal.Status.ERROR;
    }

    @Override
    public DealTransition handle(DealContext dealContext) {
        if (isFalse(transitionGate.emergencyTerminalContract(dealContext))) {
            return harvest(dealContext);
        }
        return emergencyTerminal(dealContext);
    }

    /**
     * Добыча фактов о живом риске звеном системного действия: пока
     * отсутствие риска не доказано, обработчик остаётся в ошибочном
     * состоянии.
     */
    private DealTransition harvest(DealContext dealContext) {
        return systemActionExecutor.next(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION, dealContext, null)
                .map(DealTransition.stay()::withCommand)
                .orElseGet(DealTransition::stay);
    }

    /**
     * Аварийный терминал: обработчик его ЗАТРЕБУЕТ, ребро пишет звено в
     * одной транзакции со своим завершением.
     *
     * <p><b>Причину закрытия на этом ребре пишет он же</b> — значением
     * {@code EMERGENCY_CLOSE}, той же транзакцией, которой затребует
     * ребро: терминальное звено причину не пишет, оно ребро применяет
     * (docs/lifecycles/Deal.md).
     */
    private DealTransition emergencyTerminal(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isNull(deal.getCloseReason())) {
            deal.setCloseReason(Deal.CloseReason.EMERGENCY_CLOSE);
        }
        if (isTrue(deal.isTerminal())) {
            return DealTransition.stay();
        }
        return systemActionExecutor.next(SystemActionType.FINALIZE_DEAL_ERROR_ACTION, dealContext, null)
                .map(DealTransition.stay()::withCommand)
                .orElseGet(DealTransition::stay);
    }
}

package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Применяет ребро сделки в ошибочное состояние — первое звено аварийного
 * системного действия (docs/components/MarkDealErrorExecutor.md).
 *
 * <p>Статус нетерминален: он передаёт сделку аварийному обработчику.
 * Аварийный терминал — <b>отдельное исполнение</b> того же действия, и
 * разбор до него ведёт обработчик ошибочного состояния.
 *
 * <p><b>Не всякое ребро в ошибку едет через это звено:</b> на тропе
 * ПЕРЕХВАТА — энфорсмент ступени, контролируемое исключение, граница
 * исполнения прохода — статус пишет петля прямой записью, без действия и
 * без анкера (docs/processes/fsm-execution-layering.md). Там нужен
 * гарантированный перевод, а не ещё одна точка отказа.
 *
 * <p><b>Причина закрытия на этом ребре не пишется:</b> ошибочное состояние
 * терминалом не является, итоговая причина ещё не определена
 * (docs/lifecycles/Deal.md).
 *
 * <p><b>Ребро пишется ТОЧЕЧНЫМ гардированным запросом</b> — тем же, что
 * и у перехвата петли: троп две, а запись одна, и обе причины не пишут
 * ({@code DealRepository.applyErrorEdge}). Гард — активные статусы:
 * сделка, уже уведённая каскадом ступени либо терминализованная, ребра не
 * получает, и модель тогда не сдвигается тоже — иначе граф прохода
 * объявлял бы ошибочным то, что в базе закрыто.
 *
 * <p><b>Энфорсеров счёта звено не двигает:</b> ни серии убытков, ни базы
 * риска — сделка идёт дальше аварийной тропой и будет учтена аварийным
 * терминалом ровно один раз (docs/rules/loss-streak-halt.md).
 *
 * <p>Звено локальное: наружу не ходит и сущностей не заводит, поэтому своё
 * исполнение завершает прямым ребром {@code PLANNED → COMPLETED}.
 */
@Component
@RequiredArgsConstructor
public class MarkDealErrorExecutor implements CommandExecutor {

    private final DealDataService dealDataService;
    private final DealActionStateDataService dealActionStateDataService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.MARK_DEAL_ERROR_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isFalse(Deal.Status.ERROR.equals(deal.getStatus()))
                && isTrue(dealDataService.applyErrorEdge(deal.getId()))) {
            deal.setStatus(Deal.Status.ERROR);
        }
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.COMPLETED);
            dealActionStateDataService.save(actionState);
        }
        return ServiceCommandExecutionResult.ok();
    }
}

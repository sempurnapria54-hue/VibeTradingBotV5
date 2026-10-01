package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.util.InternalIdFactory;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.TargetEntityType;
import com.example.tradingcore.domain.command.payload.CreateAlgoOrderCommandPayload;
import com.example.tradingcore.domain.command.risk.DealRiskNumbersService;
import com.example.tradingcore.integration.internal.event.CoreEventWriter;
import com.example.tradingcore.persistence.service.AlgoOrderDataService;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Создаёт локальную отдельную условную заявку: клиентский идентификатор,
 * рассчитанные параметры условия, цель строки исполнения и её статус. На
 * площадку не ходит (docs/components/CreateAlgoOrderExecutor.md).
 *
 * <p><b>Планового риска ноги здесь не пишется:</b> входной тропы условной
 * заявкой не существует — все роды условия защитные либо закрывающие.
 *
 * <p><b>Четвёрку чисел риска пересчитывает.</b> Постановка защиты меняет
 * операнд четвёртого числа — уровень действующей защиты, — а числа
 * пересчитываются целиком тем, кто меняет любой их операнд.
 *
 * <p><b>Писатель класса {@code ALGO_ORDER_DECIDED}:</b> решение о защите,
 * стоящей отдельной заявкой, иначе журналу не видно вовсе.
 */
@Component
@RequiredArgsConstructor
public class CreateAlgoOrderExecutor implements CommandExecutor {

    private final AlgoOrderDataService algoOrderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final DealRiskNumbersService dealRiskNumbersService;
    private final CoreEventWriter coreEventWriter;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.CREATE_ALGO_ORDER_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        CreateAlgoOrderCommandPayload payload = (CreateAlgoOrderCommandPayload) command.getPayload();
        AlgoOrder algoOrder = resolveTarget(actionState, payload, dealContext);
        actionState.targetAt(TargetEntityType.ALGO_ORDER, algoOrder.getId());
        actionState.setStatus(DealActionStateStatus.CREATED);
        dealActionStateDataService.save(actionState);
        appendToGraph(dealContext.getDeal(), algoOrder);
        if (isFalse(dealRiskNumbersService.recompute(dealContext))) {
            return ServiceCommandExecutionResult.notCompleted(
                    "Deal graph incomplete: risk numbers not recomputed for algo order " + algoOrder.getId());
        }
        return ServiceCommandExecutionResult.ok();
    }

    /**
     * Локальная сущность звена: уже заведённая либо новая. Повтор до
     * завершения работает с тем же клиентским идентификатором — анкер
     * повтора и есть цель строки исполнения.
     */
    private AlgoOrder resolveTarget(DealActionState actionState, CreateAlgoOrderCommandPayload payload,
                                    DealContext dealContext) {
        if (nonNull(actionState.getTargetEntityId())
                && TargetEntityType.ALGO_ORDER.equals(actionState.getTargetEntityType())) {
            return algoOrderDataService.getRequiredById(actionState.getTargetEntityId());
        }
        AlgoOrder saved = algoOrderDataService.save(buildAlgoOrder(payload, dealContext.getDeal().getId()));
        publishDecided(dealContext, saved);
        return saved;
    }

    /**
     * Событие решения об условной заявке — <b>той же транзакцией</b>, что
     * заводит её строку и присваивает клиентский идентификатор
     * (docs/architecture/contracts.md §«У каждого класса события назван
     * писатель, и он же писатель решения»).
     *
     * <p><b>Повтор звена события не производит:</b> строка уже заведена, и
     * решение принято однажды — тем же правилом, что у решения о заявке.
     *
     * <p><b>Идентичность транша читается из графа прохода</b>: транши в нём
     * уже загружены, и чтение строки транша ради одного поля было бы
     * запросом по прочитанному.
     */
    private void publishDecided(DealContext dealContext, AlgoOrder algoOrder) {
        Deal deal = dealContext.getDeal();
        coreEventWriter.algoOrderDecided(dealContext.getExchangeAccount().getTenantId(), algoOrder,
                deal.getInternalId(), deal.trancheInternalId(algoOrder.getDealTrancheId()),
                dealContext.getExchangeAccount().getInternalId(),
                dealContext.getInstrument().getInternalId());
    }

    /**
     * Свежесозданная защита входит в граф прохода ТОЙ ЖЕ транзакцией —
     * иначе четвёрка считалась бы по графу без только что поставленного
     * уровня. Кладётся <b>на свой транш</b>: покрытие потраншевое.
     */
    private void appendToGraph(Deal deal, AlgoOrder saved) {
        emptyIfNull(deal.getTranches()).stream()
                .filter(tranche -> Objects.equals(saved.getDealTrancheId(), tranche.getId()))
                .forEach(tranche -> {
                    List<AlgoOrder> own = new ArrayList<>(emptyIfNull(tranche.getAlgoOrders()));
                    if (own.stream().noneMatch(algo -> Objects.equals(saved.getId(), algo.getId()))) {
                        own.add(saved);
                        tranche.setAlgoOrders(own);
                    }
                });
    }

    /**
     * Размер приходит рассчитанным: для защитного класса последняя ступень
     * набора получает остаток, а ступень меньше минимального размера даёт
     * контролируемую ошибку расчёта — исполнитель её не сглаживает.
     */
    private AlgoOrder buildAlgoOrder(CreateAlgoOrderCommandPayload payload, Long dealId) {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setDealId(dealId);
        algoOrder.setDealTrancheId(payload.getDealTrancheId());
        algoOrder.setInternalId(InternalIdFactory.forExchangeBoundEntity());
        algoOrder.setStatus(AlgoOrder.Status.CREATED);
        algoOrder.setConditionType(payload.getConditionType());
        algoOrder.setDirection(payload.getDirection());
        algoOrder.setSize(payload.getSizeContracts());
        algoOrder.setPositionReducingOnly(payload.getPositionReducingOnly());
        algoOrder.setCondition(payload.getCondition());
        algoOrder.validateConditionProjection();
        return algoOrder;
    }
}

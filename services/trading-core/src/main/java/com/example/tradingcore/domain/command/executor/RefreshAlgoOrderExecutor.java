package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.payload.RefreshAlgoOrderCommandPayload;
import com.example.tradingcore.domain.command.risk.DealRiskNumbersService;
import com.example.tradingcore.exception.ControlledExchangeException;
import com.example.tradingcore.exception.ExternalInvariantViolationException;
import com.example.tradingcore.exception.ExternalNotFoundException;
import com.example.tradingcore.exception.ExternalStatusException;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.mapping.AlgoOrderMapper;
import com.example.tradingcore.persistence.service.AlgoOrderDataService;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Добыча состояния отдельной условной заявки. Цикл добычи идёт ВНУТРИ
 * одной команды, с обрывом на первом нашедшем источнике: заявка по
 * идентификатору → ожидающие → история
 * (docs/components/RefreshAlgoOrderExecutor.md). Архива глубже истории у
 * условных заявок нет.
 *
 * <p><b>Терминал выносит сам:</b> не найдена после ПОЛНОГО цикла — строка
 * уходит в ошибочное состояние с причиной «не найдена после добычи», и
 * дальше идёт контролируемое исключение. Пустой ответ одного источника
 * основанием не является (docs/rules/controlled-exchange-exceptions.md).
 * У НЕОТПРАВЛЕННОЙ заявки исчерпанный цикл терминал другой — «не дошла до
 * площадки», без броска и без биржевой ступени
 * (docs/lifecycles/AlgoOrder.md). У ЛОКАЛЬНО ТЕРМИНАЛЬНОЙ — третий: её
 * запись ушла за горизонт выдачи, ничего не пишется, броска нет
 * (docs/spec/external-status-resolution.json, {@code notFoundPastLocalTerminal}).
 *
 * <p><b>Сырой статус резолвит коннектор, и отказ приезжает броском
 * ЧТЕНИЯ.</b> Неизвестный либо проблемный статус роняет вызов целиком, а
 * не возвращается значением: словарь площадки живёт на той стороне
 * (docs/rules/external-status-resolution.md §«Где резолвится — сторона
 * выбирается по словарю источника»). Списочная нога цикла роняется
 * целиком и на чужой записи — реакция всё равно биржевая, на весь счёт,
 * и сужать её было бы нечем.
 *
 * <p><b>Эхо найденной записи сверяется с нашей строкой первым ходом</b> —
 * признак «только уменьшать», сторона, база каждой триггерной ноги;
 * расхождение — контролируемый отказ чтения, и строка этим проходом не
 * пишется вовсе (docs/models/mapping/AlgoOrder.md §«Сверка эха»). Из
 * условия на строку садится одно поле — наблюдённый уровень трейлинга.
 *
 * <p><b>Отметку исхода транзакция сохраняет, хотя звено и бросает.</b>
 * Контролируемое исключение изъято из отката: без этого сущность
 * осталась бы без назначенного исхода, а обработчик прохода поднял бы
 * биржевую ступень по факту, которого в базе нет. Ловим, помечаем и
 * бросаем дальше — проглоти мы класс, ступень не поднялась бы вовсе
 * (docs/components/ServiceCommandExecutor.md).
 */
@Component
@RequiredArgsConstructor
public class RefreshAlgoOrderExecutor implements CommandExecutor {

    private final AlgoOrderDataService algoOrderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final ExchangeOperationsClient exchangeOperationsClient;
    private final AlgoOrderMapper algoOrderMapper;
    private final DealRiskNumbersService dealRiskNumbersService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.REFRESH_ALGO_ORDER_COMMAND;
    }

    @Override
    @Transactional(noRollbackFor = ControlledExchangeException.class)
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        RefreshAlgoOrderCommandPayload payload = (RefreshAlgoOrderCommandPayload) command.getPayload();
        AlgoOrder algoOrder = target(payload.getAlgoOrderId(), dealContext);
        AlgoOrder fetched = fetchOrFail(algoOrder, dealContext);
        Boolean recordsObservation = nonNull(fetched) || isTrue(algoOrder.isNotSubmitted())
                || isTrue(algoOrder.isError());
        if (nonNull(fetched)) {
            algoOrderMapper.updateFromFetched(fetched, algoOrder);
            applyStatus(algoOrder, fetched);
        } else if (isTrue(algoOrder.isNotSubmitted())) {
            algoOrder.toNotPlaced();
        }
        if (isTrue(recordsObservation)) {
            algoOrderDataService.save(algoOrder);
        }
        if (isFalse(dealRiskNumbersService.recompute(dealContext))) {
            return ServiceCommandExecutionResult.notCompleted(
                    "Deal graph incomplete: risk numbers not recomputed for algo order " + algoOrder.getId());
        }
        completeAction(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    /**
     * Цель звена — строка ИЗ ГРАФА прохода, а при её отсутствии durable-
     * чтение. Ход несущий: числа риска считаются по графу, и правка
     * отдельной копии осталась бы им не видна — четвёртое число сделки
     * посчиталось бы по прежнему уровню защиты.
     */
    private AlgoOrder target(Long algoOrderId, DealContext dealContext) {
        return emptyIfNull(dealContext.getDeal().getTranches()).stream()
                .flatMap(tranche -> emptyIfNull(tranche.getAlgoOrders()).stream())
                .filter(item -> Objects.equals(algoOrderId, item.getId()))
                .findFirst()
                .orElseGet(() -> algoOrderDataService.getRequiredById(algoOrderId));
    }

    /**
     * Цикл добычи; у живой заявки исчерпан без находки — сущность в
     * ошибочное состояние и бросок. Контролируемое исключение чтения
     * помечает сущность своей причиной и уходит дальше нетронутым.
     *
     * <p><b>Пусто — у неотправленной заявки:</b> исчерпанный цикл
     * доказывает, что на площадке её нет, и терминал ей ставит вызывающий.
     *
     * <p><b>Пусто — и у локально терминальной, без броска:</b> её запись
     * ушла за горизонт выдачи, а факт терминала добыт раньше и стоит. Ничего
     * не пишется, исключения и биржевой ступени нет, звено завершается
     * (docs/spec/external-status-resolution.json, величина
     * {@code notFoundPastLocalTerminal}).
     *
     * <p><b>Отказ разбора статуса бросок сохраняет и у терминальной</b> —
     * неизвестное слово есть дефект словаря площадки, а не свойство заявки, —
     * но ошибочного состояния ей не ставит: рёбер из терминала матрица
     * жизненного цикла не содержит (docs/spec/algo-order-lifecycle.json).
     *
     * <p><b>Каждая добыча пишет наблюдённую живость заявки</b>
     * ({@code AlgoOrder.externalLive}) — до броска, тем же проходом, в том
     * числе у заявки, чей статус матрица уже не двигает: запись найдена
     * живой — истина; найдена терминальной либо полный цикл её не нашёл
     * (обе ветви — {@code MISSING_AFTER_REFRESH} и
     * {@code notFoundPastLocalTerminal}) — ложь; отказ разбора известным
     * словом отказа — ложь, словом, которого словарь площадки не знает, —
     * пусто. Заявке в {@code ERROR} это единственный носитель живости: его
     * читают снятие риска и гейт доказанного отсутствия риска сделки
     * (docs/lifecycles/AlgoOrder.md §«Заявка в {@code ERROR}: живость на
     * площадке читается наблюдением»). Поэтому ненайденность локально
     * терминальной заявки сохраняется только у заявки в {@code ERROR}:
     * у сработавшей и отменённой живость читает статус, и по
     * {@code notFoundPastLocalTerminal} у них не пишется ничего.
     *
     * <p><b>Исключение одно — запись, чьё эхо разошлось с нашей строкой:</b>
     * живости она не пишет, потому что ответ, нарушивший контракт,
     * наблюдением не является ({@link #requireEchoMatch}); дом исключения —
     * тот же раздел docs/lifecycles/AlgoOrder.md.
     */
    private AlgoOrder fetchOrFail(AlgoOrder algoOrder, DealContext dealContext) {
        AlgoOrder fetched;
        try {
            fetched = findFetched(algoOrder, dealContext);
        } catch (ExternalStatusException e) {
            algoOrder.observeRefusedStatus(e.getReasonCode());
            if (isTrue(algoOrder.isLive())) {
                algoOrder.toError(toCloseReason(e.getReasonCode()));
            }
            algoOrderDataService.save(algoOrder);
            throw e;
        }
        requireEchoMatch(algoOrder, fetched);
        algoOrder.observeOnVenue(fetched);
        if (nonNull(fetched) || isTrue(algoOrder.isNotSubmitted()) || isTrue(algoOrder.isLocallyTerminal())) {
            return fetched;
        }
        if (isTrue(algoOrder.isLive())) {
            failWith(algoOrder, AlgoOrder.CloseReason.MISSING_AFTER_REFRESH);
        }
        throw new ExternalNotFoundException(
                "Algo order not found after full evidence cycle: " + algoOrder.getInternalId());
    }

    /**
     * Сверка эха найденной записи с нашей строкой — первым ходом над ней, до
     * переноса фактов, применения статуса и наблюдённой живости: ожидаемое —
     * наша строка, а коннектор её при чтении не видит. Оси сверки и правило
     * пустоты держит модель ({@link AlgoOrder#matchesEcho}); перечень и довод —
     * docs/models/mapping/AlgoOrder.md §«Сверка эха».
     *
     * <p><b>Расхождение — контролируемый отказ чтения, и строка этим проходом
     * не пишется вовсе</b> — ни перенос, ни статус, ни живость: ответ,
     * нарушивший контракт, наблюдением не является, а статус сущности класс
     * отказа не меняет (docs/rules/controlled-exchange-exceptions.md).
     */
    private void requireEchoMatch(AlgoOrder algoOrder, AlgoOrder fetched) {
        if (isNull(fetched) || isTrue(algoOrder.matchesEcho(fetched))) {
            return;
        }
        throw new ExternalInvariantViolationException(
                "Algo order echo diverges from our row on reduceOnly, side or trigger base: internalId="
                + algoOrder.getInternalId() + " direction=" + algoOrder.getDirection() + "/" + fetched.getDirection()
                + " reduceOnly=" + algoOrder.getPositionReducingOnly() + "/" + fetched.getPositionReducingOnly());
    }

    /** Заявка по идентификатору → ожидающие → история; обрыв на первом нашедшем. */
    private AlgoOrder findFetched(AlgoOrder algoOrder, DealContext dealContext) {
        String accountInternalId = dealContext.getExchangeAccount().getInternalId();
        String externalInstrumentId = dealContext.getInstrument().getExternalId();
        AlgoOrder single = exchangeOperationsClient.getAlgoOrder(accountInternalId, externalInstrumentId,
                algoOrder.getExternalId(), algoOrder.getInternalId());
        if (nonNull(single)) {
            return single;
        }
        AlgoOrder.ConditionType conditionType = algoOrder.getConditionType();
        AlgoOrder pending = matchByInternalId(exchangeOperationsClient.getPendingAlgoOrders(accountInternalId,
                externalInstrumentId, conditionType), algoOrder.getInternalId());
        if (nonNull(pending)) {
            return pending;
        }
        // Обязательный операнд истории закрывается идентификатором записи,
        // если он известен, иначе — терминальными состояниями на стороне
        // коннектора: без обоих эндпоинт отвечает отказом, а не пустой
        // историей.
        return matchByInternalId(exchangeOperationsClient.getAlgoOrderHistory(accountInternalId,
                externalInstrumentId, conditionType, algoOrder.getExternalId()), algoOrder.getInternalId());
    }

    /** Матч по КЛИЕНТСКОМУ идентификатору: биржевого у ненайденной записи может не быть вовсе. */
    private AlgoOrder matchByInternalId(List<AlgoOrder> candidates, String internalId) {
        if (isEmpty(candidates)) {
            return null;
        }
        return candidates.stream()
                .filter(candidate -> isNotBlank(candidate.getInternalId())
                        && Objects.equals(internalId, candidate.getInternalId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Доменный статус приезжает готовым с добытой модели; здесь он
     * применяется доменным переходом, а тот держит write-once причины.
     *
     * <p><b>Причина отмены берётся из НАШЕГО намерения</b>, а не из статуса
     * источника: намерение стои́т на сущности с момента отправки снятия.
     * Намерения нет — ставится {@code UNKNOWN}: ребро в отменённое требует
     * непустой причины, и пустота сделала бы отмену, инициированную
     * биржей, непроходимой (docs/rules/external-status-resolution.md).
     *
     * <p><b>Подтверждение прежнего статуса переходом не является.</b>
     * Граф переходов условной заявки петель не содержит, и повторное
     * наблюдение живой заявки — а оно штатное и идёт каждым тиком —
     * упало бы на охране {@code ACTIVE -> ACTIVE}.
     *
     * <p>{@code CREATED}, {@code PENDING} и {@code ERROR} площадкой не
     * наблюдаются: первые два локальны, третий — наше safety-состояние.
     * Появление ошибочного у живой заявки означает наш дефект и роняет
     * проход; первые два отсекаются матрицей раньше — рёбер в них из
     * отправленного и дальше нет.
     *
     * <p><b>Найденная неотправленная заявка сначала становится
     * отправленной</b> — ответ на отправку потерян, но запись на площадке и
     * есть подтверждение приёма; прямых рёбер из созданного в наблюдаемые
     * статусы матрица не содержит. <b>Частичное срабатывание отправленной
     * проходит через активную</b> по тому же доводу: сработать частично
     * могла только вставшая заявка, а ребра «отправлена → частично
     * сработала» в матрице нет.
     *
     * <p><b>Ребро вне матрицы не применяется</b> — модель его спрашивает
     * предикатом допустимости, а не отказом броском: у терминальной заявки
     * рёбер нет вовсе, а откат живого статуса назад (частично сработавшая,
     * наблюдённая активной) состояния не двигает.
     */
    private void applyStatus(AlgoOrder algoOrder, AlgoOrder fetched) {
        AlgoOrder.Status observed = fetched.getStatus();
        if (isTrue(algoOrder.isNotSubmitted())) {
            algoOrder.toPending();
        }
        if (AlgoOrder.Status.PARTIALLY_COMPLETED.equals(observed)
                && AlgoOrder.Status.PENDING.equals(algoOrder.getStatus())) {
            algoOrder.toActive();
        }
        if (Objects.equals(observed, algoOrder.getStatus()) || isFalse(algoOrder.canTransitionTo(observed))) {
            return;
        }
        switch (observed) {
            case ACTIVE -> algoOrder.toActive();
            case PARTIALLY_COMPLETED -> algoOrder.toPartiallyComplete();
            case COMPLETED -> algoOrder.toComplete();
            case CANCELED -> algoOrder.toCancel(nonNull(algoOrder.getCloseReason())
                    ? algoOrder.getCloseReason()
                    : AlgoOrder.CloseReason.UNKNOWN);
            default -> throw new IllegalStateException("Unobservable algo order status from source: " + observed);
        }
    }

    /** Ошибочное состояние с причиной; причина write-once — ранее стоящая не перетирается. */
    private void failWith(AlgoOrder algoOrder, AlgoOrder.CloseReason closeReason) {
        algoOrder.toError(closeReason);
        algoOrderDataService.save(algoOrder);
    }

    private AlgoOrder.CloseReason toCloseReason(ExternalStatusReason reason) {
        return switch (reason) {
            case ORDER_FAILED -> AlgoOrder.CloseReason.ORDER_FAILED;
            case PARTIALLY_FAILED -> AlgoOrder.CloseReason.PARTIALLY_FAILED;
            case UNKNOWN_EXTERNAL_STATUS -> AlgoOrder.CloseReason.UNKNOWN_EXTERNAL_STATUS;
        };
    }

    private void completeAction(DealActionState actionState) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.COMPLETED);
            dealActionStateDataService.save(actionState);
        }
    }
}

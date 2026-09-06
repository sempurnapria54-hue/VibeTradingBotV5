package com.example.tradingcore.domain.jobs;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.config.DealOrchestratorProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RetryBudgetExhaustedException;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.action.SystemActionExecutor;
import com.example.tradingcore.domain.command.executor.ServiceCommandExecutor;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.fsm.DealStateMachine;
import com.example.tradingcore.domain.fsm.DealTransition;
import com.example.tradingcore.domain.fsm.TrancheEdge;
import com.example.tradingcore.domain.safety.HoldService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.integration.exchange.ControlledExchangeException;
import com.example.tradingcore.integration.exchange.CredentialsRejectedException;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.DealTrancheDataService;
import com.example.tradingcore.util.Constants;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Сопровождает уже созданные сделки: один проход на тик по каждой
 * нетерминальной сделке окна (docs/components/DealOrchestratorJob.md).
 *
 * <p><b>Торговых решений не принимает</b> — их принимают обработчики
 * статуса; команд не строит — их отдаёт переход; на биржу не ходит.
 *
 * <p><b>Энфорсмент жёсткой ступени стои́т РАНЬШЕ сборки контекста.</b>
 * Порядок несущий: на тропе отвергнутых кредов добыча фактов отказывает
 * целиком, и энфорсмент, стоящий после неё, съедался бы этим отказом —
 * то есть каскад активных сделок радиуса не отрабатывал бы ровно тогда,
 * когда он единственный элемент реакции, исполняющийся целиком
 * (docs/rules/exchange-hold.md §«Ступень 2 — сворачивание», п. 5).
 *
 * <p><b>Перехватчиков четыре: три выделенных вокруг диспетчеризации и
 * один общий вокруг прохода.</b> Выделенный стои́т до общего: блокировка и
 * разбор живого риска не конкурируют, у каждой своя ветка. Дискриминатор
 * развязки — <b>класс броска</b>, а не уровень строки: контролируемое
 * исключение уводит сделку ошибочной тропой всегда, исчерпание бюджета —
 * только у системной строки.
 *
 * <p><b>Все четыре пишут статус ошибки прямой записью, звена не
 * эмитируя</b>, и причины выхода из штатного ведения не пишут: писателя
 * у этой тропы нет по построению (docs/lifecycles/Deal.md §«Причина
 * выхода из штатного ведения»).
 *
 * <p><b>Затребованная ступень поднимается даже после неуспешной
 * команды.</b> Отказ команды отменяет применение перехода — статус,
 * записанный поверх отказа собственной команды, разошёлся бы с фактами
 * площадки, — но не реакцию: ступень, затребованная каскадом траншей
 * (непокрытый живой риск), не может ждать следующего прохода из-за
 * неудачи соседней команды. Правило прохода режет <b>команды</b>
 * (docs/components/DealOrchestratorJob.md §«Цикл прохода», шаг
 * «диспетчеризация»), не реакцию.
 *
 * <p>Период тика и выключатель — в конфигурации; перекрывающийся запуск
 * пропускается защитой от конкурентного выполнения, in-memory на
 * экземпляр (.claude/rules/codestyle.md §Джобы).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DealOrchestratorJob {

    private static final String JOB_NAME = "dealOrchestratorJob";

    private final DealOrchestratorProperties properties;
    private final JobExecutionGuard executionGuard;
    private final DealDataService dealDataService;
    private final DealTrancheDataService dealTrancheDataService;
    private final DealContextService dealContextService;
    private final SystemActionExecutor systemActionExecutor;
    private final DealStateMachine dealStateMachine;
    private final ServiceCommandExecutor serviceCommandExecutor;
    private final HoldService holdService;

    @Scheduled(cron = "${deal-orchestrator.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    /**
     * Тик: выборка нетерминальных сделок окна, операнды энфорсмента одним
     * чтением на радиус, затем проход по каждой сделке.
     */
    private void run() {
        List<Deal> deals = dealDataService.findActive(properties.getBatchSize());
        if (isEmpty(deals)) {
            return;
        }
        List<Long> dealIds = deals.stream().map(Deal::getId).collect(Collectors.toList());
        Set<Long> underAccountRung = new HashSet<>(dealDataService.findIdsUnderAccountRung(dealIds));
        Set<Long> underInstrumentRung = new HashSet<>(dealDataService.findIdsUnderInstrumentRung(dealIds));
        for (Deal deal : deals) {
            passSafely(deal, underAccountRung, underInstrumentRung);
        }
    }

    /**
     * Общий перехватчик — граница исполнения прохода: сборка контекста,
     * прогон FSM, применение перехода. Уводит сделку в ошибку <b>без</b>
     * радиусной реакции: что именно сломалось, здесь неизвестно, и
     * блокировать по этому радиус означало бы соразмерять реакцию с
     * собственным багом.
     *
     * <p>Отказ одной сделки прохода по остальным не отменяет: они
     * независимы, и общая ветка — не выход из тика.
     */
    private void passSafely(Deal deal, Set<Long> underAccountRung, Set<Long> underInstrumentRung) {
        try {
            pass(deal, underAccountRung, underInstrumentRung);
        } catch (RuntimeException e) {
            log.error("Deal pass failed dealId={}", deal.getId(), e);
            interceptToError(deal);
        }
    }

    /** Цикл прохода одной сделки в объявленном порядке шагов. */
    private void pass(Deal deal, Set<Long> underAccountRung, Set<Long> underInstrumentRung) {
        enforceHardRung(deal, underAccountRung, underInstrumentRung);
        DealContext dealContext = dealContextService.build(deal);
        systemActionExecutor.reviseLiveExecutions(dealContext);
        DealTransition transition = dealStateMachine.run(dealContext);
        if (isTrue(dispatch(dealContext, transition))) {
            applyTransition(dealContext, transition);
        }
        raise(transition.getHoldSignal(), dealContext);
    }

    // ------------------------------------------------------------------
    // Энфорсмент жёсткой ступени
    // ------------------------------------------------------------------

    /**
     * Активная сделка уводится в ошибку, если жёсткая ступень стои́т на
     * любом её радиусе. Шаг гоняется <b>каждым проходом</b>, а не только
     * в момент постановки ступени: каскад ступени — первый ход
     * энфорсмента, а не весь (docs/rules/error-handling-policy.md).
     *
     * <p>Он же — писатель причины выхода из штатного ведения, той же
     * транзакцией, которой пишется статус: без неё у сделки, поднятой в
     * ошибку каскадом, durable-ответа «почему» не остаётся.
     */
    private void enforceHardRung(Deal deal, Set<Long> underAccountRung, Set<Long> underInstrumentRung) {
        Deal.ShutdownReason reason = shutdownReasonOf(deal, underAccountRung, underInstrumentRung);
        if (isNull(reason)) {
            return;
        }
        if (isTrue(dealDataService.enforceHardRung(deal.getId(), reason))) {
            log.warn("Deal is moved to error by the hard rung enforcement dealId={} reason={}",
                    deal.getId(), reason);
            deal.setStatus(Deal.Status.ERROR);
            deal.setShutdownReason(reason);
        }
    }

    /**
     * Причина по радиусу стоящей ступени; пусто — жёсткой ступени нет ни
     * на одном.
     *
     * <p><b>Счёт читается первым, и при обоих стоящих радиусах пишется
     * биржевая причина:</b> биржевой радиус старше, и старшинство
     * согласовано с доминированием биржевых ступеней
     * (docs/lifecycles/Deal.md §«Причина выхода из штатного ведения»).
     * Цена названа там же: причина одновременно стоявшего инструментного
     * холда в поле не видна — её несёт строка статуса инструмента.
     */
    private Deal.ShutdownReason shutdownReasonOf(Deal deal, Set<Long> underAccountRung,
                                                 Set<Long> underInstrumentRung) {
        if (underAccountRung.contains(deal.getId())) {
            return Deal.ShutdownReason.EXCHANGE_HOLD;
        }
        if (underInstrumentRung.contains(deal.getId())) {
            return Deal.ShutdownReason.RISK_POLICY;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Диспетчеризация и выделенные перехватчики
    // ------------------------------------------------------------------

    /**
     * Диспетчеризация команд перехода. <b>Прерывается на первом
     * неуспешном результате</b>: остальные команды перехода в этом
     * проходе не гонятся. На многотраншевой сделке отказ одного транша
     * тормозит остальные в этом проходе; следующий их подберёт —
     * названная цена, не дефект.
     *
     * <p>Ступень, затребованная ЗВЕНОМ, поднимается здесь же — после
     * возврата исполнителя, то есть после коммита его транзакции: исход
     * звена есть намерение, а не право
     * (docs/processes/fsm-execution-layering.md).
     *
     * @return все команды прошли — переход можно применять
     */
    private Boolean dispatch(DealContext dealContext, DealTransition transition) {
        if (isFalse(transition.hasCommands())) {
            return true;
        }
        try {
            for (ServiceCommand command : transition.getCommands()) {
                ServiceCommandExecutionResult result = serviceCommandExecutor.execute(command, dealContext);
                result.getHoldSignals().forEach(signal -> raise(signal, dealContext));
                if (isFalse(result.getSuccess())) {
                    log.debug("Command is not successful, the rest of the transition is deferred"
                            + " dealId={} type={}", command.getDealId(), command.getType());
                    return false;
                }
            }
            return true;
        } catch (ControlledExchangeException e) {
            return interceptControlledFailure(dealContext, e);
        } catch (CredentialsRejectedException e) {
            return interceptRejectedCredentials(dealContext, e);
        } catch (RetryBudgetExhaustedException e) {
            return interceptExhaustedBudget(dealContext, e);
        }
    }

    /**
     * Выделенный перехватчик: площадка ответила отказом, который мы
     * опознаём поимённо.
     *
     * <p>Ступень <b>безусловна</b> и радиус у неё счётный: контролируемая
     * ошибка интеграции — сигнал недоверенной интеграции, истинный радиус
     * поражения неизвестен (docs/rules/controlled-exchange-exceptions.md).
     * Ось «судьба принятого риска» разрешается здесь без чтения признака
     * покрытия: сам этот признак считается по фактам площадки, которой
     * доверять уже нельзя.
     *
     * <p>Порядок — реакция, затем ошибочная тропа сделки.
     */
    private Boolean interceptControlledFailure(DealContext dealContext, ControlledExchangeException e) {
        log.error("Controlled exchange failure dealId={}", dealContext.getDeal().getId(), e);
        raise(HoldSignal.exchangeAccount(Constants.Hold.EXCHANGE_CONTROLLED_FAILURE), dealContext);
        interceptToError(dealContext.getDeal());
        return false;
    }

    /**
     * Выделенный перехватчик: источник отверг наши креды.
     *
     * <p><b>Порядок обратный</b> порядку контролируемого исключения —
     * сперва ошибочная тропа сделки, затем биржевая ступень 2, — и это не
     * важно только на вид: каскад ступени всё равно уводит активные
     * сделки радиуса каждым проходом, поэтому увод собственной сделки
     * первым ничего не отменяет и ничего не ждёт.
     *
     * <p>Ступень выводится той же осью: сопровождать принятый риск нечем
     * — подтвердить нечем и починить нечем, — значит он подлежит снятию.
     * Реакция исполняется best-effort, и ограничение названо в доме
     * лестницы (docs/rules/exchange-hold.md), а не обходится здесь.
     */
    private Boolean interceptRejectedCredentials(DealContext dealContext, CredentialsRejectedException e) {
        log.error("Source rejected our credentials dealId={}", dealContext.getDeal().getId(), e);
        interceptToError(dealContext.getDeal());
        raise(HoldSignal.exchangeAccount(Constants.Hold.EXCHANGE_CREDENTIALS_REJECTED), dealContext);
        return false;
    }

    /**
     * Выделенный перехватчик: строка исполнения израсходовала бюджет
     * попыток.
     *
     * <p><b>Ошибочной тропой уводится только СИСТЕМНАЯ (финализационная)
     * строка.</b> У стратегийной сделка остаётся в своём статусе: класс
     * говорит не «биржа отвергла», а «мы не смогли дозвониться», и на
     * стратегийной надобности это не основание рвать принятый риск —
     * реакцию даёт лестница, а повтор той же надобности гейтится стоящей
     * ступенью (docs/rules/strategy-step-once-per-episode.md).
     */
    private Boolean interceptExhaustedBudget(DealContext dealContext, RetryBudgetExhaustedException e) {
        log.warn("Retry budget exhausted dealId={} strategyLevel={}",
                dealContext.getDeal().getId(), e.getStrategyLevel(), e);
        raise(exhaustedBudgetSignal(dealContext, e), dealContext);
        if (isFalse(e.getStrategyLevel())) {
            interceptToError(dealContext.getDeal());
        }
        return false;
    }

    /**
     * Ступень исчерпанного бюджета: радиус — инструмент (это риск-условие
     * уровня 3), ступень — ось «судьба принятого риска» по признаку
     * покрытия, выбранному по УРОВНЮ отказавшего исполнения.
     *
     * <p><b>Покрытие выполнено — мягкая ступень:</b> принятый риск покрыт
     * и рвать его нечем. <b>Покрытие нарушено либо не резолвится —
     * ступени этот триггер не назначает вовсе</b> (пусто): живой риск без
     * покрытия есть нарушение инварианта системы, его реакцию поднимает
     * свой триггер и радиус там шире инструментного
     * (docs/rules/instrument-hold.md §«Форма реакции на исчерпание
     * бюджета попыток»). Одно состояние не получает двух ответов.
     */
    private HoldSignal exhaustedBudgetSignal(DealContext dealContext, RetryBudgetExhaustedException e) {
        if (isFalse(dealContext.coveredAtLevelOf(e.getActionState()))) {
            return null;
        }
        return HoldSignal.instrumentSoft(Constants.Hold.INSTRUMENT_RETRY_BUDGET_EXHAUSTED);
    }

    // ------------------------------------------------------------------
    // Применение перехода и подъём ступени
    // ------------------------------------------------------------------

    /**
     * Применение перехода: рёбра траншей и статусное ребро сделки.
     *
     * <p><b>Рёбра траншей применяет проход, а не каскад.</b> Записанный
     * до диспетчера, статус транша пережил бы отказ собственной команды и
     * разошёлся бы с фактами площадки, поэтому применение идёт ПОСЛЕ
     * диспетчеризации (docs/processes/fsm-execution-layering.md §«Ребро в `ERROR`: два механизма по природе тропы»).
     */
    private void applyTransition(DealContext dealContext, DealTransition transition) {
        applyTrancheEdges(transition);
        if (isFalse(transition.movesStatus())) {
            return;
        }
        Deal deal = dealContext.getDeal();
        Deal.Status fromStatus = deal.getStatus();
        if (nonNull(transition.getShutdownReason())) {
            deal.setShutdownReason(transition.getShutdownReason());
        }
        if (isNull(deal.getCloseReason()) && nonNull(transition.getCloseReason())) {
            deal.setCloseReason(transition.getCloseReason());
        }
        deal.setStatus(transition.getNextStatus());
        if (isFalse(dealDataService.applyStatusEdge(deal, fromStatus))) {
            log.warn("Deal status edge is not applied: the row has moved dealId={} from={} to={}",
                    deal.getId(), fromStatus, transition.getNextStatus());
        }
    }

    /**
     * Одобренные каскадом рёбра траншей. Причина закрытия едет вместе со
     * статусом и не перезаписывается: писатель у них один и транзакция
     * одна (docs/lifecycles/DealTranche.md).
     */
    private void applyTrancheEdges(DealTransition transition) {
        for (TrancheEdge edge : transition.getTrancheEdges()) {
            DealTranche tranche = edge.getTranche();
            tranche.setStatus(edge.getStatus());
            if (isNull(tranche.getCloseReason()) && nonNull(edge.getCloseReason())) {
                tranche.setCloseReason(edge.getCloseReason());
            }
            dealTrancheDataService.save(tranche);
        }
    }

    /**
     * Единственная точка подъёма затребованной ступени — на оба уровня
     * прохода и на звенья: сделочный переход несёт свой сигнал,
     * траншевый приезжает в нём же каскадом, звено отдаёт свои
     * результатом. Кто ведёт саму реакцию, решает общий исполнитель
     * блокировки, а не этот проход.
     */
    private void raise(HoldSignal signal, DealContext dealContext) {
        if (isNull(signal)) {
            return;
        }
        holdService.raise(signal, dealContext);
    }

    /**
     * Ребро в ошибочное состояние перехватом: прямая запись статуса, без
     * звена и без причины выхода из штатного ведения. Отказ самой записи
     * логируется и проход по остальным сделкам не рвёт.
     */
    private void interceptToError(Deal deal) {
        try {
            if (isTrue(dealDataService.interceptToError(deal.getId()))) {
                deal.setStatus(Deal.Status.ERROR);
            }
        } catch (RuntimeException e) {
            log.error("Failed to move the deal to error dealId={}", deal.getId(), e);
        }
    }
}

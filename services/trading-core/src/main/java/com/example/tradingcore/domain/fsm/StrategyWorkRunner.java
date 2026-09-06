package com.example.tradingcore.domain.fsm;

import static com.example.strategy.engine.calc.util.CalculationErrorCodes.PROTECTION_LADDER_STEP_BELOW_MIN_SIZE;
import static com.example.strategy.engine.calc.util.CalculationErrorCodes.STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.strategy.engine.calc.CalculationError;
import com.example.strategy.engine.calc.CalculationErrorType;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RetryError;
import com.example.tradingcore.domain.command.RetryPolicyService;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.strategy.ActionPlan;
import com.example.tradingcore.domain.command.strategy.StrategyActionOrchestrator;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Исполняет работу стратегии за один проход: продвигает уже живое
 * исполнение либо начинает следующее действие отобранного шага.
 *
 * <p><b>Продвижение живого исполнения отбором шага НЕ гейтится, и это
 * несущее разведение.</b> Строка на действие в эпизоде одна, а признак
 * применённости шага стои́т на её существовании
 * (docs/rules/strategy-step-once-per-episode.md §«Пакет исполняется по
 * действию за проход»): пропусти проход живую строку через отбор —
 * одно-действенный шаг выпал бы из набора сразу после её заведения, и
 * команда второй стадии («отправить заведённую ногу») не была бы выдана
 * никогда. Отбор шага отвечает на вопрос «что НАЧАТЬ», живая строка — на
 * вопрос «что доиграть».
 *
 * <p><b>Живая строка тратит проход целиком.</b> Пока она есть, новое
 * действие не начинается, даже если её план пуст (повтор ждёт отката):
 * обгонять начатое значило бы менять объявленный порядок пакета
 * (docs/components/StrategyActionOrchestrator.md).
 *
 * <p><b>Границы.</b> Статуса транша и сделки не двигает, команд не
 * исполняет, реакции преконтроля не интерпретирует — их доносит план.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyWorkRunner {

    /**
     * Коды контролируемых ошибок расчёта, по которым сделка в аварийный
     * контур НЕ уходит. Перечень — исполнимая форма карв-аута дома
     * (docs/processes/risk-evaluation.md §«Карв-аут исчерпанного бюджета
     * сделки»); членство читается там, здесь только исполняется.
     */
    private static final Set<String> DEAL_ERROR_CARVE_OUT = Set.of(
            STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING,
            PROTECTION_LADDER_STEP_BELOW_MIN_SIZE);

    private final StrategyActionOrchestrator orchestrator;
    private final RetryPolicyService retryPolicyService;
    private final DealActionStateDataService dealActionStateDataService;

    /**
     * Продвинуть живое исполнение своего уровня; пусто — живого
     * исполнения нет, и проход волен начать новое.
     *
     * <p>Строки берутся в порядке заведения: пакет доигрывается тем же
     * порядком, каким начинался.
     */
    public Optional<ActionPlan> advanceLive(DealContext dealContext, DealTranche tranche) {
        List<DealActionState> live = dealContext.liveStrategyActionStates(tranche);
        if (isEmpty(live)) {
            return Optional.empty();
        }
        DealActionState state = live.getFirst();
        StrategyAction action = actionOf(dealContext, state);
        if (isNull(action)) {
            log.warn("Live execution row references an unknown action dealActionStateId={} actionId={}",
                    state.getId(), state.getStrategyActionId());
            return Optional.of(ActionPlan.nothing());
        }
        StrategyStep step = dealContext.getStrategyDetail().stepOf(action);
        return Optional.of(plan(step, action, state, dealContext, tranche));
    }

    /**
     * Начать следующее действие отобранного шага. Пустой план означает
     * «начинать нечего»: пакет исчерпан либо его следующее действие
     * отложено предусловием.
     */
    public ActionPlan startNext(StrategyStep step, DealContext dealContext, DealTranche tranche) {
        StrategyAction action = orchestrator.nextAction(step, dealContext, tranche).orElse(null);
        if (isNull(action)) {
            return ActionPlan.nothing();
        }
        return plan(step, action, null, dealContext, tranche);
    }

    private ActionPlan plan(StrategyStep step, StrategyAction action, DealActionState state,
                            DealContext dealContext, DealTranche tranche) {
        ActionPlan plan = orchestrator.plan(step, action, state, dealContext, tranche);
        if (isTrue(plan.hasCalculationError())) {
            accountCalculationFailure(plan.getCalculationError(), dealContext, action, tranche);
        }
        return plan;
    }

    private StrategyAction actionOf(DealContext dealContext, DealActionState state) {
        StrategyDetail detail = dealContext.getStrategyDetail();
        return isNull(detail) ? null : detail.actionById(state.getStrategyActionId());
    }

    /**
     * Учёт контролируемой ошибки расчёта на строке исполнения: временная —
     * ожидание отката, постоянная — отказ строки.
     *
     * <p><b>Отказ по стороне уровня в учёт не попадает вовсе.</b> Он есть
     * сработавший контроль, а не неудача исполнения: строка остаётся
     * запланированной и повторится следующим проходом, когда структура
     * даст уровень на убыточной стороне.
     *
     * <p><b>Политика повтора берётся умолчанием конфигурации.</b> Отказ
     * расчёта командой не является, и типа команды у него нет; выбирать
     * политику по команде, которой не было, значило бы приписать отказу
     * чужой бюджет.
     */
    private void accountCalculationFailure(CalculationError error, DealContext dealContext,
                                           StrategyAction action, DealTranche tranche) {
        if (STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING.equals(error.getCode())) {
            log.info("Calculation refused by control, step not executed actionKey={} code={}",
                    action.getKey(), error.getCode());
            return;
        }
        DealActionState state = dealContext.actionState(action.getId(), tranche).orElse(null);
        if (isNull(state)) {
            return;
        }
        Integer attemptCount = isNull(state.getAttemptCount()) ? 0 : state.getAttemptCount();
        state.setAttemptCount(attemptCount + 1);
        state.setLastError(new RetryError(error.getCode(), error.getMessage(), RuntimeErrorCode.VALIDATION_ERROR));
        boolean retry = CalculationErrorType.TEMPORARY.equals(error.getType())
                && isTrue(retryPolicyService.canRetry(state, null));
        if (retry) {
            state.setNextRetryAt(retryPolicyService.calculateNextRetryAt(state, null));
        }
        state.setStatus(retry ? DealActionStateStatus.RETRY_PENDING : DealActionStateStatus.FAILED);
        dealActionStateDataService.save(state);
    }

    /**
     * Отказ расчёта, по которому сделка обязана уйти ошибочной тропой:
     * строка исполнения ушла в отказ, и надобности больше некому
     * доиграть.
     *
     * <p><b>Карв-аут не совпадает с карв-аутом учёта, и это несущее.</b>
     * Отказ по стороне уровня не попадает в учёт вовсе — строка остаётся
     * запланированной. Ступень лестницы ниже минимального размера
     * строку <b>роняет</b> (отказ обязан быть видимым), но сделку в
     * аварию не уводит: прежняя защита жива, и увод закрыл бы по рынку
     * позицию, риск которой под контролем
     * (docs/processes/risk-evaluation.md §«Карв-аут исчерпанного бюджета
     * сделки»). Два разных карв-аута, а не один.
     */
    public Boolean calculationFailureIsFatal(CalculationError error) {
        return nonNull(error)
                && isFalse(DEAL_ERROR_CARVE_OUT.contains(error.getCode()))
                && CalculationErrorType.PERMANENT.equals(error.getType());
    }
}

package com.example.tradingbot.domain.command.risk;

import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingbot.domain.command.risk.RiskCheckResult.RiskCheckStatus;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.position.Position;
import java.util.stream.Collectors;
import java.util.List;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Превращает результат risk-проверки в действие handler'а, чтобы handler
 * не содержал большой switch по всем risk-кодам:
 * RiskValidationResult → {@link RiskBlockAction}
 * (docs/components/RiskBlockResolver.md). Сам команды не исполняет;
 * политику маппинга кодов риска в действие держит здесь, исполнение — у FSM
 * handler. {@code liveRiskExists} — производное состояние (DealContext +
 * currentStatus + runtime graph), отдельным параметром не передаётся.
 */
@Component
public class RiskBlockResolver {

    /**
     * Коды, не уводящие сделку в ERROR при живом риске. Дом перечня —
     * docs/processes/risk-evaluation.md §«Карв-аут исчерпанного бюджета
     * сделки»; здесь стоя́т те его члены, чьи коды в перечне
     * {@link RiskCheckCode} уже заведены. Остальные приезжают вместе со
     * своими кодами (потолки риска на сделку, пол дистанции стопа,
     * ступень safety-холда).
     */
    private static final Set<RiskCheckCode> DEAL_ERROR_CARVE_OUT = EnumSet.of(
            RiskCheckCode.SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET,
            RiskCheckCode.BALANCE_NOT_ENOUGH,
            RiskCheckCode.LOSS_LIMIT_NOT_CONFIGURED,
            RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED,
            RiskCheckCode.STOP_LOSS_INVALID_SIDE,
            RiskCheckCode.STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION,
            RiskCheckCode.PROTECTION_COVERAGE_REDUCED);

    private static final Set<DealTranche.Status> LIVE_RISK_STATUSES = EnumSet.of(
            DealTranche.Status.ENTRY_SUBMITTED,
            DealTranche.Status.ENTRY_FINALIZED,
            DealTranche.Status.PROTECTION_SWITCHED,
            DealTranche.Status.MANAGING,
            DealTranche.Status.EXIT_PENDING);

    public RiskBlockAction resolve(DealContext dealContext, DealTranche.Status currentStatus,
                                   RiskValidationResult riskValidationResult) {
        return switch (riskValidationResult.getDecision()) {
            case ALLOWED -> RiskBlockAction.builder()
                    .type(RiskBlockAction.Type.CONTINUE)
                    .comment("risk allowed")
                    .build();
            case WARNING -> RiskBlockAction.builder()
                    .type(RiskBlockAction.Type.CONTINUE_WITH_WARNING)
                    .comment(riskValidationResult.getComment())
                    .build();
            case BLOCKED -> resolveBlocked(dealContext, currentStatus, riskValidationResult);
        };
    }

    private RiskBlockAction resolveBlocked(DealContext dealContext, DealTranche.Status currentStatus,
                                           RiskValidationResult result) {
        if (hasBlockingCode(result, RiskCheckCode.DEAL_GRAPH_INCOMPLETE)) {
            // Не вердикт риск-политики: операндов не предъявлено. Схема по
            // стадиям к нему не применяется ни одной строкой — на всех стадиях
            // тропа ошибки сделки, чтобы разбор по данным отличал «риск не
            // позволил» от «контекст не загрузился» (docs/processes/risk-evaluation.md).
            return RiskBlockAction.builder()
                    .type(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR)
                    .errorCode(RuntimeErrorCode.INTERNAL_ERROR)
                    .comment("deal graph incomplete: " + result.getComment())
                    .build();
        }
        if (hasBlockingCode(result, RiskCheckCode.BALANCE_NOT_FRESH)
                || hasBlockingCode(result, RiskCheckCode.BALANCE_INVALID)) {
            return RiskBlockAction.builder()
                    .type(RiskBlockAction.Type.REQUEST_REFRESH)
                    .comment("balance not fresh/invalid; refresh required")
                    .build();
        }
        if (isTrue(liveRiskExists(dealContext, currentStatus))) {
            // Карв-аут исчерпанного бюджета сделки: реджект, который НЕ является
            // признаком рассогласования, действие просто не исполняет — сделка
            // остаётся в статусе и доживает под своей защитой. Увод в ERROR
            // создавал бы исполнение по рынку там, где риск уже под контролем
            // (docs/processes/risk-evaluation.md §«Карв-аут исчерпанного бюджета сделки»).
            if (isTrue(carvedOut(result))) {
                return RiskBlockAction.builder()
                        .type(RiskBlockAction.Type.SKIP_ACTION)
                        .comment("risk blocked within carve-out, action skipped: " + result.getComment())
                        .build();
            }
            return RiskBlockAction.builder()
                    .type(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR)
                    .errorCode(RuntimeErrorCode.VALIDATION_ERROR)
                    .comment("risk blocked with live risk present: " + result.getComment())
                    .build();
        }
        // Живого риска нет: род реакции даёт СТАДИЯ, а терминал ставит только
        // БЕССРОЧНЫЙ вердикт. Временный отказ (занятый бюджет, несвежий
        // баланс) закрывал бы уровень сетки навсегда — бюджет освободится
        // выходом соседнего транша, а транша, который должен был войти, уже
        // не будет (docs/components/RiskBlockResolver.md §«Карта «вердикт →
        // действие»»).
        if (isFalse(verdictPermanent(result))) {
            return RiskBlockAction.builder()
                    .type(RiskBlockAction.Type.SKIP_ACTION)
                    .comment("risk blocked temporarily before live risk: " + result.getComment())
                    .build();
        }
        return RiskBlockAction.builder()
                .type(RiskBlockAction.Type.CLOSE_CANDIDATE_DEAL)
                .closeReason(Deal.CloseReason.RISK_CONTROL)
                .comment("risk blocked permanently before live risk: " + result.getComment())
                .build();
    }

    /**
     * Вердикт целиком лежит в карв-ауте исчерпанного бюджета сделки —
     * перечень членства держит `docs/processes/risk-evaluation.md`
     * §«Карв-аут исчерпанного бюджета сделки», здесь он исполняется.
     *
     * <p>Свёртка — конъюнкция по блокирующим кодам, а не дизъюнкция:
     * один код вне карв-аута возвращает вердикт на тропу ошибки.
     * Направление консервативное — членство освобождает от аварийного
     * контура, и освобождать по одному коду из перечня, когда рядом
     * стои́т признак рассогласования, было бы разрешающей ошибкой.
     *
     * <p>Пустой перечень блокирующих кодов членством не считается — тот
     * же довод, что у бессрочности: терминал по нему был бы решением по
     * недобытому факту.
     */
    private Boolean carvedOut(RiskValidationResult result) {
        List<RiskCheckResult> blocking = blockingChecks(result);
        if (isEmpty(blocking)) {
            return false;
        }
        return blocking.stream().allMatch(check -> DEAL_ERROR_CARVE_OUT.contains(check.getCode()));
    }

    /**
     * Бессрочность ВЕРДИКТА — конъюнкция бессрочности его кодов: один
     * временный код делает вердикт временным, потому что повтор может
     * пройти. Свёртка нужна оттого, что вердикт несёт ПЕРЕЧЕНЬ отказов, а
     * действие односоставно; род действия при этом берёт стадия, и от
     * состава перечня он не зависит.
     *
     * <p>Пустой перечень бессрочным не считается: вердикт `BLOCKED` без
     * единого блокирующего кода — рассогласование самого валидатора, и
     * терминал по нему был бы решением по недобытому факту.
     */
    private Boolean verdictPermanent(RiskValidationResult result) {
        List<RiskCheckResult> blocking = blockingChecks(result);
        if (isEmpty(blocking)) {
            return false;
        }
        return blocking.stream().allMatch(check -> isTrue(check.getCode().isPermanent()));
    }

    private List<RiskCheckResult> blockingChecks(RiskValidationResult result) {
        return result.getChecks().stream()
                .filter(check -> RiskCheckStatus.BLOCKED.equals(check.getStatus()))
                .collect(Collectors.toList());
    }

    private Boolean liveRiskExists(DealContext dealContext, DealTranche.Status currentStatus) {
        Deal deal = dealContext.getDeal();
        Position position = deal.livePosition();
        if (nonNull(position) && isTrue(position.hasLiveRisk())) {
            return Boolean.TRUE;
        }
        if (LIVE_RISK_STATUSES.contains(currentStatus)) {
            return Boolean.TRUE;
        }
        if (isNotEmpty(deal.getOrders()) && deal.getOrders().stream().anyMatch(order -> isTrue(order.isLive()))) {
            return Boolean.TRUE;
        }
        return isNotEmpty(deal.getAlgoOrders())
                && deal.getAlgoOrders().stream().anyMatch(algo -> isTrue(algo.isLive()));
    }

    private boolean hasBlockingCode(RiskValidationResult result, RiskCheckCode code) {
        return result.getChecks().stream()
                .anyMatch(check -> Objects.equals(check.getCode(), code)
                        && RiskCheckStatus.BLOCKED.equals(check.getStatus()));
    }
}

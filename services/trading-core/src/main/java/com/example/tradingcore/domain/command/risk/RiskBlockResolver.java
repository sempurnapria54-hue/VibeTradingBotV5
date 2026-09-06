package com.example.tradingcore.domain.command.risk;

import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckStatus;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Превращает вердикт риск-проверки в действие обработчика, чтобы тот не
 * содержал большой {@code switch} по всем кодам риска
 * (docs/components/RiskBlockResolver.md).
 *
 * <p><b>Первый операнд — стадия объекта действия, второй — код.</b> Стадия
 * решает РОД реакции, код — только ПРИЧИНУ; порядок именно такой, потому
 * что цену ошибки задаёт стадия: до живого риска рвать нечего, после —
 * есть.
 *
 * <p><b>Живой риск отдельным параметром не приходит</b> — это производное
 * состояние контекста прохода, стадии транша и графа сделки.
 */
@Component
public class RiskBlockResolver {

    /**
     * Карв-аут исчерпанного бюджета сделки: коды, не уводящие сделку в
     * {@code ERROR} при живом риске. Дом перечня —
     * docs/processes/risk-evaluation.md §«Карв-аут исчерпанного бюджета
     * сделки»; здесь он исполняется.
     *
     * <p>Ось разведения одна — является ли реджект признаком
     * РАССОГЛАСОВАНИЯ. Не является ⇒ действие просто не исполняется, а
     * сделка доживает под своей защитой: увод в {@code ERROR} создавал бы
     * исполнение по рынку там, где риск уже под контролем, и загрязнял бы
     * выборку {@code R} исходом, к торговому решению не относящимся.
     */
    private static final Set<RiskCheckCode> DEAL_ERROR_CARVE_OUT = EnumSet.of(
            RiskCheckCode.SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET,
            RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED,
            RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED,
            RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED,
            RiskCheckCode.DEAL_NOTIONAL_EXCEEDED,
            RiskCheckCode.STOP_DISTANCE_BELOW_FLOOR,
            RiskCheckCode.STOP_LOSS_INVALID_SIDE,
            RiskCheckCode.STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION,
            RiskCheckCode.PROTECTION_COVERAGE_REDUCED,
            RiskCheckCode.LOSS_LIMIT_NOT_CONFIGURED,
            RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED,
            RiskCheckCode.BALANCE_NOT_ENOUGH,
            RiskCheckCode.RISK_CREATING_UNDER_COLLAPSE,
            RiskCheckCode.INSTRUMENT_SAFETY_HOLD);

    /** Стадии транша, на которых живой риск уже есть либо мог появиться. */
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
        List<RiskCheckResult> blocking = blockingChecks(result);
        if (hasCode(blocking, RiskCheckCode.DEAL_GRAPH_INCOMPLETE)) {
            // Не вердикт риск-политики: операндов не предъявлено. Стадия его
            // реакцию не делит ни одной строкой — на всех идёт тропа ошибки
            // сделки, чтобы разбор по данным отличал «риск не позволил» от
            // «контекст не загрузился» (docs/processes/risk-evaluation.md
            // §«Отказ по неполноте графа реакцию не делит со схемой»).
            return RiskBlockAction.builder()
                    .type(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR)
                    .riskCode(RiskCheckCode.DEAL_GRAPH_INCOMPLETE)
                    .comment("deal graph incomplete: " + result.getComment())
                    .build();
        }
        if (isTrue(liveRiskExists(dealContext, currentStatus))) {
            if (isTrue(carvedOut(blocking))) {
                return RiskBlockAction.builder()
                        .type(RiskBlockAction.Type.SKIP_ACTION)
                        .riskCode(seniorCode(blocking))
                        .comment("risk blocked within carve-out, action skipped: " + result.getComment())
                        .build();
            }
            return RiskBlockAction.builder()
                    .type(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR)
                    .riskCode(seniorCode(blocking))
                    .comment("risk blocked with live risk present: " + result.getComment())
                    .build();
        }
        // Живого риска ещё нет. ОПЕРАНД НЕ ДОБЫТ — вердикт откладывается, а
        // не выносится: снимок средств добывается звеном REFRESH_BALANCE, и
        // причины у такой реакции нет (docs/components/RiskBlockResolver.md
        // §«Карта «вердикт → действие»»). Ветвь стои́т ПОСЛЕ живого риска, а
        // не до него: при живом риске несвежий снимок — рассогласование
        // учёта и ведёт в ERROR (docs/processes/risk-evaluation.md
        // §«Карв-аут исчерпанного бюджета сделки»).
        if (hasCode(blocking, RiskCheckCode.BALANCE_NOT_FRESH)) {
            return RiskBlockAction.builder()
                    .type(RiskBlockAction.Type.REQUEST_REFRESH)
                    .comment("balance not fresh; refresh required")
                    .build();
        }
        // Род реакции даёт СТАДИЯ, а терминал ставит только БЕССРОЧНЫЙ
        // вердикт. Временный отказ закрывал бы уровень сетки навсегда —
        // бюджет освободится выходом соседнего транша, а транша, который
        // должен был войти, уже не будет.
        if (isFalse(verdictPermanent(blocking))) {
            return RiskBlockAction.builder()
                    .type(RiskBlockAction.Type.SKIP_ACTION)
                    .riskCode(seniorCode(blocking))
                    .comment("risk blocked temporarily before live risk: " + result.getComment())
                    .build();
        }
        return RiskBlockAction.builder()
                .type(RiskBlockAction.Type.CLOSE_CANDIDATE_DEAL)
                .closeReason(Deal.CloseReason.RISK_CONTROL)
                .riskCode(seniorCode(blocking))
                .comment("risk blocked permanently before live risk: " + result.getComment())
                .build();
    }

    /**
     * Вердикт целиком лежит в карв-ауте.
     *
     * <p>Свёртка — КОНЪЮНКЦИЯ по блокирующим кодам, а не дизъюнкция: один
     * код вне карв-аута возвращает вердикт на тропу ошибки. «Мягкая»
     * свёртка маскировала бы настоящее рассогласование учёта соседним
     * ожидаемым отказом — ошибка в РАЗРЕШАЮЩУЮ сторону; конъюнкция
     * ошибается в противоположную, и это направление консервативное.
     *
     * <p>Пустой перечень членством не считается — тот же довод, что у
     * бессрочности: решение по недобытому факту.
     */
    private Boolean carvedOut(List<RiskCheckResult> blocking) {
        if (isEmpty(blocking)) {
            return false;
        }
        return blocking.stream().allMatch(check -> DEAL_ERROR_CARVE_OUT.contains(check.getCode()));
    }

    /**
     * Бессрочность ВЕРДИКТА — конъюнкция бессрочности его кодов: один
     * временный код делает вердикт временным, потому что повтор может
     * пройти. Свёртка нужна оттого, что вердикт несёт ПЕРЕЧЕНЬ отказов, а
     * действие односоставно.
     *
     * <p>Пустой перечень бессрочным не считается: {@code BLOCKED} без
     * единого блокирующего кода — рассогласование самого валидатора, и
     * терминал по нему был бы решением по недобытому факту.
     */
    private Boolean verdictPermanent(List<RiskCheckResult> blocking) {
        if (isEmpty(blocking)) {
            return false;
        }
        return blocking.stream().allMatch(check -> isTrue(check.getCode().isPermanent()));
    }

    /**
     * Причину берёт СТАРШИЙ код перечня. Старшинство — порядок причин
     * закрытия транша (docs/spec/deal-lifecycle.json,
     * {@code trancheCloseReasonRank}), а среди кодов ОДНОГО ранга — первый
     * по порядку проверок валидатора.
     *
     * <p><b>Первое правило все коды риска ставит в один ранг:</b> причина
     * закрытия у них общая — {@code RISK_CONTROL}, — поэтому решает
     * исключительно второе, и оно исполняется само собой: валидатор
     * накапливает отказы в порядке проверок, значит старший код есть
     * ПЕРВЫЙ в перечне. Отдельного носителя ранга поэтому не заводится: он
     * разошёлся бы с порядком проверок первой же правкой
     * (.claude/rules/carrier-levels.md).
     */
    private RiskCheckCode seniorCode(List<RiskCheckResult> blocking) {
        return blocking.stream()
                .map(RiskCheckResult::getCode)
                .findFirst()
                .orElse(null);
    }

    private List<RiskCheckResult> blockingChecks(RiskValidationResult result) {
        return emptyIfNull(result.getChecks()).stream()
                .filter(check -> RiskCheckStatus.BLOCKED.equals(check.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Живой риск у сделки есть: живой эпизод, стадия транша после
     * отправленного входа либо живая нога или условная заявка в графе.
     *
     * <p><b>Ноги и условные заявки собираются обходом траншей</b>, а не
     * донорскими полями агрегата: в целевой модели их там нет
     * (docs/models/domain/aggregate/Deal.md §Структура).
     */
    private Boolean liveRiskExists(DealContext dealContext, DealTranche.Status currentStatus) {
        Deal deal = dealContext.getDeal();
        Position position = deal.livePosition();
        if (nonNull(position) && isTrue(position.hasLiveRisk())) {
            return true;
        }
        if (LIVE_RISK_STATUSES.contains(currentStatus)) {
            return true;
        }
        return emptyIfNull(deal.getTranches()).stream()
                .anyMatch(tranche -> isFalse(tranche.liveOrders().isEmpty())
                        || isFalse(tranche.liveAlgoOrders().isEmpty()));
    }

    private boolean hasCode(List<RiskCheckResult> blocking, RiskCheckCode code) {
        return blocking.stream().anyMatch(check -> Objects.equals(code, check.getCode()));
    }
}

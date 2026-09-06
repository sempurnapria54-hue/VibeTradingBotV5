package com.example.tradingcore.domain.command.strategy;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingcore.domain.command.ActionKind;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Выбирает следующее действие пакета шага, гейтит повтор и маршрутизирует
 * действие в исполнитель своего типа
 * (docs/components/StrategyActionOrchestrator.md).
 *
 * <p><b>Выбор действия и его продвижение разведены.</b> {@code nextAction}
 * отвечает, какое действие пакета можно НАЧАТЬ этим проходом — строки у
 * него ещё нет; {@code plan} продвигает действие дальше. Иначе пакет
 * каждым проходом брал бы первое действие и на нём же стоя́л: ступень из
 * двух действий (снять защиту, поставить новую) не доигрывалась бы
 * никогда.
 *
 * <p><b>Ключ порядка — риск-класс: устанавливающие защиту раньше
 * снимающих</b> (docs/rules/live-risk-protection.md). Порядок объявления
 * ключом порядка не служит и внутри класса сохраняется как есть.
 *
 * <p><b>Границы.</b> Статус исполнения сам не пишет — его двигают
 * исполнители команд и учёт повторов; состав команд действия не знает —
 * это собственность исполнителя типа; системные действия не ведёт.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyActionOrchestrator {

    private final List<StrategyActionExecutor> executors;
    private final DealActionStateDataService dealActionStateDataService;

    /**
     * Действие пакета, которое можно начать этим проходом.
     *
     * <p><b>Начинается действие, у которого строки ещё нет.</b> Строка —
     * носитель признака «шаг применён на этом эпизоде»
     * (docs/rules/strategy-step-once-per-episode.md), и завершённая либо
     * отказавшая строка нового начала не даёт: повтор надобности после
     * исчерпания бюджета гейтится стоящей ступенью радиуса, а не этим
     * выбором.
     *
     * <p><b>Отложенное действие ОСТАНАВЛИВАЕТ пакет, неактуальное —
     * пропускается.</b> Обгонять отложенное значило бы менять объявленный
     * порядок пакета; ждать неактуального — стоять вечно, предмета у него
     * больше нет.
     */
    public Optional<StrategyAction> nextAction(StrategyStep step, DealContext dealContext, DealTranche tranche) {
        for (StrategyAction action : orderedActions(step)) {
            if (dealContext.actionState(action.getId(), tranche).isPresent()) {
                continue;
            }
            ActionReadiness readiness = executorFor(action)
                    .map(executor -> executor.readiness(action, dealContext, tranche))
                    .orElse(ActionReadiness.IRRELEVANT);
            if (ActionReadiness.READY.equals(readiness)) {
                return Optional.of(action);
            }
            if (ActionReadiness.DEFERRED.equals(readiness)) {
                log.debug("Step package halted on deferred action actionKey={}", action.getKey());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Продвинуть действие: заведение строки при первом ходе, гейт повтора,
     * маршрутизация в исполнитель типа.
     *
     * <p><b>Строка заводится здесь, а не вызывающим.</b> Она анкер той
     * самой команды, которую план и несёт: заведённая в другом месте, она
     * позволила бы спланировать действие без анкера — то есть без
     * идемпотентности, повторов и цели
     * (docs/components/models/ServiceCommand.md).
     *
     * <p><b>Ожидающая повтора строка отдаёт команду только по наступлении
     * времени попытки.</b> Завершённые и отказавшие терминальны: новая
     * надобность — новая строка, и заводит её не этот проход.
     */
    public ActionPlan plan(StrategyStep step, StrategyAction action, DealActionState state,
                           DealContext dealContext, DealTranche tranche) {
        StrategyActionExecutor executor = executorFor(action).orElse(null);
        if (isNull(executor)) {
            log.warn("No executor supports action actionKey={} type={}", action.getKey(), action.getActionType());
            return ActionPlan.nothing();
        }
        DealActionState anchor = anchor(action, state, dealContext, tranche);
        if (isNull(anchor)) {
            return ActionPlan.nothing();
        }
        return executor.next(step, action, anchor, dealContext, tranche);
    }

    /**
     * Строка исполнения под действие: заведённая — та же, ожидающая
     * повтора — перевзведённая по наступлении времени, отсутствующая —
     * новая. Пусто ровно в одном случае: повтор ещё ждёт отката.
     */
    private DealActionState anchor(StrategyAction action, DealActionState state, DealContext dealContext,
                                   DealTranche tranche) {
        if (isNull(state)) {
            return createPlanned(action, dealContext, tranche);
        }
        if (isFalse(DealActionStateStatus.RETRY_PENDING.equals(state.getStatus()))) {
            return state;
        }
        if (isFalse(retryDue(state))) {
            return null;
        }
        state.setStatus(DealActionStateStatus.PLANNED);
        return dealActionStateDataService.save(state);
    }

    private Boolean retryDue(DealActionState state) {
        return isNull(state.getNextRetryAt())
                || isFalse(OffsetDateTime.now(ZoneOffset.UTC).isBefore(state.getNextRetryAt()));
    }

    /**
     * Новая строка стратегийного исполнения. Транш и номер эпизода
     * непусты у потраншевого объявления и пусты у агрегатного: пустой
     * транш участвует в отборе как пустой, а не как «любой».
     */
    private DealActionState createPlanned(StrategyAction action, DealContext dealContext, DealTranche tranche) {
        DealActionState state = new DealActionState();
        state.setDealId(dealContext.getDeal().getId());
        state.setActionKind(ActionKind.STRATEGY);
        state.setStrategyActionId(action.getId());
        state.setDealTrancheId(nonNull(tranche) ? tranche.getId() : null);
        state.setTrancheEpisodeSeq(nonNull(tranche) ? tranche.getEpisodeSeq() : null);
        state.setStatus(DealActionStateStatus.PLANNED);
        DealActionState saved = dealActionStateDataService.save(state);
        dealContext.register(saved);
        return saved;
    }

    /**
     * Действия пакета в порядке исполнения: снимающие защиту — последними.
     *
     * <p>Сортировка устойчива, поэтому внутри класса порядок объявления
     * сохраняется: правило разводит только устанавливающие и снимающие, и
     * третьего класса у него нет.
     */
    private List<StrategyAction> orderedActions(StrategyStep step) {
        return emptyIfNull(step.getActions()).stream()
                .sorted(Comparator.comparingInt(this::riskClassRank))
                .collect(Collectors.toList());
    }

    private int riskClassRank(StrategyAction action) {
        return isTrue(removesProtection(action)) ? 1 : 0;
    }

    /** Действие забирает уже стоящую защиту: снятие защитного объявления. */
    private Boolean removesProtection(StrategyAction action) {
        return action instanceof StrategyAlgoOrderAction algoAction
                && StrategyActionType.CANCEL_ACTION.equals(action.getActionType())
                && isTrue(algoAction.isProtective());
    }

    private Optional<StrategyActionExecutor> executorFor(StrategyAction action) {
        return executors.stream().filter(executor -> isTrue(executor.supports(action))).findFirst();
    }
}

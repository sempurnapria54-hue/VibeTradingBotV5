package com.example.tradingcore.domain.fsm.deal;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.action.SystemActionExecutor;
import com.example.tradingcore.domain.command.payload.ClosePositionCommandPayload;
import com.example.tradingcore.domain.fsm.DealHandler;
import com.example.tradingcore.domain.fsm.DealTransition;
import com.example.tradingcore.domain.fsm.DealTransitionGate;
import com.example.tradingcore.domain.fsm.TrancheCascade;
import com.example.tradingcore.domain.fsm.TrancheCascadeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Сворачивает сделку целиком: каскадирует выход в транши, дожидается их
 * терминальности, добывает факты закрытия и готовит терминал
 * (docs/components/DealExitPendingHandler.md).
 *
 * <p><b>Каскад в транши обязателен.</b> Без него выходная проверка «все
 * транши терминальны» не наступает никогда, и сделка висит в
 * координированном выходе с живой позицией
 * (docs/processes/fsm-execution-layering.md §«Кто прогоняет FSM транша»).
 *
 * <p><b>Полное закрытие нетто-экспозиции идёт НЕ РАНЬШЕ, чем предусловие
 * истинно:</b> живых входных ног у траншей не осталось и граф предъявлен
 * целиком (docs/rules/exit-teardown-order.md). Пока предусловие ложно,
 * шаг не эмитится — каскад доводит транши до снятия.
 *
 * <p><b>Названное ограничение: явную форму выхода этот обработчик не
 * различает.</b> Когда выход объявлен действием
 * ({@code actionKind = POSITION}), команду шлёт исполнитель действия, и
 * две команды по одной позиции дали бы биржевой отказ; различитель форм —
 * дом docs/rules/no-partial-close.md, а операнда «объявлен ли выход
 * действием» на этой поверхности нет: шаги живут на закреплённой детали,
 * а сюда сделка приходит и без неё. Здесь охрана выражена тем, что
 * закрытие эмитится только при отсутствии живой строки исполнения
 * выходного действия. Условие снятия — ход, вводящий явный признак формы
 * выхода на контексте прохода; задача и владелец —
 * .claude/work/backlog.md §«Названные ограничения кодирования шага 7 —
 * возврат по появлению носителя».
 *
 * <p><b>Деталь требуется у той сделки, у которой она обязана быть.</b> У
 * восстановленной её нет, и сворачиванию она не нужна: каскад, закрытие
 * нетто-экспозиции, добыча и финализация её операндов не читают.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DealExitPendingHandler implements DealHandler {

    private final TrancheCascade trancheCascade;
    private final DealTransitionGate transitionGate;
    private final SystemActionExecutor systemActionExecutor;

    @Override
    public Deal.Status handledStatus() {
        return Deal.Status.EXIT_PENDING;
    }

    @Override
    public DealTransition handle(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isTrue(deal.moreThanOneLiveEpisode())) {
            log.warn("More than one live episode on a collapsing deal dealId={}", deal.getId());
            return errorPath(dealContext);
        }
        TrancheCascadeResult cascade = trancheCascade.run(dealContext);
        if (isTrue(cascade.getDealErrorRequested())) {
            return errorPath(dealContext)
                    .withTrancheEdges(cascade.getEdges())
                    .withRung(cascade.getHoldSignal());
        }
        if (isTrue(cascade.acted())) {
            return DealTransition.commands(cascade.getCommands())
                    .withTrancheEdges(cascade.getEdges())
                    .withRung(cascade.getHoldSignal());
        }
        ServiceCommand netClose = netClose(dealContext);
        if (nonNull(netClose)) {
            return DealTransition.stay().withCommand(netClose).withRung(cascade.getHoldSignal());
        }
        return finalizeExit(dealContext).withRung(cascade.getHoldSignal());
    }

    /**
     * Полное закрытие нетто-экспозиции — одна команда уровня сделки;
     * пусто — закрывать нечего либо ещё рано.
     *
     * <p>Закрытие законно ровно потому, что выходят ВСЕ транши: закрытый
     * объём приписывается им правилом сопоставления, и на полном закрытии
     * его хватает на всех.
     */
    private ServiceCommand netClose(DealContext dealContext) {
        Position live = dealContext.getDeal().livePosition();
        if (isNull(live) || isFalse(live.hasLiveRisk())) {
            return null;
        }
        if (isFalse(transitionGate.netCloseAllowed(dealContext))) {
            log.debug("Net close is not allowed yet dealId={}", dealContext.getDeal().getId());
            return null;
        }
        // Охрана явной формы выхода: живая строка агрегатного исполнения
        // означает, что закрытие уже ведёт исполнитель объявленного действия, и
        // вторая команда по той же позиции дала бы биржевой отказ на штатной
        // тропе выхода (см. §«Названное ограничение» в шапке класса).
        if (isFalse(dealContext.liveStrategyActionStates(null).isEmpty())) {
            log.debug("Exit is driven by a declared action, no net close emitted dealId={}",
                    dealContext.getDeal().getId());
            return null;
        }
        return ServiceCommand.builder()
                .type(ServiceCommandType.CLOSE_POSITION_COMMAND)
                .dealId(dealContext.getDeal().getId())
                .payload(new ClosePositionCommandPayload(live.getId(), Position.CloseReason.CLOSED_BY_STRATEGY))
                .build();
    }

    /**
     * Завершение — через действие финализации выхода: подтвердить причину,
     * посчитать число, затем поставить терминал. Затребователь на этом
     * ребре один и тот же независимо от факта входа — этот обработчик;
     * само ребро пишет звено.
     */
    private DealTransition finalizeExit(DealContext dealContext) {
        return systemActionExecutor.next(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, dealContext, null)
                .map(DealTransition.stay()::withCommand)
                .orElseGet(DealTransition::stay);
    }

    /** Ошибочная тропа: обработчик гейтит эмиссию звена, ребро пишет оно. */
    private DealTransition errorPath(DealContext dealContext) {
        return systemActionExecutor.next(SystemActionType.FINALIZE_DEAL_ERROR_ACTION, dealContext, null)
                .map(DealTransition.stay()::withCommand)
                .orElseGet(DealTransition::stay);
    }
}

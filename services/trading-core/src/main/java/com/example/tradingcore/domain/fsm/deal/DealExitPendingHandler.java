package com.example.tradingcore.domain.fsm.deal;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.action.SystemActionExecutor;
import com.example.tradingcore.domain.command.calc.DealResultCalculator;
import com.example.tradingcore.domain.command.payload.ClosePositionCommandPayload;
import com.example.tradingcore.domain.fsm.DealHandler;
import com.example.tradingcore.domain.fsm.DealTransition;
import com.example.tradingcore.domain.fsm.DealTransitionGate;
import com.example.tradingcore.domain.fsm.TrancheCascade;
import com.example.tradingcore.domain.fsm.TrancheCascadeResult;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Сворачивает сделку целиком: каскадирует выход в транши, закрывает
 * нетто-экспозицию, дожидается терминальности траншей, добывает факты
 * закрытия и готовит терминал (docs/components/DealExitPendingHandler.md).
 *
 * <p><b>Каскад в транши обязателен.</b> Без него выходная проверка «все
 * транши терминальны» не наступает никогда, и сделка висит в
 * координированном выходе с живой позицией
 * (docs/processes/fsm-execution-layering.md §«Кто прогоняет FSM транша»).
 *
 * <p><b>Полное закрытие нетто-экспозиции идёт НЕ РАНЬШЕ, чем предусловие
 * истинно</b> — живых входных ног у траншей не осталось и граф предъявлен
 * целиком, — <b>и РАНЬШЕ снятия защит траншей:</b> транш при живом риске
 * позиции под сворачиванием молчит, и каскад закрытию не мешает
 * (docs/rules/exit-teardown-order.md).
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
    private final DealResultCalculator resultCalculator;

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
        // Закрытие нетто-экспозиции дочистки траншей не ждёт: транш с живой
        // экспозицией под сворачиванием молчит, а снимает своё только транш,
        // чья экспозиция уже ноль, — его ход позицию соседа не защищает, и
        // зависшая его дочистка не должна держать позицию открытой.
        DealTransition close = isTrue(deal.hasLivePositionRisk()) ? netClose(dealContext) : DealTransition.stay();
        // На сворачивании добыча траншей занимает проход наравне с работой:
        // финализировать, пока снятое не наблюдено, рано.
        if (isTrue(cascade.acted()) || isTrue(cascade.observed())) {
            List<ServiceCommand> pass = new ArrayList<>(close.getCommands());
            pass.addAll(cascade.passCommands());
            return DealTransition.commands(pass)
                    .withTrancheEdges(cascade.getEdges())
                    .withRung(cascade.getHoldSignal());
        }
        if (isTrue(deal.hasLivePositionRisk())) {
            return close.withRung(cascade.getHoldSignal());
        }
        if (isFalse(deal.allTranchesTerminal())) {
            log.debug("Tranches are not terminal yet, nothing to finalize dealId={}", deal.getId());
            return DealTransition.stay().withRung(cascade.getHoldSignal());
        }
        return harvestThenFinalize(dealContext).withRung(cascade.getHoldSignal());
    }

    /**
     * Полное закрытие нетто-экспозиции — одна команда уровня сделки, и
     * добыча позиции тем же проходом, ПЕРЕД закрытием: отказ площадки на
     * закрытии («позиции нет») остановил бы диспетчер раньше добычи, и
     * закрытие слалось бы бесконечно. Пустой исход — ещё рано.
     *
     * <p><b>Закрытие, чьё намерение уже стоит, повторяется только за
     * наблюдением того же прохода.</b> Каждое закрытие идёт за добычей, и
     * исполнитель закрытия читает позицию, которую та добыча только что
     * записала: позиция, пережившая принятое закрытие (частичное исполнение
     * по ценовым пределам), закрывается снова, а закрытая — нет. Без
     * добычи в этом проходе (звено ждёт отката повтора) «отправлено, ещё
     * не наблюдено» от «наблюдено, осталась» не отличить, и повтора нет.
     *
     * <p>Закрытие законно ровно потому, что выходят ВСЕ транши: закрытый
     * объём приписывается им правилом сопоставления, и на полном закрытии
     * его хватает на всех.
     */
    private DealTransition netClose(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isFalse(transitionGate.netCloseAllowed(dealContext))) {
            log.debug("Net close is not allowed yet dealId={}", deal.getId());
            return DealTransition.stay();
        }
        // Охрана явной формы выхода: живая строка агрегатного исполнения
        // означает, что закрытие уже ведёт исполнитель объявленного действия, и
        // вторая команда по той же позиции дала бы биржевой отказ на штатной
        // тропе выхода (см. §«Названное ограничение» в шапке класса).
        if (isFalse(dealContext.liveStrategyActionStates(null).isEmpty())) {
            log.debug("Exit is driven by a declared action, no net close emitted dealId={}", deal.getId());
            return DealTransition.stay();
        }
        Position live = deal.livePosition();
        ServiceCommand observation = fetch(dealContext, ServiceCommandType.REFRESH_POSITION_COMMAND);
        DealTransition observed = DealTransition.stay().withCommand(observation);
        if (nonNull(live.getCloseReason()) && isNull(observation)) {
            return observed;
        }
        return observed.withCommand(ServiceCommand.builder()
                .type(ServiceCommandType.CLOSE_POSITION_COMMAND)
                .dealId(deal.getId())
                .payload(new ClosePositionCommandPayload(live.getId(), Position.CloseReason.CLOSED_BY_STRATEGY))
                .build());
    }

    /**
     * Добыча фактов закрытия, затем финализация: запись закрытия каждого
     * эпизода, затем движения средств окна сделки. Звено движений —
     * звено ВЫХОДНОЙ тропы, и называет его этот обработчик: производный
     * вывод цикла добычи его не даёт намеренно
     * (docs/components/SystemActionExecutor.md §«Состав цикла добычи»).
     *
     * <p>У не вошедшей сделки добывать нечего: итог там ноль по
     * проверенному признаку, и финализация идёт сразу.
     */
    private DealTransition harvestThenFinalize(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isTrue(deal.positionObserved()) && isNull(deal.getResultProfit())) {
            if (isNotEmpty(deal.episodesAwaitingCloseRecord())) {
                return DealTransition.stay()
                        .withCommand(fetch(dealContext, ServiceCommandType.REFRESH_POSITION_COMMAND));
            }
            if (isTrue(resultCalculator.flowsAwaitFetch(dealContext))) {
                return DealTransition.stay()
                        .withCommand(fetch(dealContext, ServiceCommandType.REFRESH_BILLS_COMMAND));
            }
        }
        return finalizeExit(dealContext);
    }

    /** Звено добычи, названное явно; пусто — звено ждёт отката повтора. */
    private ServiceCommand fetch(DealContext dealContext, ServiceCommandType link) {
        return systemActionExecutor.next(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION, dealContext, null,
                        link, null)
                .orElse(null);
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

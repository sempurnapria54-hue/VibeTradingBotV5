package com.example.tradingbot.domain.deal.handler;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.SystemActionType;
import com.example.tradingbot.domain.deal.DealFsmHandler;
import com.example.tradingbot.domain.deal.DealFsmSupport;
import com.example.tradingbot.domain.deal.DealTransition;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.position.Position;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FSM handler статуса EXIT_PENDING: сворачивает сделку целиком —
 * каскадирует выход в транши, закрывает нетто-экспозицию, добывает факты
 * закрытия и готовит терминал.
 *
 * <p><b>Каскад в транши идёт не отсюда:</b> каждый нетерминальный транш
 * прогоняется своей машиной тем же проходом оркестратора, до сделочного
 * прохода (docs/components/DealOrchestratorJob.md). Агрегат заявок за
 * транши не снимает.
 *
 * <p>Окно сворачивания открыто именно этим статусом: в нём закрывающее
 * исполнение УРОВНЯ СДЕЛКИ приписывается траншам правилом сопоставления
 * (docs/models/domain/aggregate/DealTranche.md), и потому сверка
 * экспозиции в окне не расходится.
 *
 * <p>Все транши терминальны → намерение закрыть сделку; право на терминал
 * даёт гейт живого риска в машине сделки.
 *
 * <p>См. docs/components/DealExitPendingHandler.md.
 */
@Component
@RequiredArgsConstructor
public class DealExitPendingHandler implements DealFsmHandler {

    private final DealFsmSupport support;

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.EXIT_PENDING;
    }

    /**
     * <b>Терминала прямым ребром здесь нет, и это не пропуск.</b> Штатный
     * терминал требует посчитанного числа, а считает его звено расчёта,
     * которое эмитит уже {@link #handle}; ребро ставит терминальное звено
     * той же цепочки (docs/components/MarkDealClosedExecutor.md). Прямой
     * переход по «все транши терминальны» опережал бы расчёт и закрывал
     * сделку без числа — а вместе с числом и без причины закрытия.
     */
    @Override
    public Optional<DealTransition> checkTransition(DealContext dealContext) {
        return Optional.empty();
    }

    /**
     * Порядок шагов сворачивания — инвариант
     * docs/rules/exit-teardown-order.md, а не удобство:
     *
     * <ol>
     *   <li>полное закрытие нетто-экспозиции — <b>не раньше</b>, чем
     *       предусловие {@code netCloseAllowed} истинно;</li>
     *   <li>добыча движений средств выходной тропы;</li>
     *   <li>баланс после снятия риска;</li>
     *   <li>финализация: расчёт числа, затем терминальное ребро.</li>
     * </ol>
     */
    @Override
    public DealTransition handle(DealContext dealContext) {
        Optional<DealTransition> netClose = netClose(dealContext);
        if (netClose.isPresent()) {
            return netClose.get();
        }
        if (isTrue(cashFlowFetchDue(dealContext))) {
            return support.refreshBillsCommand(dealContext)
                    .map(DealTransition::command)
                    .orElseGet(DealTransition::stay);
        }
        if (isFalse(support.balanceUsable(dealContext))) {
            return support.refreshBalanceCommand(dealContext)
                    .map(DealTransition::command)
                    .orElseGet(DealTransition::stay);
        }
        return finalize(dealContext);
    }

    /**
     * Полное закрытие нетто-экспозиции. Два гейта, и оба обязательны:
     *
     * <ul>
     *   <li><b>{@code netCloseAllowed}</b> — граф предъявлен целиком И у
     *       траншей не осталось живых входных ног
     *       (docs/spec/deal-lifecycle.json). Пока предусловие ложно,
     *       команда не эмитится — каскад траншей доводит их до снятия;</li>
     *   <li><b>форма выхода — условная</b>. На форме с явным действием
     *       команду шлёт исполнитель действия, и вторая команда по одной
     *       позиции получила бы биржевой отказ на штатной тропе выхода
     *       (docs/rules/no-partial-close.md §«Две законные формы полного
     *       выхода»). Различитель — наличие живой строки исполнения
     *       уровня сделки: явная форма её заводит, условная нет.</li>
     * </ul>
     */
    private Optional<DealTransition> netClose(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isFalse(deal.hasLivePositionRisk()) || isTrue(support.hasDealLevelExecution(dealContext))) {
            return Optional.empty();
        }
        if (isFalse(netCloseAllowed(dealContext))) {
            return Optional.empty();
        }
        return Optional.of(DealTransition.command(support.closePositionCommand(dealContext,
                deal.livePosition().getId(), Position.CloseReason.CLOSED_BY_STRATEGY)));
    }

    /**
     * Предусловие полного закрытия: граф предъявлен целиком и живых
     * входных ног у траншей не осталось. Порядок обратный — закрытие
     * поверх живой входной ноги открыло бы окно, в котором нога
     * доливается уже после снятия экспозиции.
     */
    private Boolean netCloseAllowed(DealContext dealContext) {
        if (isFalse(support.graphComplete(dealContext))) {
            return false;
        }
        return dealContext.getDeal().liveTranches().stream()
                .noneMatch(tranche -> isTrue(tranche.hasLiveEntryOrder()));
    }

    /**
     * Звено добычи движений средств — звено ВЫХОДНОЙ тропы: на аварийной
     * оно не эмитится вовсе (docs/components/SystemActionExecutor.md
     * §«Состав цикла добычи»). Надобность читается durable-фактом
     * «добыча выполнялась»: пусто = не добывали, а не «добыли, движений
     * нет» (docs/models/domain/aggregate/Deal.md).
     */
    private Boolean cashFlowFetchDue(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        return isFalse(deal.hasLivePositionRisk()) && isNull(deal.getBillsFetchedThrough());
    }

    /**
     * Завершение — через ОДНО действие финализации выхода с двумя
     * звеньями: расчёт числа, затем терминальное ребро. Звено выбирает
     * исполнитель системного действия по durable-факту «число уже
     * посчитано», а не handler: стадия выводится из подтверждённого
     * факта (docs/components/SystemActionExecutor.md).
     *
     * <p>Исчерпание бюджета действия — ошибочная тропа: числа после него
     * не будет, а штатный терминал его требует.
     */
    private DealTransition finalize(DealContext dealContext) {
        if (isTrue(support.systemActionFailed(dealContext, SystemActionType.FINALIZE_DEAL_EXIT_ACTION))) {
            return support.markError(dealContext);
        }
        return support.systemAction(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, dealContext, null)
                .map(DealTransition::command)
                .orElseGet(DealTransition::stay);
    }
}

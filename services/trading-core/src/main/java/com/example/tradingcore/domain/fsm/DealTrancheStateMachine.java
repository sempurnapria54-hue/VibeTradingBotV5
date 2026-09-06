package com.example.tradingcore.domain.fsm;

import static java.util.Objects.isNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Выбирает обработчик по текущему статусу транша, запускает его и
 * возвращает переход: команды и, если он разрешён, новый статус
 * (docs/components/DealTrancheStateMachine.md).
 *
 * <p><b>Зовут её обработчики активной сделки и координированного
 * выхода</b> — по одному проходу на каждый нетерминальный транш;
 * обработчик ошибочного состояния её не зовёт. Дом ответа —
 * docs/processes/fsm-execution-layering.md §«Кто прогоняет FSM транша».
 *
 * <p><b>Порядок прогона между траншами значения не имеет:</b> экспозиция
 * производная и пересчитывается из полного состояния. Из этого не
 * следует, что безразличен порядок СОПОСТАВЛЕНИЯ закрывающего исполнения
 * уровня сделки с траншами — он несущий и задан правилом
 * (docs/models/domain/aggregate/DealTranche.md).
 *
 * <p><b>Терминальный статус обработчика не имеет:</b> делать по закрытому
 * траншу нечего, и проход по нему — пустой переход.
 */
@Slf4j
@Service
public class DealTrancheStateMachine {

    private final Map<DealTranche.Status, DealTrancheHandler> handlers;
    private final TrancheTransitionGate transitionGate;

    public DealTrancheStateMachine(List<DealTrancheHandler> handlers, TrancheTransitionGate transitionGate) {
        this.handlers = handlers.stream().collect(toMap(DealTrancheHandler::handledStatus, identity()));
        this.transitionGate = transitionGate;
    }

    /**
     * Один проход FSM транша.
     *
     * <p><b>Ребро, предложенное обработчиком, гейтится матрицей здесь, а
     * не в обработчике.</b> Проверка у каждого из шести была бы шестью
     * носителями одного инварианта; отвергнутое ребро при этом не отменяет
     * команд прохода — работа сделана, а статус остаётся прежним до
     * следующего прохода.
     */
    public TrancheTransition run(DealContext dealContext, DealTranche tranche) {
        DealTrancheHandler handler = handlers.get(tranche.getStatus());
        if (isNull(handler)) {
            log.debug("No handler for tranche status trancheId={} status={}",
                    tranche.getId(), tranche.getStatus());
            return TrancheTransition.stay();
        }
        TrancheTransition transition = handler.handle(dealContext, tranche);
        if (isFalse(transition.movesStatus())) {
            return transition;
        }
        if (isTrue(transitionGate.transitionAllowed(dealContext, tranche, transition.getNextStatus()))) {
            bumpEpisodeOnReopen(tranche, transition.getNextStatus());
            return transition;
        }
        log.warn("Tranche transition refused by matrix trancheId={} from={} to={}",
                tranche.getId(), tranche.getStatus(), transition.getNextStatus());
        return transition.withoutStatus();
    }

    /**
     * Номер эпизода растёт на ОДОБРЕННОМ ребре переоткрытия — здесь, а не
     * в обработчике.
     *
     * <p>Признаки, чья область — эпизод, обязаны сбрасываться на
     * переоткрытии, и носителем сброса служит номер
     * (docs/rules/strategy-step-once-per-episode.md). Инкремент стои́т
     * после гейта: выданный до него, он пережил бы отвергнутое ребро и
     * обнулил бы признаки эпизода, который не начинался. Сохраняется он
     * той же транзакцией, что и статус, — оба поля живут на одной строке,
     * и пишет её применение перехода (docs/components/DealOrchestratorJob.md).
     */
    private void bumpEpisodeOnReopen(DealTranche tranche, DealTranche.Status target) {
        if (isFalse(transitionGate.reopenEdge(tranche.getStatus(), target))) {
            return;
        }
        Integer current = isNull(tranche.getEpisodeSeq()) ? 0 : tranche.getEpisodeSeq();
        tranche.setEpisodeSeq(current + 1);
    }
}

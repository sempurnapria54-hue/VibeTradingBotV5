package com.example.tradingcore.domain.service;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.event.StrategyActivatedContent;
import com.example.tradingbot.domain.event.StrategyEventType;
import com.example.tradingbot.domain.event.StrategyLifecycleContent;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingcore.persistence.service.InboxDataService;
import com.example.tradingcore.persistence.service.StrategyDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Применяет события владельца определений к копии в базе ядра —
 * <b>целевой писатель копии</b> (docs/architecture/data-ownership.md
 * §«Копии чужих данных»).
 *
 * <p><b>Дерево пишется один раз, статус — сколько угодно.</b> Определение
 * неизменяемо: повторная активация того же определения дерева не
 * переписывает, а двигает только статус
 * (docs/models/domain/aggregate/Strategy.md §«Что у копии неизменяемо, а
 * что состояние»). Переписывание дерева оборвало бы закрепление детали у
 * живой сделки.
 *
 * <p><b>Строка копии не удаляется никогда:</b> {@code DELETED} —
 * логический терминал. Сделке её деталь нужна до самого терминала, в том
 * числе на координированном выходе, который удаление и запускает.
 *
 * <p><b>Отметка обработки ложится ТОЙ ЖЕ транзакцией</b>, что и
 * следствие: отметка без следствия потеряла бы событие навсегда, а
 * следствие без отметки применилось бы дважды.
 *
 * <p><b>Событие о определении, копии которого нет, — штатный исход, а не
 * авария.</b> Копия заводится ТОЛЬКО активацией, а удалить определение
 * владелец позволяет и из {@code CREATED}: тропа «завёл — удалил, не
 * активируя» проходится держателем в один ход и событие удаления при
 * этом производит. Отказ на ней уронил бы обработку в цикл повторов,
 * заняв партию, и остановил бы применение СЛЕДУЮЩИХ событий — то есть
 * ошибался бы в разрешающую сторону по отношению к копиям, которые
 * подвинуть было нужно.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyDefinitionApplier {

    private final StrategyDataService strategyDataService;
    private final InboxDataService inboxDataService;

    /**
     * Применить событие активации: завести копию либо перевести
     * существующую в {@code ACTIVE}.
     */
    @Transactional
    public void applyActivated(String eventId, StrategyActivatedContent content) {
        if (isTrue(inboxDataService.isConsumed(eventId))) {
            return;
        }
        Strategy definition = content.definition();
        if (isNull(definition) || isNull(definition.getInternalId())) {
            throw new IllegalArgumentException("Activation event carries no definition identity: " + eventId);
        }
        definition.setStatus(Strategy.Status.ACTIVE);
        if (strategyDataService.findByInternalId(definition.getInternalId()).isPresent()) {
            strategyDataService.applyStatus(definition.getInternalId(), Strategy.Status.ACTIVE);
        } else {
            strategyDataService.saveTree(definition);
        }
        inboxDataService.markConsumed(eventId, StrategyEventType.STRATEGY_ACTIVATED.name());
        log.info("Strategy copy activated internalId={}", definition.getInternalId());
    }

    /**
     * Применить событие деактивации либо удаления: двигается только
     * статус — дерево у читателя уже лежит и неизменяемо.
     */
    @Transactional
    public void applyLifecycle(String eventId, StrategyEventType type, StrategyLifecycleContent content) {
        if (isTrue(inboxDataService.isConsumed(eventId))) {
            return;
        }
        String internalId = content.strategyInternalId();
        if (strategyDataService.findByInternalId(internalId).isEmpty()) {
            inboxDataService.markConsumed(eventId, type.name());
            log.info("Strategy fact concerns a definition that was never activated: no copy to move "
                    + "internalId={} eventType={}", internalId, type);
            return;
        }
        strategyDataService.applyStatus(internalId, targetStatus(type));
        inboxDataService.markConsumed(eventId, type.name());
        log.info("Strategy copy moved internalId={} status={}", internalId, targetStatus(type));
    }

    /** Целевой статус копии по классу события: перечень закрыт таблицей привязки. */
    private Strategy.Status targetStatus(StrategyEventType type) {
        return switch (type) {
            case STRATEGY_DEACTIVATED -> Strategy.Status.INACTIVE;
            case STRATEGY_DELETED -> Strategy.Status.DELETED;
            case STRATEGY_ACTIVATED -> throw new IllegalStateException(
                    "Activation carries the definition tree and is applied by its own method");
        };
    }
}

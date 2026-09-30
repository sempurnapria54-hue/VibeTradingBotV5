package com.example.tradingcore.domain.service;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.event.StrategyEventType;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingcore.exception.PoisonStrategyFactException;
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
 * <p><b>Дедуп — безопасной вставкой отметки ПЕРВЫМ ходом, а не проверкой
 * «обработано ли уже»</b> (docs/rules/idempotency-via-unique.md). Проверка
 * и следствие не атомарны: два конкурентных применения одного события оба
 * прочли бы «не обработано», и второе падало бы нарушением ключа отметки
 * уже ПОСЛЕ своего следствия. Вставка по ключу решает, кто первый, до
 * следствия; проигравший ждёт исхода победителя и уходит холостым. Отказ
 * следствия откатывает и отметку — транзакция одна.
 *
 * <p><b>Отказ применения не глотается здесь, а объявлен у приёма</b>
 * (docs/architecture/data-ownership.md §«Копии чужих данных»): отказ,
 * который лечит время (проекции счёта либо инструмента ещё нет), всплывает
 * как есть, и применение откладывается; дефект самой записи — класс
 * {@link PoisonStrategyFactException}, и запись пропускается со следом.
 *
 * <p><b>Событие о определении, копии которого нет, — штатный исход, а не
 * авария.</b> Копия заводится ТОЛЬКО активацией, а удалить определение
 * владелец позволяет и из {@code CREATED}: тропа «завёл — удалил, не
 * активируя» проходится держателем в один ход и событие удаления при
 * этом производит. Отказ на ней уронил бы обработку в цикл повторов,
 * заняв партию, и остановил бы применение СЛЕДУЮЩИХ событий — то есть
 * ошибался бы в разрешающую сторону по отношению к копиям, которые
 * подвинуть было нужно.
 *
 * <p><b>Формы сообщения этот сервис не видит.</b> Он доменный, и входы у
 * него доменные: снимок определения и его идентичность. Разбор конверта и
 * содержимого остаётся на границе шины
 * (.claude/rules/codestyle.md §«Слой сообщения: внутренняя шина»).
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
    public void applyActivated(String eventId, Strategy definition) {
        if (isNull(definition) || isNull(definition.getInternalId())) {
            throw new PoisonStrategyFactException("Activation event carries no definition identity: " + eventId);
        }
        if (isFalse(inboxDataService.markConsumedIfAbsent(eventId, StrategyEventType.STRATEGY_ACTIVATED.name()))) {
            return;
        }
        definition.setStatus(Strategy.Status.ACTIVE);
        if (strategyDataService.findByInternalId(definition.getInternalId()).isPresent()) {
            strategyDataService.applyStatus(definition.getInternalId(), Strategy.Status.ACTIVE);
        } else {
            strategyDataService.saveTree(definition);
        }
        log.info("Strategy copy activated internalId={}", definition.getInternalId());
    }

    /**
     * Применить событие деактивации либо удаления: двигается только
     * статус — дерево у читателя уже лежит и неизменяемо.
     */
    @Transactional
    public void applyLifecycle(String eventId, StrategyEventType type, String internalId) {
        if (isFalse(inboxDataService.markConsumedIfAbsent(eventId, type.name()))) {
            return;
        }
        if (strategyDataService.findByInternalId(internalId).isEmpty()) {
            log.info("Strategy fact concerns a definition that was never activated: no copy to move "
                    + "internalId={} eventType={}", internalId, type);
            return;
        }
        strategyDataService.applyStatus(internalId, targetStatus(type));
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

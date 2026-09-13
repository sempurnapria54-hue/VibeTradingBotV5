package com.example.statistics.persistence.service;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Операнды полноты чисел статистики, читаемые своим подключением под ролью
 * владельца (docs/rules/durable-consumer-reception.md).
 *
 * <p><b>Операндов три, а не четыре, и это следствие двух уже принятых
 * решений, а не упрощение</b> (docs/models/domain/other/StatisticsFact.md
 * §«Состояние приёма и полнота чисел статистики»): нижняя граница у
 * статистики считается ОДНИМ операндом — позднейшим моментом наблюдения её
 * пар. Второго у неё нет, потому что факты не чистятся ни в одном
 * окружении — начало ряда двигать нечем, — и потому что второй оси времени
 * факт не хранит вовсе.
 *
 * <p><b>Класс делегирует уже существующей границе</b>
 * ({@link ReceptionStateDataService}), а своих запросов не держит: тропа к
 * этим величинам уже проложена, и второй её носитель разошёлся бы с первым
 * молча.
 *
 * <p><b>Собственной транзакции методы не открывают:</b> границу называет
 * вызывающий.
 */
@Service
@RequiredArgsConstructor
public class ReceptionCompletenessSource {

    private final ReceptionStateDataService receptionStateDataService;

    /**
     * Позднейший момент наблюдения по строкам группы — включая снятые с
     * подписки; пусто — строк у группы нет
     * (docs/spec/durable-reception.json, {@code pairsObservedSince}).
     */
    public OffsetDateTime latestObservedSince(String consumerGroup) {
        return receptionStateDataService.latestObservedSince(consumerGroup);
    }

    /**
     * Сколько пар группа наблюдает сейчас — область квантора обоих её
     * предикатов; считаются только ПОДПИСАННЫЕ пары
     * (docs/spec/durable-reception.json, {@code pairsObserved}).
     */
    public Long countSubscribedPairs(String consumerGroup) {
        return receptionStateDataService.countSubscribedPairs(consumerGroup);
    }

    /**
     * Сколько подписанных пар группы <b>не</b> утверждают непрерывность:
     * ноль означает, что дыры нет ни на одной.
     *
     * @param staleBefore момент, раньше которого строка состояния приёма
     *                    считается устаревшей
     */
    public Long countSubscribedPairsWithBreak(String consumerGroup, OffsetDateTime staleBefore) {
        return receptionStateDataService.countSubscribedPairsWithBreak(consumerGroup, staleBefore);
    }
}

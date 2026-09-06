package com.example.tradingcore.domain.jobs;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingcore.config.OutboxRelayProperties;
import com.example.tradingcore.integration.EventPublisher;
import com.example.tradingcore.persistence.model.OutboxEntity;
import com.example.tradingcore.persistence.service.OutboxDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Читает неопубликованные строки outbox, публикует их в тему
 * производителя и помечает опубликованные
 * (docs/components/OutboxRelayJob.md). Больше ничего: содержимого событий
 * не собирает, решений не принимает, статусов сущностей не двигает.
 *
 * <p><b>Писатель строки — не он.</b> Строку пишет тот код, который пишет
 * решение, и той же транзакцией; реле начинается там, где транзакция
 * решения закончилась.
 *
 * <p><b>Порядок «сперва опубликовать, потом пометить» — не наоборот.</b>
 * Отметка и публикация в одной транзакции не лежат и лежать не могут:
 * брокер в транзакцию базы не входит. Обратный порядок ТЕРЯЛ бы событие
 * при обрыве, а этот дублирует — и дубль уже закрыт: потребитель
 * дедуплицирует по идентичности события.
 *
 * <p><b>Отказ брокера — не ошибка сделки.</b> Решение уже записано и от
 * публикации не зависит: проход прекращается на первом отказе (повторять
 * внутри тика бессмысленно — отказ у всех строк общий), строки остаются
 * непомеченными, следующий тик повторяет.
 *
 * <p><b>Названное ограничение: у накопления нет верхней границы.</b> Пока
 * брокера нет, outbox растёт линейно по числу решений. Гасить его
 * усечением нельзя — потерянное событие есть потерянный факт; гасит его
 * чистка опубликованных строк, и её глубина — отдельная величина с
 * условием возврата (`.claude/work/backlog.md` §«Размеры томов данных»).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

    private static final String JOB_NAME = "outboxRelayJob";

    private final OutboxRelayProperties properties;
    private final JobExecutionGuard executionGuard;
    private final OutboxDataService outboxDataService;
    private final EventPublisher eventPublisher;

    @Scheduled(cron = "${outbox-relay.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    private void run() {
        List<OutboxEntity> unpublished = outboxDataService.findUnpublished(properties.getBatchSize());
        if (isEmpty(unpublished)) {
            return;
        }
        for (OutboxEntity row : unpublished) {
            try {
                eventPublisher.publish(row);
            } catch (RuntimeException e) {
                log.warn("Broker refused the outbox row: the pass stops, rows stay unpublished eventId={}",
                        row.getEventId(), e);
                return;
            }
            markPublished(row);
        }
    }

    /**
     * Отметка публикации. Отказ самой отметки проход не рвёт: строка
     * останется непомеченной и будет опубликована повторно, а повтор
     * безопасен по построению.
     */
    private void markPublished(OutboxEntity row) {
        try {
            outboxDataService.markPublished(row.getId(), OffsetDateTime.now(ZoneOffset.UTC));
        } catch (RuntimeException e) {
            log.error("Outbox row is published but not marked eventId={}", row.getEventId(), e);
        }
    }
}

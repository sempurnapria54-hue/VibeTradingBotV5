package com.example.auditstatistics.domain.jobs;

import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.auditstatistics.config.ReceptionProperties;
import com.example.auditstatistics.domain.model.PairLagOperands;
import com.example.auditstatistics.domain.model.ReceptionPairMoments;
import com.example.auditstatistics.domain.service.ReceptionStateSyncService;
import com.example.auditstatistics.integration.ConsumerLagProvider;
import com.example.auditstatistics.integration.TopicRetentionProvider;
import com.example.auditstatistics.metrics.JournalReceptionMetrics;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ведёт состояние приёма журнала: держит состав строк
 * {@code journal_reception_states} равным подписке группы и отмечает, что
 * приём жив (docs/components/ReceptionStateJob.md).
 *
 * <p><b>Он заведён потому, что у слушателя такого момента нет.</b> Метод
 * слушателя вызывается сообщением; момента «прошли опрос, принимать было
 * нечего» в прикладном коде не существует, а величина, обновляемая только
 * при работе, не отличает «всё спокойно» от «потребителя нет».
 *
 * <p><b>Тем же обходом подписки тик добывает у брокера срок хранения
 * темы</b> и выводит из него порог алерта на лаг — умножением на долю,
 * величину конфигурации сервиса. Второго обходчика подписки не заводится:
 * этот и так её обходит.
 *
 * <p><b>Он же — ЕДИНСТВЕННЫЙ ПИСАТЕЛЬ РЯДОВ ЭКСПОРТА</b>
 * ({@link JournalReceptionMetrics}). Ряды пары живут ровно столько,
 * сколько живёт их измеритель: такт, который не измерил, уносит их все, а
 * не оставляет прежними. Возраст последнего принятого события при этом
 * едет наружу МОМЕНТОМ, а не готовым числом, — замороженный тактом, он
 * перестал бы расти вместе с умершим тиком.
 *
 * <p><b>Ручного фасада и защиты от перекрытия у тика нет, и это
 * объявлено, а не забыто</b> (.claude/rules/codestyle.md §Джобы, клауза о
 * тике живости). Доводов два, и они разные: поверхность
 * {@code audit-statistics} <b>только читает</b>, и ручной триггер завёл бы
 * входящую точку записи, которой инвентарь сервису не даёт; а догонять
 * тику нечего — пропущенный такт не оставляет невыполненной работы, и
 * {@code fixedDelay} следующий такт до конца текущего не запускает.
 * Выключатель у него есть — как у всякой джобы.
 *
 * <p><b>Границы:</b> он не пишет ни флага остановки, ни момента разрыва,
 * ни момента последнего принятого события — их писатель слушатель и его
 * обработчик ошибок. Момент наблюдения он <b>заводит</b>, но не
 * возобновляет: возобновление опирается на зафиксированное смещение
 * группы, которого тик не видит. Журнала он не читает и агрегатов не
 * трогает.
 */
@Component
@RequiredArgsConstructor
public class ReceptionStateJob {

    private final ReceptionProperties properties;
    private final KafkaListenerEndpointRegistry listenerRegistry;
    private final ReceptionStateSyncService receptionStateSyncService;
    private final TopicRetentionProvider topicRetentionProvider;
    private final ConsumerLagProvider consumerLagProvider;
    private final JournalReceptionMetrics receptionMetrics;

    /**
     * Такт тика; период — величина конфигурации, не хардкод.
     *
     * <p><b>Ряды экспорта живут ровно один такт.</b> Всякая тропа, на
     * которой такт не измерил — снятый выключатель, неживой приём, пустая
     * подписка, отказ чтения, — уносит их все, а не оставляет прежними:
     * ряд от прошлого такта утверждал бы измеренное там, где ничего не
     * измерялось (docs/components/ReceptionStateJob.md §«Молчание тика
     * уносит и порог, и это уже покрыто»).
     *
     * <p><b>Отказ посреди такта уносит ряды и уходит наружу.</b>
     * Проглоченный, он оставил бы наблюдателю картину прошлого такта под
     * видом нынешнего; погашенные ряды поднимает второе правило алерта —
     * на пропажу ряда (docs/architecture/platform.md §Наблюдаемость).
     */
    @Scheduled(fixedDelayString = "${reception.state-tick-interval}")
    public void tick() {
        if (isFalse(properties.getStateTickEnabled())) {
            receptionMetrics.forget();
            return;
        }
        Collection<MessageListenerContainer> containers = listenerRegistry.getListenerContainers();
        if (isFalse(isReceptionLive(containers))) {
            receptionMetrics.forget();
            return;
        }
        try {
            run(containers);
        } catch (RuntimeException e) {
            receptionMetrics.forget();
            throw e;
        }
    }

    /**
     * Такт живого приёма: состав строк приводится к подписке, ряды
     * экспорта — к измеренному этим тактом.
     */
    private void run(Collection<MessageListenerContainer> containers) {
        Set<String> subscription = declaredSubscription(containers);
        if (isEmpty(subscription)) {
            receptionMetrics.forget();
            return;
        }
        OffsetDateTime moment = OffsetDateTime.now(ZoneOffset.UTC);
        receptionStateSyncService.syncSubscription(properties.getGroupId(), subscription, moment);
        receptionMetrics.replaceWith(measure(containers));
    }

    /**
     * Операнды алерта по каждой подписанной паре.
     *
     * <p><b>Состав пар берётся у строк состояния, а не у подписки.</b>
     * Строки только что приведены к подписке этим же тактом, и источник
     * состава остаётся один: возраст выводится из durable-колонок строки,
     * и пара без строки его не имела бы вовсе.
     *
     * <p><b>Пустое остаётся пустым.</b> Тема, чей срок не добыт, порога не
     * получает; пара, чей лаг клиент не отдал, остатка не получает. Ни то,
     * ни другое не подменяется нулём: нулевой порог кричал бы всегда, а
     * нулевой остаток гасил бы алерт ровно там, где измеритель не мерит
     * (docs/spec/audit-journal.json, {@code pairLagAlertFires}).
     */
    private List<PairLagOperands> measure(Collection<MessageListenerContainer> containers) {
        List<PairLagOperands> operands = new ArrayList<>();
        for (ReceptionPairMoments pair : receptionStateSyncService.subscribedPairMoments(properties.getGroupId())) {
            operands.add(new PairLagOperands(
                    pair.getTopic(),
                    pair.lastEventMoment(),
                    threshold(pair.getTopic()),
                    consumerLagProvider.unconsumedRecords(containers, pair.getTopic()).orElse(null)));
        }
        return operands;
    }

    /**
     * Живость приёма: контейнеры запущены и держат назначенные партиции.
     *
     * <p><b>Это несущее свойство, а не оптимизация.</b> Тик, бьющийся по
     * расписанию независимо от живости приёма, доказывал бы ровно то, что
     * в сервисе тикает таймер: строка обновляется, возраст мал, предикат
     * свежести истинен — и непрерывность утверждается в состоянии, о
     * котором не известно ничего. Пустое назначение означает, что группа
     * развалилась либо связи с брокером нет.
     */
    private Boolean isReceptionLive(Collection<MessageListenerContainer> containers) {
        if (isEmpty(containers)) {
            return Boolean.FALSE;
        }
        return containers.stream().allMatch(this::isLive);
    }

    private Boolean isLive(MessageListenerContainer container) {
        return isTrue(container.isRunning()) && isFalse(isEmpty(container.getAssignedPartitions()));
    }

    /**
     * Состав пар — из ОБЪЯВЛЕННОЙ подписки контейнера, а не из
     * назначенных партиций.
     *
     * <p><b>Разведение операндов существенно.</b> Назначенные партиции —
     * доля этой реплики: тема, отданная другой реплике той же группы, у
     * этой пуста, и по назначению её строка получила бы признак «не
     * подписана» при живой подписке — ошибка в разрешающую сторону,
     * опускающая ту самую границу, ради которой строка не удаляется.
     * Поэтому назначение — операнд живости, объявленная подписка —
     * операнд состава.
     */
    private Set<String> declaredSubscription(Collection<MessageListenerContainer> containers) {
        Set<String> topics = new LinkedHashSet<>();
        for (MessageListenerContainer container : containers) {
            String[] declared = container.getContainerProperties().getTopics();
            if (nonNull(declared)) {
                topics.addAll(List.of(declared));
            }
        }
        return topics;
    }

    /**
     * Порог алерта по теме: доля от добытого срока её хранения.
     *
     * <p><b>Тема, чей срок не добыт, порога не получает</b> — ни нуля, ни
     * прежнего значения: пустой порог не читается как «алерта нет», и
     * алерт на такой паре срабатывает, потому что измеритель не мерит
     * (docs/spec/audit-journal.json, {@code lagAlertThreshold}).
     */
    private Long threshold(String topic) {
        return topicRetentionProvider.retentionMs(topic)
                .map(this::fractionOf)
                .orElse(null);
    }

    private Long fractionOf(Long retentionMs) {
        return Math.round(retentionMs * properties.getLagAlertThresholdFraction());
    }
}

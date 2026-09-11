package com.example.auditstatistics.integration;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.auditstatistics.domain.model.AuditRecord;
import com.example.auditstatistics.domain.service.AuditReceptionService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Принимает событие в журнал аудита
 * (docs/components/AuditEventListener.md).
 *
 * <p>Больше ничего: содержимого не интерпретирует, агрегатов не двигает,
 * решений не принимает. Что он пишет и с какой семантикой — у владельца
 * предмета (docs/models/domain/other/AuditRecord.md).
 *
 * <p><b>Метод вызывается сообщением.</b> Опрос существует, но он у
 * контейнера: холостого прохода в прикладном коде нет по построению.
 * Отсюда две вещи — величины, чей момент «есть ли что принимать»
 * независимо от приёма, пишет тик состояния приёма, а границы транзакций
 * называет этот класс, потому что контейнер транзакции не открывает.
 *
 * <p><b>Подписка — на тему КАЖДОГО производителя несомых классов</b>, а не
 * на одну: событие каждого производителя едет в свою тему, и подписка на
 * одну оставила бы журнал без классов остальных.
 *
 * <p><b>Неполный вход роняет обработку, а не пропускается.</b> Пропуск,
 * законный у периметра, здесь запрещён: журнал есть носитель факта, и
 * пропущенное им не восстанавливается ничем.
 */
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final JournalEnvelopeReader envelopeReader;
    private final ReceptionOffsetTracker offsetTracker;
    private final AuditReceptionService receptionService;

    /**
     * Принять сообщение.
     *
     * <p><b>Порядок ходов несущий.</b> Разрыв смещений отмечается
     * <b>своей</b> транзакцией до приёма: он есть свидетельство о ходе
     * приёма, а не следствие принятого сообщения, и откат обработки не
     * вправе его унести. Строка журнала, момент последнего принятого
     * события и снятие флага остановки ложатся одной транзакцией — их
     * откат обязан быть общим.
     *
     * <p><b>Тем столько, сколько производителей у несомых классов</b>, а
     * не одна: событие каждого производителя едет в свою тему
     * (docs/architecture/contracts.md §«Событие → тема»), и подписка на
     * одну оставила бы журнал без классов остальных.
     *
     * <p><b>Ключ конфигурации разбирается ЗДЕСЬ, и второго его читателя
     * не заводится.</b> Форма значения — скаляр через запятую: она же
     * мерится пробой подписки ({@code ReceptionSubscriptionTest}) по
     * умолчанию {@code application.yaml}. Объявленное рядом поле
     * {@code List<String>} потребителя бы не нашло, а перечнем YAML этот
     * плейсхолдер не разрешается (.claude/rules/policy-home.md).
     */
    @KafkaListener(topics = "#{'${reception.topics}'.split(',')}")
    public void onEvent(ConsumerRecord<String, String> record) {
        if (isTrue(offsetTracker.observeDelivery(record))) {
            receptionService.noteGap(record.topic(), OffsetDateTime.now(ZoneOffset.UTC));
        }
        AuditRecord auditRecord = envelopeReader.read(record);
        if (isFalse(auditRecord.hasCompleteInput())) {
            throw new IncompleteEventException("Событие без обязательного значения конверта либо содержимого"
                    + " topic=" + record.topic() + " offset=" + record.offset());
        }
        receptionService.accept(auditRecord, record.topic());
    }
}

package com.example.tradingcore.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Строка следа пропуска: отравленная запись, которую группа ядра на теме
 * определений пропустила (docs/rules/durable-consumer-reception.md §«След
 * пропуска — таблица `reception_skips`»).
 *
 * <p><b>Предмет строки — происшествие, а не состояние:</b> строка на каждую
 * пропущенную запись, и второй пропуск первого не переписывает. Строки не
 * чистятся и не снимаются — у состояния «разобран» читателя нет.
 *
 * <p><b>Координаты несущие:</b> по ним пропущенное возвращается приёмом
 * после правки формы, и по ним же повторная доставка той же записи строку
 * не удваивает (ключ {@code uk_reception_skip_record}).
 *
 * <p><b>Аудита у строки нет намеренно</b> — состав колонок закрыт домом:
 * строка сама и есть запись факта со своим моментом, а правок у неё не
 * бывает. Форма та же, что у отметки обработанного
 * ({@link InboxEventEntity}).
 */
@Getter
@Setter
@Entity
@Table(name = "reception_skips")
public class ReceptionSkipEntity {

    /** Числовой ключ строки. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Группа потребителя, пропустившая запись. */
    @Column(name = "consumer_group", nullable = false, updatable = false)
    private String consumerGroup;

    /** Тема пропущенной записи. */
    @Column(name = "topic", nullable = false, updatable = false)
    private String topic;

    /** Партиция пропущенной записи. */
    @Column(name = "record_partition", nullable = false, updatable = false)
    private Integer recordPartition;

    /** Смещение пропущенной записи в партиции. */
    @Column(name = "record_offset", nullable = false, updatable = false)
    private Long recordOffset;

    /** Идентичность события из конверта; пусто — конверт её не нёс. */
    @Column(name = "event_id", updatable = false)
    private String eventId;

    /** Класс события из конверта; пусто — конверт его не нёс. */
    @Column(name = "event_type", updatable = false)
    private String eventType;

    /** Ключ записи — тенант; пусто — запись его не несёт. */
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;

    /** Первопричина отказа: класс и сообщение по цепочке причин. */
    @Column(name = "cause", nullable = false, updatable = false)
    private String cause;

    /** Момент пропуска. */
    @Column(name = "skipped_at", nullable = false, updatable = false)
    private OffsetDateTime skippedAt;
}

package com.example.auditstatistics.persistence.model.journal;

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
 * Текущее состояние приёма по паре «группа потребителей × тема»
 * (docs/models/domain/other/AuditRecord.md §«Строка состояния приёма —
 * таблица journal_reception_states»).
 *
 * <p><b>Ключ — пара, а не одна группа:</b> остановка приёма случается на
 * сообщении, то есть на конкретной теме, и строка на группу целиком
 * склеивала бы состояния разных производителей.
 *
 * <p><b>Истории строка не хранит:</b> её предмет — текущее состояние, а не
 * происшествие; историю несёт сам журнал.
 *
 * <p><b>Писателей у величин несколько, и каждый назван домом</b> (там же,
 * таблица писателей). Слушатель приёма и его обработчик ошибок пишут
 * величины самого приёма, тик состояния приёма — состав пар и живость.
 * Строку <b>заводит только тик</b>: величины приёма ложатся обновлением
 * существующей строки, и вставки у них нет — иначе у состава строк
 * появился бы второй писатель.
 */
@Getter
@Setter
@Entity
@Table(name = "journal_reception_states")
public class JournalReceptionStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Группа потребителей — первая половина ключа пары. */
    @Column(name = "consumer_group", nullable = false, updatable = false)
    private String consumerGroup;

    /** Тема производителя — вторая половина ключа пары. */
    @Column(name = "topic", nullable = false, updatable = false)
    private String topic;

    /**
     * Момент, с которого группа наблюдает тему непрерывно. Двигается
     * только вперёд: заведением строки и возобновлением наблюдения.
     */
    @Column(name = "observed_since", nullable = false)
    private OffsetDateTime observedSince;

    /** Тема сейчас в подписке группы. Строка ушедшей темы не удаляется. */
    @Column(name = "subscribed", nullable = false)
    private Boolean subscribed;

    /** Приём по паре остановлен; при заведении строки — ложь. */
    @Column(name = "reception_halted", nullable = false)
    private Boolean receptionHalted;

    /** Момент обнаружения разрыва по сроку хранения; пусто = разрыва не было. */
    @Column(name = "lag_gap_at")
    private OffsetDateTime lagGapAt;

    /**
     * Момент происшествия последнего принятого события пары; пусто = не
     * принято ещё ничего.
     */
    @Column(name = "last_accepted_occurred_at")
    private OffsetDateTime lastAcceptedOccurredAt;

    /** Момент последнего такта тика — свежесть самой строки. */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

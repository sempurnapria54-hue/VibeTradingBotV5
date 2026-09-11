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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Строка журнала событий по тенанту
 * (docs/models/domain/other/AuditRecord.md §Персистентность).
 *
 * <p><b>Строка неизменяема по построению:</b> событие произошло однажды, и
 * правок у записи о нём не бывает. Отсюда все колонки объявлены
 * {@code updatable = false}, а полей аудита сущности у неё нет вовсе —
 * набор {@code Auditable} бинарен, и три его колонки остались бы пустыми
 * навсегда.
 *
 * <p><b>Содержимое лежит навесом, а не колонками.</b> Форму содержимого
 * знает только производитель класса; журнал хранит факт, а не его
 * интерпретацию. Колонками вынесены операнд ключа дедупа и четыре радиуса
 * отбора — по объявленным исключениям правила представления
 * (docs/rules/persistence-representation.md).
 *
 * <p><b>Вставляется эта строка НЕ через сохранение сущности:</b> форма
 * вставки предписана домом — поглощающая конфликт по ключу
 * (docs/components/AuditEventListener.md §«Форма вставки — поглощающая
 * конфликт по ключу»), и живёт она нативным запросом репозитория. Класс
 * держит отображение схемы, по которому строки читаются.
 */
@Getter
@Setter
@Entity
@Table(name = "audit_records")
public class AuditRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Идентичность принятого события; она же ключ дедупа доставки и
     * внешняя идентичность строки — наружу журнал отдаёт её, а не ключ
     * базы.
     */
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    /** Тенант-владелец события: радиус отбора по умолчанию. */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    /** Класс события — дискриминатор содержимого, имя из перечня производителя. */
    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    /** Момент происшествия из конверта, от производителя. */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    /**
     * Момент приёма события журналом. Первый операнд нижней границы
     * полноты; монотонным не является — значение присваивается кодом до
     * фиксации транзакции.
     */
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private OffsetDateTime recordedAt;

    /** Версия формы содержимого из конверта. */
    @Column(name = "version", nullable = false, updatable = false)
    private Integer version;

    /** Контекст трассировки; пусто законно и означает «трассировки не было». */
    @Column(name = "trace_context", updatable = false)
    private String traceContext;

    /** Радиус отбора: биржевой счёт, если содержимое его несёт. */
    @Column(name = "exchange_account_internal_id", updatable = false)
    private String exchangeAccountInternalId;

    /** Радиус отбора: инструмент, если содержимое его несёт. */
    @Column(name = "instrument_internal_id", updatable = false)
    private String instrumentInternalId;

    /** Радиус отбора: сделка, если содержимое её несёт. */
    @Column(name = "deal_internal_id", updatable = false)
    private String dealInternalId;

    /** Радиус отбора: определение стратегии, если содержимое его несёт. */
    @Column(name = "strategy_internal_id", updatable = false)
    private String strategyInternalId;

    /** Содержимое события целиком, как доставлено. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false, updatable = false)
    private String content;
}

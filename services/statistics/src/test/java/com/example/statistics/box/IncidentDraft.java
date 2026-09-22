package com.example.statistics.box;

import java.time.OffsetDateTime;
import lombok.Builder;

/**
 * Заготовка строки факта происшествия: все её колонки одной формой, разрезы —
 * названные клеткой
 * (.claude/tests/cases/statistics.md §«B9 — Счётчики происшествий: своё
 * зерно»).
 *
 * <p><b>Форма та же, что у соседа по зерну</b> ({@link DealDraft}), и повод
 * тот же: клетки счётчиков расходятся ровно по одной-двум колонкам разрезов —
 * жёсткость ступени, критичность отчёта, код ручной операции, — а перегрузка
 * на сочетание дала бы подписи с тремя пустотами подряд, у которых порядок
 * читается только счётом запятых.
 *
 * <p><b>Умолчание разрезов — ПУСТО, и это не «нездоровая» заготовка.</b> У
 * сделочного факта умолчания суть здоровая сделка, потому что каждый его
 * операнд обязан приехать у всякой сделки; у факта происшествия разрез есть
 * свойство КЛАССА события, а не здоровья факта: жёсткости нет у отчёта,
 * критичности нет у подъёма ступени, и пустота у них означает «разрез у этого
 * класса не определён» (docs/rules/absent-value-semantics.md). Умолчание
 * «жёсткость SOFT» поставило бы у заведения сделки разрез, которого у её
 * класса нет вовсе.
 *
 * <p><b>Команда вставки живёт здесь одна на обе тропы.</b> {@link Facts}
 * кладёт свои штатные факты ею же: две записи одного списка колонок разошлись
 * бы при первом же расширении таблицы, и разошлись бы молча — пропущенная
 * колонка легла бы пустотой.
 *
 * <p><b>Пустые разрезы кладутся с ЯВНЫМ приведением типа.</b> Драйвер выводит
 * тип пустого аргумента из метаданных, а не из цели, и без приведения вставка
 * отвергается прежде, чем дойдёт до предмета клетки — тот же довод, что у
 * пустых компонентов ключа зерна.
 *
 * <p><b>Слова разрезов берутся у {@link Bodies}</b>: там дом перечней чужого
 * производителя, и вторая их запись разошлась бы с первой при первой же
 * правке (.claude/rules/carrier-levels.md).
 */
@Builder
final class IncidentDraft {

    /** Команда вставки факта происшествия: все колонки таблицы поимённо. */
    private static final String INSERT = """
            insert into incident_facts
                (event_id, tenant_id, exchange_account_internal_id, occurred_at, event_type,
                 hold_rung, anomaly_severity, operation_code)
            values (?, ?, ?, ?, ?,
                    cast(? as varchar), cast(? as varchar), cast(? as varchar))
            """;

    /** Идентичность события: она же половина ключа строки. */
    private final String eventId;

    /** Тенант зерна — радиус владения. */
    private final String tenantId;

    /** Момент происшествия: ось времени зерна и вторая половина ключа. */
    private final OffsetDateTime occurredAt;

    /** Класс события: по нему счётчик отбирает свои строки. */
    private final String eventType;

    /** Биржевой счёт — обязательный ключ зерна; он же объект радиуса ступени. */
    @Builder.Default
    private final String account = Facts.ACCOUNT;

    /** Жёсткость поднятой ступени; пусто — разрез у класса не определён. */
    private final String holdRung;

    /** Критичность отчёта; пусто — разрез у класса не определён. */
    private final String anomalySeverity;

    /** Код операции: им и только им различается ручная тропа. */
    private final String operationCode;

    /**
     * Заготовка факта названного класса без разрезов.
     *
     * @param eventId    идентичность события
     * @param tenantId   тенант зерна
     * @param eventType  класс события
     * @param occurredAt момент происшествия
     */
    static IncidentDraftBuilder of(String eventId, String tenantId, String eventType,
                                   OffsetDateTime occurredAt) {
        return IncidentDraft.builder()
                .eventId(eventId)
                .tenantId(tenantId)
                .eventType(eventType)
                .occurredAt(occurredAt);
    }

    /** Кладёт заготовку строкой таблицы фактов происшествий. */
    void put() {
        Rows.shared().write(INSERT, eventId, tenantId, account, occurredAt, eventType,
                holdRung, anomalySeverity, operationCode);
    }
}

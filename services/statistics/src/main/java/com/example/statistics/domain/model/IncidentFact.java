package com.example.statistics.domain.model;

import static java.util.Objects.nonNull;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Факт происшествия — строка-предшественник строки агрегата происшествий
 * (docs/models/domain/other/StatisticsFact.md §«Факт происшествия»).
 *
 * <p><b>Ключ зерна у́же сделочного на две колонки, и это не экономия:</b>
 * расчётной валюты не несёт ни одно из несомых событий, а определения
 * стратегии нет у части из них по построению радиуса — введённое ради
 * остальных, оно дало бы пустой ключ со вторым смыслом
 * (docs/rules/absent-value-semantics.md).
 *
 * <p><b>Колонок идентичности радиуса у факта нет</b>, и это решение: ни
 * один объявленный счётчик по сделке, инструменту или определению не
 * отбирает (docs/rules/statistics-aggregates.md §«Счётчики происшествий —
 * своё зерно, а не строка сделочного агрегата»).
 */
@Value
@Builder
public class IncidentFact {

    /** Идентичность принятого события; она же отметка обработанного. */
    String eventId;

    /** Тенант-владелец: ключ зерна и радиус отбора. */
    String tenantId;

    /** Биржевой счёт: ключ зерна. */
    String exchangeAccountInternalId;

    /** Момент происшествия из конверта. <b>Ось времени таблицы.</b> */
    OffsetDateTime occurredAt;

    /** Класс события — по нему счётчик отбирает свои строки. */
    String eventType;

    /** Жёсткость поднятой ступени; пусто — разрез у этого класса не определён. */
    String holdRung;

    /** Критичность отчёта; пусто — разрез у этого класса не определён. */
    String anomalySeverity;

    /**
     * Код операции: им и только им различается ручная тропа.
     *
     * <p>Актор строки для этого не годится: у джобы, запущенной ручным
     * триггером, он равен принципалу, и автоматическая остановка, найденная
     * в ручном прогоне детекции, попала бы в число ручных.
     */
    String operationCode;

    /** Вход полон: ключ дедупа, оба ключа зерна, ось времени и класс. */
    public Boolean hasCompleteInput() {
        return nonNull(eventId)
                && nonNull(tenantId)
                && nonNull(exchangeAccountInternalId)
                && nonNull(occurredAt)
                && nonNull(eventType);
    }
}

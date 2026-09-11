package com.example.auditstatistics.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Строка зерна происшествий в доменной форме
 * (docs/rules/statistics-aggregates.md §«Счётчики происшествий — своё
 * зерно, а не строка сделочного агрегата»).
 *
 * <p><b>Зерно у неё своё, и разводит зёрна состав события, а не то, сумма
 * это или счёт:</b> расчётной валюты не несёт ни одно из её четырёх
 * событий, а ключом сделочного зерна она стои́т.
 *
 * <p><b>Каждая величина — отбор строк журнала по классу события</b>, то есть
 * считает СОБЫТИЯ, а не их предметы; условие верности этой формы живёт в
 * доме и здесь не пересказывается. Следствие для этой строки одно: счётчика
 * остановленных сделок среди её величин нет — он был бы объявлен считать
 * сделки, а событие остановки публикуется на каждом ребре присвоения
 * причины.
 */
@Value
@Builder
public class IncidentAggregate {

    /** Тенант-владелец строки: ключ зерна и радиус чтения. */
    String tenantId;

    /** Биржевой счёт: ключ зерна. */
    String exchangeAccountInternalId;

    /** Сутки UTC: ключ зерна и порция прохода пересчёта. */
    LocalDate bucketDate;

    /** Заведённых сделок за сутки. */
    Integer openedDeals;

    /** Решений о создании обычной заявки; перестановка ноги даёт своё решение. */
    Integer orderDecisions;

    /** Поднятых ступеней — обе тропы вместе. */
    Integer raisedHolds;

    /** Из них жёстких. */
    Integer hardRaisedHolds;

    /** Из них поднятых ручной тропой; тропу различает код операции, а не актор. */
    Integer manuallyRaisedHolds;

    /** Отчётов о происшествиях — обе тропы вместе. */
    Integer anomalyReports;

    /** Из них критичных. */
    Integer criticalAnomalyReports;

    /** Из них заведённых ручной тропой, а не детекцией. */
    Integer manualOperationReports;

    /** Момент сборки строки — показание читателю о лаге проекции. */
    OffsetDateTime assembledAt;
}

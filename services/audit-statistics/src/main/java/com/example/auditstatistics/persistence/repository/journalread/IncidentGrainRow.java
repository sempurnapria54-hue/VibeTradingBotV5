package com.example.auditstatistics.persistence.repository.journalread;

/**
 * Строка зерна происшествий, собранная группировкой В БАЗЕ ЖУРНАЛА
 * (docs/rules/statistics-aggregates.md §«Счётчики происшествий — своё
 * зерно, а не строка сделочного агрегата»).
 *
 * <p><b>Каждый счётчик — отбор строк журнала по классу события</b>, то есть
 * считает СОБЫТИЯ, а не их предметы. Условие верности формы и разбор по
 * каждому отбираемому классу — в доме, здесь не пересказываются; следствие
 * для этой строки одно: счётчика остановленных сделок в перечне не бывает —
 * ему нужен отбор по различным сделкам, а не по строкам.
 *
 * <p>Довод отсутствия доменной формы — тот же, что у
 * {@link DealGrainRow}: считает запрос, а не Java.
 */
public interface IncidentGrainRow {

    String getTenantId();

    String getExchangeAccountInternalId();

    Integer getOpenedDeals();

    Integer getOrderDecisions();

    Integer getRaisedHolds();

    Integer getHardRaisedHolds();

    Integer getManuallyRaisedHolds();

    Integer getAnomalyReports();

    Integer getCriticalAnomalyReports();

    Integer getManualOperationReports();
}

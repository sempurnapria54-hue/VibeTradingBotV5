package com.example.auditstatistics.persistence.repository.journalread;

import java.math.BigDecimal;

/**
 * Строка сделочного зерна, собранная группировкой В БАЗЕ ЖУРНАЛА
 * (docs/rules/statistics-aggregates.md §«Пересчёт — проекция, а не
 * накопитель»).
 *
 * <p><b>Через границу процесса проходит число строк ЗЕРНА, а не число
 * событий.</b> Объём этой выдачи ограничен произведением операндов ключа
 * зерна — величиной, которая растёт с бизнесом, а не с потоком событий;
 * «вытащить сутки по всем тенантам в память» правило больших выборок
 * запрещает прямо (.claude/rules/codestyle.md §«Выборка данных…»).
 *
 * <p><b>Доменной формы у строки не заводится, и это решение.</b> Считает
 * запрос, а не Java: арифметика зерна объявлена домом как группировка в
 * базе журнала, и доменная копия строки не несла бы ни одного предиката —
 * она была бы носителем без предмета (.claude/rules/design-simplicity.md).
 * Сутки зерна в строке не едут: они операнд порции, один на всю выдачу.
 */
public interface DealGrainRow {

    String getTenantId();

    String getExchangeAccountInternalId();

    String getStrategyInternalId();

    String getResultCurrency();

    Integer getClosedDeals();

    Integer getRiskBearingDeals();

    Integer getWinningDeals();

    Integer getLosingDeals();

    Integer getNeutralDeals();

    Integer getResultUnavailableDeals();

    Integer getCurrencyUnresolvedDeals();

    Integer getRiskUnsizedDeals();

    Integer getLiquidatedDeals();

    Integer getForcedReductionDeals();

    Integer getOutcomeUndeterminedDeals();

    Integer getReconciliationMismatchedDeals();

    Integer getReconciliationNotRunDeals();

    Integer getBreakdownIncompleteDeals();

    Integer getBreakdownNotAssessedDeals();

    Integer getRiskBenchmarkMissingDeals();

    Integer getRDenominatorDeals();

    BigDecimal getResultBeforeFundingSum();

    BigDecimal getNetResultSum();

    BigDecimal getFeeSum();

    BigDecimal getFundingSum();

    BigDecimal getLiquidationPenaltySum();

    BigDecimal getWinResultSum();

    BigDecimal getLossResultSum();

    BigDecimal getPlannedRiskSum();

    BigDecimal getPlannedRiskExcludedSum();

    BigDecimal getRSum();
}

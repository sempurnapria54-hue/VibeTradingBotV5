package com.example.statistics.persistence.repository.facts;

import com.example.statistics.persistence.model.facts.DealFactEntity;
import com.example.statistics.persistence.model.facts.DealFactId;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Запись сделочного факта
 * (docs/models/domain/other/StatisticsFact.md §«Отметка обработанного»).
 *
 * <p><b>Наследует {@code Repository}, а не {@code JpaRepository}:</b>
 * тропа у строки одна — вставка, поглощающая конфликт по ключу события;
 * унаследованные {@code save} и {@code delete} завели бы в коде тропы,
 * которых у неизменяемого факта не бывает (глубины хранения у фактов нет
 * ни в одном окружении — там же, §Персистентность).
 *
 * <p><b>Вставка НАТИВНАЯ, и это не вкус.</b> Дедуп доставки выражается
 * конфликтом по уникальному ключу, поглощаемым базой
 * (docs/rules/idempotency-via-unique.md); проверка существования перед
 * вставкой — построенный класс дефекта, а не альтернатива
 * (.claude/work/backlog.md §«Дедуп проверкой существования перед
 * вставкой»).
 */
public interface DealFactRepository extends Repository<DealFactEntity, DealFactId> {

    /**
     * Вставить строку факта, поглотив повторную доставку.
     *
     * <p>Ключ поглощения — идентичность события: следствие приёма у
     * статистики одно, и его ключ служит отметкой обработанного. Отдельной
     * таблицы inbox поэтому нет — она была бы вторым носителем той же
     * истины (.claude/rules/carrier-levels.md).
     *
     * <p><b>Ограничение называется ИМЕНЕМ, а не перечнем колонок, и это не
     * стиль.</b> Таблица — гипертаблица Timescale, и уникальное ограничение
     * у неё обязано включать колонку разбиения: миграция объявляет
     * {@code pk_deal_fact (event_id, closed_at)} и сама называет это
     * требованием Timescale. Спецификация конфликта по одному
     * {@code event_id} уникального индекса не находит и роняет <b>каждую</b>
     * вставку — не редкую и не повторную; отказ при этом приходит на приёме,
     * то есть группа встаёт на первом же принятом событии. Прежняя редакция
     * несла ровно её, и предъявил долг первый прогон чёрного ящика на
     * настоящей базе (кейсы {@code B1.1}, {@code B1.9} —
     * .claude/tests/cases/statistics.md, находка {@code F-1}).
     *
     * <p><b>Дедуп от именования не слабеет:</b> идентичность события
     * уникальна по построению, и пара с осью времени остаётся уникальной
     * ровно тогда же. Форма совпала с той, которой уже пользуется upsert
     * агрегатов ({@code on conflict on constraint}), и второй формы у
     * поглощения конфликта в дереве теперь нет.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into deal_facts
                (event_id, tenant_id, exchange_account_internal_id, strategy_internal_id,
                 result_currency, closed_at, took_risk, graph_complete, net_result, fee,
                 funding, liquidation_penalty, planned_risk, close_outcome,
                 reconciliation_status, breakdown_incomplete, risk_benchmark_availability)
            values
                (:eventId, :tenantId, :exchangeAccountInternalId, :strategyInternalId,
                 :resultCurrency, :closedAt, :tookRisk, :graphComplete, :netResult, :fee,
                 :funding, :liquidationPenalty, :plannedRisk, :closeOutcome,
                 :reconciliationStatus, :breakdownIncomplete, :riskBenchmarkAvailability)
            on conflict on constraint pk_deal_fact do nothing
            """)
    void insertAbsorbingDuplicate(@Param("eventId") String eventId,
                                  @Param("tenantId") String tenantId,
                                  @Param("exchangeAccountInternalId") String exchangeAccountInternalId,
                                  @Param("strategyInternalId") String strategyInternalId,
                                  @Param("resultCurrency") String resultCurrency,
                                  @Param("closedAt") OffsetDateTime closedAt,
                                  @Param("tookRisk") Boolean tookRisk,
                                  @Param("graphComplete") Boolean graphComplete,
                                  @Param("netResult") BigDecimal netResult,
                                  @Param("fee") BigDecimal fee,
                                  @Param("funding") BigDecimal funding,
                                  @Param("liquidationPenalty") BigDecimal liquidationPenalty,
                                  @Param("plannedRisk") BigDecimal plannedRisk,
                                  @Param("closeOutcome") String closeOutcome,
                                  @Param("reconciliationStatus") String reconciliationStatus,
                                  @Param("breakdownIncomplete") String breakdownIncomplete,
                                  @Param("riskBenchmarkAvailability") String riskBenchmarkAvailability);
}

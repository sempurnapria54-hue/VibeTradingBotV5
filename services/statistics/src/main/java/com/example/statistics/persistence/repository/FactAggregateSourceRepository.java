package com.example.statistics.persistence.repository;

import com.example.statistics.persistence.model.facts.DealFactEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Входы пересчёта агрегатов, читаемые из СВОИХ фактов
 * (docs/rules/statistics-aggregates.md §«Пересчёт — проекция, а не
 * накопитель»).
 *
 * <p><b>Источник — собственные факты сервиса, а не журнал аудита.</b>
 * Общей истины у двух сторон нет, и каждая потребляет события сама
 * (.claude/decisions/audit-statistics-split.md); чужого подключения у этого
 * прохода нет ни одного, и кросс-базового запроса в нём не существует по
 * построению.
 *
 * <p><b>Наследует {@code Repository}, а не {@code JpaRepository}, и это
 * несущий выбор.</b> Пересчёт факты только ЧИТАЕТ: пишет их приём, чистки у
 * них нет ни в одном окружении. Унаследованный {@code save} и
 * {@code delete} завёл бы в коде тропы, которых у неизменяемого факта не
 * бывает.
 *
 * <p><b>Агрегирование выполняется В БАЗЕ, а не в памяти джобы:</b> запрос
 * группирует строки суток по ключу зерна собираемой строки и отдаёт по
 * строке на ключ. Другого ключа у запроса нет — колонка входит в
 * группировку тогда и только тогда, когда она <b>измеряет</b> строку
 * агрегата.
 *
 * <p><b>Сутки зерна — операнд порции, а не колонка группировки:</b> проход
 * идёт посуточными порциями (запрос и upsert на каждые сутки окна), и одним
 * запросом ключи разных суток не собираются.
 *
 * <p><b>Литералов чужих перечней в тексте стало меньше, и это следствие
 * фактов.</b> Исход закрытия, состояние сверки, полнота разбивки и
 * доступность базы риска по-прежнему сравниваются со значениями перечней
 * <b>производителя</b> — общей библиотеки его перечней в дереве этого
 * сервиса нет, и единого носителя у них тут быть не может. Класс события,
 * жёсткость ступени и критичность отчёта из текста ушли: они стали
 * <b>колонками</b> факта, и раскладывает их приём.
 *
 * <p><b>Псевдонимы выдачи заключены в кавычки НАМЕРЕННО.</b>
 * Незакавыченный псевдоним Postgres отдаёт меткой в нижнем регистре, а
 * проекция ищет значение по имени свойства — метка обязана совпасть с ним
 * <b>дословно</b>. Два псевдонима начинаются с заглавной по той же
 * причине: имя свойства у геттеров {@code getRSum} и
 * {@code getRDenominatorDeals} по правилу JavaBeans остаётся {@code RSum} и
 * {@code RDenominatorDeals} — две первые заглавные подряд декапитализации
 * не подлежат.
 */
public interface FactAggregateSourceRepository extends Repository<DealFactEntity, Long> {

    /**
     * Начало ряда сделочных фактов — операнд отбора суток
     * (docs/spec/statistics-aggregates.json, {@code grainMinFactMoment});
     * пусто — ряд пуст.
     *
     * <p><b>Ось у величины та же, по которой считаются сутки зерна</b>, и
     * запаса на расхождение шкал сравнению поэтому не нужно.
     *
     * <p><b>Запрос по отображению, а не нативный</b> — по замеру соседнего
     * захода: у нативного скалярного чтения тип результата выводится из
     * метаданных JDBC, а драйвер объявляет {@code min(timestamptz)} кодом
     * 93 ({@code TIMESTAMP}) и отдаёт значение <b>без смещения</b>. Ошибка
     * пришла бы смещением времени, то есть тихо.
     */
    @Query("select min(fact.closedAt) from DealFactEntity fact")
    OffsetDateTime earliestDealFactMoment();

    /**
     * Начало ряда фактов происшествий — тот же операнд у второго зерна.
     *
     * <p><b>Величина своя у каждого зерна, а не одна на обе таблицы:</b>
     * ряды наполняются независимо, и общий минимум запретил бы пересчёт
     * суток, покрытых одним зерном и не покрытых другим.
     */
    @Query("select min(fact.occurredAt) from IncidentFactEntity fact")
    OffsetDateTime earliestIncidentFactMoment();

    /**
     * Сделочное зерно за одни сутки окна.
     *
     * <p><b>Отбор идёт по моменту ТЕРМИНАЛА сделки</b> — оси времени
     * таблицы: сутки зерна те, в которых сделка закрылась, а не те, в
     * которых её событие приняли.
     *
     * <p><b>Ценовой результат СКЛАДЫВАЕТСЯ ЗДЕСЬ, а не хранится колонкой</b>
     * (docs/concept.md, П4: производное вторым носителем не заводится).
     * Форма — дома величины (docs/spec/loss-streak-halt.json,
     * {@code priceResult}): конъюнкт полноты графа несущий, потому что
     * второе слагаемое на усечённой загрузке выходит нулём молча.
     *
     * <p><b>Пустой операнд даёт НЕДОСТУПНОСТЬ, а не ноль.</b> Операнд, не
     * приехавший в содержимом, оставляет ценовой результат пустым, а
     * сделку — в счётчике недоступного результата; подстановка нуля выдала
     * бы недобытое за исход (docs/concept.md, П1).
     *
     * <p><b>Признаки-предикаты не гасятся {@code coalesce}</b>: пустой
     * признак принятия риска исключает сделку из долей трёхзначной логикой
     * самого SQL, и «не приехало» не превращается в «не принимала».
     *
     * <p><b>Сумма пустого множества есть ноль</b>, и только этот
     * {@code coalesce} здесь законен: колонки сумм объявлены
     * {@code not null}, потому что ноль у них — исход, а не пробел.
     */
    @Query(nativeQuery = true, value = """
            select grain.tenant_id                    as "tenantId",
                   grain.exchange_account_internal_id as "exchangeAccountInternalId",
                   grain.strategy_internal_id         as "strategyInternalId",
                   grain.result_currency              as "resultCurrency",
                   cast(count(*) as integer)                                                          as "closedDeals",
                   cast(count(*) filter (where grain.took_risk) as integer)                           as "riskBearingDeals",
                   cast(count(*) filter (where grain.took_risk and grain.price_result > 0) as integer) as "winningDeals",
                   cast(count(*) filter (where grain.took_risk and grain.price_result < 0) as integer) as "losingDeals",
                   cast(count(*) filter (where grain.took_risk and grain.price_result = 0) as integer) as "neutralDeals",
                   cast(count(*) filter (where grain.took_risk and grain.price_result is null) as integer)
                       as "resultUnavailableDeals",
                   cast(count(*) filter (where grain.took_risk and grain.result_currency is null) as integer)
                       as "currencyUnresolvedDeals",
                   cast(count(*) filter (where grain.took_risk and grain.planned_risk = 0) as integer)
                       as "riskUnsizedDeals",
                   cast(count(*) filter (where grain.took_risk and grain.close_outcome = 'LIQUIDATION') as integer)
                       as "liquidatedDeals",
                   cast(count(*) filter (where grain.took_risk and grain.close_outcome = 'FORCED_REDUCTION') as integer)
                       as "forcedReductionDeals",
                   cast(count(*) filter (where grain.took_risk and grain.close_outcome = 'UNDETERMINED') as integer)
                       as "outcomeUndeterminedDeals",
                   cast(count(*) filter (where grain.took_risk and grain.reconciliation_status = 'MISMATCHED') as integer)
                       as "reconciliationMismatchedDeals",
                   cast(count(*) filter (where grain.took_risk and grain.reconciliation_status = 'NOT_RUN') as integer)
                       as "reconciliationNotRunDeals",
                   cast(count(*) filter (where grain.took_risk and grain.breakdown_incomplete = 'INCOMPLETE_BY_WINDOW') as integer)
                       as "breakdownIncompleteDeals",
                   cast(count(*) filter (where grain.took_risk and grain.breakdown_incomplete = 'NOT_ASSESSED') as integer)
                       as "breakdownNotAssessedDeals",
                   cast(count(*) filter (where grain.took_risk and grain.risk_benchmark_availability = 'MISSING') as integer)
                       as "riskBenchmarkMissingDeals",
                   cast(count(*) filter (where grain.enters_row_sums and grain.planned_risk > 0) as integer)
                       as "RDenominatorDeals",
                   coalesce(sum(grain.price_result) filter (where grain.enters_row_sums), 0)
                       as "resultBeforeFundingSum",
                   coalesce(sum(grain.net_result) filter (where grain.enters_row_sums), 0)
                       as "netResultSum",
                   coalesce(sum(grain.fee) filter (where grain.enters_row_sums), 0)
                       as "feeSum",
                   coalesce(sum(grain.funding) filter (where grain.enters_row_sums), 0)
                       as "fundingSum",
                   coalesce(sum(grain.liquidation_penalty) filter (where grain.enters_row_sums), 0)
                       as "liquidationPenaltySum",
                   coalesce(sum(grain.price_result) filter (where grain.took_risk
                             and grain.price_result > 0 and grain.result_currency is not null), 0)
                       as "winResultSum",
                   coalesce(sum(grain.price_result) filter (where grain.took_risk
                             and grain.price_result < 0 and grain.result_currency is not null), 0)
                       as "lossResultSum",
                   coalesce(sum(grain.planned_risk) filter (where grain.enters_row_sums
                             and grain.planned_risk > 0), 0)
                       as "plannedRiskSum",
                   coalesce(sum(grain.planned_risk) filter (where grain.took_risk and grain.planned_risk > 0
                             and not grain.enters_row_sums and grain.result_currency is not null), 0)
                       as "plannedRiskExcludedSum",
                   coalesce(sum(grain.price_result / grain.planned_risk) filter (where grain.enters_row_sums
                             and grain.planned_risk > 0), 0)
                       as "RSum"
              from (select operand.*,
                           operand.took_risk
                               and operand.price_result is not null
                               and operand.result_currency is not null as enters_row_sums
                      from (select fact.tenant_id,
                                   fact.exchange_account_internal_id,
                                   fact.strategy_internal_id,
                                   fact.result_currency,
                                   fact.took_risk,
                                   fact.net_result,
                                   fact.fee,
                                   fact.funding,
                                   fact.liquidation_penalty,
                                   fact.planned_risk,
                                   fact.close_outcome,
                                   fact.reconciliation_status,
                                   fact.breakdown_incomplete,
                                   fact.risk_benchmark_availability,
                                   case when fact.graph_complete and fact.net_result is not null
                                        then fact.net_result + fact.funding
                                   end                                 as price_result
                              from deal_facts fact
                             where fact.closed_at >= :dayStart
                               and fact.closed_at < :dayEnd) operand) grain
             group by grain.tenant_id,
                      grain.exchange_account_internal_id,
                      grain.strategy_internal_id,
                      grain.result_currency
            """)
    List<DealGrainRow> collectDealGrain(@Param("dayStart") OffsetDateTime dayStart,
                                        @Param("dayEnd") OffsetDateTime dayEnd);

    /**
     * Зерно происшествий за одни сутки окна.
     *
     * <p><b>Ключ зерна у́же сделочного на две колонки, и это не экономия:</b>
     * расчётной валюты не несёт ни одно из несомых событий, а определения
     * стратегии нет у части из них по построению радиуса — введённое ради
     * остальных, оно дало бы пустой ключ со вторым смыслом
     * (docs/rules/absent-value-semantics.md).
     *
     * <p><b>Коды ручной операции приезжают параметром, а не литералом
     * запроса:</b> их дом — правило ручной остановки, и в коде сервиса они
     * лежат одним перечнем ({@code Constants.ManualOperation}). Различает
     * тропу именно КОД, а не актор строки: у джобы, запущенной ручным
     * триггером, актор равен принципалу.
     */
    @Query(nativeQuery = true, value = """
            select fact.tenant_id                    as "tenantId",
                   fact.exchange_account_internal_id as "exchangeAccountInternalId",
                   cast(count(*) filter (where fact.event_type = 'DEAL_OPENED') as integer)
                       as "openedDeals",
                   cast(count(*) filter (where fact.event_type = 'ORDER_DECIDED') as integer)
                       as "orderDecisions",
                   cast(count(*) filter (where fact.event_type = 'HOLD_RAISED') as integer)
                       as "raisedHolds",
                   cast(count(*) filter (where fact.event_type = 'HOLD_RAISED'
                             and fact.hold_rung = 'HARD') as integer)
                       as "hardRaisedHolds",
                   cast(count(*) filter (where fact.event_type = 'HOLD_RAISED'
                             and fact.operation_code in (:manualOperationCodes)) as integer)
                       as "manuallyRaisedHolds",
                   cast(count(*) filter (where fact.event_type = 'ANOMALY_REPORTED') as integer)
                       as "anomalyReports",
                   cast(count(*) filter (where fact.event_type = 'ANOMALY_REPORTED'
                             and fact.anomaly_severity = 'CRITICAL') as integer)
                       as "criticalAnomalyReports",
                   cast(count(*) filter (where fact.event_type = 'ANOMALY_REPORTED'
                             and fact.operation_code in (:manualOperationCodes)) as integer)
                       as "manualOperationReports"
              from incident_facts fact
             where fact.occurred_at >= :dayStart
               and fact.occurred_at < :dayEnd
             group by fact.tenant_id,
                      fact.exchange_account_internal_id
            """)
    List<IncidentGrainRow> collectIncidentGrain(@Param("dayStart") OffsetDateTime dayStart,
                                                @Param("dayEnd") OffsetDateTime dayEnd,
                                                @Param("manualOperationCodes")
                                                Collection<String> manualOperationCodes);
}

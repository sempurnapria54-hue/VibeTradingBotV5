package com.example.auditstatistics.persistence.repository.journalread;

import com.example.auditstatistics.persistence.model.journal.AuditRecordEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Входы пересчёта агрегатов, читаемые ИЗ БАЗЫ ЖУРНАЛА под ролью агрегатов
 * (docs/rules/statistics-aggregates.md §«Пересчёт — проекция, а не
 * накопитель»).
 *
 * <p><b>Наследует {@code Repository}, а не {@code JpaRepository}, и это
 * несущий выбор.</b> Тропа к журналу у модуля статистики объявлена
 * читающей, и держит её грант базы; унаследованный {@code save} и
 * {@code delete} завёл бы в коде тропу записи, которую отвергала бы
 * <b>база</b> — то есть отказ пришёл бы в рантайме, а не отсутствовал
 * вовсе.
 *
 * <p><b>Агрегирование выполняется В БАЗЕ ЖУРНАЛА, а не в памяти джобы:</b>
 * запрос группирует строки порции по ключу зерна собираемой строки и
 * отдаёт по строке на ключ. Другого ключа у запроса нет — колонка входит
 * в группировку тогда и только тогда, когда она <b>измеряет</b> строку
 * агрегата.
 *
 * <p><b>Сутки зерна — операнд порции, а не колонка группировки:</b> проход
 * идёт посуточными порциями (запрос и upsert на каждые сутки окна), и
 * одним запросом ключи разных суток не собираются.
 *
 * <p><b>Значения чужих перечней стоя́т в тексте запросов литералами, и цена
 * этого названа.</b> Класс события, ступень, критичность, исход закрытия,
 * состояние сверки, полнота разбивки и доступность базы риска принадлежат
 * перечням <b>производителя</b>, а его общей библиотеки в дереве этого
 * сервиса нет вовсе — единого носителя у них тут быть не может. Вынесенные
 * параметрами, они превратили бы предикат в полтора десятка связываний и
 * перестали бы читаться как то, что запрос считает. Отсюда граница: в
 * параметр уходит только <b>множество</b>, названное больше одного раза
 * ({@code Constants.ManualOperation}), — у него копия внутри запроса
 * разошлась бы сама с собой. Остаток — переименование значения у
 * производителя сюда не приедет; механической охраны у него нет, и
 * оживитель тот же, что у имён ключей содержимого: кодовый заход по
 * писателям событий ядра (.claude/work/code-gate-ledger.json).
 *
 * <p><b>Псевдонимы выдачи заключены в кавычки НАМЕРЕННО.</b> Незакавыченный
 * псевдоним Postgres отдаёт меткой в нижнем регистре, а проекция ищет
 * значение по имени свойства — метка обязана совпасть с ним <b>дословно</b>.
 * Два псевдонима начинаются с заглавной по той же причине: имя свойства
 * у геттеров {@code getRSum} и {@code getRDenominatorDeals} по правилу
 * JavaBeans остаётся {@code RSum} и {@code RDenominatorDeals} — две первые
 * заглавные подряд декапитализации не подлежат.
 */
public interface JournalAggregateSourceRepository extends Repository<AuditRecordEntity, Long> {

    /**
     * Первый операнд отбора порции: самый ранний момент приёма среди
     * СОХРАНИВШИХСЯ строк журнала (docs/spec/audit-journal.json,
     * {@code journalMinRecordedAt}).
     *
     * <p><b>Читается тем же подключением, что и группировка</b> — под
     * ролью агрегатов: величина принадлежит журналу, и брать её у
     * подключения владельца значило бы ходить в чужую базу второй тропой.
     * Соседний носитель той же величины у модуля аудита
     * ({@code AuditRecordDataService}) читает её под ролью журнала для
     * СВОИХ читателей — чистки и выдачи полноты.
     *
     * <p><b>Запрос по отображению, а не нативный</b> — по замеру захода K5:
     * у нативного скалярного чтения тип результата выводится из метаданных
     * JDBC, а драйвер объявляет {@code min(timestamptz)} кодом 93
     * ({@code TIMESTAMP}) и отдаёт значение <b>без смещения</b>. Ошибка
     * пришла бы смещением времени, то есть тихо.
     *
     * <p>Пустой журнал отдаёт пустое значение: ветвь разбирает вызывающий.
     */
    @Query("select min(record.recordedAt) from AuditRecordEntity record")
    OffsetDateTime earliestRecordedAt();

    /**
     * Сделочное зерно за одни сутки окна.
     *
     * <p><b>Отбор идёт по моменту ПРОИСШЕСТВИЯ:</b> сутки зерна — те, в
     * которых лежит сделка, а не те, в которых её событие приняли. Отбор
     * обслуживается индексом {@code ix_audit_record_type_occurred}
     * ({@code event_type}, {@code occurred_at}): пересчёт берёт сутки ПО
     * ВСЕМ тенантам сразу, и индекс с ведущим тенантом такой скан не
     * обслуживает, а индекс с ведущим моментом заставлял бы план читать
     * ВСЕ строки суток — терминалов среди них меньшинство.
     *
     * <p><b>Ценовой результат СКЛАДЫВАЕТСЯ ЗДЕСЬ, а не приезжает полем</b>
     * (docs/architecture/contracts.md §События: «Результата до
     * финансирования в перечне нет: он производен от трёх его членов»).
     * Форма — дома величины (docs/spec/loss-streak-halt.json,
     * {@code priceResult}): конъюнкт полноты графа несущий, потому что
     * второе слагаемое на усечённой загрузке выходит нулём молча.
     *
     * <p><b>Пустой операнд даёт НЕДОСТУПНОСТЬ, а не ноль.</b> Операнд, не
     * приехавший в содержимом, оставляет ценовой результат пустым, а
     * сделку — в счётчике недоступного результата; подстановка нуля
     * выдала бы недобытое за исход (docs/concept.md, П1).
     *
     * <p><b>Признаки-предикаты не гасятся {@code coalesce}</b>: пустой
     * признак принятия риска исключает сделку из долей трёхзначной
     * логикой самого SQL, и «не приехало» не превращается в «не
     * принимала».
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
                   cast(count(*) filter (where grain.took_risk and grain.breakdown = 'INCOMPLETE_BY_WINDOW') as integer)
                       as "breakdownIncompleteDeals",
                   cast(count(*) filter (where grain.took_risk and grain.breakdown = 'NOT_ASSESSED') as integer)
                       as "breakdownNotAssessedDeals",
                   cast(count(*) filter (where grain.took_risk and grain.risk_benchmark = 'MISSING') as integer)
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
                      from (select record.tenant_id,
                                   record.exchange_account_internal_id,
                                   record.strategy_internal_id,
                                   record.content ->> 'resultCurrency'                as result_currency,
                                   (record.content ->> 'tookRisk')::boolean           as took_risk,
                                   (record.content ->> 'result')::numeric             as net_result,
                                   (record.content ->> 'fee')::numeric                as fee,
                                   (record.content ->> 'funding')::numeric            as funding,
                                   (record.content ->> 'liquidationPenalty')::numeric as liquidation_penalty,
                                   (record.content ->> 'plannedRisk')::numeric        as planned_risk,
                                   record.content ->> 'closeOutcome'                  as close_outcome,
                                   record.content ->> 'reconciliationStatus'          as reconciliation_status,
                                   record.content ->> 'breakdownIncomplete'           as breakdown,
                                   record.content ->> 'riskBenchmarkAvailability'     as risk_benchmark,
                                   case when (record.content ->> 'graphComplete')::boolean
                                             and (record.content ->> 'result') is not null
                                        then (record.content ->> 'result')::numeric
                                             + (record.content ->> 'funding')::numeric
                                   end                                                as price_result
                              from audit_records record
                             where record.event_type = 'DEAL_CLOSED'
                               and record.occurred_at >= :dayStart
                               and record.occurred_at < :dayEnd) operand) grain
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
     * расчётной валюты не несёт ни одно из четырёх событий, а определения
     * стратегии нет у двух из них по построению радиуса — введённое ради
     * двух других, оно дало бы у остальных пустой ключ со вторым смыслом
     * (docs/rules/absent-value-semantics.md).
     *
     * <p><b>Коды ручной операции приезжают параметром, а не литералом
     * запроса:</b> их дом — правило ручной остановки, и в коде сервиса они
     * лежат одним перечнем ({@code Constants.ManualOperation}). Различает
     * тропу именно КОД, а не актор строки: у джобы, запущенной ручным
     * триггером, актор равен принципалу, и автоматическая остановка,
     * найденная в ручном прогоне детекции, попала бы в число ручных.
     */
    @Query(nativeQuery = true, value = """
            select record.tenant_id                    as "tenantId",
                   record.exchange_account_internal_id as "exchangeAccountInternalId",
                   cast(count(*) filter (where record.event_type = 'DEAL_OPENED') as integer)
                       as "openedDeals",
                   cast(count(*) filter (where record.event_type = 'ORDER_DECIDED') as integer)
                       as "orderDecisions",
                   cast(count(*) filter (where record.event_type = 'HOLD_RAISED') as integer)
                       as "raisedHolds",
                   cast(count(*) filter (where record.event_type = 'HOLD_RAISED'
                             and record.content ->> 'rung' = 'HARD') as integer)
                       as "hardRaisedHolds",
                   cast(count(*) filter (where record.event_type = 'HOLD_RAISED'
                             and record.content ->> 'code' in (:manualOperationCodes)) as integer)
                       as "manuallyRaisedHolds",
                   cast(count(*) filter (where record.event_type = 'ANOMALY_REPORTED') as integer)
                       as "anomalyReports",
                   cast(count(*) filter (where record.event_type = 'ANOMALY_REPORTED'
                             and record.content ->> 'severity' = 'CRITICAL') as integer)
                       as "criticalAnomalyReports",
                   cast(count(*) filter (where record.event_type = 'ANOMALY_REPORTED'
                             and record.content ->> 'code' in (:manualOperationCodes)) as integer)
                       as "manualOperationReports"
              from audit_records record
             where record.event_type in ('DEAL_OPENED', 'ORDER_DECIDED', 'HOLD_RAISED', 'ANOMALY_REPORTED')
               and record.occurred_at >= :dayStart
               and record.occurred_at < :dayEnd
             group by record.tenant_id,
                      record.exchange_account_internal_id
            """)
    List<IncidentGrainRow> collectIncidentGrain(@Param("dayStart") OffsetDateTime dayStart,
                                                @Param("dayEnd") OffsetDateTime dayEnd,
                                                @Param("manualOperationCodes")
                                                Collection<String> manualOperationCodes);
}

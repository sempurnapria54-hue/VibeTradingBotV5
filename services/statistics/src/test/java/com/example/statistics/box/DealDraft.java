package com.example.statistics.box;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.Builder;

/**
 * Заготовка строки сделочного факта: все её колонки одной формой, штатные
 * значения умолчанием, отличия — названные клеткой
 * (.claude/tests/cases/statistics.md §«B8 — Арифметика сделочного зерна»).
 *
 * <p><b>Зачем заготовка, а не ещё одна перегрузка {@link Facts}.</b> Клетки
 * арифметики расходятся ровно по одной-двум колонкам каждая — признак
 * принятия риска, полнота графа, пустой итог, нулевой плановый риск, одно из
 * восьми нездоровых значений признаков отбора, — и различных сочетаний у них
 * больше десятка. Перегрузка на сочетание дала бы дюжину подписей с
 * позиционными аргументами, у которых порядок читается только счётом
 * запятых; заготовка называет отличие ИМЕНЕМ, а всё прочее оставляет
 * штатным.
 *
 * <p><b>Умолчания — ЗДОРОВАЯ сделка, и это несущее.</b> Клетка, ставящая
 * ровно одно нездоровое значение, тем самым утверждает, что остальные
 * счётчики стоя́т на нуле не потому, что их операнды не поданы, а потому, что
 * поданы здоровыми. Умолчания взяты у {@link Bodies} — там дом слов чужого
 * производителя и штатных денежных операндов, и вторая их запись разошлась
 * бы с первой при первой же правке (.claude/rules/carrier-levels.md).
 *
 * <p><b>Команда вставки живёт здесь одна на обе тропы.</b> {@link Facts}
 * кладёт свои штатные факты ею же: две записи одного списка колонок
 * разошлись бы при первом же расширении таблицы, и разошлись бы молча —
 * пропущенная колонка легла бы пустотой.
 *
 * <p><b>Пустые аргументы кладутся с ЯВНЫМ приведением типа.</b> Драйвер
 * выводит тип пустого аргумента из метаданных, а не из цели, и без
 * приведения вставка отвергается прежде, чем дойдёт до предмета клетки. Тот
 * же довод уже стои́т у пустых компонентов ключа зерна.
 *
 * <p><b>Денежные операнды приезжают ЗАПИСЬЮ, а не двоичным числом</b>
 * (docs/rules/decimal-arithmetic.md): клетки арифметики сравнивают суммы
 * дословно, и вход, прошедший через тип с плавающей точкой, мерил бы
 * округление подачи.
 */
@Builder
final class DealDraft {

    /** Команда вставки сделочного факта: все колонки таблицы поимённо. */
    private static final String INSERT = """
            insert into deal_facts
                (event_id, tenant_id, exchange_account_internal_id, strategy_internal_id,
                 result_currency, closed_at, took_risk, graph_complete, net_result, fee, funding,
                 liquidation_penalty, planned_risk, close_outcome, reconciliation_status,
                 breakdown_incomplete, risk_benchmark_availability)
            values (?, ?, ?, cast(? as varchar), cast(? as varchar), ?, ?, ?,
                    cast(? as numeric), cast(? as numeric), cast(? as numeric),
                    cast(? as numeric), cast(? as numeric),
                    cast(? as varchar), cast(? as varchar), cast(? as varchar),
                    cast(? as varchar))
            """;

    /** Идентичность события: она же половина ключа строки. */
    private final String eventId;

    /** Тенант зерна — радиус владения. */
    private final String tenantId;

    /** Момент терминала сделки: ось времени зерна и вторая половина ключа. */
    private final OffsetDateTime closedAt;

    /** Биржевой счёт — обязательный ключ зерна. */
    @Builder.Default
    private final String account = Facts.ACCOUNT;

    /** Определение стратегии — ключ зерна; пусто законно. */
    @Builder.Default
    private final String strategy = Facts.STRATEGY;

    /** Расчётная валюта результата — ключ зерна; пусто законно. */
    @Builder.Default
    private final String currency = Bodies.CURRENCY;

    /** Сделка приняла риск: популяция всех долей — она. */
    @Builder.Default
    private final Boolean tookRisk = Boolean.TRUE;

    /** Граф сделки полон: конъюнкт доступности ценового результата. */
    @Builder.Default
    private final Boolean graphComplete = Boolean.TRUE;

    /** Итог сделки записью; пусто означает «результат недоступен». */
    @Builder.Default
    private final String netResult = Bodies.NET_RESULT;

    /** Комиссия обеих ног записью. */
    @Builder.Default
    private final String fee = Bodies.FEE;

    /** Накопленное финансирование записью. */
    @Builder.Default
    private final String funding = Bodies.FUNDING;

    /** Штраф принудительного закрытия записью. */
    @Builder.Default
    private final String liquidationPenalty = Bodies.LIQUIDATION_PENALTY;

    /** Плановый риск записью: знаменатель отношения к риску. */
    @Builder.Default
    private final String plannedRisk = Bodies.PLANNED_RISK;

    /** Торговый исход закрытия. */
    @Builder.Default
    private final String closeOutcome = Bodies.NORMAL_EXIT;

    /** Состояние сверки разбивки движений. */
    @Builder.Default
    private final String reconciliationStatus = Bodies.MATCHED;

    /** Полнота разбивки движений. */
    @Builder.Default
    private final String breakdownIncomplete = Bodies.COMPLETE;

    /** Доступность базы риска. */
    @Builder.Default
    private final String riskBenchmarkAvailability = Bodies.AVAILABLE;

    /**
     * Заготовка здоровой сделки с названными ключом строки и тенантом.
     *
     * @param eventId  идентичность события
     * @param tenantId тенант зерна
     * @param closedAt момент терминала сделки
     */
    static DealDraftBuilder of(String eventId, String tenantId, OffsetDateTime closedAt) {
        return DealDraft.builder().eventId(eventId).tenantId(tenantId).closedAt(closedAt);
    }

    /** Кладёт заготовку строкой таблицы фактов. */
    void put() {
        Rows.shared().write(INSERT, eventId, tenantId, account, strategy, currency, closedAt,
                tookRisk, graphComplete, decimal(netResult), decimal(fee), decimal(funding),
                decimal(liquidationPenalty), decimal(plannedRisk), closeOutcome,
                reconciliationStatus, breakdownIncomplete, riskBenchmarkAvailability);
    }

    /**
     * Денежный операнд числом колонки; пустая запись остаётся пустотой.
     *
     * @param record запись операнда десятичной формой
     */
    private static BigDecimal decimal(String record) {
        return Objects.isNull(record) ? null : new BigDecimal(record);
    }
}

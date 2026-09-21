package com.example.statistics.box;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Строка сделочного агрегата, положенная ПРЯМОЙ ЗАПИСЬЮ, — предусловие
 * клеток ОКНА пересчёта (.claude/tests/cases/statistics.md §«B7 — Пересчёт:
 * окно, порции и охрана начала ряда»).
 *
 * <p><b>Это вход, а не подмена выхода, и довод у него механический.</b> Обе
 * клетки окна начинаются со слов «строка суток собрана прежде; окно сужено
 * конфигурацией так, что эти сутки в него не входят» — то есть требуют
 * строки СТАРШЕ окна. Тропой ящика такой строки не производится ни при
 * каком числе тактов: проход пишет ровно сутки окна
 * ({@code AggregateRecomputeJob}), а ширина окна есть ось конфигурации и
 * связывается при подъёме контекста — сдвинуть её посреди клетки нечем.
 * Ровно в этом и состоит предмет обеих клеток, поэтому «собрать строку
 * тиком» значило бы предъявить противоположное.
 *
 * <p><b>Строка кладётся НЕВЕРНОЙ намеренно.</b> Числа её не совпадают с
 * рядом фактов тех же суток, и это делает клетку неспособной пройти впустую:
 * проход, дотянувшийся до суток вне окна, числа обязан ИЗМЕНИТЬ, и молчание
 * ассерта тогда невозможно. Строка, положенная верной, различала бы
 * «пересчитано и вышло то же» от «не пересчитывалось» одним лишь моментом
 * сборки.
 *
 * <p><b>Ключ зерна берётся у {@link Facts} и {@link Bodies}</b>, а не
 * выдумывается рядом: строку обязан НАЙТИ upsert прохода по именованному
 * ключу (счёт, определение, валюта, сутки), иначе клетка о доведении чисел
 * расширенным окном мерила бы вставку соседней строки, а не пересчёт этой.
 *
 * <p><b>Форма строки объявлена домом и читается наружу</b>
 * (docs/rules/statistics-aggregates.md §«Что это за числа и кто их читает»),
 * поэтому запись по колонкам говорит о том же, о чём читает ассерт, — тот
 * же довод, которым положены строки фактов и строки состояния приёма.
 */
final class Aggregates {

    /**
     * Сутки, лежащие ЗА умолчанием окна сервиса.
     *
     * <p><b>Ими обе клетки окна строят свои {@code D0}</b> — и та, что
     * сужает окно до двух суток, и та, что расширяет его до двенадцати.
     * Величина одна, и вторая её запись разошлась бы с первой при первом же
     * сдвиге (.claude/rules/carrier-levels.md).
     */
    static final Integer LATE_DAY = 9;

    /** Числа положенной строки: они заведомо расходятся с рядом её суток. */
    static final Integer STALE_CLOSED_DEALS = 1;

    /**
     * Строка сделочного зерна со ВСЕМИ обязательными колонками.
     *
     * <p>Счётчики и суммы, кроме счёта закрытых сделок, кладутся нулями:
     * предмет клеток окна — тронул ли проход строку вообще, а не какое
     * именно число в ней стоит.
     */
    private static final String INSERT_DEAL_AGGREGATE = """
            insert into deal_aggregates
                (tenant_id, exchange_account_internal_id, strategy_internal_id, bucket_date,
                 result_currency,
                 closed_deals, risk_bearing_deals, winning_deals, losing_deals, neutral_deals,
                 result_unavailable_deals, currency_unresolved_deals, risk_unsized_deals,
                 liquidated_deals, forced_reduction_deals, outcome_undetermined_deals,
                 reconciliation_mismatched_deals, reconciliation_not_run_deals,
                 breakdown_incomplete_deals, breakdown_not_assessed_deals,
                 risk_benchmark_missing_deals, r_denominator_deals,
                 result_before_funding_sum, net_result_sum, fee_sum, funding_sum,
                 liquidation_penalty_sum, win_result_sum, loss_result_sum,
                 planned_risk_sum, planned_risk_excluded_sum, r_sum, assembled_at)
            values (?, ?, ?, ?, ?,
                    ?, 0, 0, 0, 0,
                    0, 0, 0,
                    0, 0, 0,
                    0, 0,
                    0, 0,
                    0, 0,
                    0, 0, 0, 0,
                    0, 0, 0,
                    0, 0, 0, ?)
            """;

    private Aggregates() {
    }

    /**
     * Кладёт строку сделочного зерна названных суток со штатным ключом.
     *
     * @param tenantId    тенант зерна
     * @param bucketDate  сутки зерна
     * @param closedDeals счётчик закрытых сделок: им читается, тронул ли
     *                    строку проход
     * @param assembledAt момент сборки: им различается «пересчитано» и «не
     *                    тронуто»
     */
    static void dealRow(String tenantId, LocalDate bucketDate, Integer closedDeals,
                        OffsetDateTime assembledAt) {
        Rows.shared().write(INSERT_DEAL_AGGREGATE, tenantId, Facts.ACCOUNT, Facts.STRATEGY,
                bucketDate, Bodies.CURRENCY, closedDeals, assembledAt);
    }
}

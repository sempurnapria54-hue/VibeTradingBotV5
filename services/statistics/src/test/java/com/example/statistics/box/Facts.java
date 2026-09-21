package com.example.statistics.box;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Строки фактов обоих зёрен, положенные ПРЯМОЙ ЗАПИСЬЮ, — durable-вход
 * клеток пересчёта (.claude/tests/cases/statistics.md §«Новая ось формы —
 * ПЕРЕСЧЁТ ПРОЕКЦИИ»).
 *
 * <p><b>Это вход, а не подмена выхода, и довод у него предметный.</b> У
 * события два следствия, разнесённые во времени разными исполнителями:
 * приём кладёт факт, а строку агрегата собирает позже отдельный тик.
 * Пересчётный кейс поэтому <b>начинается строками фактов как
 * предусловием</b> и кончается строкой агрегата; склеенный с приёмом, он
 * проверял бы две конструкции одним ассертом и на красном прогоне не
 * говорил бы, которая из них отказала.
 *
 * <p><b>Поверхности у фактов нет вовсе, и наружу они не отдаются</b>
 * (docs/models/domain/other/StatisticsFact.md §«Почему это не второй
 * журнал»), поэтому и подаются, и читаются они колонками — ровно так, как
 * называет §«Чем достаются выходы» документа кейсов.
 *
 * <p><b>Тропа приёма от этого не остаётся непроверенной.</b> Что строку
 * факта кладёт слушатель и что писателя у неё больше нет ни одного, держат
 * клетки групп {@code B1} и {@code B7.21}; здесь факт есть ПРЕДУСЛОВИЕ, и
 * подача его сообщением сделала бы девять клеток пересчёта зависящими от
 * живости приёма — то есть красными по чужой причине.
 *
 * <p><b>Операнды берутся у {@link Bodies}, а не выдумываются рядом:</b>
 * величина одна, и вторая её запись разошлась бы с первой при первой же
 * правке (.claude/rules/carrier-levels.md).
 */
final class Facts {

    /** Биржевой счёт — обязательный ключ обоих зёрен. */
    static final String ACCOUNT = "ACCOUNT-1";

    /** Определение стратегии — законно пустой ключ сделочного зерна. */
    static final String STRATEGY = "S-1";

    /** Строка сделочного факта: все ключи зерна и все операнды. */
    private static final String INSERT_DEAL = """
            insert into deal_facts
                (event_id, tenant_id, exchange_account_internal_id, strategy_internal_id,
                 result_currency, closed_at, took_risk, graph_complete, net_result, fee, funding,
                 liquidation_penalty, planned_risk, close_outcome, reconciliation_status,
                 breakdown_incomplete, risk_benchmark_availability)
            values (?, ?, ?, cast(? as varchar), cast(? as varchar), ?, true, true, ?, ?, ?, ?, ?,
                    ?, 'MATCHED', 'COMPLETE', 'AVAILABLE')
            """;

    /** Строка факта происшествия: ключи зерна, ось времени и класс события. */
    private static final String INSERT_INCIDENT = """
            insert into incident_facts
                (event_id, tenant_id, exchange_account_internal_id, occurred_at, event_type)
            values (?, ?, ?, ?, ?)
            """;

    private Facts() {
    }

    /**
     * Кладёт сделочный факт со штатными ключами зерна.
     *
     * @param eventId  идентичность события: она же ключ строки
     * @param tenantId тенант зерна
     * @param closedAt момент терминала сделки — ось времени зерна
     */
    static void deal(String eventId, String tenantId, OffsetDateTime closedAt) {
        deal(eventId, tenantId, STRATEGY, Bodies.CURRENCY, closedAt);
    }

    /**
     * Кладёт сделочный факт с названными законно пустыми ключами зерна.
     *
     * <p><b>Пустые компоненты ключа кладутся с явным приведением типа</b>:
     * драйвер выводит тип пустого аргумента из метаданных, а не из цели, и
     * без приведения вставка отвергается прежде, чем дойдёт до предмета
     * клетки.
     *
     * @param eventId  идентичность события
     * @param tenantId тенант зерна
     * @param strategy определение стратегии; пусто законно
     * @param currency расчётная валюта результата; пусто законно
     * @param closedAt момент терминала сделки
     */
    static void deal(String eventId, String tenantId, String strategy, String currency,
                     OffsetDateTime closedAt) {
        Rows.shared().write(INSERT_DEAL, eventId, tenantId, ACCOUNT, strategy, currency, closedAt,
                new BigDecimal(Bodies.NET_RESULT),
                new BigDecimal(Bodies.FEE),
                new BigDecimal(Bodies.FUNDING),
                new BigDecimal(Bodies.LIQUIDATION_PENALTY),
                new BigDecimal(Bodies.PLANNED_RISK),
                Bodies.NORMAL_EXIT);
    }

    /**
     * Кладёт факт происшествия названного класса.
     *
     * @param eventId    идентичность события
     * @param tenantId   тенант зерна
     * @param eventType  класс события: по нему счётчик отбирает свои строки
     * @param occurredAt момент происшествия — ось времени СВОЕГО зерна
     */
    static void incident(String eventId, String tenantId, String eventType,
                         OffsetDateTime occurredAt) {
        Rows.shared().write(INSERT_INCIDENT, eventId, tenantId, ACCOUNT, occurredAt, eventType);
    }
}

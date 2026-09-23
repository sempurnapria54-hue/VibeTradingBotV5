package com.example.bff.box;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Тела, которыми кейсы кормят ящик: ответы владельцев и содержимое
 * записей тем.
 *
 * <p><b>Собираются от КОНТРАКТА владельца, а не сняты с живого
 * сервиса.</b> Предметом здесь является периметр, и форма ответа
 * владельца ему вход, а не выход; что владелец эту форму и отдаёт, мерит
 * ящик владельца.
 *
 * <p><b>Тексты — строками, а не сборкой объекта.</b> Часть клеток
 * утверждает о теле ДОСЛОВНО (лишнее поле, поле неразбираемой формы,
 * пустой перечень), и типизованная сборка такой вход выразить не даёт.
 */
final class Bodies {

    private Bodies() {
    }

    /** Ответ владельца членств: одно членство названного тенанта и роли. */
    static String memberships(String tenantId, String role) {
        return "[" + membership(tenantId, role) + "]";
    }

    /**
     * Ответ владельца членств: перечень членств названных тенантов, роль
     * у всех одна.
     *
     * <p><b>Имя своё, а не перегрузка {@link #memberships(String,
     * String)}, и это не вкус:</b> вызов с двумя строками разрешался бы в
     * ОДНОместную форму — членство одного тенанта с ролью, равной имени
     * второго, — и клетка о двух членствах получала бы годный контекст
     * вместо отказа.
     */
    static String membershipsOf(String... tenantIds) {
        return Arrays.stream(tenantIds)
                .map(tenantId -> membership(tenantId, BffBox.ROLE))
                .collect(Collectors.joining(",", "[", "]"));
    }

    /** Ответ владельца членств: членств у предъявителя нет. */
    static String noMemberships() {
        return "[]";
    }

    /**
     * Содержимое события «сделка создана» — форма общего артефакта
     * {@code DealOpenedMessage}.
     *
     * <p>Собирается текстом, а не сборкой записи: часть клеток кладёт в
     * тему содержимое, которого форма не описывает вовсе, и типизованная
     * сборка такой вход выразить не даёт.
     *
     * @param dealInternalId идентичность сделки — ею клетка узнаёт свою запись
     */
    static String dealOpened(String dealInternalId) {
        return """
                {"dealInternalId": "%s",
                 "exchangeAccountInternalId": "EA-1",
                 "instrumentInternalId": "I-1",
                 "strategyInternalId": "S-1",
                 "entryReason": "STRATEGY_SIGNAL",
                 "direction": "LONG",
                 "entryMarketPhase": "TREND_UP"}""".formatted(dealInternalId);
    }

    /**
     * Содержимое события названного класса — ПОЛНАЯ форма общего артефакта
     * {@code *Message}, со всеми её компонентами.
     *
     * <p><b>Полная, а не урезанная до формы периметра, и это вход группы
     * {@code B7}:</b> компоненты, которых форма периметра не объявила,
     * обязаны в содержимом БЫТЬ — иначе «не уехали» было бы верно по
     * построению входа, а не по решению формы.
     *
     * @param eventType класс события — имя значения перечня производителя
     * @param mark      метка клетки: ею узнаются идентичности записи
     */
    static String fullMessage(String eventType, String mark) {
        return switch (eventType) {
            case "ORDER_DECIDED" -> """
                    {"orderInternalId": "O-%1$s", "dealInternalId": "D-%1$s",
                     "dealTrancheInternalId": "DT-%1$s", "exchangeAccountInternalId": "EA-%1$s",
                     "instrumentInternalId": "I-%1$s", "orderType": "LIMIT", "direction": "LONG",
                     "plannedSizeContracts": 3, "plannedEntryPrice": 101.5}""".formatted(mark);
            case "DEAL_OPENED" -> """
                    {"dealInternalId": "D-%1$s", "exchangeAccountInternalId": "EA-%1$s",
                     "instrumentInternalId": "I-%1$s", "strategyInternalId": "S-%1$s",
                     "entryReason": "STRATEGY_SIGNAL", "direction": "LONG",
                     "entryMarketPhase": "TREND_UP"}""".formatted(mark);
            case "DEAL_SHUTDOWN_INITIATED" -> """
                    {"dealInternalId": "D-%1$s", "exchangeAccountInternalId": "EA-%1$s",
                     "instrumentInternalId": "I-%1$s", "strategyInternalId": "S-%1$s",
                     "status": "SHUTTING_DOWN", "shutdownReason": "MANUAL", "actor": "user-%1$s"}"""
                    .formatted(mark);
            case "DEAL_CLOSED" -> """
                    {"dealInternalId": "D-%1$s", "exchangeAccountInternalId": "EA-%1$s",
                     "instrumentInternalId": "I-%1$s", "strategyInternalId": "S-%1$s",
                     "status": "CLOSED", "closeReason": "TAKE_PROFIT", "tookRisk": true,
                     "graphComplete": true, "result": 12.5, "resultCurrency": "USDT", "fee": 0.4,
                     "funding": 0.1, "liquidationPenalty": 0, "plannedRisk": 5,
                     "closeOutcome": "PROFIT", "reconciliationStatus": "RECONCILED",
                     "breakdownIncomplete": "NONE", "riskBenchmarkAvailability": "AVAILABLE"}"""
                    .formatted(mark);
            case "HOLD_RAISED" -> """
                    {"exchangeAccountInternalId": "EA-%1$s", "instrumentInternalId": "I-%1$s",
                     "scope": "INSTRUMENT", "rung": "SOFT", "code": "STALE_DATA",
                     "actor": "system-%1$s"}""".formatted(mark);
            case "ANOMALY_REPORTED" -> """
                    {"anomalyReportInternalId": "AR-%1$s", "exchangeAccountInternalId": "EA-%1$s",
                     "instrumentInternalId": "I-%1$s", "scope": "INSTRUMENT", "severity": "WARNING",
                     "code": "UNEXPECTED_POSITION", "actor": "system-%1$s"}""".formatted(mark);
            case "STRATEGY_ACTIVATED" -> strategyActivated(mark);
            case "STRATEGY_DEACTIVATED", "STRATEGY_DELETED" -> """
                    {"strategyInternalId": "S-%1$s", "exchangeAccountInternalId": "EA-%1$s",
                     "instrumentInternalId": "I-%1$s", "actor": "user-%1$s"}""".formatted(mark);
            default -> throw new IllegalArgumentException("Класса " + eventType + " у производителей нет");
        };
    }

    /**
     * Содержимое активации определения: идентичности радиуса на ВЕРХНЕМ
     * уровне и снимок дерева определения целиком.
     *
     * <p><b>Снимок несёт свои идентичности, отличные от верхних, намеренно:</b>
     * так «идентичности взяты с верхнего уровня» различимо по значению, а не
     * совпадает по построению входа.
     *
     * @param mark метка клетки
     */
    static String strategyActivated(String mark) {
        return """
                {"strategyInternalId": "S-%1$s", "exchangeAccountInternalId": "EA-%1$s",
                 "instrumentInternalId": "I-%1$s", "actor": "user-%1$s",
                 "definition": {"internalId": "S-nested-%1$s", "name": "Definition %1$s",
                   "exchangeAccountInternalId": "EA-nested-%1$s",
                   "instrumentInternalId": "I-nested-%1$s",
                   "details": [{"riskPerActionPercent": 1.5, "targetRiskRewardRatio": 2.0,
                                "tranches": []}]}}""".formatted(mark);
    }

    /** Одно членство: идентичность его выводится из тенанта — она периметру не нужна. */
    private static String membership(String tenantId, String role) {
        return """
                {"internalId": "M-%s", "tenantId": "%s", "role": "%s"}"""
                .formatted(tenantId, tenantId, role);
    }
}

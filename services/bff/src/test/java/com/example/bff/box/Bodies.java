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

    /** Одно членство: идентичность его выводится из тенанта — она периметру не нужна. */
    private static String membership(String tenantId, String role) {
        return """
                {"internalId": "M-%s", "tenantId": "%s", "role": "%s"}"""
                .formatted(tenantId, tenantId, role);
    }
}

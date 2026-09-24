package com.example.tradingcore.box;

/**
 * Тела вызовов поверхности ядра — вход ящика.
 *
 * <p><b>Тела — строки, а не собранные api-модели.</b> Часть кейсов подаёт
 * форму, которой в моделях сервиса нет вовсе: неразбираемое тело,
 * неизвестный класс вмешательства, недопустимая пара «класс × радиус»,
 * тело с полем вне контракта. Типизованная сборка такой вход выразить не
 * даёт, а сборка из модели сервиса к тому же брала бы его внутренность.
 */
final class Bodies {

    private Bodies() {
    }

    /** Ручная операция на радиусе счёта. */
    static String halt(String haltClass, String accountInternalId) {
        return """
                {"haltClass": "%s", "exchangeAccountInternalId": "%s"}
                """.formatted(haltClass, accountInternalId);
    }

    /** Ручная операция на радиусе пары «счёт, инструмент». */
    static String halt(String haltClass, String accountInternalId, String instrumentInternalId) {
        return """
                {"haltClass": "%s", "exchangeAccountInternalId": "%s", "instrumentInternalId": "%s"}
                """.formatted(haltClass, accountInternalId, instrumentInternalId);
    }

    /** Снимок намерения держателя по числам риск-аппетита целиком. */
    static String riskAppetite(String simultaneousPercent, String catastrophicMultiplier,
                               String consecutiveLossLimit) {
        return """
                {
                  "globalSimultaneousRiskPerDealPercent": %s,
                  "globalCatastrophicRiskPerDealMultiplier": %s,
                  "globalConsecutiveLossLimit": %s
                }
                """.formatted(simultaneousPercent, catastrophicMultiplier, consecutiveLossLimit);
    }

    /** Снимок намерения держателя по настройкам пары: рабочее плечо. */
    static String pairSettings(Integer leverage) {
        return """
                {"leverage": %s}
                """.formatted(leverage);
    }

    /** Тело, которое не разбирается ни одной моделью. */
    static String unparseable() {
        return "{\"haltClass\": ";
    }
}

package com.example.audit.box;

/**
 * Содержимое событий, которые кейсы кладут в тему.
 *
 * <p><b>Содержимое подаётся ДОСЛОВНО, а не собирается формой.</b> Журнал
 * его не интерпретирует и хранит как доставлено
 * (docs/architecture/services/audit.md §«Чего не делает намеренно»);
 * собранное типизованной формой, оно приезжало бы уже причёсанным
 * сериализатором — то есть кейс проверял бы наш сборщик, а не поведение
 * сервиса.
 *
 * <p><b>Здесь нет ни одной формы производителя, и это не упущение.</b>
 * Вход предмета есть документ произвольной формы: класс события —
 * дискриминатор, которого журнал не толкует, и содержимое, собранное по
 * образцу чужой модели, сузило бы вход до той формы, которую сегодня
 * кладёт построенный сосед.
 */
final class Bodies {

    private Bodies() {
    }

    /** Содержимое общего вида: документ без единого имени радиуса. */
    static String reference() {
        return """
                {"outcome": "OPENED", "note": "reference"}""";
    }

    /**
     * Содержимое с четырьмя именами радиуса на ВЕРХНЕМ уровне — все
     * строками.
     *
     * @param exchangeAccount биржевой счёт
     * @param instrument      инструмент
     * @param deal            сделка
     * @param strategy        определение стратегии
     */
    static String withRadius(String exchangeAccount, String instrument, String deal, String strategy) {
        return """
                {"exchangeAccountInternalId": "%s",
                 "instrumentInternalId": "%s",
                 "dealInternalId": "%s",
                 "strategyInternalId": "%s",
                 "outcome": "OPENED"}"""
                .formatted(exchangeAccount, instrument, deal, strategy);
    }

    /**
     * Содержимое, у которого имена радиуса лежат на ГЛУБИНЕ, а на верхнем
     * уровне одноимённых компонентов нет.
     *
     * @param deal     сделка внутри вложенного объекта
     * @param strategy определение стратегии внутри того же объекта
     */
    static String nestedRadius(String deal, String strategy) {
        return """
                {"payload": {"dealInternalId": "%s", "strategyInternalId": "%s"},
                 "outcome": "OPENED"}"""
                .formatted(deal, strategy);
    }

    /** Содержимое, у которого одноимённый компонент верхнего уровня — объект. */
    static String dealAsObject() {
        return """
                {"dealInternalId": {"value": "D-1"}, "outcome": "OPENED"}""";
    }

    /** Содержимое, у которого тот же компонент — число. */
    static String dealAsNumber() {
        return """
                {"dealInternalId": 42, "outcome": "OPENED"}""";
    }

    /**
     * Содержимое, которое документом не является и не разбирается вовсе.
     *
     * <p><b>Оборвано, а не заменено скаляром.</b> Скаляр — число, строка,
     * {@code true} — разбирается, и колонка {@code jsonb} его принимает:
     * это ДРУГАЯ ветвь, у которой сегодня нет ни кейса, ни дома
     * (.claude/tests/cases/audit.md §«Пробелы покрытия», {@code G4}).
     */
    static String notADocument() {
        return "{\"outcome\": ";
    }

    /**
     * Документ со всеми формами, которые обязаны пережить хранение:
     * вложенные объекты, массивы, дробные числа и не-ASCII.
     */
    static String richDocument() {
        return """
                {"nested": {"level": {"kept": true}},
                 "series": [1, 2, 3],
                 "objects": [{"a": 1}, {"b": 2}],
                 "fraction": 0.125,
                 "text": "стратегия «альфа» — вход"}""";
    }
}

package com.example.strategies.box;

/**
 * Ответы соседа по ярусу — ВХОД ящика.
 *
 * <p><b>Собираются от контракта соседа, а не сняты с живого сервиса:</b>
 * предметом здесь является владелец определений, и форма ответа ядра ему
 * вход. Что ядро эту форму и отдаёт, мерит ящик ядра.
 *
 * <p><b>Числа риск-аппетита — ДВА, и это предмет клетки {@code B1.17}.</b>
 * Доля запаса нотинала, которую дом валидации называет третьим числом
 * тенанта, на строке тенанта не живёт и в ответе соседа не едет: её дом
 * объявляет её константой правила (находка F-1,
 * .claude/tests/cases/strategies.md §«Находки владельцам»).
 *
 * <p><b>Пустое число подаётся строкой {@code null}, а не отсутствием
 * поля.</b> Разница несущая: отсутствие поля и его пустое значение — два
 * разных входа, и клетки {@code B1.8} и {@code B1.9} разводят именно их.
 */
final class Feed {

    private Feed() {
    }

    /**
     * Разрешимость ссылок определения: три признака, а не один.
     *
     * @param accountFound           счёт есть в проекции реестра
     * @param accountBelongsToTenant счёт принадлежит названному тенанту
     * @param instrumentFound        инструмент есть в проекции каталога
     */
    static String pairCheck(Boolean accountFound, Boolean accountBelongsToTenant, Boolean instrumentFound) {
        return """
                {"accountFound": %s, "accountBelongsToTenant": %s, "instrumentFound": %s}
                """.formatted(accountFound, accountBelongsToTenant, instrumentFound);
    }

    /** Все три ссылки разрешились: штатный вход создания. */
    static String resolvingPairCheck() {
        return pairCheck(Boolean.TRUE, Boolean.TRUE, Boolean.TRUE);
    }

    /**
     * Принятые ядром числа риск-аппетита — все шесть, как их отдаёт ядро;
     * {@code null} — ответ без числа, то есть нарушение контракта ядра:
     * работающее ядро пустого числа не отдаёт.
     *
     * <p><b>Три числа преконтроля ядра едут постоянными:</b> владелец
     * определений их не читает, и в ответе они стоят ради формы — лишние
     * ключи разбор не роняют. Значения — числа тестового окружения.
     *
     * @param simultaneousPercent  потолок одновременного риска на сделку
     * @param cumulativeMultiplier предел множителя кумулятивного потолка
     * @param maxLeverage          предел плеча
     */
    static String riskAppetite(String simultaneousPercent, String cumulativeMultiplier, String maxLeverage) {
        return """
                {
                  "globalSimultaneousRiskPerDealPercent": %s,
                  "globalSimultaneousRiskPerAccountPercent": 10,
                  "globalSimultaneousRiskPerTenantPercent": 30,
                  "globalCumulativeRiskPerDealMultiplier": %s,
                  "globalMaxLeverage": %s,
                  "globalConsecutiveLossLimit": 3
                }
                """.formatted(simultaneousPercent, cumulativeMultiplier, maxLeverage);
    }
}

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
     * Числа риск-аппетита тенанта; {@code null} — держатель числа не
     * назначил.
     *
     * @param tenantInternalId                        идентичность тенанта
     * @param simultaneousPercent                     потолок одновременного риска на сделку
     * @param catastrophicMultiplier                  предел множителя катастрофического потолка
     */
    static String riskAppetite(String tenantInternalId, String simultaneousPercent,
                               String catastrophicMultiplier) {
        return """
                {
                  "tenantInternalId": "%s",
                  "globalSimultaneousRiskPerDealPercent": %s,
                  "globalCatastrophicRiskPerDealMultiplier": %s
                }
                """.formatted(tenantInternalId, simultaneousPercent, catastrophicMultiplier);
    }
}

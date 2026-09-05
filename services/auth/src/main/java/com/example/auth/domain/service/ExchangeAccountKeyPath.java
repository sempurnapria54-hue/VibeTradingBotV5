package com.example.auth.domain.service;

import lombok.experimental.UtilityClass;

/**
 * Путь ключей биржевого счёта в Vault.
 *
 * <p>Форма пути объявлена в docs/architecture/platform.md §Безопасность:
 * {@code <окружение>/exchange-accounts/<accountInternalId>}. Здесь —
 * её исполнимая запись, и она одна: путь выводится из идентификатора
 * счёта, а не хранится рядом с ним. Хранимый путь был бы вторым носителем
 * той же истины и разошёлся бы с идентификатором при первом же переносе.
 *
 * <p><b>Чтения и записи ключей здесь нет.</b> Шаг 4 фазы 2 заводит форму
 * пути и роль; сама работа с Vault приезжает вместе с первым читателем
 * ключей — коннектором (шаг 5), у которого и появляется потребность.
 * Клиент, заведённый раньше читателя, был бы механизмом без предмета.
 */
@UtilityClass
public class ExchangeAccountKeyPath {

    /** Сегмент, отделяющий ключи счетов от секретов сервисов. */
    private static final String ACCOUNTS_SEGMENT = "exchange-accounts";

    /**
     * Путь ключей счёта.
     *
     * @param environment      имя окружения — первый сегмент пути; именно
     *                         он делает границу между окружениями
     *                         выразимой одной политикой Vault
     * @param accountInternalId идентичность счёта
     */
    public static String of(String environment, String accountInternalId) {
        return environment + "/" + ACCOUNTS_SEGMENT + "/" + accountInternalId;
    }
}

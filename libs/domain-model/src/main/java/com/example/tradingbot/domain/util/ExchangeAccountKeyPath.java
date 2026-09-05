package com.example.tradingbot.domain.util;

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
 * <p><b>Носитель формы один на два сервиса, и это несущее.</b> Путь
 * вычисляют оба конца: {@code auth} по нему ключи ПИШЕТ, коннектор —
 * ЧИТАЕТ. Копия формы у каждого разошлась бы молча — записанные ключи
 * просто перестали бы находиться, — поэтому форма живёт в общем артефакте
 * рядом с {@link InternalIdFactory}, по тому же доводу: одинаковость не
 * держится на дисциплине нескольких сервисов.
 *
 * <p><b>Чтения и записи ключей здесь нет</b> — только форма адреса.
 * Работа с хранилищем живёт у того, кто в него ходит.
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

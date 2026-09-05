package com.example.auth.domain.service;

import com.example.auth.config.EnvironmentProperties;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.util.ExchangeAccountKeyPath;
import com.example.tradingbot.domain.util.ExchangeAccountSecretFields;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;

/**
 * Запись ключей биржевого счёта в хранилище.
 *
 * <p><b>Единственное место платформы, где ключи пишутся</b>
 * ({@code docs/architecture/tenant-and-exchange.md} §Ключи): право записи
 * в префикс путей счетов есть только у {@code auth}, у коннектора —
 * только чтение.
 *
 * <p><b>В базу ключи не попадают ни в каком виде</b> — ни колонкой, ни
 * логом, ни сообщением отказа. Отсюда и форма метода: он принимает ключи
 * и ничего о них не возвращает.
 *
 * <p><b>Контур пишется вместе с ключами</b>, а не выводится из строки
 * счёта в базе: у обоих один источник — действие владельца при
 * регистрации, и разведи их по носителям, они разойдутся при первой же
 * правке одного из них.
 */
@Component
@RequiredArgsConstructor
public class ExchangeAccountKeyWriter {

    private final VaultTemplate vaultTemplate;
    private final EnvironmentProperties environment;

    /**
     * Кладёт ключи счёта по адресу, выводимому из его идентификатора.
     *
     * @param accountInternalId идентичность счёта — из неё выводится адрес
     * @param apiKey            API-ключ счёта
     * @param secret            секрет ключа
     * @param passphrase        passphrase ключа
     * @param contour           контур, которому ключи принадлежат
     */
    public void write(String accountInternalId, String apiKey, String secret, String passphrase,
                      ExchangeAccount.Contour contour) {
        requireEnvironment();
        vaultTemplate.write(
                ExchangeAccountKeyPath.of(environment.getName(), accountInternalId),
                Map.of(
                        ExchangeAccountSecretFields.API_KEY, apiKey,
                        ExchangeAccountSecretFields.SECRET, secret,
                        ExchangeAccountSecretFields.PASSPHRASE, passphrase,
                        ExchangeAccountSecretFields.CONTOUR, contour.name()));
    }

    /**
     * Окружение обязано быть названным: без первого сегмента ключи легли
     * бы в чужое окружение либо в корень хранилища, и граница, которую
     * этот сегмент и проводит, исчезла бы.
     */
    private void requireEnvironment() {
        if (StringUtils.isBlank(environment.getName())) {
            throw new IllegalStateException("Имя окружения не задано: адрес ключей счёта невычислим");
        }
    }
}

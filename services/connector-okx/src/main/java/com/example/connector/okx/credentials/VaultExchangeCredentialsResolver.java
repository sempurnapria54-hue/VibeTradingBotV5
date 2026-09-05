package com.example.connector.okx.credentials;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.connector.okx.config.EnvironmentProperties;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.util.ExchangeAccountKeyPath;
import com.example.tradingbot.domain.util.ExchangeAccountSecretFields;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * Ключи счёта из Vault по адресу, выводимому из идентификатора счёта.
 *
 * <p><b>Ни адрес, ни состав секрета здесь не объявлены</b> — обе формы
 * приезжают из общего артефакта ({@link ExchangeAccountKeyPath},
 * {@link ExchangeAccountSecretFields}), потому что вторая их сторона —
 * пишущий {@code auth}.
 *
 * <p><b>Адрес не хранится, а вычисляется</b> — общей формой
 * {@link ExchangeAccountKeyPath}, той же, по которой ключи кладёт
 * {@code auth}. Хранимый адрес был бы вторым носителем той же истины и
 * разошёлся бы с идентификатором при первом переносе счёта.
 *
 * <p><b>Кэша здесь нет, и он здесь не нужен.</b> Короткий кэш, которого
 * требует архитектура, живёт слоем выше
 * ({@link CachingExchangeCredentialsResolver}): чтение хранилища и
 * решение «перечитывать ли» — две разные заботы, и слитые вместе они
 * дали бы класс, который нельзя проверить без хранилища.
 *
 * <p><b>Отсутствие ключей — не «пусто», а отказ.</b> Пустой ответ
 * хранилища означает, что подписать запрос нечем; вернуть {@code null}
 * значило бы отложить отказ до NPE в подписи, потеряв причину.
 */
@Component
@RequiredArgsConstructor
public class VaultExchangeCredentialsResolver implements ExchangeCredentialsResolver {

    private final VaultTemplate vaultTemplate;
    private final EnvironmentProperties environment;

    @Override
    public ExchangeCredentials resolve(String accountInternalId) {
        requireEnvironment();
        VaultResponse response = vaultTemplate.read(
                ExchangeAccountKeyPath.of(environment.getName(), accountInternalId));
        if (isNull(response) || isNull(response.getData())) {
            throw new CredentialsUnavailableException(accountInternalId);
        }
        Map<String, Object> data = response.getData();
        return new ExchangeCredentials(
                field(data, ExchangeAccountSecretFields.API_KEY, accountInternalId),
                field(data, ExchangeAccountSecretFields.SECRET, accountInternalId),
                field(data, ExchangeAccountSecretFields.PASSPHRASE, accountInternalId),
                contour(data, accountInternalId));
    }

    /**
     * Окружение обязано быть названным: без первого сегмента адрес ключей
     * указывал бы в чужое окружение либо в корень хранилища.
     */
    private void requireEnvironment() {
        if (isBlank(environment.getName())) {
            throw new IllegalStateException("Имя окружения не задано: адрес ключей счёта невычислим");
        }
    }

    /**
     * Поле секрета. Пустое поле — тот же отказ, что и отсутствие секрета:
     * половина ключей не подписывает.
     */
    private String field(Map<String, Object> data, String name, String accountInternalId) {
        Object value = data.get(name);
        if (isNull(value) || isBlank(value.toString())) {
            throw new CredentialsUnavailableException(accountInternalId);
        }
        return value.toString();
    }

    /**
     * Контур ключей.
     *
     * <p>Умолчания у него нет намеренно: угадать контур значило бы при
     * первой же неполноте секрета отправить боевую заявку в демо либо
     * демо-ключ на боевую площадку.
     */
    private ExchangeAccount.Contour contour(Map<String, Object> data, String accountInternalId) {
        return ExchangeAccount.Contour.valueOf(field(data, ExchangeAccountSecretFields.CONTOUR, accountInternalId));
    }
}

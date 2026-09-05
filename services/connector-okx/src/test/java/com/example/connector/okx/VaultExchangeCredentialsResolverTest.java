package com.example.connector.okx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.connector.okx.config.EnvironmentProperties;
import com.example.connector.okx.credentials.CredentialsUnavailableException;
import com.example.connector.okx.credentials.ExchangeCredentials;
import com.example.connector.okx.credentials.VaultExchangeCredentialsResolver;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.util.ExchangeAccountKeyPath;
import com.example.tradingbot.domain.util.ExchangeAccountSecretFields;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * Чтение ключей счёта из хранилища.
 *
 * <p><b>Главная проверка — адрес.</b> Читатель обязан спрашивать ровно по
 * тому пути, по которому пишет {@code auth}; разойдись они, секрет лежал
 * бы на месте, а вызов получал бы «ключей нет» — отказ, неотличимый от
 * незаведённого счёта. Поэтому ожидание строится общей формой
 * {@link ExchangeAccountKeyPath}, а не строковым литералом теста: литерал
 * зафиксировал бы копию формы третьим носителем.
 */
class VaultExchangeCredentialsResolverTest {

    private static final String ENVIRONMENT = "stage";
    private static final String ACCOUNT = "acc-42";

    private final VaultTemplate vault = mock(VaultTemplate.class);

    @Test
    void readsKeysAtTheAddressAuthWritesTo() {
        when(vault.read(ExchangeAccountKeyPath.of(ENVIRONMENT, ACCOUNT))).thenReturn(response(secret()));

        ExchangeCredentials credentials = resolver(ENVIRONMENT).resolve(ACCOUNT);

        assertThat(credentials.getApiKey()).isEqualTo("key-1");
        assertThat(credentials.getSecret()).isEqualTo("secret-1");
        assertThat(credentials.getPassphrase()).isEqualTo("pass-1");
        assertThat(credentials.getContour()).isEqualTo(ExchangeAccount.Contour.DEMO);
        assertThat(credentials.isDemo()).isTrue();
    }

    /**
     * Ответа нет — отказ, а не пустые ключи: пустота дошла бы до подписи и
     * обернулась бы отказом площадки, потеряв настоящую причину.
     */
    @Test
    void absentSecretIsRefusal() {
        when(vault.read(anyString())).thenReturn(null);

        assertThatThrownBy(() -> resolver(ENVIRONMENT).resolve(ACCOUNT))
                .isInstanceOf(CredentialsUnavailableException.class)
                .hasMessageContaining(ACCOUNT);
    }

    /** Половина ключей не подписывает: пустое поле — тот же отказ. */
    @Test
    void blankFieldIsRefusal() {
        Map<String, Object> partial = secret();
        partial.put(ExchangeAccountSecretFields.PASSPHRASE, "");
        when(vault.read(anyString())).thenReturn(response(partial));

        assertThatThrownBy(() -> resolver(ENVIRONMENT).resolve(ACCOUNT))
                .isInstanceOf(CredentialsUnavailableException.class);
    }

    /**
     * Контур не угадывается. Умолчание отправило бы демо-ключ на боевую
     * площадку — отказ здесь дешевле.
     */
    @Test
    void absentContourIsRefusal() {
        Map<String, Object> withoutContour = secret();
        withoutContour.remove(ExchangeAccountSecretFields.CONTOUR);
        when(vault.read(anyString())).thenReturn(response(withoutContour));

        assertThatThrownBy(() -> resolver(ENVIRONMENT).resolve(ACCOUNT))
                .isInstanceOf(CredentialsUnavailableException.class);
    }

    /**
     * Без имени окружения адрес невычислим: первый сегмент и есть граница
     * между окружениями, и без него запрос ушёл бы в корень хранилища.
     */
    @Test
    void unnamedEnvironmentRefusesBeforeReading() {
        assertThatThrownBy(() -> resolver("").resolve(ACCOUNT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("окружения");
    }

    private VaultExchangeCredentialsResolver resolver(String environmentName) {
        EnvironmentProperties environment = new EnvironmentProperties();
        environment.setName(environmentName);
        return new VaultExchangeCredentialsResolver(vault, environment);
    }

    private Map<String, Object> secret() {
        Map<String, Object> data = new HashMap<>();
        data.put(ExchangeAccountSecretFields.API_KEY, "key-1");
        data.put(ExchangeAccountSecretFields.SECRET, "secret-1");
        data.put(ExchangeAccountSecretFields.PASSPHRASE, "pass-1");
        data.put(ExchangeAccountSecretFields.CONTOUR, ExchangeAccount.Contour.DEMO.name());
        return data;
    }

    private VaultResponse response(Map<String, Object> data) {
        VaultResponse response = new VaultResponse();
        response.setData(data);
        return response;
    }
}

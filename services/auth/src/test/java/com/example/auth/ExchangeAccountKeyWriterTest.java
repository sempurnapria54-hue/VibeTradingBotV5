package com.example.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.auth.config.EnvironmentProperties;
import com.example.auth.domain.service.ExchangeAccountKeyWriter;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.util.ExchangeAccountKeyPath;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.vault.config.VaultProperties;
import org.springframework.vault.core.VaultTemplate;

/**
 * Охрана адреса хранилища у писателя ключей счёта.
 *
 * <p><b>Клетка ящика B6.4 этого не различает, и потому проба здесь:</b>
 * там адрес-умолчание клиента пришпилен к мёртвому, и отказ соединения даёт
 * тот же исход «успеха нет, записи нет», что и отказ охраны. Различает их
 * только то, дошёл ли ход до клиента хранилища, — а это видно на
 * коллабораторе, не на поверхности.
 */
class ExchangeAccountKeyWriterTest {

    private static final String ENVIRONMENT = "dev";
    private static final String ACCOUNT = "account-1";

    @Test
    void anUndeclaredSecretStoreAddressRefusesBeforeTheClient() {
        VaultTemplate vaultTemplate = mock(VaultTemplate.class);
        ExchangeAccountKeyWriter writer = writer(vaultTemplate, "");

        assertThatThrownBy(() -> writer.write(ACCOUNT, "key", "secret", "phrase", ExchangeAccount.Contour.DEMO))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(vaultTemplate);
    }

    @Test
    void aDeclaredSecretStoreAddressWritesUnderTheEnvironmentPrefix() {
        VaultTemplate vaultTemplate = mock(VaultTemplate.class);
        ExchangeAccountKeyWriter writer = writer(vaultTemplate, "http://vault.platform:8200");

        writer.write(ACCOUNT, "key", "secret", "phrase", ExchangeAccount.Contour.DEMO);

        verify(vaultTemplate).write(eq(ExchangeAccountKeyPath.of(ENVIRONMENT, ACCOUNT)), anyMap());
    }

    private ExchangeAccountKeyWriter writer(VaultTemplate vaultTemplate, String uri) {
        VaultProperties vaultProperties = new VaultProperties();
        vaultProperties.setUri(uri);
        EnvironmentProperties environment = new EnvironmentProperties();
        environment.setName(ENVIRONMENT);
        return new ExchangeAccountKeyWriter(vaultTemplate, vaultProperties, environment);
    }
}

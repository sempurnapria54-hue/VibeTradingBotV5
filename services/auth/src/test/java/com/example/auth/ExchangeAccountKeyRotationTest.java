package com.example.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.auth.domain.service.ExchangeAccountKeyWriter;
import com.example.auth.domain.service.ExchangeAccountRegistrationService;
import com.example.auth.domain.service.ExchangeAccountService;
import com.example.auth.exception.ExchangeAccountClosedException;
import com.example.auth.exception.ExchangeAccountNotFoundException;
import com.example.auth.persistence.model.ExchangeAccountEntity;
import com.example.auth.persistence.repository.ExchangeAccountRepository;
import com.example.auth.persistence.repository.TenantRepository;
import com.example.auth.persistence.service.ExchangeAccountDataService;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Смена ключей счёта у исполнителя: откуда берётся контур, что отвергает
 * запись и в каком порядке идут записи.
 *
 * <p><b>Ящик этого не различает, и потому проба здесь:</b> порядок «строка,
 * затем хранилище» и то, что до писателя ключей ход не дошёл вовсе, видны на
 * коллабораторах, а не на поверхности. Строка счёта — настоящая, с
 * настоящими полями; подменены только коллабораторы
 * (.claude/rules/codestyle.md §«Тесты доменных моделей»).
 */
class ExchangeAccountKeyRotationTest {

    private static final String ACCOUNT = "account-1";
    private static final Long ACCOUNT_ID = 7L;

    private ExchangeAccountDataService accountData;
    private ExchangeAccountKeyWriter keyWriter;
    private ExchangeAccountService service;

    @BeforeEach
    void setUp() {
        accountData = mock(ExchangeAccountDataService.class);
        keyWriter = mock(ExchangeAccountKeyWriter.class);
        service = new ExchangeAccountService(mock(ExchangeAccountRepository.class), accountData,
                mock(TenantRepository.class), mock(ExchangeAccountRegistrationService.class), keyWriter);
    }

    @Test
    void theContourIsTakenFromTheAccountRowAndTheRowIsMarkedBeforeTheKeys() {
        ExchangeAccountEntity account = account(ExchangeAccount.Contour.LIVE);
        when(accountData.getRequiredByInternalId(ACCOUNT)).thenReturn(account);
        when(accountData.markModifiedInStatus(ACCOUNT_ID, ExchangeAccount.Status.ACTIVE)).thenReturn(Boolean.TRUE);

        ExchangeAccountEntity rotated = service.rotateKeys(ACCOUNT, "key", "secret", "phrase");

        assertThat(rotated).isSameAs(account);
        InOrder order = inOrder(accountData, keyWriter);
        order.verify(accountData).markModifiedInStatus(ACCOUNT_ID, ExchangeAccount.Status.ACTIVE);
        order.verify(keyWriter).write(ACCOUNT, "key", "secret", "phrase", ExchangeAccount.Contour.LIVE);
    }

    /**
     * Точечная запись не легла — счёт уже не {@code ACTIVE}: ключи не
     * пишутся, отказ называет отключённый счёт.
     */
    @Test
    void aClosedAccountRefusesBeforeTheKeyWriter() {
        when(accountData.getRequiredByInternalId(ACCOUNT)).thenReturn(account(ExchangeAccount.Contour.DEMO));
        when(accountData.markModifiedInStatus(ACCOUNT_ID, ExchangeAccount.Status.ACTIVE)).thenReturn(Boolean.FALSE);

        assertThatThrownBy(() -> service.rotateKeys(ACCOUNT, "key", "secret", "phrase"))
                .isInstanceOf(ExchangeAccountClosedException.class);
        verifyNoInteractions(keyWriter);
    }

    @Test
    void anUnknownAccountRefusesBeforeAnyWrite() {
        when(accountData.getRequiredByInternalId(ACCOUNT)).thenThrow(new ExchangeAccountNotFoundException(ACCOUNT));

        assertThatThrownBy(() -> service.rotateKeys(ACCOUNT, "key", "secret", "phrase"))
                .isInstanceOf(ExchangeAccountNotFoundException.class);
        verify(accountData).getRequiredByInternalId(ACCOUNT);
        verifyNoInteractions(keyWriter);
    }

    private ExchangeAccountEntity account(ExchangeAccount.Contour contour) {
        ExchangeAccountEntity account = new ExchangeAccountEntity();
        account.setId(ACCOUNT_ID);
        account.setInternalId(ACCOUNT);
        account.setTenantId("tenant-1");
        account.setExchangeCode("OKX");
        account.setLabel("main");
        account.setContour(contour.name());
        account.setStatus(ExchangeAccount.Status.ACTIVE.name());
        return account;
    }
}

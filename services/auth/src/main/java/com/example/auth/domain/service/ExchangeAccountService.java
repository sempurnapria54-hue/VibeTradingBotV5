package com.example.auth.domain.service;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.auth.persistence.model.ExchangeAccountEntity;
import com.example.auth.persistence.repository.ExchangeAccountRepository;
import com.example.auth.persistence.repository.TenantRepository;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.util.InternalIdFactory;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Реестр биржевых счетов тенанта: заведение и чтение. */
@Service
public class ExchangeAccountService {

    private final ExchangeAccountRepository accountRepository;
    private final TenantRepository tenantRepository;
    private final ExchangeAccountRegistrationService registration;

    public ExchangeAccountService(ExchangeAccountRepository accountRepository,
                                  TenantRepository tenantRepository,
                                  ExchangeAccountRegistrationService registration) {
        this.accountRepository = accountRepository;
        this.tenantRepository = tenantRepository;
        this.registration = registration;
    }

    /**
     * Регистрирует счёт, если окружение допускает его контур.
     *
     * <p>Проверка допуска стои́т здесь, в момент РЕГИСТРАЦИИ: отказ тут
     * дёшев, а отказ в момент сделки приходит отказом доступа площадки и
     * поздно (docs/architecture/platform.md §«Чем различаются окружения»).
     */
    @Transactional
    public ExchangeAccountEntity register(String tenantInternalId, String exchangeCode,
                                          String label, ExchangeAccount.Contour contour) {
        tenantRepository.findByInternalId(tenantInternalId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Тенант не найден: " + tenantInternalId));
        if (isFalse(registration.contourAdmitted(contour))) {
            throw new ContourNotAdmittedException(contour);
        }
        ExchangeAccountEntity account = new ExchangeAccountEntity();
        account.setInternalId(InternalIdFactory.forInternalEntity());
        account.setTenantId(tenantInternalId);
        account.setExchangeCode(exchangeCode);
        account.setLabel(label);
        account.setContour(contour.name());
        account.setStatus(ExchangeAccount.Status.ACTIVE.name());
        return accountRepository.save(account);
    }

    /** Счета тенанта. */
    @Transactional(readOnly = true)
    public List<ExchangeAccountEntity> byTenant(String tenantInternalId) {
        return accountRepository.findAllByTenantId(tenantInternalId);
    }
}

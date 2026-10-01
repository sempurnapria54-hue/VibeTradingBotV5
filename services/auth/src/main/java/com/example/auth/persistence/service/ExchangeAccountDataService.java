package com.example.auth.persistence.service;

import com.example.auth.exception.ExchangeAccountNotFoundException;
import com.example.auth.persistence.model.ExchangeAccountEntity;
import com.example.auth.persistence.repository.ExchangeAccountRepository;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;

/**
 * Граница domain ↔ persistence для строки реестра биржевых счетов — по
 * ходам, которым нужна ненайденность как отказ либо точечная запись.
 *
 * <p><b>Автор и момент точечной записи — от ТЕХ ЖЕ поставщиков, что у
 * слушателя аудита</b> ({@code JpaAuditConfig}): второе правило актора или
 * второй источник часов дали бы одной строке два правила в зависимости от
 * того, какой ход её тронул
 * (docs/models/domain/other/Auditable.md §«Системные поля и точечная запись»).
 * Та же пара у {@code market-data} и {@code trading-core} собрана классом
 * {@code PointWriteAudit}; здесь потребитель у неё один, и отдельного класса
 * под два вызова не заводится.
 */
@Service
@RequiredArgsConstructor
public class ExchangeAccountDataService {

    private final ExchangeAccountRepository repository;
    private final AuditorAware<String> auditorAware;
    private final DateTimeProvider auditingDateTimeProvider;

    /**
     * Строка счёта по идентичности либо отказ ненайденности.
     *
     * @param accountInternalId идентичность счёта
     * @return строка счёта
     */
    public ExchangeAccountEntity getRequiredByInternalId(String accountInternalId) {
        return repository.findByInternalId(accountInternalId)
                .orElseThrow(() -> new ExchangeAccountNotFoundException(accountInternalId));
    }

    /**
     * Двигает момент и автора изменения строки, если счёт в ожидаемом
     * статусе.
     *
     * @param accountId      ключ строки
     * @param expectedStatus статус, в котором запись допустима
     * @return {@code true} — строка записана; {@code false} — счёт уже не в
     *         ожидаемом статусе
     */
    public Boolean markModifiedInStatus(Long accountId, ExchangeAccount.Status expectedStatus) {
        Integer written = repository.markModifiedInStatus(accountId, expectedStatus.name(), moment(), writer());
        return written > 0;
    }

    private String writer() {
        return auditorAware.getCurrentAuditor()
                .orElseThrow(() -> new IllegalStateException("Auditor is not resolved for a point write"));
    }

    private OffsetDateTime moment() {
        return auditingDateTimeProvider.getNow()
                .map(OffsetDateTime::from)
                .orElseThrow(() -> new IllegalStateException("Audit clock returned no moment for a point write"));
    }
}

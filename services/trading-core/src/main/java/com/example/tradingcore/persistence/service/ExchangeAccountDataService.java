package com.example.tradingcore.persistence.service;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingcore.mapping.ExchangeAccountMapper;
import com.example.tradingcore.persistence.model.ExchangeAccountEntity;
import com.example.tradingcore.persistence.repository.ExchangeAccountRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для проекции реестра счетов.
 *
 * <p><b>Стартовые значения торговых колонок ставит ЗАВЕДЕНИЕ строки, а не
 * {@code DEFAULT} колонки.</b> {@code DEFAULT} отвечает за строки,
 * вставленные мимо приложения, а здесь вставляет приложение
 * (docs/models/domain/core/ExchangeAccount.md §«Писатель заведения строки
 * назван, и это тот же синк»). База риска и её валюта остаются пустыми:
 * пустота есть отказ risk-creating действия, а не ноль.
 */
@Service
@RequiredArgsConstructor
public class ExchangeAccountDataService {

    /** Серия убытков и слепые проходы у новой строки — с нуля. */
    private static final Integer COUNTER_START = 0;

    private final ExchangeAccountRepository repository;
    private final ExchangeAccountMapper mapper;

    /**
     * Сводит строку проекции с реестром владельца: заводит недостающую,
     * обновляет проекционные колонки существующей.
     *
     * @param account     счёт, каким его отдал реестр
     * @param projectedAt момент снимка
     */
    @Transactional
    public void upsertProjection(ExchangeAccount account, OffsetDateTime projectedAt) {
        ExchangeAccountEntity entity = repository.findByInternalId(account.getInternalId())
                .orElseGet(() -> newProjection(account.getInternalId()));
        mapper.updateProjection(account, entity);
        entity.setProjectedAt(projectedAt);
        repository.save(entity);
    }

    /** Идентичности тенантов, у которых в проекции есть счёт. */
    @Transactional(readOnly = true)
    public List<String> findTenantInternalIds() {
        return repository.findDistinctTenantInternalIds();
    }

    private ExchangeAccountEntity newProjection(String internalId) {
        ExchangeAccountEntity entity = new ExchangeAccountEntity();
        entity.setInternalId(internalId);
        entity.setConsecutiveLossCount(COUNTER_START);
        entity.setBlindPassCount(COUNTER_START);
        entity.setSafetyRung(ExchangeAccount.SafetyRung.ACTIVE.name());
        return entity;
    }
}

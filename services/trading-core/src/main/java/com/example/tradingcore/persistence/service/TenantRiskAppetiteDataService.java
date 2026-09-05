package com.example.tradingcore.persistence.service;

import com.example.tradingcore.persistence.model.TenantRiskAppetiteEntity;
import com.example.tradingcore.persistence.repository.TenantRiskAppetiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для чисел риск-аппетита тенанта.
 *
 * <p><b>Строка заводится пустой, и пустота её несущая.</b> Числа
 * риск-аппетита назначает держатель, машина их из концепции не выводит;
 * пустое значение означает ОТКАЗ risk-creating действия, а не ноль
 * (docs/models/domain/core/Tenant.md §Персистентность). Место под число
 * завести можно, само число — нет.
 *
 * <p>Тенант узнаётся из счёта: перечня тенантов у ядра нет и быть не
 * должно — ими владеет {@code auth}.
 */
@Service
@RequiredArgsConstructor
public class TenantRiskAppetiteDataService {

    private final TenantRiskAppetiteRepository repository;

    /**
     * Заводит пустую строку риск-аппетита, если её ещё нет.
     *
     * <p>Уже заведённую строку не трогает: числа в ней — от держателя, и
     * тик синка их не переписывает.
     */
    @Transactional
    public void ensureRow(String tenantInternalId) {
        if (repository.findByTenantInternalId(tenantInternalId).isPresent()) {
            return;
        }
        TenantRiskAppetiteEntity entity = new TenantRiskAppetiteEntity();
        entity.setTenantInternalId(tenantInternalId);
        repository.save(entity);
    }
}

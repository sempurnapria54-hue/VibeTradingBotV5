package com.example.tradingcore.persistence.service;

import com.example.tradingbot.domain.model.core.tenant.Tenant;
import com.example.tradingcore.mapping.TenantRiskAppetiteMapper;
import com.example.tradingcore.persistence.model.TenantRiskAppetiteEntity;
import com.example.tradingcore.persistence.repository.TenantRiskAppetiteRepository;
import java.util.Optional;
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
    private final TenantRiskAppetiteMapper mapper;

    /**
     * Числа риск-аппетита тенанта; пусто — строки нет вовсе (тик синка ещё
     * не видел ни одного счёта этого тенанта).
     *
     * <p><b>Пустая строка и отсутствие строки различаются, и различие
     * несущее.</b> Пустое ЧИСЛО означает «держатель не назначил», пустая
     * СТРОКА — «ядро о тенанте ещё не знает»; оба ведут к отказу
     * risk-creating действия, но по разным поводам, и разбор по данным
     * обязан их отличать (docs/concept.md П3).
     */
    @Transactional(readOnly = true)
    public Optional<Tenant> findByTenantInternalId(String tenantInternalId) {
        return repository.findByTenantInternalId(tenantInternalId)
                .map(mapper::persistenceToDomain);
    }

    /**
     * Назначить числа риск-аппетита тенанта. Строку заводит, если её ещё
     * нет: держатель вправе назначить числа раньше, чем тик синка увидит
     * первый счёт тенанта.
     *
     * <p><b>Пустое значение поля стирает число, а не сохраняет
     * прежнее.</b> Назначение — снимок намерения держателя целиком, и
     * частичная правка сделала бы «не прислал» неотличимым от «снял»:
     * оба ведут к отказу risk-creating действия, но по разным поводам, и
     * различать их обязаны данные, а не память вызывающего
     * (docs/concept.md П3).
     */
    @Transactional
    public Tenant applyRiskAppetite(Tenant appetite) {
        TenantRiskAppetiteEntity entity = repository
                .findByTenantInternalId(appetite.getInternalId())
                .orElseGet(() -> newRow(appetite.getInternalId()));
        entity.setGlobalSimultaneousRiskPerDealPercent(
                appetite.getGlobalSimultaneousRiskPerDealPercent());
        entity.setGlobalCatastrophicRiskPerDealMultiplier(
                appetite.getGlobalCatastrophicRiskPerDealMultiplier());
        entity.setGlobalConsecutiveLossLimit(appetite.getGlobalConsecutiveLossLimit());
        return mapper.persistenceToDomain(repository.save(entity));
    }

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
        repository.save(newRow(tenantInternalId));
    }

    private TenantRiskAppetiteEntity newRow(String tenantInternalId) {
        TenantRiskAppetiteEntity entity = new TenantRiskAppetiteEntity();
        entity.setTenantInternalId(tenantInternalId);
        return entity;
    }
}

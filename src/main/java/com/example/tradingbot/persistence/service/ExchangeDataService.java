package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.mapping.ExchangeMapper;
import com.example.tradingbot.persistence.repository.ExchangeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link Exchange}. Здесь же —
 * fetch-or-throw чтения ({@code getRequiredBy*}).
 */
@Service
@RequiredArgsConstructor
public class ExchangeDataService {

    private final ExchangeRepository repository;
    private final ExchangeMapper mapper;

    @Transactional
    public Exchange save(Exchange exchange) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(exchange)));
    }

    @Transactional(readOnly = true)
    public Exchange getRequiredByInternalId(String internalId) {
        return repository.findByInternalId(internalId)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("Exchange not found: " + internalId));
    }

    @Transactional(readOnly = true)
    public Exchange getRequiredById(Long id) {
        return repository.findById(id)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("Exchange not found: " + id));
    }

    /** Проекция id бирж в статусе — каскадный фильтр входов по TRADE_BLOCKED-биржам. */
    @Transactional(readOnly = true)
    public List<Long> findIdsByStatus(Exchange.Status status) {
        return repository.findIdsByStatus(status.name());
    }

    /**
     * Заморозка торговли по аварии: ACTIVE → TRADE_BLOCKED (гардирована статусом,
     * только из ACTIVE — decision B). Возвращает {@code true}, если переход
     * применился (биржа была ACTIVE) — анкер идемпотентности реакции холда.
     * Обратный переход (ручная разморозка TRADE_BLOCKED → ACTIVE) — с операцией
     * un-hold (step 9 / backlog п.9), здесь не вводится превентивно.
     */
    @Transactional
    public Boolean blockTrade(Long id) {
        return repository.updateStatus(id, Exchange.Status.ACTIVE.name(),
                Exchange.Status.TRADE_BLOCKED.name()) > 0;
    }

    /**
     * Ручная разморозка торговли: TRADE_BLOCKED → ACTIVE (гардирована статусом,
     * только из TRADE_BLOCKED — обратная сторона {@link #blockTrade}). Одно действие
     * отпускает весь L4-каскад: enforcement читает статус биржи живьём, поэтому
     * возврат биржи в ACTIVE снимает блок входов по всем её инструментам (per-instrument
     * холды L4 не пишутся). Возвращает {@code true}, если переход применился.
     */
    @Transactional
    public Boolean unblockTrade(Long id) {
        return repository.updateStatus(id, Exchange.Status.TRADE_BLOCKED.name(),
                Exchange.Status.ACTIVE.name()) > 0;
    }

    /** Проекция: только internalId по id — без вытягивания всей сущности. */
    @Transactional(readOnly = true)
    public String getRequiredInternalIdById(Long id) {
        return repository.findInternalIdById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exchange not found: " + id));
    }

    /** Проекция: только id по internalId — без вытягивания всей сущности. */
    @Transactional(readOnly = true)
    public Long getRequiredIdByInternalId(String internalId) {
        return repository.findIdByInternalId(internalId)
                .orElseThrow(() -> new IllegalArgumentException("Exchange not found: " + internalId));
    }
}

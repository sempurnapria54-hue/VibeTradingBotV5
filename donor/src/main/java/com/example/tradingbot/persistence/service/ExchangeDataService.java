package com.example.tradingbot.persistence.service;

import static java.util.stream.Collectors.toList;

import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.mapping.ExchangeMapper;
import com.example.tradingbot.persistence.repository.ExchangeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
     * Заморозка торговли по аварии: <b>любой статус</b> → TRADE_BLOCKED.
     * Возвращает {@code true}, если переход применился, — анкер
     * идемпотентности реакции холда.
     *
     * <p><b>Гард только из ACTIVE не годится:</b> «`HOLD →
     * TRADE_BLOCKED` разрешён и реакцию не пропускает: мягкий холд
     * анкером идемпотентности не является»
     * (docs/rules/exchange-hold.md §«Границы и эскалация», там же —
     * «`TRADE_BLOCKED` — из любого»). С прежним гардом жёсткая находка
     * на бирже под мягкой ступенью проглатывалась молча: kill-switch не
     * гонялся, отчёт не заводился. Инструментная лестница разведена так
     * же ({@link InstrumentDataService#blockTrade}).
     *
     * <p>Обратный переход (ручная разморозка TRADE_BLOCKED → HOLD) —
     * операция снятия, здесь не вводится.
     */
    @Transactional
    public Boolean blockTrade(Long id) {
        return repository.updateStatusUnlessAlready(id, Exchange.Status.TRADE_BLOCKED.name()) > 0;
    }

    /**
     * Мягкая ступень: ACTIVE → HOLD (гардирована статусом, только из ACTIVE).
     * Множество входа мягкой ступени — только рабочее состояние: запрос слабее
     * стоящей жёсткой ступени поглощается, а понижение делает снятие, не
     * постановка (docs/rules/exchange-hold.md §«Границы и эскалация»). Гард и
     * есть анкер: не переставился статус — состояние уже держится.
     */
    @Transactional
    public Boolean blockEntry(Long id) {
        return repository.updateStatus(id, Exchange.Status.ACTIVE.name(),
                Exchange.Status.HOLD.name()) > 0;
    }

    /**
     * Снятие сворачивания: TRADE_BLOCKED → <b>HOLD</b>, а не в рабочее состояние
     * (гардирована статусом). Условий снятия два — «риска не осталось» и «причина
     * понята», — и лестница проверяет их по одному: прыжка сразу в ACTIVE нет
     * (docs/rules/exchange-hold.md §«Снятие — вручную и только в `HOLD`»).
     * Возвращает {@code true}, если переход применился.
     */
    @Transactional
    public Boolean unblockTrade(Long id) {
        return repository.updateStatus(id, Exchange.Status.TRADE_BLOCKED.name(),
                Exchange.Status.HOLD.name()) > 0;
    }

    /**
     * Второй ход снятия: HOLD → ACTIVE (гардирована статусом). Машинного
     * предусловия у него нет — оно есть у первого хода; здесь остаётся
     * «причина понята», а это суждение держателя, и энфорсера у него нет по
     * построению (docs/rules/manual-halt.md). Гард отвергает прыжок через
     * ступень: биржа под сворачиванием этим вызовом не разблокируется.
     */
    @Transactional
    public Boolean clearHold(Long id) {
        return repository.updateStatus(id, Exchange.Status.HOLD.name(),
                Exchange.Status.ACTIVE.name()) > 0;
    }

    /**
     * Проекция id бирж, чья ступень гасит новые входы, — обе ступени лестницы.
     * Мягкая и жёсткая одинаково отменяют право набирать новый риск и
     * различаются судьбой уже принятого, поэтому выборка входа читает обе.
     */
    @Transactional(readOnly = true)
    public List<Long> findIdsBlockingEntry() {
        return repository.findIdsByStatusIn(List.of(Exchange.Status.HOLD.name(),
                Exchange.Status.TRADE_BLOCKED.name()));
    }

    /**
     * Биржи, которые контур ведёт, — вход прохода проактивной детекции,
     * ограниченным окном. Ступень биржи выборку НЕ сужает: под холдом
     * учёт уже существующего риска продолжается, гасятся только новые
     * входы. Имя не обещает фильтра по статусу — его здесь нет.
     */
    @Transactional(readOnly = true)
    public List<Exchange> findContourWithin(Integer limit) {
        return repository.findAllBy(PageRequest.of(0, limit)).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }

    /**
     * Отметить проход детекции: полный сбрасывает счёт слепоты в ноль,
     * неполный увеличивает его на единицу. Возвращает счёт ПОСЛЕ отметки
     * — операнд предела слепоты (docs/components/AnomalyJob.md §«Гейт
     * полноты среза»).
     */
    @Transactional
    public Integer markPass(Long id, Boolean complete) {
        repository.updateBlindPassCount(id, complete);
        return repository.findBlindPassCountById(id).orElse(0);
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

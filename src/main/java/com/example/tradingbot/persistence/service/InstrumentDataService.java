package com.example.tradingbot.persistence.service;

import static java.util.stream.Collectors.toList;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.mapping.InstrumentMapper;
import com.example.tradingbot.persistence.repository.InstrumentRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link Instrument}. Здесь же —
 * fetch-or-throw чтения ({@code getRequiredBy*}). Доменный enum статуса
 * конвертируется в строку на границе репозитория.
 */
@Service
@RequiredArgsConstructor
public class InstrumentDataService {

    private final InstrumentRepository repository;
    private final InstrumentMapper mapper;

    @Transactional
    public Instrument save(Instrument instrument) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(instrument)));
    }

    @Transactional(readOnly = true)
    public Optional<Instrument> findById(Long id) {
        return repository.findById(id).map(mapper::persistenceToDomain);
    }

    @Transactional(readOnly = true)
    public Instrument getRequiredById(Long id) {
        return findById(id).orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + id));
    }

    /**
     * Инструмент вместе с группами свечей (join fetch) — для проверок,
     * которым нужны группы (например {@code isReadyForActivation}).
     */
    @Transactional(readOnly = true)
    public Instrument getRequiredByIdWithCandleGroups(Long id) {
        return repository.findByIdWithCandleGroups(id)
                .map(mapper::persistenceToDomainWithCandleGroups)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + id));
    }

    @Transactional(readOnly = true)
    public Instrument getRequiredByInternalId(String internalId) {
        return repository.findByInternalId(internalId)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + internalId));
    }

    @Transactional(readOnly = true)
    public List<Instrument> findByStatus(Instrument.Status status) {
        return repository.findByStatus(status.name()).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }

    /**
     * Заморозка торговли по аварии: ACTIVE либо ENTRY_BLOCKED →
     * TRADE_BLOCKED. Возвращает {@code true}, если переход применился —
     * анкер идемпотентности реакции холда.
     *
     * <p><b>Исходных статуса два, и это эскалация, а не послабление.</b>
     * Мягкая ступень стоящей на инструменте не отменяет права поднять
     * жёсткую: монотонность лестницы разрешает подъём и запрещает
     * понижение (docs/rules/exchange-hold.md §«Границы и эскалация»).
     * Гард только из ACTIVE запирал бы инструмент с мягкой ступенью в
     * ней навсегда — жёсткая реакция на него не встала бы ни разу.
     * Обратный переход (ручная разморозка) — {@link #unblockTrade}.
     */
    @Transactional
    public Boolean blockTrade(Long id) {
        return repository.updateStatusFromAny(id,
                List.of(Instrument.Status.ACTIVE.name(), Instrument.Status.ENTRY_BLOCKED.name()),
                Instrument.Status.TRADE_BLOCKED.name()) > 0;
    }

    /**
     * Мягкая safety-ступень: ACTIVE → ENTRY_BLOCKED (гардирована статусом,
     * только из ACTIVE). Возвращает {@code true}, если переход применился —
     * анкер идемпотентности мягкой реакции.
     *
     * <p>Из TRADE_BLOCKED мягкая ступень НЕ ставится: это понижение, а
     * понижение даёт только снятие (`docs/rules/instrument-hold.md`
     * §«Снятие — вручную у обоих классов»). Запрос слабее стоящей ступени
     * поглощается — гард и есть механизм поглощения.
     */
    @Transactional
    public Boolean blockEntry(Long id) {
        return repository.updateStatus(id, Instrument.Status.ACTIVE.name(),
                Instrument.Status.ENTRY_BLOCKED.name()) > 0;
    }

    /**
     * Ручная разморозка торговли: TRADE_BLOCKED → ACTIVE (гардирована статусом,
     * только из TRADE_BLOCKED — обратная сторона {@link #blockTrade}). Возвращает
     * {@code true}, если переход применился (инструмент был TRADE_BLOCKED).
     */
    @Transactional
    public Boolean unblockTrade(Long id) {
        return repository.updateStatus(id, Instrument.Status.TRADE_BLOCKED.name(),
                Instrument.Status.ACTIVE.name()) > 0;
    }

    /**
     * Ручное снятие мягкой ступени: ENTRY_BLOCKED → ACTIVE (гардировано
     * статусом). Возвращает {@code true}, если переход применился.
     *
     * <p>Обратная сторона {@link #blockEntry}. Без неё инструмент, вставший
     * в мягкую ступень, из неё бы не вышел: автоматического снятия по
     * восстановлению признака нет ни у одного класса
     * (`docs/rules/instrument-hold.md` §«Снятие — вручную у обоих
     * классов»), а жёсткая разморозка гардирована своим статусом.
     */
    @Transactional
    public Boolean unblockEntry(Long id) {
        return repository.updateStatus(id, Instrument.Status.ENTRY_BLOCKED.name(),
                Instrument.Status.ACTIVE.name()) > 0;
    }

    @Transactional(readOnly = true)
    public List<Instrument> findByStatusIn(Collection<Instrument.Status> statuses) {
        List<String> names = statuses.stream().map(Enum::name).collect(toList());
        return repository.findByStatusIn(names).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }

    /** Проекция: только internalId по id — без вытягивания всей сущности. */
    @Transactional(readOnly = true)
    public String getRequiredInternalIdById(Long id) {
        return repository.findInternalIdById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + id));
    }

    /** Проекция: только id по internalId — без вытягивания всей сущности. */
    @Transactional(readOnly = true)
    public Long getRequiredIdByInternalId(String internalId) {
        return repository.findIdByInternalId(internalId)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + internalId));
    }
}

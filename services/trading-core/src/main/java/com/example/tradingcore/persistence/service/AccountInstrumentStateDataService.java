package com.example.tradingcore.persistence.service;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.service.ActorProvider;
import com.example.tradingcore.mapping.AccountInstrumentStateMapper;
import com.example.tradingcore.persistence.repository.AccountInstrumentStateRepository;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для ступени и торговых настроек счёта на
 * инструменте (docs/models/domain/core/Instrument.md §«Ступень и настройки
 * счёта на инструменте — своя таблица ядра»).
 *
 * <p><b>Строка материализуется ЛЕНИВО — первым читателем пары.</b>
 * Писателя, который завёл бы её заранее, нет и быть не может: пары «счёт ×
 * инструмент» порождает не реестр, а торговое намерение, и перебирать
 * декартово произведение реестров означало бы заводить строки под пары,
 * которых никто никогда не коснётся.
 *
 * <p><b>Стартовые значения названы поимённо, и умолчания среди них нет.</b>
 * Ступень — {@code ACTIVE}: рабочее состояние есть отсутствие ступени, а
 * не её значение. Режим маржи — {@code ISOLATED}: это ОГРАНИЧЕНИЕ КОНТУРА,
 * единственное допустимое значение (docs/rules/trading-constraints.md), а
 * не догадка о настройке. Плечо остаётся ПУСТЫМ: оно объявлено ручной
 * статичной настройкой держателя, и подставленное число выглядело бы
 * назначенным, не будучи им.
 *
 * <p><b>Автора строки эта граница спрашивает сама, и это названное
 * исключение.</b> Безопасная вставка по ключу идёт нативным запросом и
 * слушателей аудита не проходит вовсе, поэтому {@code createdBy} она обязана
 * положить своей рукой — а спросить обязана ТОГО ЖЕ поставщика, которого
 * спрашивает слушатель: вторая копия правила «принципал либо контур»
 * разошлась бы с первой (.claude/rules/policy-home.md). Материализовать
 * строку может и ручная тропа — ручная остановка читает состояние пары, — и
 * подставленный класс контура был бы тогда ложной записью
 * (docs/models/domain/other/Auditable.md §«Область значений актора»).
 * Параметр в сигнатуре сделал бы писателем значения каждого вызывающего, а
 * о происхождении хода не знает ни один из них.
 */
@Service
@RequiredArgsConstructor
public class AccountInstrumentStateDataService {

    private final AccountInstrumentStateRepository repository;
    private final AccountInstrumentStateMapper mapper;
    private final ActorProvider actorProvider;

    /**
     * Поднять ступень пары до запрошенной; {@code true} — переход
     * применился, то есть вызов и есть первый.
     *
     * <p><b>Возврат — анкер идемпотентности реакции</b>
     * (docs/components/SafetyHoldCoordinator.md §Последовательность):
     * поглощённый вызов отличается от применившегося ровно им.
     *
     * <p>Строка материализуется тем же ленивым ходом, что и у читателя:
     * ступень может быть поднята раньше, чем пары коснулась торговля.
     */
    @Transactional
    public Boolean raiseRung(Long exchangeAccountId, Long instrumentId, Instrument.SafetyRung requested) {
        repository.insertIfAbsent(exchangeAccountId, instrumentId,
                Instrument.SafetyRung.ACTIVE.name(), Instrument.MarginMode.ISOLATED.name(),
                actorProvider.currentActor());
        return repository.raiseRung(exchangeAccountId, instrumentId, requested.name(),
                lowerRungs(requested)) > 0;
    }

    /**
     * Опустить НАЗВАННУЮ ступень пары; {@code true} — переход применился.
     * Ложь означает холостой вызов: названная ступень не стои́т.
     */
    @Transactional
    public Boolean clearRung(Long exchangeAccountId, Long instrumentId,
                             Instrument.SafetyRung standing, Instrument.SafetyRung target) {
        return repository.clearRung(exchangeAccountId, instrumentId, standing.name(), target.name()) > 0;
    }

    /**
     * Ступени строго ниже запрошенной — множество входа подъёма. Ранг, а
     * не порядок объявления: монотонность лестницы объявлена рангом
     * (docs/rules/exchange-hold.md §«Границы и эскалация»).
     */
    private static List<String> lowerRungs(Instrument.SafetyRung requested) {
        return Arrays.stream(Instrument.SafetyRung.values())
                .filter(rung -> rung.rank() < requested.rank())
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    /**
     * Инструменты счёта со стоящей ступенью — операнд отбора входа: обе
     * ступени лестницы инструмента гасят новые входы, и различать их
     * отбору не нужно (docs/rules/instrument-hold.md §Enforcement).
     *
     * <p>Одним чтением на счёт, а не по паре: чтение по паре
     * материализовало бы строку у каждого инструмента каталога — то есть
     * заводило бы строки под пары, которых никто не коснулся.
     */
    @Transactional(readOnly = true)
    public List<Long> findInstrumentIdsWithStandingRung(Long exchangeAccountId) {
        return repository.findInstrumentIdsInRungs(exchangeAccountId, standingRungs());
    }

    /**
     * Инструменты счёта под ЖЁСТКОЙ ступенью — популяция детектора
     * «жёсткая ступень радиуса не проэнфорсена»
     * (docs/components/AnomalyJob.md §«Что ищет»).
     *
     * <p>Мягкая сюда не входит: она живых сущностей на бирже и не
     * обещает убирать, а детектор наблюдает именно неисполненное
     * обещание жёсткой.
     */
    @Transactional(readOnly = true)
    public List<Long> findInstrumentIdsUnderHardRung(Long exchangeAccountId) {
        return repository.findInstrumentIdsInRungs(exchangeAccountId,
                List.of(Instrument.SafetyRung.TRADE_BLOCKED.name()));
    }

    /** Ступени строго выше рабочей — любая из них гасит новый вход. */
    private static List<String> standingRungs() {
        return Arrays.stream(Instrument.SafetyRung.values())
                .filter(rung -> rung.rank() > Instrument.SafetyRung.ACTIVE.rank())
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    /**
     * Состояние пары, материализуя строку, если её ещё нет.
     *
     * <p>Вставка идёт БЕЗОПАСНО ПО КЛЮЧУ, поэтому конкурент, пришедший
     * вторым, читает строку победителя, а не падает нарушением ключа
     * (docs/rules/idempotency-via-unique.md).
     */
    @Transactional
    public AccountInstrumentState getRequiredByPair(Long exchangeAccountId, Long instrumentId) {
        repository.insertIfAbsent(exchangeAccountId, instrumentId,
                Instrument.SafetyRung.ACTIVE.name(), Instrument.MarginMode.ISOLATED.name(),
                actorProvider.currentActor());
        return repository.findByExchangeAccountIdAndInstrumentId(exchangeAccountId, instrumentId)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalStateException(
                        "Account instrument state is missing right after a safe insert: account "
                                + exchangeAccountId + ", instrument " + instrumentId));
    }
}

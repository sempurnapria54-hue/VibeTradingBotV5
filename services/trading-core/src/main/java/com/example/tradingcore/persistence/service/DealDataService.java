package com.example.tradingcore.persistence.service;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.mapping.DealMapper;
import com.example.tradingcore.persistence.repository.DealRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для сделки.
 *
 * <p>Граф агрегата — транши, эпизоды и ноги — собирается своими
 * DataService'ами по {@code deal_id}, не здесь: строка сделки каскадных
 * коллекций не несёт.
 *
 * <p><b>Охраняемые обновления делегируются запросам как есть.</b>
 * Вызывающий монотонность и write-once не проверяет — иначе охрана
 * держалась бы ровно до второго вызывающего
 * (docs/models/domain/aggregate/Deal.md §Персистентность).
 */
@Service
@RequiredArgsConstructor
public class DealDataService {

    private static final List<String> TERMINAL_STATUSES = List.of(
            Deal.Status.CLOSED.name(), Deal.Status.EMERGENCY_CLOSED.name());

    /**
     * Откуда каскад уводит сделку в ошибочное состояние. Ошибочный статус
     * в перечень не входит — сделка уже там, и повторная запись сдвигала
     * бы момент изменения строки без изменения состояния.
     */
    private static final List<String> CASCADE_SOURCE_STATUSES = List.of(
            Deal.Status.ACTIVE.name(), Deal.Status.EXIT_PENDING.name());

    private final DealRepository repository;
    private final DealMapper mapper;

    @Transactional
    public Deal save(Deal deal) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(deal)));
    }

    @Transactional(readOnly = true)
    public Deal getRequiredByInternalId(String internalId) {
        return repository.findByInternalId(internalId)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("Deal not found: " + internalId));
    }

    /**
     * Нетерминальные сделки ограниченным окном — вход прохода
     * оркестратора. {@code ERROR} сюда входит: аварийная тропа сделки
     * тоже ведётся проходом.
     */
    @Transactional(readOnly = true)
    public List<Deal> findActive(Integer limit) {
        return repository.findByStatusNotInOrderByIdAsc(TERMINAL_STATUSES, PageRequest.of(0, limit)).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }

    /**
     * Нетерминальные сделки счёта — популяция каскадного снятия риска
     * биржевого радиуса (docs/components/KillSwitchService.md).
     */
    @Transactional(readOnly = true)
    public List<Deal> findNonTerminalByExchangeAccountId(Long exchangeAccountId) {
        return repository.findByExchangeAccountIdAndStatusNotIn(exchangeAccountId, TERMINAL_STATUSES).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }

    /**
     * Сделки счёта недавним окном — чтение поверхности. Статусом выборка
     * не сужается: читателю нужна торговая строка целиком, включая
     * закрытое.
     */
    @Transactional(readOnly = true)
    public List<Deal> findRecentOnAccount(Long exchangeAccountId, Integer limit) {
        return repository.findRecentOnAccount(exchangeAccountId, PageRequest.of(0, limit)).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }

    /**
     * Сделки радиуса, по которым предусловие снятия холда проверяет живой
     * риск: <b>нетерминальные целиком плюс терминальные недавним окном</b>
     * (docs/rules/manual-halt.md §«Выборка и производитель предусловия
     * названы»).
     *
     * <p>Нетерминальные окна не требуют: слот пары держит не больше одной
     * незакрытой сделки, то есть их не больше, чем инструментов контура.
     * Терминальные требуют — история счёта растёт без предела.
     *
     * @param instrumentId инструмент радиуса; пусто — радиус счёта целиком
     */
    @Transactional(readOnly = true)
    public List<Deal> findRiskCandidatesOnScope(Long exchangeAccountId, Long instrumentId,
                                                Integer terminalWindow) {
        List<Deal> candidates = new ArrayList<>(isNull(instrumentId)
                ? repository.findByExchangeAccountIdAndStatusNotIn(exchangeAccountId, TERMINAL_STATUSES)
                        .stream().map(mapper::persistenceToDomain).collect(Collectors.toList())
                : repository.findByExchangeAccountIdAndInstrumentIdAndStatusNotIn(exchangeAccountId,
                                instrumentId, TERMINAL_STATUSES)
                        .stream().map(mapper::persistenceToDomain).collect(Collectors.toList()));
        PageRequest window = PageRequest.of(0, terminalWindow);
        candidates.addAll((isNull(instrumentId)
                ? repository.findRecentTerminalOnAccount(exchangeAccountId, TERMINAL_STATUSES, window)
                : repository.findRecentTerminalOnPair(exchangeAccountId, instrumentId, TERMINAL_STATUSES,
                        window))
                .stream().map(mapper::persistenceToDomain).collect(Collectors.toList()));
        return candidates;
    }

    /**
     * Каскад активных сделок счёта в ошибочное состояние; возвращает число
     * уведённых. Радиус — весь счёт
     * (docs/rules/error-handling-policy.md §«Жёсткая ступень энфорсится
     * непрерывно, а не одним ходом»).
     */
    @Transactional
    public Integer cascadeAccountToError(Long exchangeAccountId) {
        return repository.cascadeAccountToError(exchangeAccountId, CASCADE_SOURCE_STATUSES,
                Deal.Status.ERROR.name());
    }

    /** Тот же каскад радиусом пары «счёт, инструмент». */
    @Transactional
    public Integer cascadeInstrumentToError(Long exchangeAccountId, Long instrumentId) {
        return repository.cascadeInstrumentToError(exchangeAccountId, instrumentId, CASCADE_SOURCE_STATUSES,
                Deal.Status.ERROR.name());
    }

    /**
     * Какие из названных сделок стоя́т на счёте в жёсткой ступени —
     * операнд энфорсмента прохода. Пустой вход запроса не производит:
     * {@code in ()} на пустом множестве — синтаксическая ошибка у части
     * диалектов, а ответ известен заранее.
     */
    @Transactional(readOnly = true)
    public List<Long> findIdsUnderAccountRung(List<Long> dealIds) {
        if (isEmpty(dealIds)) {
            return List.of();
        }
        return repository.findIdsUnderAccountRung(dealIds, ExchangeAccount.SafetyRung.TRADE_BLOCKED.name());
    }

    /** Тот же операнд радиусом пары «счёт, инструмент». */
    @Transactional(readOnly = true)
    public List<Long> findIdsUnderInstrumentRung(List<Long> dealIds) {
        if (isEmpty(dealIds)) {
            return List.of();
        }
        return repository.findIdsUnderInstrumentRung(dealIds, Instrument.SafetyRung.TRADE_BLOCKED.name());
    }

    /**
     * Увести сделку в ошибочное состояние энфорсментом жёсткой ступени,
     * записав причину той же транзакцией; {@code true} — ребро
     * применилось, то есть сделка стояла активной.
     */
    @Transactional
    public Boolean enforceHardRung(Long dealId, Deal.ShutdownReason shutdownReason) {
        return repository.enforceHardRung(dealId, shutdownReason.name(), Deal.Status.ERROR.name(),
                CASCADE_SOURCE_STATUSES) > 0;
    }

    /**
     * Увести сделку в ошибочное состояние перехватом петли — без причины
     * выхода из штатного ведения; {@code true} — ребро применилось.
     */
    @Transactional
    public Boolean interceptToError(Long dealId) {
        return repository.interceptToError(dealId, Deal.Status.ERROR.name(), CASCADE_SOURCE_STATUSES) > 0;
    }

    /**
     * Применить статусное ребро прохода к строке сделки; {@code true} —
     * ребро применилось. Значения берутся с модели, уже сведённой
     * проходом: причина закрытия write-once, причина выхода из штатного
     * ведения перезаписываема (docs/lifecycles/Deal.md).
     *
     * @param fromStatus статус, в котором сделка была прочитана проходом
     */
    @Transactional
    public Boolean applyStatusEdge(Deal deal, Deal.Status fromStatus) {
        return repository.applyStatusEdge(deal.getId(), deal.getStatus().name(),
                name(deal.getShutdownReason()), name(deal.getCloseReason()), fromStatus.name()) > 0;
    }

    /** Имя значения перечня либо пусто — колонка допускает отсутствие. */
    private static String name(Enum<?> value) {
        return isNull(value) ? null : value.name();
    }

    /**
     * У счёта есть незакрытая сделка хоть по одной паре — контурная
     * половина гейта входа: она энфорсит ограничение «торгуется один
     * инструмент на счёт» (docs/rules/trading-constraints.md).
     */
    @Transactional(readOnly = true)
    public Boolean existsActiveOnAccount(Long exchangeAccountId) {
        return repository.existsByExchangeAccountIdAndStatusNotIn(exchangeAccountId, TERMINAL_STATUSES);
    }

    /** Слот пары «счёт, инструмент» занят незакрытой сделкой — гейт входа. */
    @Transactional(readOnly = true)
    public Boolean existsActiveOnPair(Long exchangeAccountId, Long instrumentId) {
        return repository.existsByExchangeAccountIdAndInstrumentIdAndStatusNotIn(
                exchangeAccountId, instrumentId, TERMINAL_STATUSES);
    }

    /**
     * Инструменты счёта, объяснённые незакрытой сделкой, — тот же вопрос,
     * что и у гейта пары, но сразу по всему счёту.
     *
     * <p>Читатель — обход проактивной детекции: он задаёт вопрос по
     * каждому инструменту контура, и пачка избавляет его от чтения на
     * итерацию (.claude/rules/codestyle.md §«Выборка данных»).
     */
    @Transactional(readOnly = true)
    public Set<Long> findInstrumentIdsWithActiveDeal(Long exchangeAccountId) {
        return new HashSet<>(
                repository.findInstrumentIdsWithActiveDeal(exchangeAccountId, TERMINAL_STATUSES));
    }

    /** Двигает порог доказанного покрытия вперёд по наблюдённому моменту. */
    @Transactional
    public void advanceCoverageProvenThrough(Long dealId, OffsetDateTime observedAt) {
        repository.advanceCoverageProvenThrough(dealId, observedAt);
    }

    /** Ставит нижнюю границу окна линковки движений, если её ещё нет. */
    @Transactional
    public void applyBillsWindowBegin(Long dealId, OffsetDateTime observedAt) {
        repository.applyBillsWindowBegin(dealId, observedAt);
    }

    /** Двигает метку «движения добыты по …» вперёд по времени источника прохода. */
    @Transactional
    public void advanceBillsFetchedThrough(Long dealId, OffsetDateTime fetchedThrough) {
        repository.advanceBillsFetchedThrough(dealId, fetchedThrough);
    }

    /** Фиксирует базу риска первым сайзингом сделки, если она ещё не зафиксирована. */
    @Transactional
    public void applyPlannedRiskEquityBase(Long dealId, BigDecimal equityBase) {
        repository.applyPlannedRiskEquityBase(dealId, equityBase);
    }

    /** Фиксирует валюту риска первым сайзингом сделки, если она ещё не зафиксирована. */
    @Transactional
    public void applyPlannedRiskCurrency(Long dealId, String currency) {
        repository.applyPlannedRiskCurrency(dealId, currency);
    }
}

package com.example.tradingcore.persistence.service;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingcore.mapping.ExchangeAccountMapper;
import com.example.tradingcore.persistence.model.ExchangeAccountEntity;
import com.example.tradingcore.persistence.repository.ExchangeAccountRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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

    /**
     * Фиксирует первое наблюдение базы риска счёта, если она ещё пуста.
     *
     * <p>Точечный писатель, а не сохранение строки целиком: строка счёта —
     * ПРОЕКЦИЯ чужого реестра, и запись её целиком перетёрла бы
     * проекционные колонки значением, устаревшим на возраст контекста
     * прохода.
     */
    @Transactional
    public void applyRiskBase(Long id, BigDecimal riskBase, String currency) {
        repository.applyRiskBase(id, riskBase, currency);
    }

    /**
     * Двинуть серию убытков счёта: инкремент либо обнуление. Ход идёт
     * запросом, а не записью строки целиком: строка счёта — проекция
     * чужого реестра, а сам счёт общий для всех его сделок
     * (docs/rules/loss-streak-halt.md).
     *
     * @param increment {@code true} — убыток, {@code false} — прибыль
     */
    @Transactional
    public void applyLossStreak(Long id, Boolean increment) {
        if (Boolean.TRUE.equals(increment)) {
            repository.incrementConsecutiveLossCount(id);
            return;
        }
        repository.resetConsecutiveLossCount(id);
    }

    /**
     * Поднять ступень счёта до запрошенной; {@code true} — переход
     * применился, то есть вызов и есть первый.
     *
     * <p>Возврат — анкер идемпотентности реакции, тот же, что у лестницы
     * инструмента (docs/components/SafetyHoldCoordinator.md
     * §Последовательность).
     *
     * <p>Точечный писатель, а не сохранение строки целиком: строка счёта —
     * ПРОЕКЦИЯ чужого реестра, и запись её целиком перетёрла бы
     * проекционные колонки значением возраста контекста прохода.
     */
    @Transactional
    public Boolean raiseRung(Long id, ExchangeAccount.SafetyRung requested) {
        return repository.raiseRung(id, requested.name(), lowerRungs(requested)) > 0;
    }

    /**
     * Опустить НАЗВАННУЮ ступень счёта; {@code true} — переход
     * применился. Ложь означает холостой вызов: названная ступень не
     * стои́т, и по лестнице снятие не шагает.
     */
    @Transactional
    public Boolean clearRung(Long id, ExchangeAccount.SafetyRung standing,
                             ExchangeAccount.SafetyRung target) {
        return repository.clearRung(id, standing.name(), target.name()) > 0;
    }

    /**
     * Ступени строго ниже запрошенной — множество входа подъёма
     * (docs/rules/exchange-hold.md §«Границы и эскалация»).
     */
    private static List<String> lowerRungs(ExchangeAccount.SafetyRung requested) {
        return Arrays.stream(ExchangeAccount.SafetyRung.values())
                .filter(rung -> rung.rank() < requested.rank())
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    /** Идентичности тенантов, у которых в проекции есть счёт. */
    @Transactional(readOnly = true)
    public List<String> findTenantInternalIds() {
        return repository.findDistinctTenantInternalIds();
    }

    /**
     * Биржевой счёт сделки строкой проекции; нет — авария тропы: сделка
     * ссылается на счёт, которого в проекции реестра нет, и ни ключа
     * вызова площадки, ни торгового состояния у неё не будет.
     */
    @Transactional(readOnly = true)
    public ExchangeAccount getRequiredById(Long id) {
        return repository.findById(id)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalStateException("ExchangeAccount projection not found: " + id));
    }

    /**
     * Биржевой счёт по идентичности, пересекающей границу сервиса; нет —
     * негодный вход вызова, а не авария тропы: идентичность приходит
     * снаружи (.claude/rules/codestyle.md §«Идентичность наружу»).
     */
    @Transactional(readOnly = true)
    public ExchangeAccount getRequiredByInternalId(String internalId) {
        return repository.findByInternalId(internalId)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ExchangeAccount not found: " + internalId));
    }

    /**
     * Числовой ключ строки проекции по идентичности счёта.
     *
     * <p><b>Проекция поля, а не сущность</b>
     * (.claude/rules/codestyle.md §«Выборка данных: не тянем сущность
     * ради одного поля»). Ненайденность — негодный вход вызова.
     */
    @Transactional(readOnly = true)
    public Long getRequiredIdByInternalId(String internalId) {
        return repository.findIdByInternalId(internalId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ExchangeAccount not found: " + internalId));
    }

    /**
     * Идентичность счёта по его числовому ключу — <b>проекцией поля</b>, а
     * не строкой целиком (.claude/rules/codestyle.md §«Выборка данных: не
     * тянем сущность ради одного поля»). Ненайденность — авария тропы:
     * сделка ссылается на счёт, которого в проекции реестра нет.
     */
    @Transactional(readOnly = true)
    public String getRequiredInternalIdById(Long id) {
        return repository.findInternalIdById(id)
                .orElseThrow(() -> new IllegalStateException("ExchangeAccount not found: " + id));
    }

    /** Тенант-владелец счёта по числовому ключу — той же проекцией поля. */
    @Transactional(readOnly = true)
    public String getRequiredTenantInternalIdById(Long id) {
        return repository.findTenantInternalIdById(id)
                .orElseThrow(() -> new IllegalStateException("ExchangeAccount not found: " + id));
    }

    /**
     * Тенант-владелец счёта по его идентичности; пусто — счёта в проекции
     * нет вовсе. Пустота здесь <b>ответ, а не авария</b>: вызывающий и
     * спрашивает «существует ли».
     */
    @Transactional(readOnly = true)
    public Optional<String> findTenantInternalIdByInternalId(String internalId) {
        return repository.findTenantInternalIdByInternalId(internalId);
    }

    /**
     * Счета, по которым разрешено заводить новый риск, — популяция отбора
     * входа. Мягкая ступень счёта энфорсится именно здесь: счёт под
     * холдом из выборки выпадает, а живые его сделки ведутся полностью
     * (docs/components/EntryScannerJob.md §«Гейт входа»).
     */
    @Transactional(readOnly = true)
    public List<ExchangeAccount> findEntryEligibleAccounts() {
        return repository.findByStatusAndSafetyRung(ExchangeAccount.Status.ACTIVE.name(),
                        ExchangeAccount.SafetyRung.ACTIVE.name()).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }

    /**
     * Отметить проход safety-сети и вернуть счёт подряд идущих
     * НЕнаблюдённых проходов.
     *
     * <p><b>Операнд — исход НАБЛЮДЕНИЯ, а не полнота среза.</b> Проход,
     * чей срез добыт целиком, но детекция по нему не отработала, есть
     * такая же слепота; отметка «полон» до обхода сбрасывала бы счёт и на
     * таком проходе — то есть различение «ничего не нашли» против «не
     * смотрели» переставало бы работать ровно в момент сбоя.
     *
     * @param observed проход наблюдён: срез добыт целиком И детекция по
     *                 нему отработала
     */
    @Transactional
    public Integer markPass(Long id, Boolean observed) {
        if (isTrue(observed)) {
            repository.resetBlindPassCount(id);
            return COUNTER_START;
        }
        repository.incrementBlindPassCount(id);
        return repository.findBlindPassCount(id).orElse(COUNTER_START);
    }

    private ExchangeAccountEntity newProjection(String internalId) {
        ExchangeAccountEntity entity = new ExchangeAccountEntity();
        entity.setInternalId(internalId);
        entity.setConsecutiveLossCount(COUNTER_START);
        entity.setBlindPassCount(COUNTER_START);
        entity.setSafetyRung(ExchangeAccount.SafetyRung.ACTIVE.name());
        return entity;
    }

    /**
     * Счета, доступные торговле, доменными моделями.
     *
     * <p>Здесь тянется строка целиком, а не проекция: читателю нужен и
     * ключ записи, и идентичность вызова коннектора, и торговое
     * состояние, — то есть сам счёт, а не поле
     * (.claude/rules/codestyle.md §«Выборка данных»).
     */
    @Transactional(readOnly = true)
    public List<ExchangeAccount> findTradingAccounts() {
        return repository.findByStatus(ExchangeAccount.Status.ACTIVE.name()).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }
}

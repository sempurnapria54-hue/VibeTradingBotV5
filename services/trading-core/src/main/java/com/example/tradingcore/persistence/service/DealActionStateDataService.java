package com.example.tradingcore.persistence.service;

import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingcore.domain.command.ActionKind;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.mapping.DealActionStateMapper;
import com.example.tradingcore.persistence.repository.DealStrategyActionStateRepository;
import com.example.tradingcore.persistence.repository.DealSystemActionStateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence строки исполнения.
 *
 * <p>Модель одна, таблиц две — ветвление идёт по виду действия
 * ({@link ActionKind}), и никакой другой развилки у сервиса нет: вид
 * кодируется таблицей, а не колонкой.
 *
 * <p><b>Живое исполнение адресуется частичным ключом, а не парой «сделка,
 * узел»:</b> одно объявление, материализованное N траншами сетки, даёт N
 * законных живых исполнений, и ключ из пары отдавал бы первое попавшееся.
 * Отбор идёт в памяти прохода по строкам сделки — одним чтением на
 * сделку, без запроса на каждый узел.
 */
@Service
@RequiredArgsConstructor
public class DealActionStateDataService {

    private final DealStrategyActionStateRepository strategyRepository;
    private final DealSystemActionStateRepository systemRepository;
    private final DealActionStateMapper mapper;

    /** Сохранить строку исполнения в таблицу её вида. */
    @Transactional
    public DealActionState save(DealActionState state) {
        if (isTrue(state.isSystem())) {
            return mapper.systemPersistenceToDomain(
                    systemRepository.save(mapper.domainToSystemPersistence(state)));
        }
        return mapper.strategyPersistenceToDomain(
                strategyRepository.save(mapper.domainToStrategyPersistence(state)));
    }

    /**
     * Закрыть живые СИСТЕМНЫЕ исполнения сделки как неактуальные, кроме
     * названного — того, чьей транзакцией идёт терминальное ребро.
     *
     * <p>Ход принадлежит терминалу: терминализованная сделка в выборку
     * следующего прохода не попадает, поэтому её живые строки не закроет
     * никто, кроме ребра, применившего терминал
     * (docs/components/MarkDealClosedExecutor.md §«Побочные эффекты
     * терминала»). Строки берутся из контекста прохода — читать их заново
     * незачем, они уже предъявлены.
     */
    @Transactional
    public void skipLiveSystemExecutions(List<DealActionState> states, Long exceptId) {
        emptyIfNull(states).stream()
                .filter(state -> isTrue(state.isSystem()) && isTrue(state.isLive()))
                .filter(state -> isFalse(Objects.equals(exceptId, state.getId())))
                .forEach(state -> {
                    state.setStatus(DealActionStateStatus.SKIPPED);
                    save(state);
                });
    }

    /** Все строки исполнения сделки — оба вида, двумя чтениями. */
    @Transactional(readOnly = true)
    public List<DealActionState> findByDealId(Long dealId) {
        List<DealActionState> states = new ArrayList<>(strategyRepository.findByDealId(dealId).stream()
                .map(mapper::strategyPersistenceToDomain)
                .collect(Collectors.toList()));
        states.addAll(systemRepository.findByDealId(dealId).stream()
                .map(mapper::systemPersistenceToDomain)
                .collect(Collectors.toList()));
        return states;
    }

    /**
     * Строка исполнения по идентификатору — вида не требует: обе таблицы
     * берут идентичность из ОДНОЙ последовательности, поэтому анкер
     * команды однозначен одним числом
     * (docs/components/models/ServiceCommand.md).
     */
    @Transactional(readOnly = true)
    public Optional<DealActionState> findById(Long id) {
        Optional<DealActionState> strategy = strategyRepository.findById(id)
                .map(mapper::strategyPersistenceToDomain);
        if (strategy.isPresent()) {
            return strategy;
        }
        return systemRepository.findById(id).map(mapper::systemPersistenceToDomain);
    }
}

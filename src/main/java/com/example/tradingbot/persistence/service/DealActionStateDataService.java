package com.example.tradingbot.persistence.service;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.ActionKind;
import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.mapping.DealActionStateMapper;
import com.example.tradingbot.persistence.repository.DealStrategyActionStateRepository;
import com.example.tradingbot.persistence.repository.DealSystemActionStateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link DealActionState}. Модель одна,
 * таблиц две — ветвление идёт по виду действия
 * ({@link ActionKind}), и никакой другой развилки у сервиса нет: вид
 * кодируется таблицей, а не колонкой.
 *
 * <p>Живое исполнение адресуется <b>частичным ключом</b>, а не парой
 * (сделка, узел): одно объявление, материализованное N траншами сетки,
 * даёт N законных живых исполнений, и прежний ключ отдавал бы первое
 * попавшееся. Отбор идёт в памяти прохода по строкам сделки — одним
 * чтением на сделку, без запроса на каждый узел.
 */
@Service
@RequiredArgsConstructor
public class DealActionStateDataService {

    private final DealStrategyActionStateRepository strategyRepository;
    private final DealSystemActionStateRepository systemRepository;
    private final DealActionStateMapper mapper;

    @Transactional
    public DealActionState save(DealActionState state) {
        if (isTrue(state.isSystem())) {
            return mapper.systemPersistenceToDomain(
                    systemRepository.save(mapper.domainToSystemPersistence(state)));
        }
        return mapper.strategyPersistenceToDomain(
                strategyRepository.save(mapper.domainToStrategyPersistence(state)));
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
     * команды однозначен одним числом и второго поля в команде не
     * заводится (docs/components/models/ServiceCommand.md).
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

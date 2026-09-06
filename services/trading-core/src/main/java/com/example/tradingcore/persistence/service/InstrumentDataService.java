package com.example.tradingcore.persistence.service;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingcore.mapping.InstrumentExternalRulesJsonConverter;
import com.example.tradingcore.mapping.InstrumentMapper;
import com.example.tradingcore.persistence.model.InstrumentEntity;
import com.example.tradingcore.persistence.repository.InstrumentRepository;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для проекции каталога инструментов.
 *
 * <p><b>Спецификация и правила кладутся ОДНИМ ходом, и это условие
 * гейта.</b> Момент снимка описывает строку целиком; двинуть его,
 * записав половину, значило бы объявить свежими правила, которых не
 * читали (docs/models/domain/core/Instrument.md §«Срок свежести проекции:
 * величина, писатель, реакция»).
 */
@Service
@RequiredArgsConstructor
public class InstrumentDataService {

    private final InstrumentRepository repository;
    private final InstrumentMapper mapper;
    private final InstrumentExternalRulesJsonConverter rulesConverter;

    /**
     * Расчётная валюта инструмента площадки; пусто — инструмента в
     * проекции каталога нет.
     *
     * <p>Читается проекцией одного поля, а не загрузкой строки: у
     * вызывающего — лестницы курса — нужда ровно в валюте
     * (.claude/rules/codestyle.md §«Выборка данных»).
     */
    @Transactional(readOnly = true)
    public Optional<String> findSettlementCurrency(String exchangeCode, String externalId) {
        return repository.findSettlementCurrency(exchangeCode, externalId);
    }

    /**
     * Сводит строку проекции с каталогом владельца: заводит недостающую,
     * обновляет спецификацию и навес правил существующей.
     *
     * @param instrument  инструмент, каким его отдал каталог
     * @param rules       справочные правила; пусто — навес у владельца
     *                    ещё не материализован
     * @param projectedAt момент снимка строки целиком
     */
    @Transactional
    public void upsertProjection(Instrument instrument, InstrumentExternalRules rules,
                                 OffsetDateTime projectedAt) {
        InstrumentEntity entity = repository.findByInternalId(instrument.getInternalId())
                .orElseGet(() -> newProjection(instrument));
        mapper.updateProjection(instrument, entity);
        entity.setExternalRules(rulesConverter.rulesToJson(rules));
        entity.setProjectedAt(projectedAt);
        repository.save(entity);
    }

    /**
     * Заводит строку неизменяемой частью: идентичность инструмента —
     * владельца каталога, ядро своей не назначает.
     */
    private InstrumentEntity newProjection(Instrument instrument) {
        InstrumentEntity entity = new InstrumentEntity();
        entity.setInternalId(instrument.getInternalId());
        entity.setExchangeCode(instrument.getExchangeCode());
        entity.setExternalId(instrument.getExternalId());
        return entity;
    }

    /**
     * Инструмент сделки строкой проекции; нет — авария тропы: сделка
     * ссылается на инструмент, которого в проекции каталога нет, и вести
     * её не по чему.
     *
     * <p>Здесь тянется строка целиком, а не поле: читателю — сборке
     * контекста прохода — нужна сама модель
     * (.claude/rules/codestyle.md §«Выборка данных»).
     */
    @Transactional(readOnly = true)
    public Instrument getRequiredById(Long id) {
        return repository.findById(id)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalStateException("Instrument projection not found: " + id));
    }

    /**
     * Инструмент по идентичности, пересекающей границу сервиса; нет —
     * негодный вход вызова, а не авария тропы.
     */
    @Transactional(readOnly = true)
    public Instrument getRequiredByInternalId(String internalId) {
        return repository.findByInternalId(internalId)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + internalId));
    }

    /**
     * Числовой ключ строки проекции по идентичности инструмента.
     *
     * <p><b>Проекция поля, а не сущность:</b> резолв связи внутри базы
     * ядра ничего сверх ключа не читает
     * (.claude/rules/codestyle.md §«Выборка данных: не тянем сущность
     * ради одного поля»). Ненайденность — негодный вход вызова:
     * идентичность пришла снаружи.
     */
    @Transactional(readOnly = true)
    public Long getRequiredIdByInternalId(String internalId) {
        return repository.findIdByInternalId(internalId)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + internalId));
    }

    /**
     * Есть ли инструмент с такой идентичностью в проекции каталога.
     * Пустота — ответ, а не авария: вызывающий и спрашивает «существует
     * ли».
     */
    @Transactional(readOnly = true)
    public Boolean existsByInternalId(String internalId) {
        return repository.findIdByInternalId(internalId).isPresent();
    }

    /**
     * Торгуемые инструменты площадки ограниченным окном — популяция
     * отбора входа. Расчётная валюта обязана быть резолвена: без неё
     * торгуемым инструмент не считается
     * (docs/components/EntryScannerJob.md §Шаги).
     */
    @Transactional(readOnly = true)
    public List<Instrument> findTradable(String exchangeCode, Integer limit) {
        return repository.findTradable(exchangeCode, Instrument.Status.ACTIVE.name(),
                        PageRequest.of(0, limit)).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }

    /**
     * Инструменты площадки целиком — популяция обхода детекции. Статус в
     * отборе не участвует: обход идёт по факту живого риска, а не по
     * готовности инструмента к торговле.
     */
    @Transactional(readOnly = true)
    public List<Instrument> findContourWithin(String exchangeCode, Integer limit) {
        return repository.findContour(exchangeCode, PageRequest.of(0, limit)).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }

    /**
     * Сырые типы инструментов проекции каталога.
     *
     * <p>Читатель — синк ставок комиссии: ставка есть атрибут группы
     * счёта, и вызов идёт на пару «счёт, тип», а не на инструмент.
     * Перечень берётся ИЗ ДАННЫХ, а не константой: контур фазы 1
     * односоставен, но это факт данных, а не конвенция кода.
     */
    @Transactional(readOnly = true)
    public List<String> findDistinctExternalTypes() {
        return repository.findDistinctExternalTypes();
    }

    /**
     * Раскладка «ключ инструмента → его идентичность» на пачку ключей —
     * одним чтением проекции.
     *
     * <p>Читатель — поверхность чтения ядра, которой на каждую строку
     * ответа нужна ровно идентичность связанного инструмента. Пустая
     * пачка запроса не делает: {@code in ()} у части диалектов не
     * компилируется вовсе.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> findInternalIdsByIds(Collection<Long> ids) {
        if (isEmpty(ids)) {
            return Map.of();
        }
        return repository.findInternalIdsByIdIn(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1]));
    }
}

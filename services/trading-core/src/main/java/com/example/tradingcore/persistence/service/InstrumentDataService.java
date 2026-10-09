package com.example.tradingcore.persistence.service;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingcore.mapping.InstrumentExternalRulesJsonConverter;
import com.example.tradingcore.mapping.InstrumentMapper;
import com.example.tradingcore.persistence.model.InstrumentEntity;
import com.example.tradingcore.persistence.repository.InstrumentRepository;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
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

    /**
     * Ключ «до первой строки» обхода контура: меньше всякого ключа проекции,
     * и первая страница читается тем же запросом, что и следующие.
     */
    private static final Long BEFORE_FIRST_ID = Long.MIN_VALUE;

    private final InstrumentRepository repository;
    private final InstrumentMapper mapper;
    private final InstrumentExternalRulesJsonConverter rulesConverter;

    /**
     * Расчётная валюта инструмента площадки; пусто — инструмента в
     * проекции каталога нет.
     *
     * <p>Читается проекцией одного поля, а не загрузкой строки: у
     * вызывающего — лестницы курса — нужда ровно в валюте
     * (.claude/rules/codestyle.md §«Выборка данных: не тянем сущность ради
     * одного поля»).
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
     * Идентичность инструмента по его числовому ключу — <b>проекцией
     * поля</b>: писателю события нужна ровно она, а не строка каталога
     * (.claude/rules/codestyle.md §«Выборка данных: не тянем сущность ради
     * одного поля»). Ненайденность — авария тропы.
     */
    @Transactional(readOnly = true)
    public String getRequiredInternalIdById(Long id) {
        return repository.findInternalIdById(id)
                .orElseThrow(() -> new IllegalStateException("Instrument not found: " + id));
    }

    /**
     * Инструмент сделки строкой проекции; нет — авария тропы: сделка
     * ссылается на инструмент, которого в проекции каталога нет, и вести
     * её не по чему.
     *
     * <p>Здесь тянется строка целиком, а не поле: читателю — сборке
     * контекста прохода — нужна сама модель
     * (.claude/rules/codestyle.md §«Выборка данных: не тянем сущность ради
     * одного поля»).
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
     * Инструменты площадки ЦЕЛИКОМ, страница за страницей, — популяция
     * обхода детекции и детектора несвежести ставки. Статус в отборе не
     * участвует: обход идёт по факту живого риска, а не по готовности
     * инструмента к торговле.
     *
     * <p><b>Размер страницы — не предел выборки.</b> Страницы читаются до
     * первой неполной (либо пустой): контур есть весь каталог площадки в
     * проекции, и усечённый обход объявил бы чужими строки среза, которым не
     * хватило места. Неполнота обхода бывает только отказом чтения — он
     * уходит вызывающему исключением.
     *
     * <p><b>Ключом, а не смещением.</b> Следующая страница — строки с ключом
     * строго больше последнего прочитанного. Проекцию между страницами
     * правит синк каталога: удалённая строка сдвинула бы смещение, и строка
     * за ней выпала бы из обхода молча, а ключ от удалений не зависит. Новые
     * строки получают ключ больше всех прежних и попадают в хвост обхода.
     * Чтение страницы по ключу не дорожает с её номером — смещение
     * перечитывало бы весь пройденный префикс.
     *
     * <p><b>Транзакции на обход нет намеренно.</b> Страница отдаётся
     * потребителю между чтениями, и его запись (подъём ступени у детектора
     * несвежести) в транзакции только для чтения не сбросилась бы в базу
     * вовсе. Строка проекции связей не несёт, и перевод в домен вне
     * транзакции ничего не догружает.
     *
     * @param exchangeCode код площадки
     * @param pageSize     размер страницы
     * @param pageConsumer потребитель каждой непустой страницы в порядке ключа
     */
    public void forEachContourPage(String exchangeCode, Integer pageSize, Consumer<List<Instrument>> pageConsumer) {
        List<Instrument> page = findContourAfter(exchangeCode, BEFORE_FIRST_ID, pageSize);
        while (isNotEmpty(page)) {
            pageConsumer.accept(page);
            if (page.size() < pageSize) {
                return;
            }
            page = findContourAfter(exchangeCode, page.getLast().getId(), pageSize);
        }
    }

    /** Одна страница контура: строки площадки с ключом больше названного. */
    private List<Instrument> findContourAfter(String exchangeCode, Long afterId, Integer pageSize) {
        return repository.findContourAfter(exchangeCode, afterId, PageRequest.of(0, pageSize)).stream()
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

    /**
     * Биржевые имена названных инструментов. Пустой вход запроса не
     * производит: ответ известен заранее.
     */
    @Transactional(readOnly = true)
    public Set<String> findExternalIdsByIds(Collection<Long> ids) {
        if (isEmpty(ids)) {
            return new HashSet<>();
        }
        return new HashSet<>(repository.findExternalIdsByIdIn(ids));
    }
}

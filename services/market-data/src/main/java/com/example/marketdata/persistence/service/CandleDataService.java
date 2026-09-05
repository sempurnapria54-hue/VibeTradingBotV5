package com.example.marketdata.persistence.service;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.marketdata.mapping.CandleMapper;
import com.example.marketdata.persistence.model.CandleEntity;
import com.example.marketdata.persistence.repository.CandleRepository;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для свечного ряда. Запись идемпотентна:
 * свеча с уже присутствующим временем открытия в группе повторно не
 * вставляется (естественный ключ (группа, открытие бара)).
 *
 * <p><b>Вставка идёт {@code persist}, а не {@code save}.</b> Ключ у ряда
 * присвоенный, и {@code save} на присвоенном ключе означает для JPA
 * слияние — то есть select перед каждой вставкой. На бэкфилле, где
 * страницы идут сотнями, это удваивает число запросов ровно там, где их
 * и так много; новизна строк здесь уже установлена отбором выше.
 */
@Service
@RequiredArgsConstructor
public class CandleDataService {

    private final CandleRepository repository;
    private final CandleMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Сохраняет только новые свечи группы.
     *
     * @return число фактически вставленных свечей.
     */
    @Transactional
    public Integer saveCandles(Long candleGroupId, List<Candle> candles) {
        if (isEmpty(candles)) {
            return 0;
        }
        long from = candles.stream().mapToLong(Candle::getOpenTimestamp).min().orElseThrow();
        long to = candles.stream().mapToLong(Candle::getOpenTimestamp).max().orElseThrow();
        Set<Long> existing = new HashSet<>(repository.findOpenTimestampsInRange(candleGroupId, from, to));
        List<CandleEntity> toInsert = candles.stream()
                .filter(candle -> isFalse(existing.contains(candle.getOpenTimestamp())))
                .map(candle -> toEntity(candleGroupId, candle))
                .collect(toList());
        toInsert.forEach(entityManager::persist);
        return toInsert.size();
    }

    @Transactional(readOnly = true)
    public Long count(Long candleGroupId) {
        return repository.countByCandleGroupId(candleGroupId);
    }

    /**
     * Ограниченное недавнее окно закрытых свечей группы по возрастанию
     * открытия — вход расчёта производных. Грузит не более {@code limit}
     * последних свечей, не всю историю.
     */
    @Transactional(readOnly = true)
    public List<Candle> findRecentByGroup(Long candleGroupId, Integer limit) {
        List<Candle> descending = repository
                .findByCandleGroupIdOrderByOpenTimestampDesc(candleGroupId, PageRequest.of(0, limit)).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
        Collections.reverse(descending);
        return descending;
    }

    /**
     * Окно истории группы от границы по возрастанию — пакетное чтение для
     * бэктеста. Окно обязательно: безлимитного чтения истории нет.
     */
    @Transactional(readOnly = true)
    public List<Candle> findHistoryFrom(Long candleGroupId, Long fromMillis, Integer limit) {
        return repository
                .findByCandleGroupIdAndOpenTimestampGreaterThanEqualOrderByOpenTimestampAsc(
                        candleGroupId, fromMillis, PageRequest.of(0, limit))
                .stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }

    @Transactional(readOnly = true)
    public Long countInRange(Long candleGroupId, Long fromMillis, Long toMillis) {
        return repository.countInRange(candleGroupId, fromMillis, toMillis);
    }

    @Transactional(readOnly = true)
    public Long findMinOpenTimestamp(Long candleGroupId) {
        return repository.findMinOpenTimestamp(candleGroupId);
    }

    @Transactional(readOnly = true)
    public Long findMaxOpenTimestamp(Long candleGroupId) {
        return repository.findMaxOpenTimestamp(candleGroupId);
    }

    private CandleEntity toEntity(Long candleGroupId, Candle candle) {
        CandleEntity entity = mapper.domainToPersistence(candle);
        entity.setCandleGroupId(candleGroupId);
        return entity;
    }
}

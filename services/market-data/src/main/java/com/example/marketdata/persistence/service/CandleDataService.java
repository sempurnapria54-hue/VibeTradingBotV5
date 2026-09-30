package com.example.marketdata.persistence.service;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.marketdata.mapping.CandleMapper;
import com.example.marketdata.persistence.model.CandleEntity;
import com.example.marketdata.persistence.repository.CandleRepository;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import java.time.OffsetDateTime;
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
 * <p><b>Дедуп держит ключ {@code pk_candle}, а не отбор перед вставкой</b>
 * (docs/rules/idempotency-via-unique.md): каждая свеча идёт безопасной
 * вставкой {@code on conflict do nothing}. Отбор по окну остаётся, но как
 * <b>отсечка объёма</b> — страница бэкфилла, целиком лежащая в ряду, не
 * порождает ни одной вставки, — а не как механизм: при конкурентном
 * писателе отбор и вставка не атомарны, и повтор, проскочивший отбор,
 * поглощается ключом, а не роняет страницу.
 */
@Service
@RequiredArgsConstructor
public class CandleDataService {

    private final CandleRepository repository;
    private final CandleMapper mapper;
    private final PointWriteAudit audit;

    /**
     * Сохраняет только новые свечи группы.
     *
     * @return число фактически вставленных свечей — по ответу базы, а не по
     *         отбору: свеча, которую отбор счёл новой, а ключ поглотил, в
     *         счёт не входит.
     */
    @Transactional
    public Integer saveCandles(Long candleGroupId, List<Candle> candles) {
        if (isEmpty(candles)) {
            return 0;
        }
        long from = candles.stream().mapToLong(Candle::getOpenTimestamp).min().orElseThrow();
        long to = candles.stream().mapToLong(Candle::getOpenTimestamp).max().orElseThrow();
        Set<Long> existing = new HashSet<>(repository.findOpenTimestampsInRange(candleGroupId, from, to));
        List<CandleEntity> candidates = candles.stream()
                .filter(candle -> isFalse(existing.contains(candle.getOpenTimestamp())))
                .map(candle -> toEntity(candleGroupId, candle))
                .collect(toList());
        OffsetDateTime writtenAt = audit.moment();
        String writer = audit.writer();
        int inserted = 0;
        for (CandleEntity candidate : candidates) {
            inserted += repository.insertIfAbsent(candidate.getCandleGroupId(), candidate.getOpenTimestamp(),
                    candidate.getOpen(), candidate.getHigh(), candidate.getLow(), candidate.getClose(),
                    candidate.getVolume(), candidate.getExternalCreatedAt(), candidate.getExternalModifiedAt(),
                    writtenAt, writer);
        }
        return inserted;
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

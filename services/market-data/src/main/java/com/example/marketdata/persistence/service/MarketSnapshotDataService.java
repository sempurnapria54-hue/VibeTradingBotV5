package com.example.marketdata.persistence.service;

import com.example.marketdata.mapping.MarketSnapshotMapper;
import com.example.marketdata.persistence.model.OrderBookSnapshotEntity;
import com.example.marketdata.persistence.model.TickerSnapshotEntity;
import com.example.marketdata.persistence.repository.OrderBookSnapshotRepository;
import com.example.marketdata.persistence.repository.TickerSnapshotRepository;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketOrderBook;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketTicker;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для невосполнимых срезов.
 *
 * <p><b>Повтор среза того же момента отбрасывается, а не переписывает
 * строку.</b> Ключ — инструмент плюс метка времени ПЛОЩАДКИ; если
 * площадка на двух проходах отдала один и тот же момент, второго факта не
 * произошло, и запись его как нового исказила бы ряд задержки.
 *
 * <p><b>Отбрасывает повтор ключ, а не проверка перед вставкой</b>
 * (docs/rules/idempotency-via-unique.md): вставка идёт безопасной формой
 * {@code on conflict do nothing}. Проверка «есть ли уже» с последующей
 * вставкой не атомарна — второй писатель того же момента падал бы
 * нарушением ключа, — и стоила бы лишнего запроса на каждый срез прохода
 * по всему листингу.
 */
@Service
@RequiredArgsConstructor
public class MarketSnapshotDataService {

    private final OrderBookSnapshotRepository orderBookRepository;
    private final TickerSnapshotRepository tickerRepository;
    private final MarketSnapshotMapper mapper;
    private final PointWriteAudit audit;

    /** Пишет срез книги, если среза этого момента ещё нет. */
    @Transactional
    public void saveIfNew(MarketOrderBook orderBook) {
        OrderBookSnapshotEntity row = mapper.domainToPersistence(orderBook);
        orderBookRepository.insertIfAbsent(row.getInstrumentId(), row.getExternalTimestamp(),
                row.getObservedTimestamp(), row.getBids(), row.getAsks(), row.getExternalCreatedAt(),
                row.getExternalModifiedAt(), audit.moment(), audit.writer());
    }

    /** Пишет срез цен, если среза этого момента ещё нет. */
    @Transactional
    public void saveIfNew(MarketTicker ticker) {
        TickerSnapshotEntity row = mapper.domainToPersistence(ticker);
        tickerRepository.insertIfAbsent(row.getInstrumentId(), row.getExternalTimestamp(),
                row.getObservedTimestamp(), row.getLastPrice(), row.getVolume(), row.getMarkPrice(),
                row.getIndexPrice(), row.getExternalCreatedAt(), row.getExternalModifiedAt(),
                audit.moment(), audit.writer());
    }

    /** Последний срез книги инструмента. */
    @Transactional(readOnly = true)
    public Optional<MarketOrderBook> findLatestOrderBook(Long instrumentId) {
        return orderBookRepository.findFirstByInstrumentIdOrderByExternalTimestampDesc(instrumentId)
                .map(mapper::persistenceToDomain);
    }

    /** Последний срез цен инструмента. */
    @Transactional(readOnly = true)
    public Optional<MarketTicker> findLatestTicker(Long instrumentId) {
        return tickerRepository.findFirstByInstrumentIdOrderByExternalTimestampDesc(instrumentId)
                .map(mapper::persistenceToDomain);
    }
}

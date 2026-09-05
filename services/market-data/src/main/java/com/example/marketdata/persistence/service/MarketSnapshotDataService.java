package com.example.marketdata.persistence.service;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.marketdata.mapping.MarketSnapshotMapper;
import com.example.marketdata.persistence.model.MarketSnapshotId;
import com.example.marketdata.persistence.repository.OrderBookSnapshotRepository;
import com.example.marketdata.persistence.repository.TickerSnapshotRepository;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketOrderBook;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketTicker;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
 * <p>Вставка идёт {@code persist}: ключ присвоенный, и {@code save} на нём
 * означал бы select перед каждой вставкой — на проходе по всему листингу
 * это удвоение запросов ради проверки, которую уже делает существование
 * строки.
 */
@Service
@RequiredArgsConstructor
public class MarketSnapshotDataService {

    private final OrderBookSnapshotRepository orderBookRepository;
    private final TickerSnapshotRepository tickerRepository;
    private final MarketSnapshotMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    /** Пишет срез книги, если среза этого момента ещё нет. */
    @Transactional
    public void saveIfNew(MarketOrderBook orderBook) {
        MarketSnapshotId key = new MarketSnapshotId(
                orderBook.getInstrumentId(), orderBook.getExternalTimestamp());
        if (isTrue(orderBookRepository.existsById(key))) {
            return;
        }
        entityManager.persist(mapper.domainToPersistence(orderBook));
    }

    /** Пишет срез цен, если среза этого момента ещё нет. */
    @Transactional
    public void saveIfNew(MarketTicker ticker) {
        MarketSnapshotId key = new MarketSnapshotId(
                ticker.getInstrumentId(), ticker.getExternalTimestamp());
        if (isTrue(tickerRepository.existsById(key))) {
            return;
        }
        entityManager.persist(mapper.domainToPersistence(ticker));
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

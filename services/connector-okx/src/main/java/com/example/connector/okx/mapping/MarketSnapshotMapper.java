package com.example.connector.okx.mapping;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.connector.okx.integration.model.okx.response.MarkPriceOkxResponse;
import com.example.connector.okx.integration.model.okx.response.IndexTickerOkxResponse;
import com.example.connector.okx.integration.model.okx.response.OrderBookOkxResponse;
import com.example.connector.okx.integration.model.okx.response.TickerOkxResponse;
import com.example.connector.okx.snapshot.MarketOrderBookExternalSnapshot;
import com.example.connector.okx.snapshot.MarketTickerExternalSnapshot;
import com.example.connector.okx.snapshot.OrderBookLevelExternalSnapshot;
import com.example.connector.okx.util.OkxParse;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketOrderBook;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketTicker;
import com.example.tradingbot.domain.model.trade.market_snapshot.OrderBookLevel;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Переход рыночных срезов: ответ площадки → граничный снапшот → общая
 * модель.
 *
 * <p><b>Уровень книги разбирается вручную, и это не обход MapStruct.</b>
 * Площадка отдаёт уровень **массивом строк** {@code [цена, объём, "0",
 * число заявок]} — позиционной формой, у которой нет имён полей, а
 * MapStruct переносит по именам. Разбор позиции — работа границы, и
 * писать её приходится руками; третий элемент устарел у самой площадки и
 * всегда {@code "0"}, поэтому пропускается, а не хранится.
 *
 * <p><b>Идентификатор инструмента у площадки, числового ключа базы
 * нет.</b> Коннектор базы не имеет и ключ проставить не может; его
 * ставит читатель, когда кладёт срез к себе.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MarketSnapshotMapper {

    /** Индекс цены уровня в позиционном массиве площадки. */
    int LEVEL_PRICE_INDEX = 0;

    /** Индекс объёма уровня. */
    int LEVEL_SIZE_INDEX = 1;

    /** Индекс числа заявок уровня; второй элемент — устаревшее поле площадки. */
    int LEVEL_ORDER_COUNT_INDEX = 3;

    @Mapping(target = "externalInstrumentId", source = "instId")
    @Mapping(target = "lastPrice", source = "last")
    @Mapping(target = "volume", source = "vol24h")
    @Mapping(target = "externalTimestamp", source = "ts")
    MarketTickerExternalSnapshot integrationToSnapshot(TickerOkxResponse response);

    @Mapping(target = "instrumentId", ignore = true)
    @Mapping(target = "observedTimestamp", ignore = true)
    MarketTicker snapshotToDomain(MarketTickerExternalSnapshot snapshot);

    /**
     * Книга: имя инструмента в ответе не приходит — площадка отвечает на
     * запрос по одному инструменту, — поэтому его подставляет вызывающий.
     */
    default MarketOrderBookExternalSnapshot integrationToSnapshot(OrderBookOkxResponse response,
                                                                  String externalInstrumentId) {
        if (isNull(response)) {
            return null;
        }
        MarketOrderBookExternalSnapshot snapshot = new MarketOrderBookExternalSnapshot();
        snapshot.setExternalInstrumentId(externalInstrumentId);
        snapshot.setExternalTimestamp(OkxParse.epochMillis(response.getTs()));
        snapshot.setBids(levels(response.getBids()));
        snapshot.setAsks(levels(response.getAsks()));
        return snapshot;
    }

    default MarketOrderBook snapshotToDomain(MarketOrderBookExternalSnapshot snapshot) {
        if (isNull(snapshot)) {
            return null;
        }
        MarketOrderBook book = new MarketOrderBook();
        book.setExternalTimestamp(snapshot.getExternalTimestamp());
        book.setBids(domainLevels(snapshot.getBids()));
        book.setAsks(domainLevels(snapshot.getAsks()));
        return book;
    }

    /** Цена по инструменту из агрегатного чтения марк-цен. */
    default BigDecimal markPrice(MarkPriceOkxResponse response) {
        return isNull(response) ? null : OkxParse.decimal(response.getMarkPx());
    }

    /** Цена по индексу из агрегатного чтения индексов. */
    default BigDecimal indexPrice(IndexTickerOkxResponse response) {
        return isNull(response) ? null : OkxParse.decimal(response.getIdxPx());
    }

    /**
     * Разбор позиционных уровней.
     *
     * <p>Уровень короче ожидаемого пропускается, а не достраивается
     * пустотами: усечённая строка означает, что форма ответа разошлась с
     * контрактом, и достроенный уровень выглядел бы наблюдением.
     */
    private List<OrderBookLevelExternalSnapshot> levels(List<List<String>> raw) {
        if (isEmpty(raw)) {
            return List.of();
        }
        return raw.stream()
                .filter(level -> level.size() > LEVEL_ORDER_COUNT_INDEX)
                .map(level -> {
                    OrderBookLevelExternalSnapshot parsed = new OrderBookLevelExternalSnapshot();
                    parsed.setPrice(OkxParse.decimal(level.get(LEVEL_PRICE_INDEX)));
                    parsed.setSize(OkxParse.decimal(level.get(LEVEL_SIZE_INDEX)));
                    parsed.setOrderCount(OkxParse.integer(level.get(LEVEL_ORDER_COUNT_INDEX)));
                    return parsed;
                })
                .collect(Collectors.toList());
    }

    private List<OrderBookLevel> domainLevels(List<OrderBookLevelExternalSnapshot> snapshots) {
        if (isEmpty(snapshots)) {
            return List.of();
        }
        return snapshots.stream()
                .map(level -> new OrderBookLevel(level.getPrice(), level.getSize(), level.getOrderCount()))
                .collect(Collectors.toList());
    }
}

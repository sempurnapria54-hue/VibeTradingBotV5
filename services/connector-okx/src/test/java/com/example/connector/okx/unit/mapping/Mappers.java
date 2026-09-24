package com.example.connector.okx.unit.mapping;

import com.example.connector.okx.mapping.AlgoOrderMapperImpl;
import com.example.connector.okx.mapping.BalanceContainerMapperImpl;
import com.example.connector.okx.mapping.CandleMapperImpl;
import com.example.connector.okx.mapping.DealCashFlowMapperImpl;
import com.example.connector.okx.mapping.InstrumentExternalRulesMapperImpl;
import com.example.connector.okx.mapping.InstrumentMapperImpl;
import com.example.connector.okx.mapping.MarketPriceDataMapperImpl;
import com.example.connector.okx.mapping.MarketSnapshotMapperImpl;
import com.example.connector.okx.mapping.OkxResponseConverter;
import com.example.connector.okx.mapping.OrderMapperImpl;
import com.example.connector.okx.mapping.PositionMapperImpl;
import com.example.connector.okx.mapping.TimeFrameMapperImpl;
import com.example.connector.okx.mapping.TradeFeeRateMapperImpl;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Сборка предмета: порождённая реализация маппера плюс <b>настоящий</b>
 * конвертер границы.
 *
 * <p><b>Субстрата нет — ни контейнеров, ни контекста Spring:</b>
 * реализация собирается конструктором, а коллаборатор ставится в поле
 * напрямую. Аннотация {@code @Component} на мапперах есть деталь
 * потребителя, к переводу форм отношения не имеющая.
 *
 * <p><b>Моков нет ни одного, и это следствие, а не строгость:</b>
 * коллаборатор у мапперов один — {@code OkxResponseConverter}, и он сам
 * предмет ({@code U5}, {@code U6}). Подменить его значило бы мерить
 * заглушку ровно там, где живут знаковые конвенции.
 */
final class Mappers {

    private Mappers() {
    }

    static OrderMapperImpl order() {
        return withConverter(new OrderMapperImpl());
    }

    static AlgoOrderMapperImpl algoOrder() {
        return withConverter(new AlgoOrderMapperImpl());
    }

    static PositionMapperImpl position() {
        return withConverter(new PositionMapperImpl());
    }

    static BalanceContainerMapperImpl balance() {
        return withConverter(new BalanceContainerMapperImpl());
    }

    static DealCashFlowMapperImpl cashFlow() {
        return withConverter(new DealCashFlowMapperImpl());
    }

    static CandleMapperImpl candle() {
        return new CandleMapperImpl();
    }

    static InstrumentMapperImpl instrument() {
        return new InstrumentMapperImpl();
    }

    static InstrumentExternalRulesMapperImpl instrumentRules() {
        return new InstrumentExternalRulesMapperImpl();
    }

    static TradeFeeRateMapperImpl tradeFeeRate() {
        return new TradeFeeRateMapperImpl();
    }

    static MarketPriceDataMapperImpl marketPrice() {
        return new MarketPriceDataMapperImpl();
    }

    static MarketSnapshotMapperImpl marketSnapshot() {
        return withConverter(new MarketSnapshotMapperImpl());
    }

    static TimeFrameMapperImpl timeFrame() {
        return new TimeFrameMapperImpl();
    }

    private static <T> T withConverter(T mapper) {
        ReflectionTestUtils.setField(mapper, "okxResponseConverter", new OkxResponseConverter());
        return mapper;
    }
}

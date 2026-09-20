package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.connector.okx.integration.external.api.model.okx.response.TickerOkxResponse;
import com.example.connector.okx.mapping.MarketPriceDataMapper;
import com.example.connector.okx.snapshot.MarketPriceDataExternalSnapshot;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Цена тикера → снапшот и доменная цена — группа `U19` документа
 * `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/MarketPriceData.md §«`TickerOkxResponse` →
 * snapshot»).
 *
 * <p><b>Базовая сборка:</b> {@code TickerOkxResponse} с непустыми типом
 * и именем инструмента, последней ценой, обеими сторонами диапазона,
 * обоими объёмами лучших сторон и временем.
 */
class MarketPriceToSnapshotTest {

    private final MarketPriceDataMapper mapper = Mappers.marketPrice();

    @Test
    @DisplayName("U19.1 — базовая сборка: две строки, пять чисел и момент UTC")
    void u19_1_theBaseAssemblyLandsFieldByField() {
        MarketPriceDataExternalSnapshot snapshot = mapper.integrationToSnapshot(OkxFixture.ticker());

        assertThat(snapshot.getExternalInstrumentType()).isEqualTo("SWAP");
        assertThat(snapshot.getExternalInstrumentId()).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(snapshot.getExternalLastPrice()).isEqualByComparingTo("100.5");
        assertThat(snapshot.getExternalAskPrice()).isEqualByComparingTo("100.6");
        assertThat(snapshot.getExternalBidPrice()).isEqualByComparingTo("100.4");
        assertThat(snapshot.getExternalAskSize()).isEqualByComparingTo("5");
        assertThat(snapshot.getExternalBidSize()).isEqualByComparingTo("7");
        assertThat(snapshot.getExternalTimestamp()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
    }

    /** Тикер их не отдаёт, полей под них не объявлено, и последней они не подменяются. */
    @Test
    @DisplayName("U19.2 — марк-цены и цены индекса в снапшоте нет вовсе")
    void u19_2_markAndIndexPricesAreAbsent() {
        assertThat(MarketPriceDataExternalSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("externalMarkPrice", "externalIndexPrice", "markPrice", "indexPrice");
    }

    /** Середина вычисляется доменно из двух сторон. */
    @Test
    @DisplayName("U19.3 — середины диапазона в снапшоте нет")
    void u19_3_theMidPriceIsNotStored() {
        assertThat(MarketPriceDataExternalSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("midPrice", "externalMidPrice");
        assertThat(mapper.snapshotToDomain(mapper.integrationToSnapshot(OkxFixture.ticker())).midPrice())
                .isEqualByComparingTo("100.5");
    }

    /** Перевод идёт своей формой с проверкой пустоты, а не встроенным преобразованием. */
    @Test
    @DisplayName("U19.4 — пустая последняя цена даёт пустоту")
    void u19_4_anEmptyLastPriceIsEmptiness() {
        TickerOkxResponse response = OkxFixture.ticker();
        response.setLast("");

        assertThat(mapper.integrationToSnapshot(response).getExternalLastPrice()).isNull();
    }

    @Test
    @DisplayName("U19.5 — обрамляющие пробелы цены снимаются")
    void u19_5_aPriceIsTrimmed() {
        TickerOkxResponse response = OkxFixture.ticker();
        response.setAskPx(" 100.5 ");

        assertThat(mapper.integrationToSnapshot(response).getExternalAskPrice())
                .isEqualByComparingTo("100.5");
    }

    @Test
    @DisplayName("U19.6 — пустое время тикера даёт пустоту")
    void u19_6_anEmptyTimeIsEmptiness() {
        TickerOkxResponse response = OkxFixture.ticker();
        response.setTs("");

        assertThat(mapper.integrationToSnapshot(response).getExternalTimestamp()).isNull();
    }

    @Test
    @DisplayName("U19.7 — неразбираемое время тикера отказывает")
    void u19_7_anUnparseableTimeRefuses() {
        TickerOkxResponse response = OkxFixture.ticker();
        response.setTs("abc");

        assertThatThrownBy(() -> mapper.integrationToSnapshot(response))
                .isInstanceOf(NumberFormatException.class);
    }

    /** Суточный объём — предмет среза тикера, а не runtime-цены. */
    @Test
    @DisplayName("U19.8 — суточный объём в этот снапшот не переносится")
    void u19_8_theDailyVolumeDoesNotLandHere() {
        assertThat(MarketPriceDataExternalSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("volume", "externalVolume", "vol24h");
    }

    /** Перегрузку в дереве не зовёт никто (`M-7`), и кейс мерит её, пока она стои́т. */
    @Test
    @DisplayName("U19.9 — материализация с ключом инструмента")
    void u19_9_materializationWithTheInstrumentKey() {
        MarketPriceData price =
                mapper.snapshotToDomain(mapper.integrationToSnapshot(OkxFixture.ticker()), 42L);

        assertThat(price.getInstrumentId()).isEqualTo(42L);
        assertThat(price.getExternalInstrumentId()).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(price.getExternalLastPrice()).isEqualByComparingTo("100.5");
        assertThat(price.getExternalTimestamp()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
    }

    /** Числовой ключ базы границу сервиса не переходит: связь ставит владелец сущности. */
    @Test
    @DisplayName("U19.10 — материализация без ключа: ключ пуст")
    void u19_10_materializationWithoutTheKey() {
        MarketPriceData price =
                mapper.snapshotToDomain(mapper.integrationToSnapshot(OkxFixture.ticker()));

        assertThat(price.getInstrumentId()).isNull();
        assertThat(price.getExternalLastPrice()).isEqualByComparingTo("100.5");
    }

    /** Охрана конъюнктивна (звено `Z1`) только у перегрузки с ключом. */
    @Test
    @DisplayName("U19.11 — пустота у односоставного перехода и у обеих перегрузок материализации")
    void u19_11_emptinessMeetsTwoDifferentGuards() {
        assertThat(mapper.integrationToSnapshot(null)).isNull();
        assertThat(mapper.snapshotToDomain(null)).isNull();

        MarketPriceData withKeyOnly = mapper.snapshotToDomain(null, 42L);

        assertThat(withKeyOnly).isNotNull();
        assertThat(withKeyOnly.getInstrumentId()).isEqualTo(42L);
        assertThat(withKeyOnly.getExternalInstrumentId()).isNull();
        assertThat(withKeyOnly.getExternalLastPrice()).isNull();
        assertThat(withKeyOnly.getExternalTimestamp()).isNull();
    }
}

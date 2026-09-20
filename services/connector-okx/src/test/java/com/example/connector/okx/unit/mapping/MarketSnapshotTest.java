package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.integration.external.api.model.okx.response.IndexTickerOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.MarkPriceOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.OrderBookOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.TickerOkxResponse;
import com.example.connector.okx.mapping.MarketSnapshotMapper;
import com.example.connector.okx.snapshot.MarketOrderBookExternalSnapshot;
import com.example.connector.okx.snapshot.MarketTickerExternalSnapshot;
import com.example.connector.okx.snapshot.OrderBookLevelExternalSnapshot;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketOrderBook;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketTicker;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Рыночные срезы: тикер среза, книга, марк- и индексная цена — группа
 * `U20` документа `.claude/tests/cases/okx-mapping.md`
 * (docs/models/domain/other/MarketTicker.md §Структура;
 * docs/models/domain/other/MarketOrderBook.md).
 *
 * <p><b>Базовая сборка:</b> три разных входа — тикер площадки (та же
 * форма, что у `U19`, но переход другой), книга плюс имя инструмента
 * аргументом, марк-цена и цена индекса.
 *
 * <p><b>Одна форма источника, два разных перехода — и это не дубль:</b>
 * у первой потребитель — расчёт прямо сейчас, у второй — история
 * состояния рынка; состав полей у них разный.
 *
 * <p>Кейсы {@code U20.3}, {@code U20.4} и {@code U20.5} в код не пошли:
 * сквозная конвенция «пустая строка источника — пустота» у этого
 * перехода не держится, перевод идёт встроенным преобразованием и
 * отказывает — `.claude/work/backlog.md` §«Снапшот рыночного среза не
 * держит конвенцию пустой строки источника».
 */
class MarketSnapshotTest {

    private final MarketSnapshotMapper mapper = Mappers.marketSnapshot();

    private static OrderBookOkxResponse book() {
        OrderBookOkxResponse response = new OrderBookOkxResponse();
        response.setTs(OkxFixture.CREATED_MILLIS);
        response.setBids(List.of(List.of("100.0", "7", "0", "2"), List.of("99.9", "8", "0", "4")));
        response.setAsks(List.of(List.of("100.1", "5", "0", "3"), List.of("100.2", "6", "0", "1")));
        return response;
    }

    @Test
    @DisplayName("U20.1 — тикер среза: имя, последняя цена, суточный объём и время эпохой")
    void u20_1_theTickerSliceLandsFieldByField() {
        MarketTickerExternalSnapshot snapshot = mapper.integrationToSnapshot(OkxFixture.ticker());

        assertThat(snapshot.getExternalInstrumentId()).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(snapshot.getLastPrice()).isEqualByComparingTo("100.5");
        assertThat(snapshot.getVolume()).isEqualByComparingTo("3000");
        assertThat(snapshot.getExternalTimestamp()).isEqualTo(1_700_000_000_000L);
    }

    /** Это поля runtime-цены: у среза их нет в составе. */
    @Test
    @DisplayName("U20.2 — четырёх полей диапазона в снапшоте среза нет")
    void u20_2_theSpreadFieldsAreAbsentFromTheSlice() {
        assertThat(MarketTickerExternalSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("externalAskPrice", "externalBidPrice",
                        "externalAskSize", "externalBidSize");
    }

    /** Рабочая тропа зелена — и именно поэтому дефект пустой строки не виден штатным входом. */
    @Test
    @DisplayName("U20.6 — непустые значения разобраны верно")
    void u20_6_theWorkingPathIsGreen() {
        TickerOkxResponse response = OkxFixture.ticker();
        response.setLast("100.5");
        response.setVol24h("3000");
        response.setTs("1700000000000");

        MarketTickerExternalSnapshot snapshot = mapper.integrationToSnapshot(response);

        assertThat(snapshot.getLastPrice()).isEqualByComparingTo("100.5");
        assertThat(snapshot.getVolume()).isEqualByComparingTo("3000");
        assertThat(snapshot.getExternalTimestamp()).isEqualTo(1_700_000_000_000L);
    }

    /** Ключ и момент приёма ставит читатель, кладущий срез к себе. */
    @Test
    @DisplayName("U20.7 — доменный тикер: перенос без ключа и без момента приёма")
    void u20_7_theDomainTickerCarriesNoKeyAndNoObservedMoment() {
        MarketTicker ticker = mapper.snapshotToDomain(mapper.integrationToSnapshot(OkxFixture.ticker()));

        assertThat(ticker.getLastPrice()).isEqualByComparingTo("100.5");
        assertThat(ticker.getVolume()).isEqualByComparingTo("3000");
        assertThat(ticker.getExternalTimestamp()).isEqualTo(1_700_000_000_000L);
        assertThat(ticker.getInstrumentId()).isNull();
        assertThat(ticker.getObservedTimestamp()).isNull();
    }

    /** Они приезжают двумя другими агрегатными чтениями; подстановка последней запрещена. */
    @Test
    @DisplayName("U20.8 — марк-цена и индекс у доменного тикера пусты")
    void u20_8_markAndIndexAreEmptyOnTheDomainTicker() {
        MarketTicker ticker = mapper.snapshotToDomain(mapper.integrationToSnapshot(OkxFixture.ticker()));

        assertThat(ticker.getMarkPrice()).isNull();
        assertThat(ticker.getIndexPrice()).isNull();
    }

    @Test
    @DisplayName("U20.9 — книга: имя аргументом, время эпохой, по два уровня на сторону")
    void u20_9_theBookLandsFieldByField() {
        MarketOrderBookExternalSnapshot snapshot =
                mapper.integrationToSnapshot(book(), OkxFixture.INSTRUMENT);

        assertThat(snapshot.getExternalInstrumentId()).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(snapshot.getExternalTimestamp()).isEqualTo(1_700_000_000_000L);
        assertThat(snapshot.getBids()).hasSize(2);
        assertThat(snapshot.getAsks()).hasSize(2);
        assertThat(snapshot.getBids().getFirst().getPrice()).isEqualByComparingTo("100.0");
        assertThat(snapshot.getBids().getFirst().getSize()).isEqualByComparingTo("7");
        assertThat(snapshot.getBids().getFirst().getOrderCount()).isEqualTo(2);
    }

    /** Третий элемент устарел у самой площадки и хранения не имеет. */
    @Test
    @DisplayName("U20.10 — уровень разбирается позиционно, третий элемент пропущен")
    void u20_10_theThirdElementIsSkipped() {
        OrderBookOkxResponse response = new OrderBookOkxResponse();
        response.setTs(OkxFixture.CREATED_MILLIS);
        response.setAsks(List.of(List.of("100.1", "5", "0", "3")));

        OrderBookLevelExternalSnapshot level =
                mapper.integrationToSnapshot(response, OkxFixture.INSTRUMENT).getAsks().getFirst();

        assertThat(level.getPrice()).isEqualByComparingTo("100.1");
        assertThat(level.getSize()).isEqualByComparingTo("5");
        assertThat(level.getOrderCount()).isEqualTo(3);
        assertThat(OrderBookLevelExternalSnapshot.class.getDeclaredFields()).hasSize(3);
    }

    /** Достроенный уровень выглядел бы наблюдением. */
    @Test
    @DisplayName("U20.11 — усечённый уровень пропускается целиком")
    void u20_11_aTruncatedLevelIsSkippedEntirely() {
        OrderBookOkxResponse response = new OrderBookOkxResponse();
        response.setTs(OkxFixture.CREATED_MILLIS);
        response.setAsks(List.of(List.of("100.1", "5", "0")));

        assertThat(mapper.integrationToSnapshot(response, OkxFixture.INSTRUMENT).getAsks()).isEmpty();
    }

    @Test
    @DisplayName("U20.12 — один усечённый уровень из четырёх: три остаются, порядок сохранён")
    void u20_12_theOtherLevelsSurviveInOrder() {
        OrderBookOkxResponse response = new OrderBookOkxResponse();
        response.setTs(OkxFixture.CREATED_MILLIS);
        response.setAsks(List.of(
                List.of("100.1", "5", "0", "3"),
                List.of("100.2", "6", "0"),
                List.of("100.3", "7", "0", "1"),
                List.of("100.4", "8", "0", "2")));

        assertThat(mapper.integrationToSnapshot(response, OkxFixture.INSTRUMENT).getAsks())
                .extracting(OrderBookLevelExternalSnapshot::getPrice)
                .containsExactly(new java.math.BigDecimal("100.1"),
                        new java.math.BigDecimal("100.3"),
                        new java.math.BigDecimal("100.4"));
    }

    @Test
    @DisplayName("U20.13 — пустой список стороны остаётся пустым списком")
    void u20_13_anEmptySideStaysAnEmptyList() {
        OrderBookOkxResponse response = book();
        response.setBids(List.of());

        assertThat(mapper.integrationToSnapshot(response, OkxFixture.INSTRUMENT).getBids())
                .isNotNull().isEmpty();
    }

    /** Обе формы пустого входа дают один выход, и различить их у книги нечем. */
    @Test
    @DisplayName("U20.14 — отсутствие стороны даёт тот же пустой список")
    void u20_14_anAbsentSideAlsoGivesAnEmptyList() {
        OrderBookOkxResponse response = book();
        response.setBids(null);

        assertThat(mapper.integrationToSnapshot(response, OkxFixture.INSTRUMENT).getBids())
                .isNotNull().isEmpty();
    }

    /** Уровни разбираются формой, держащей конвенцию пустой строки, — в отличие от полей тикера. */
    @Test
    @DisplayName("U20.15 — пустая цена уровня даёт пустоту, объём не затронут")
    void u20_15_anEmptyLevelPriceIsEmptiness() {
        OrderBookOkxResponse response = new OrderBookOkxResponse();
        response.setTs(OkxFixture.CREATED_MILLIS);
        response.setAsks(List.of(List.of("", "5", "0", "3")));

        OrderBookLevelExternalSnapshot level =
                mapper.integrationToSnapshot(response, OkxFixture.INSTRUMENT).getAsks().getFirst();

        assertThat(level.getPrice()).isNull();
        assertThat(level.getSize()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("U20.16 — доменная книга: перенос без ключа, момента приёма и идентификатора")
    void u20_16_theDomainBookCarriesNoKeys() {
        MarketOrderBook domain =
                mapper.snapshotToDomain(mapper.integrationToSnapshot(book(), OkxFixture.INSTRUMENT));

        assertThat(domain.getExternalTimestamp()).isEqualTo(1_700_000_000_000L);
        assertThat(domain.getBids()).hasSize(2);
        assertThat(domain.getAsks()).hasSize(2);
        assertThat(domain.getBids().getFirst().getPrice()).isEqualByComparingTo("100.0");
        assertThat(domain.getInstrumentId()).isNull();
        assertThat(domain.getObservedTimestamp()).isNull();
        assertThat(domain.getId()).isNull();
    }

    /** Охрана написана явно. */
    @Test
    @DisplayName("U20.17 — пустота вместо снапшота книги даёт пустоту")
    void u20_17_emptinessInIsEmptinessOutForTheBook() {
        assertThat(mapper.snapshotToDomain((MarketOrderBookExternalSnapshot) null)).isNull();
        assertThat(mapper.integrationToSnapshot(null, OkxFixture.INSTRUMENT)).isNull();
    }

    @Test
    @DisplayName("U20.18 — марк-цена разбирается числом")
    void u20_18_theMarkPriceIsParsed() {
        MarkPriceOkxResponse response = new MarkPriceOkxResponse();
        response.setInstId(OkxFixture.INSTRUMENT);
        response.setMarkPx("101.2");

        assertThat(mapper.markPrice(response)).isEqualByComparingTo("101.2");
    }

    /** Форма марк-цены разбирает строку формой с проверкой пустоты. */
    @Test
    @DisplayName("U20.19 — пустая марк-цена даёт пустоту")
    void u20_19_anEmptyMarkPriceIsEmptiness() {
        MarkPriceOkxResponse response = new MarkPriceOkxResponse();
        response.setMarkPx("");

        assertThat(mapper.markPrice(response)).isNull();
    }

    @Test
    @DisplayName("U20.20 — пустота вместо формы марк-цены даёт пустоту")
    void u20_20_emptinessInIsEmptinessOutForTheMarkPrice() {
        assertThat(mapper.markPrice(null)).isNull();
    }

    @Test
    @DisplayName("U20.21 — цена индекса: три формы входа симметрично марк-цене")
    void u20_21_theIndexPriceIsSymmetric() {
        IndexTickerOkxResponse present = new IndexTickerOkxResponse();
        present.setIdxPx("99.8");
        IndexTickerOkxResponse empty = new IndexTickerOkxResponse();
        empty.setIdxPx("");

        assertThat(mapper.indexPrice(present)).isEqualByComparingTo("99.8");
        assertThat(mapper.indexPrice(empty)).isNull();
        assertThat(mapper.indexPrice(null)).isNull();
    }
}

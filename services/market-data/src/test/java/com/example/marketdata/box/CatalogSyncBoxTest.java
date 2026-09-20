package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Синк каталога — группа {@code B2} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Тик — вход, и выход его наблюдается ДВУМЯ сторонами:</b> строками
 * каталога и записями стаба коннектора. Половина клеток группы —
 * отрицания об исходящих чтениях («запроса правил после отказа нет»,
 * «запроса по третьему нет»), и их не видно ни в базе, ни в ответе
 * поверхности.
 *
 * <p><b>Листинг и правила идут разным охватом, и это предмет, а не
 * деталь:</b> листинг — агрегатное чтение на тип инструмента, правила
 * площадка отдаёт поинструментно, и полный обход стоил бы сотен запросов
 * из того же бюджета лимитов, что и сбор невосполнимых срезов
 * (docs/components/InstrumentSyncJob.md).
 */
class CatalogSyncBoxTest extends SharedMarketDataBox {

    @Test
    @DisplayName("B2.1 — первый тик зеркалит листинг в каталог")
    void b2_1_theFirstTickMirrorsTheListingIntoTheCatalog() {
        stubListing(INSTRUMENT, SECOND_INSTRUMENT, THIRD_INSTRUMENT);

        Answer answer = tick(Tick.INSTRUMENT_SYNC);

        assertThat(answer.status()).isEqualTo(202);
        assertThat(rows.count("instruments")).isEqualTo(3L);
        assertThat(rows.all("instruments")).allSatisfy(instrument -> {
            assertThat(instrument.get("status")).isEqualTo("SYNC");
            assertThat(instrument.get("internal_id")).isNotNull();
            assertThat(instrument.get("exchange_code")).isEqualTo(MarketDataSubstrate.EXCHANGE_CODE);
            assertThat(instrument.get("external_rules")).isNotNull();
        });
        assertThat(connector.count(ConnectorStub.INSTRUMENTS)).isEqualTo(1);
        assertThat(connector.count(ConnectorStub.rulesOf(INSTRUMENT))).isEqualTo(1);
        assertThat(connector.count(ConnectorStub.rulesOf(SECOND_INSTRUMENT))).isEqualTo(1);
        assertThat(connector.count(ConnectorStub.rulesOf(THIRD_INSTRUMENT))).isEqualTo(1);
    }

    @Test
    @DisplayName("B2.2 — повторный тик вторых строк не заводит и навес не затирает")
    void b2_2_aRepeatedTickCreatesNoSecondRowsAndDoesNotWipeTheOverlay() {
        stubListing(INSTRUMENT, SECOND_INSTRUMENT, THIRD_INSTRUMENT);
        tick(Tick.INSTRUMENT_SYNC);
        Map<String, Object> before = rows.row("instruments", "external_id", INSTRUMENT);
        connector.answers(ConnectorStub.INSTRUMENTS, Feed.array(
                Feed.instrumentWithState(INSTRUMENT, "BTC", "USDT", "suspend"),
                Feed.instrument(SECOND_INSTRUMENT, "ETH", "USDT"),
                Feed.instrument(THIRD_INSTRUMENT, "SOL", "USDT")));

        tick(Tick.INSTRUMENT_SYNC);

        Map<String, Object> after = rows.row("instruments", "external_id", INSTRUMENT);
        assertThat(rows.count("instruments")).isEqualTo(3L);
        assertThat(after.get("internal_id")).isEqualTo(before.get("internal_id"));
        assertThat(after.get("external_status")).isEqualTo("suspend");
        assertThat(after.get("external_rules")).isNotNull();
        assertThat(after.get("status")).isEqualTo("SYNC");
    }

    @Test
    @DisplayName("B2.3 — пустое поле ответа не затирает известного")
    void b2_3_anEmptyResponseFieldDoesNotWipeWhatIsKnown() {
        stubListing(INSTRUMENT);
        tick(Tick.INSTRUMENT_SYNC);
        Map<String, Object> before = rows.row("instruments", "external_id", INSTRUMENT);
        assertThat(before.get("external_settlement_currency")).isNotNull();
        connector.answers(ConnectorStub.INSTRUMENTS, Feed.array(Feed.bareInstrument(INSTRUMENT)));

        tick(Tick.INSTRUMENT_SYNC);

        Map<String, Object> after = rows.row("instruments", "external_id", INSTRUMENT);
        assertThat(after.get("external_settlement_currency"))
                .isEqualTo(before.get("external_settlement_currency"));
        assertThat(after.get("external_base_currency")).isEqualTo(before.get("external_base_currency"));
        assertThat(after.get("external_quote_currency")).isEqualTo(before.get("external_quote_currency"));
    }

    @Test
    @DisplayName("B2.4 — отказ доступа на листинге прекращает тик целиком")
    void b2_4_anAccessRefusalOnTheListingStopsTheWholeTick() {
        provisionInstruments(INSTRUMENT, SECOND_INSTRUMENT);
        Map<String, Long> before = rows.countsByTable();
        connector.answers(ConnectorStub.INSTRUMENTS, 429, Feed.refusal("RATE_LIMITED"));
        Integer mark = AppLog.mark();

        tick(Tick.INSTRUMENT_SYNC);

        assertThat(connector.count(ConnectorStub.rulesOf(INSTRUMENT))).isEqualTo(0);
        assertThat(connector.count(ConnectorStub.rulesOf(SECOND_INSTRUMENT))).isEqualTo(0);
        assertThat(rows.countsByTable()).isEqualTo(before);
        assertThat(AppLog.since(mark)).contains("Instrument sync tick stopped on listing");
    }

    @Test
    @DisplayName("B2.7 — отказ доступа на обходе правил прекращает обход, а сделанное остаётся")
    void b2_7_anAccessRefusalOnTheRulesRoundStopsItAndKeepsWhatIsDone() {
        provisionInstruments(INSTRUMENT, SECOND_INSTRUMENT, THIRD_INSTRUMENT);
        rows.put("update instruments set external_rules = null");
        connector.answers(ConnectorStub.rulesOf(SECOND_INSTRUMENT), 401, Feed.refusal("ACCESS_DENIED"));
        Integer mark = AppLog.mark();

        tick(Tick.INSTRUMENT_SYNC);

        assertThat(rows.row("instruments", "external_id", INSTRUMENT).get("external_rules")).isNotNull();
        assertThat(rows.row("instruments", "external_id", SECOND_INSTRUMENT).get("external_rules")).isNull();
        assertThat(rows.row("instruments", "external_id", THIRD_INSTRUMENT).get("external_rules")).isNull();
        assertThat(connector.count(ConnectorStub.rulesOf(THIRD_INSTRUMENT))).isEqualTo(0);
        assertThat(AppLog.since(mark)).contains("Instrument rules sync stopped");
    }

    @Test
    @DisplayName("B2.9 — перекрывающий запуск пропускается молча")
    void b2_9_anOverlappingTriggerIsSkippedSilently() {
        connector.answersSlowly(ConnectorStub.INSTRUMENTS,
                Feed.array(Feed.instrument(INSTRUMENT, "BTC", "USDT")), 1500);
        connector.answers(ConnectorStub.rulesOf(INSTRUMENT), Feed.rules(INSTRUMENT));

        overlappingTicks(Tick.INSTRUMENT_SYNC);

        assertThat(connector.count(ConnectorStub.INSTRUMENTS)).isEqualTo(1);
        assertThat(rows.count("instruments")).isEqualTo(1L);
    }

    /**
     * Ожидание взято из дома: валюты инструмента синк не пишет — они
     * добываются один раз при заведении и подтверждения не требуют
     * (docs/components/InstrumentSyncJob.md §Границы;
     * docs/lifecycles/Instrument.md §«Переходы и триггеры»).
     *
     * <p>Сегодня маппер обновления из листинга их ПЕРЕПИСЫВАЕТ при каждом
     * тике, когда ответ их несёт: находка {@code F-2}, долг —
     * `.claude/work/backlog.md` §«Клейм о том, что синк валют не пишет,
     * опровергнут маппером обновления из листинга». Расхождение разрешимо
     * в обе стороны, и выбор принадлежит владельцу предмета.
     */
    @Test
    @Tag("debt")
    @DisplayName("B2.10 — валюты инструмента синк не пишет")
    void b2_10_theSyncDoesNotWriteInstrumentCurrencies() {
        stubListing(INSTRUMENT);
        tick(Tick.INSTRUMENT_SYNC);
        Map<String, Object> before = rows.row("instruments", "external_id", INSTRUMENT);
        connector.answers(ConnectorStub.INSTRUMENTS, Feed.array(
                Feed.instrument(INSTRUMENT, "WBTC", "USDC")));

        tick(Tick.INSTRUMENT_SYNC);

        Map<String, Object> after = rows.row("instruments", "external_id", INSTRUMENT);
        assertThat(after.get("external_base_currency")).isEqualTo(before.get("external_base_currency"));
        assertThat(after.get("external_quote_currency")).isEqualTo(before.get("external_quote_currency"));
        assertThat(after.get("external_settlement_currency"))
                .isEqualTo(before.get("external_settlement_currency"));
    }

    private void stubListing(String... externalIds) {
        String[] listed = new String[externalIds.length];
        for (int index = 0; index < externalIds.length; index++) {
            listed[index] = Feed.instrument(externalIds[index], "BASE" + index, "USDT");
            connector.answers(ConnectorStub.rulesOf(externalIds[index]), Feed.rules(externalIds[index]));
        }
        connector.answers(ConnectorStub.INSTRUMENTS, Feed.array(listed));
    }
}

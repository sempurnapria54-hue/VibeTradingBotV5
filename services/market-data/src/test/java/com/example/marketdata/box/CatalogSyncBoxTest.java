package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;

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

    /** Субъект служебного токена, под которым ящик подаёт ручные тики. */
    private static final String PRINCIPAL = "service-account-vibetrading";

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
     * Писатель валют инструмента — тик синка каталога: при заведении и на
     * каждом следующем сведении с листингом непустое значение ответа
     * переносится на строку (docs/models/domain/core/Instrument.md §«Валюты
     * инструмента»). Сменённая площадкой валюта доходит до каталога следующим
     * тиком, а не остаётся устаревшей навсегда; обратная сторона — пустота
     * ответа известного не стирает — клетка {@code B2.3}.
     *
     * <p>Предусловие пинит валюты заведения, иначе равенство после тика было
     * бы зелено и у синка, валют не пишущего вовсе: заведение и сведение
     * разведены значениями.
     */
    @Test
    @DisplayName("B2.10 — сведение с листингом переписывает валюты инструмента")
    void b2_10_theSyncRewritesInstrumentCurrenciesFromTheListing() {
        connector.answers(ConnectorStub.rulesOf(INSTRUMENT), Feed.rules(INSTRUMENT));
        connector.answers(ConnectorStub.INSTRUMENTS, Feed.array(Feed.instrument(INSTRUMENT, "BTC", "USDT")));
        tick(Tick.INSTRUMENT_SYNC);
        Map<String, Object> before = rows.row("instruments", "external_id", INSTRUMENT);
        assertThat(before.get("external_base_currency"))
                .as("B2.10: предусловие — инструмент заведён с валютами BTC/USDT/USDT")
                .isEqualTo("BTC");
        assertThat(before.get("external_quote_currency")).isEqualTo("USDT");
        assertThat(before.get("external_settlement_currency")).isEqualTo("USDT");
        connector.answers(ConnectorStub.INSTRUMENTS, Feed.array(Feed.instrument(INSTRUMENT, "WBTC", "USDC")));

        tick(Tick.INSTRUMENT_SYNC);

        Map<String, Object> after = rows.row("instruments", "external_id", INSTRUMENT);
        assertThat(after.get("external_base_currency"))
                .as("B2.10: вход — листинг с валютами WBTC/USDC/USDC; валюты строки равны валютам ответа")
                .isEqualTo("WBTC");
        assertThat(after.get("external_quote_currency")).isEqualTo("USDC");
        assertThat(after.get("external_settlement_currency")).isEqualTo("USDC");
    }

    /**
     * Актор строки — тот, кто инициировал ход, а не сервис, который её
     * записал (docs/models/domain/other/Auditable.md §«Область значений
     * актора»). Ручной тик порождён предъявленным принципалом, а работа идёт
     * в треде асинхронного фасада: без переноса контекста строка получила бы
     * класс контура молча, а прежде — имя сервиса, которого область не знает.
     */
    @Test
    @DisplayName("B2.11 — строка, заведённая ручным тиком, несёт имя предъявленного принципала")
    void b2_11_aRowMadeByAManualTickCarriesThePresentedPrincipal() {
        stubListing(INSTRUMENT);

        tick(Tick.INSTRUMENT_SYNC);

        Map<String, Object> row = rows.row("instruments", "external_id", INSTRUMENT);
        assertThat(row.get("created_by")).isEqualTo(PRINCIPAL);
        assertThat(row.get("modified_by")).isEqualTo(PRINCIPAL);
    }

    /**
     * Позиционные тиры приезжают от коннектора в составе правил и ложатся
     * в навес той же записью (docs/components/InstrumentSyncJob.md
     * §«Листинг и правила идут разным охватом»); наружу уходят формой
     * поверхности — её читает проекция ядра
     * (docs/models/domain/other/InstrumentExternalRules.md). Правила без
     * тиров ключа тиров в строке навеса не несут: пустота остаётся
     * пустотой, а не пустым перечнем.
     */
    @Test
    @DisplayName("B2.12 — позиционные тиры правил ложатся в навес и уходят поверхностью")
    void b2_12_positionTiersOfTheRulesLandInTheOverlayAndLeaveThroughTheSurface() {
        connector.answers(ConnectorStub.rulesOf(INSTRUMENT), Feed.rulesWithTiers(INSTRUMENT));
        connector.answers(ConnectorStub.rulesOf(SECOND_INSTRUMENT), Feed.rules(SECOND_INSTRUMENT));
        connector.answers(ConnectorStub.INSTRUMENTS, Feed.array(
                Feed.instrument(INSTRUMENT, "BTC", "USDT"),
                Feed.instrument(SECOND_INSTRUMENT, "ETH", "USDT")));

        tick(Tick.INSTRUMENT_SYNC);

        List<Map<String, Object>> stored = tiersOf(overlayOf(INSTRUMENT));
        assertThat(stored).as("B2.12: два тира ответа легли в навес").hasSize(2);
        assertThat(decimal(stored.get(0).get("minSize"))).isEqualByComparingTo("0");
        assertThat(decimal(stored.get(0).get("maxSize"))).isEqualByComparingTo("1000");
        assertThat(decimal(stored.get(0).get("maintenanceMarginRate"))).isEqualByComparingTo("0.004");
        assertThat(decimal(stored.get(1).get("minSize"))).isEqualByComparingTo("1000");
        assertThat(decimal(stored.get(1).get("maxSize"))).isEqualByComparingTo("5000");
        assertThat(decimal(stored.get(1).get("maintenanceMarginRate"))).isEqualByComparingTo("0.006");
        assertThat(overlayOf(SECOND_INSTRUMENT))
                .as("B2.12: правила без тиров ключа тиров в строке навеса не несут")
                .containsKey("externalTickSize")
                .doesNotContainKey("positionTiers");

        String internalId = String.valueOf(rows.row("instruments", "external_id", INSTRUMENT).get("internal_id"));
        Answer surface = get(INSTRUMENTS + "/" + internalId + "/rules");

        assertThat(surface.status()).isEqualTo(200);
        List<Map<String, Object>> served = tiersOf(surface.asObject());
        assertThat(served).as("B2.12: поверхность отдаёт тиры навеса").hasSize(2);
        assertThat(decimal(served.get(1).get("maintenanceMarginRate"))).isEqualByComparingTo("0.006");
    }

    /** Строка навеса правил инструмента как объект. */
    private Map<String, Object> overlayOf(String externalId) {
        Object overlay = rows.row("instruments", "external_id", externalId).get("external_rules");
        assertThat(overlay).as("навес правил %s материализован", externalId).isNotNull();
        return JsonParserFactory.getJsonParser().parseMap(String.valueOf(overlay));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> tiersOf(Map<String, Object> rules) {
        return (List<Map<String, Object>>) rules.get("positionTiers");
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value));
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

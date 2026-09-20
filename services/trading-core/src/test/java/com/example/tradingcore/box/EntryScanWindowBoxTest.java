package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Окно выборки инструментов у отбора входа — клетка {@code B1.12}.
 *
 * <p><b>Своя конфигурация контекста, и это ВХОД клетки:</b> предусловие
 * требует каталога БОЛЬШЕ окна, а окно приезжает конфигурацией. Ставить
 * его общему ящику значило бы менять предмет всем его соседкам, а заводить
 * две сотни инструментов проекцией — платить временем за то, что задаётся
 * одной осью.
 *
 * <p><b>Клетка предъявляет НАЗВАННОЕ ограничение, а не дефект:</b> окно
 * обязано быть шире каталога контура, и направление калибровки объявлено
 * самим ключом ({@code EntryScannerProperties#instrumentWindow},
 * {@code application.yaml}). Здесь окно сужено намеренно — иначе упор в
 * него не наблюдался бы ничем.
 *
 * <p><b>Половина «расширение окна вход открывает» прогоняется соседней
 * клеткой, и носитель у неё назван.</b> Определение здесь той же формы,
 * что у {@code B1.1} ({@link EntryScanBoxTest}), а положение всех прочих
 * осей — то же штатное; единственное различие двух клеток есть величина
 * окна, и потому зелёная {@code B1.1} и есть вторая половина этого
 * ожидания. Прогнать обе половины одним контекстом нечем: окно читается
 * при подъёме.
 *
 * <p><b>Своя группа потребителя и своя тема владельца определений</b> —
 * довод у шапки {@link TradingCoreSubstrate}. Здесь он несущий: предикат
 * клетки читает СОСТАВ копий определений выборкой пары, и определение,
 * положенное в общую тему соседним классом прогона, приехало бы сюда
 * чужой активной стратегией на первом же инструменте каталога — ровно
 * там, где клетка утверждает, что обход ничего не нашёл.
 */
class EntryScanWindowBoxTest extends TradingCoreBox {

    /** Окно выборки: каталог клетки будет шире него. */
    private static final String WINDOW = "1";

    /** Определение клетки: оно стои́т на инструменте ЗА окном. */
    private static final String DEFINITION = "S1";

    /** Имя одиночки: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-entry-scan-window";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        TradingCoreSubstrate.registerOwn(registry, NAME, Map.of(
                TradingCoreSubstrate.ENTRY_WINDOW_KEY, WINDOW));
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }

    @Test
    @DisplayName("B1.12 — окно выборки инструментов ограничено, и упор в него молчалив")
    void anInstrumentBeyondTheWindowIsSkippedSilently() {
        Map<String, String> catalogue = new LinkedHashMap<>();
        catalogue.put(INSTRUMENT, EXTERNAL_INSTRUMENT);
        catalogue.put(SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT);
        provision(List.of(ACCOUNT), catalogue);
        // Порядок обхода — по идентификатору строки каталога, и второй
        // инструмент лежит ЗА окном в один инструмент.
        assertThat(instrumentId(INSTRUMENT)).isLessThan(instrumentId(SECOND_INSTRUMENT));
        marketData.answers(featuresPath(SECOND_INSTRUMENT),
                Feed.features(MarketPhase.Type.BULL_TREND.name()));
        connector.answers(PEER_SERVER_TIME, Feed.serverTime("2026-09-20T10:00:00Z"));
        activate(Definitions.withEntryOnPhase(DEFINITION, ACCOUNT, SECOND_INSTRUMENT,
                MarketPhase.Type.BULL_TREND, MarketPhase.Type.BULL_TREND));
        Integer mark = AppLog.mark();

        tick(Tick.ENTRY_SCANNER);

        // Сделки нет, и отказа нет: упор в окно МОЛЧАЛИВ — ни строки, ни
        // записи журнала он не оставляет, и пропущенный вход виден только
        // тому, кто окно с каталогом сравнил.
        assertThat(rows.count("deals")).isZero();
        assertThat(AppLog.since(mark)).doesNotContain("Entry scan failed");
        // Инструмент за окном не читался вовсе: обход оборвался раньше.
        assertThat(marketData.count(featuresPath(SECOND_INSTRUMENT))).isZero();
        // Между определением и входом не стои́т ничего, кроме окна: копия
        // активна, пара в каталоге, сделок у счёта нет.
        assertThat(rows.row("strategies", "internal_id", DEFINITION).get("status"))
                .isEqualTo("ACTIVE");
        assertThat(rows.count("instruments")).isEqualTo(2L);
    }

    /** Путь чтения фич момента у владельца рыночных данных. */
    private String featuresPath(String instrumentInternalId) {
        return PEER_INSTRUMENTS + "/" + instrumentInternalId + "/features";
    }
}

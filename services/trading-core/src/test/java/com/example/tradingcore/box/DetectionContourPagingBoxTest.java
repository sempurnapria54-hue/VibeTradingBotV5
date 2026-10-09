package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Постраничный обход контура у проактивной детекции — клетка {@code B7.12}.
 *
 * <p><b>Своя конфигурация контекста, и это ВХОД клетки:</b> предусловие
 * требует контура ШИРЕ страницы, а размер страницы приезжает конфигурацией.
 * Ставить его общему ящику значило бы менять предмет всем его соседкам, а
 * заводить пять сотен инструментов проекцией — платить временем за то, что
 * задаётся одной осью.
 *
 * <p><b>Размер каталога неполноты прохода не производит.</b> Страница —
 * единица чтения, а не предел выборки: проход читает страницы до последней,
 * и контур есть весь каталог площадки (docs/components/AnomalyJob.md, обход
 * контура). Прежде упор выборки в окно засчитывался неполнотой, и на полном
 * каталоге площадки — ровно пятьсот строк при окне в пятьсот — каждый проход
 * был ненаблюдён, а отбор входа пропускал счёт.
 *
 * <p><b>Предмет мерится инструментом ПОСЛЕДНЕЙ страницы.</b> Живая позиция
 * стои́т на инструменте с наибольшим ключом проекции, то есть на странице,
 * до которой обход, остановленный первой, не дошёл бы: тогда её строка среза
 * читалась бы чужой, а не восстановленной. Инструмент берётся из базы по
 * ключу, а не по порядку перечня каталога: порядок вставки проекции —
 * деталь синка, и клетка на неё не опирается.
 *
 * <p><b>Своя группа потребителя и своя тема владельца определений</b> —
 * довод у шапки {@link TradingCoreSubstrate}.
 */
class DetectionContourPagingBoxTest extends TradingCoreBox {

    /** Размер страницы обхода: каталог клетки шире него. */
    private static final String PAGE_SIZE = "2";

    /** Имя одиночки: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-detection-contour-paging";

    /** Третий инструмент каталога: с ним контур не помещается в страницу. */
    private static final String THIRD_INSTRUMENT = "I3";

    /** Биржевое имя третьего инструмента. */
    private static final String THIRD_EXTERNAL_INSTRUMENT = "XRP-USDT-SWAP";

    /** Отсутствие ступени: рабочее состояние радиуса. */
    private static final String NO_RUNG = "ACTIVE";

    /** Причина входа восстановительной сделки. */
    private static final String RECOVERY = "RECOVERY";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        TradingCoreSubstrate.registerOwn(registry, NAME, Map.of(
                TradingCoreSubstrate.CONTOUR_PAGE_SIZE_KEY, PAGE_SIZE));
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }

    @Test
    @DisplayName("B7.12 — контур шире страницы обходится целиком: проход полон, инструмент последней страницы виден")
    void aContourWiderThanAPageIsWalkedWhole() {
        Map<String, String> catalogue = new LinkedHashMap<>();
        catalogue.put(INSTRUMENT, EXTERNAL_INSTRUMENT);
        catalogue.put(SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT);
        catalogue.put(THIRD_INSTRUMENT, THIRD_EXTERNAL_INSTRUMENT);
        provision(List.of(ACCOUNT), catalogue);
        assertThat(rows.count("instruments")).isEqualTo(3L);
        // Момент наблюдённого прохода снят: его ставит только полный проход.
        rows.put("update exchange_accounts set observed_pass_at = null where internal_id = ?", ACCOUNT);
        Map<String, Object> lastOfContour = rows.allOrderedBy("instruments", "id").getLast();
        connector.answers(positionsPath(ACCOUNT), Feed.array(Feed.livePosition("ex-live-1",
                String.valueOf(lastOfContour.get("external_id")), "1", "100", "2026-09-20T10:00:05Z")));
        connector.answers(pendingOrdersPath(ACCOUNT), Feed.emptyArray());
        connector.answers(pendingAlgoOrdersPath(ACCOUNT), Feed.emptyArray());

        tick(Tick.ANOMALY_DETECTION);

        // Проход наблюдён: отчёта о неполноте нет, счёт слепоты на нуле,
        // момент прохода записан — отбор входа счёт не пропустит.
        assertThat(rows.count("anomaly_reports")).isZero();
        assertThat(blindPasses()).isZero();
        assertThat(accountRow().get("observed_pass_at")).isNotNull();
        // Инструмент последней страницы — в контуре: его живая позиция не
        // объявлена чужой, а принята в модель восстановительной сделкой.
        assertThat(rows.count("deals")).isEqualTo(1L);
        Map<String, Object> deal = rows.all("deals").getFirst();
        assertThat(deal.get("entry_reason")).isEqualTo(RECOVERY);
        assertThat(((Number) deal.get("instrument_id")).longValue())
                .isEqualTo(((Number) lastOfContour.get("id")).longValue());
        assertThat(accountRung()).isEqualTo(NO_RUNG);
    }

    /** Строка биржевого счёта, как её видит база. */
    private Map<String, Object> accountRow() {
        return rows.row("exchange_accounts", "internal_id", ACCOUNT);
    }

    /** Ступень счёта, как её видит база. */
    private String accountRung() {
        return String.valueOf(accountRow().get("safety_rung"));
    }

    /** Счёт подряд идущих ненаблюдённых проходов на строке счёта. */
    private Integer blindPasses() {
        return ((Number) accountRow().get("blind_pass_count")).intValue();
    }

    /** Корень путей счёта у коннектора. */
    private String accountPath(String accountInternalId) {
        return "/api/v1/accounts/" + accountInternalId;
    }

    /** Живые позиции счёта целиком: первый срез проактивной детекции. */
    private String positionsPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions";
    }

    /** Живые заявки счёта целиком: второй срез. */
    private String pendingOrdersPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders/pending";
    }

    /** Живые отдельные условные заявки счёта целиком: третий срез. */
    private String pendingAlgoOrdersPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/algo-orders/pending";
    }
}

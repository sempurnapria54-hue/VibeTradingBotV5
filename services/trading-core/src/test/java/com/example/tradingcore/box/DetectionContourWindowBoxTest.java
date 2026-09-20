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
 * Окно выборки контура у проактивной детекции — клетка {@code B7.12}.
 *
 * <p><b>Своя конфигурация контекста, и это ВХОД клетки:</b> предусловие
 * требует контура ШИРЕ окна, а окно приезжает конфигурацией. Ставить его
 * общему ящику значило бы менять предмет всем его соседкам, а заводить
 * пять сотен инструментов проекцией — платить временем за то, что
 * задаётся одной осью.
 *
 * <p><b>Клетка предъявляет НАЗВАННОЕ ограничение, а не дефект:</b> окно
 * обязано быть шире контура, и направление калибровки объявлено самим
 * ключом ({@code AnomalyJobProperties#contourWindow}). Здесь окно сужено
 * намеренно — иначе упор в него не наблюдался бы ничем.
 *
 * <p><b>Упор в окно засчитывается НЕПОЛНОТОЙ прохода — тем же ходом, что
 * и неполученный срез.</b> Обход по усечённому контуру объявил бы чужими
 * строки среза, которым не хватило места в выборке, то есть сносил бы
 * счёт по жёсткой ступени за наш собственный инструмент
 * (docs/components/AnomalyJob.md §«Гейт полноты среза»).
 *
 * <p><b>Вторая половина «окно шире контура — проход полон» прогоняется
 * соседним классом, и носитель у неё назван:</b> у
 * {@link ProactiveDetectionBoxTest} то же предусловие стои́т при штатном
 * окне, и его клетки наблюдают сработавшие детекторы. Прогнать обе
 * половины одним контекстом нечем: окно читается при подъёме.
 *
 * <p><b>Своя группа потребителя и своя тема владельца определений</b> —
 * довод у шапки {@link TradingCoreSubstrate}.
 */
class DetectionContourWindowBoxTest extends TradingCoreBox {

    /** Окно выборки контура: каталог клетки будет шире него. */
    private static final String WINDOW = "1";

    /** Имя одиночки: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-detection-contour-window";

    /** Отсутствие ступени: рабочее состояние радиуса. */
    private static final String NO_RUNG = "ACTIVE";

    /** Код ненаблюдённого прохода. */
    private static final String PASS_INCOMPLETE = "ANOMALY_PASS_INCOMPLETE";

    /** Биржевое имя инструмента, строки которого в каталоге ядра нет вовсе. */
    private static final String FOREIGN_INSTRUMENT = "SOL-USDT-SWAP";

    /** Код живого риска по инструменту вне контура. */
    private static final String FOREIGN_INSTRUMENT_RISK = "EXCHANGE_FOREIGN_INSTRUMENT_RISK";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        TradingCoreSubstrate.registerOwn(registry, NAME, Map.of(
                TradingCoreSubstrate.CONTOUR_WINDOW_KEY, WINDOW));
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }

    @Test
    @DisplayName("B7.12 — упор в окно обхода контура засчитывается неполнотой прохода")
    void theContourWindowHitCountsAsAnIncompletePass() {
        Map<String, String> catalogue = new LinkedHashMap<>();
        catalogue.put(INSTRUMENT, EXTERNAL_INSTRUMENT);
        catalogue.put(SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT);
        provision(List.of(ACCOUNT), catalogue);
        assertThat(rows.count("instruments")).isEqualTo(2L);
        // Срез добыт ЦЕЛИКОМ, и в нём лежит признак, срабатывающий с
        // первого наблюдения: молчание прочих детекторов производит ровно
        // упор в окно, а не недобытая выборка.
        connector.answers(positionsPath(ACCOUNT), Feed.array(Feed.livePosition("ex-live-1",
                FOREIGN_INSTRUMENT, "1", "100", "2026-09-20T10:00:05Z")));
        connector.answers(pendingOrdersPath(ACCOUNT), Feed.emptyArray());
        connector.answers(pendingAlgoOrdersPath(ACCOUNT), Feed.emptyArray());

        tick(Tick.ANOMALY_DETECTION);

        // Проход помечен неполным: отчёт свой, счёт слепоты сдвинут.
        assertThat(codesOfReports()).containsExactly(PASS_INCOMPLETE);
        assertThat(blindPasses()).isEqualTo(1);
        // Прочие детекторы молчат, и ступени нет ни на одном радиусе.
        assertThat(codesOfReports()).doesNotContain(FOREIGN_INSTRUMENT_RISK);
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        assertThat(rows.count("account_instrument_states")).isZero();
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

    /** Машинные коды заведённых отчётов в порядке записи. */
    private List<String> codesOfReports() {
        return rows.allOrderedBy("anomaly_reports", "id").stream()
                .map(row -> String.valueOf(row.get("code")))
                .toList();
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

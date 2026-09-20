package com.example.strategies.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Группа {@code B2} документа кейсов: перечень определений тенанта
 * (.claude/tests/cases/strategies.md).
 *
 * <p><b>Свой контекст, и основание названо: окно перечня есть ВХОД.</b>
 * Клетка {@code B2.1} утверждает об усечении, а усечение при
 * умолчательном окне в две сотни наблюдалось бы только заведением двух
 * сотен определений — то есть предмет клетки подменился бы стоимостью
 * прогона. Окно сужено конфигурацией, и потому класс платит своим
 * подъёмом (.claude/decisions/test-contour-design-pass.md).
 *
 * <p><b>Окно приезжает конфигурацией, а не литералом</b>, и это предмет
 * самой клетки {@code B9.3}: заданное здесь значение наблюдается числом
 * отданных элементов.
 */
class DefinitionListBoxTest extends StrategiesBox {

    /** Окно перечня этого контекста: им наблюдается усечение. */
    private static final Integer WINDOW = 2;

    /**
     * Умолчание окна, объявленное сервисом
     * ({@code SurfaceProperties#strategyListWindow}): с ним сверяется
     * то, что поданное конфигурацией окно и есть действующее.
     */
    private static final Integer DEFAULT_WINDOW = 200;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StrategiesSubstrate.register(registry,
                Map.of(StrategiesSubstrate.LIST_WINDOW_KEY, String.valueOf(WINDOW)));
    }

    @Test
    @DisplayName("B2.1 — Перечень отдаётся окном от новых к старым")
    void b2_1_theListIsGivenAsAWindowFromNewToOld() {
        peerResolvesEverything();
        String oldest = given(TENANT);
        String middle = given(TENANT);
        String newest = given(TENANT);

        Answer answer = get(STRATEGIES, TENANT);

        assertThat(answer.status()).isEqualTo(200);
        List<Map<String, Object>> listed = answer.asList();
        assertThat(listed).as("элементов ровно столько, каково окно").hasSize(WINDOW);
        assertThat(listed.stream().map(item -> item.get("internalId")).toList())
                .as("это последние заведённые, порядок — от новых к старым")
                .containsExactly(newest, middle);
        assertThat(answer.body())
                .as("самое старое отрезано окном")
                .doesNotContain(oldest);
    }

    /**
     * Клетка отличается от {@code B2.1} предметом, а не входом: там
     * предмет — что перечень ОТДАЁТСЯ окном от новых к старым, здесь —
     * что само окно приезжает конфигурацией, а не литералом. Литерал
     * отдал бы умолчание в две сотни при любом положении оси, и обе
     * клетки на одном входе расходятся ровно этим ассертом.
     */
    @Test
    @DisplayName("B9.3 — Окно перечня приходит конфигурацией, а не литералом")
    void b9_3_theListWindowComesFromConfigurationRatherThanFromALiteral() {
        peerResolvesEverything();
        given(TENANT);
        given(TENANT);
        given(TENANT);

        Answer answer = get(STRATEGIES, TENANT);

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asList())
                .as("размер ответа равен НОВОМУ окну, а не умолчанию: сборки для перекалибровки не нужно")
                .hasSize(WINDOW);
        assertThat(WINDOW)
                .as("а новое окно отличается от умолчания — иначе клетка мерила бы совпадение")
                .isNotEqualTo(DEFAULT_WINDOW);
    }

    @Test
    @DisplayName("B2.2 — Радиус перечня — тенант заголовка")
    void b2_2_theListRadiusIsTheHeaderTenant() {
        peerResolvesEverything();
        given(TENANT);
        given(TENANT);
        String foreign = given(SECOND_TENANT);

        Answer answer = get(STRATEGIES, TENANT);

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asList()).hasSize(2);
        assertThat(answer.body())
                .as("определение чужого тенанта не попало ни одним полем")
                .doesNotContain(foreign);
    }

    @Test
    @DisplayName("B2.3 — Пустой перечень — пустой ответ, а не отказ")
    void b2_3_anEmptyListIsAnEmptyAnswerNotAFailure() {
        peerResolvesEverything();
        String unknownTenant = "T9";

        Answer answer = get(STRATEGIES, unknownTenant);

        assertThat(answer.status())
                .as("отсутствие строк не есть отсутствие адресата: тенантов сервис не держит")
                .isEqualTo(200);
        assertThat(answer.asList()).isEmpty();
        assertThat(peer.count()).as("к ядру перечень не ходит вовсе").isZero();
    }

    @Test
    @DisplayName("B2.4 — Перечень дерева не отдаёт")
    void b2_4_theListCarriesNoTree() {
        peerResolvesEverything();
        given(TENANT);

        Answer answer = get(STRATEGIES, TENANT);

        Map<String, Object> listed = answer.single();
        assertThat(listed.get("internalId")).isNotNull();
        assertThat(listed.get("name")).isNotNull();
        assertThat(listed).containsEntry("status", "CREATED");
        assertThat(listed.get("exchangeAccountInternalId")).isNotNull();
        assertThat(listed.get("instrumentInternalId")).isNotNull();
        assertThat(listed.get("details"))
                .as("за деревом ходят точкой одного определения, а не перечнем")
                .isNull();
        assertThat(listed.get("indicatorSettings")).isNull();
        assertThat(listed.get("marketStructureSettings")).isNull();
        assertThat(listed.get("marketPhaseSetting")).isNull();
    }
}

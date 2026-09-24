package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.integration.external.api.model.okx.response.AlgoOrderOkxResponse;
import com.example.connector.okx.mapping.OrderMapper;
import com.example.connector.okx.snapshot.AttachedAlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Самостоятельная условная заявка → снапшот встроенной защиты — группа
 * `U9` документа `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/Order.md §«`AlgoOrderOkxResponse` →
 * `AttachedAlgoOrderExternalSnapshot`»).
 *
 * <p><b>Базовая сборка:</b> {@code AlgoOrderOkxResponse} — запись, в
 * которую источник развернул встроенную защиту: клиентский
 * идентификатор равен идентификатору защиты родителя, биржевой —
 * <b>свой</b>, статус непуст.
 *
 * <p><b>Это вторая форма ОДНОГО снапшота, и различие несущее:</b> та же
 * доменная строка приезжает двумя разными путями — из тела родителя
 * ({@code U8}) и записью цикла добычи материализованной защиты (здесь).
 * У второй формы <b>есть</b> свой статус, у первой его нет; связь с
 * родителем держится только клиентским идентификатором.
 */
class AttachedFromStandaloneAlgoTest {

    private final OrderMapper mapper = Mappers.order();

    @Test
    @DisplayName("U9.1 — базовая сборка: свой идентификатор, свой статус, размер и уровень")
    void u9_1_theBaseAssemblyLandsFieldByField() {
        AttachedAlgoOrderExternalSnapshot snapshot = mapper.integrationToSnapshot(OkxFixture.algoOrder());

        assertThat(snapshot.getInternalId()).isEqualTo("tb-9");
        assertThat(snapshot.getExternalId()).isEqualTo("9");
        assertThat(snapshot.getExternalStatus()).isEqualTo("live");
        assertThat(snapshot.getSize()).isEqualByComparingTo("100");
        assertThat(snapshot.getStopLossTriggerPrice()).isEqualByComparingTo("90");
        assertThat(snapshot.getTriggerPriceType()).isEqualTo(AlgoOrder.TriggerPriceType.MARK);
    }

    /** Обратной ссылки на родителя у записи нет. */
    @Test
    @DisplayName("U9.2 — идентификатора защиты в теле родителя у записи нет")
    void u9_2_theRecordCarriesNoBackReferenceToTheParent() {
        assertThat(mapper.integrationToSnapshot(OkxFixture.algoOrder()).getExternalAttachedId()).isNull();
    }

    @Test
    @DisplayName("U9.3 — биржевого типа защиты у самостоятельной записи нет")
    void u9_3_theStandaloneRecordCarriesNoProtectionKind() {
        assertThat(mapper.integrationToSnapshot(OkxFixture.algoOrder()).getExternalType()).isNull();
        assertThat(AlgoOrderOkxResponse.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("tpOrdKind");
    }

    /** Исход кодирует нога, нашедшая запись: через резолвер статусов не идёт. */
    @Test
    @DisplayName("U9.4 — сработавший статус приземляется строкой")
    void u9_4_theTriggeredStatusLandsAsAString() {
        AlgoOrderOkxResponse response = OkxFixture.algoOrder();
        response.setState("effective");

        assertThat(mapper.integrationToSnapshot(response).getExternalStatus()).isEqualTo("effective");
    }

    @Test
    @DisplayName("U9.5 — отказной статус и его код переносятся оба, отказа нет")
    void u9_5_aFailedStatusAndItsCodeAreBothCarried() {
        AlgoOrderOkxResponse response = OkxFixture.algoOrder();
        response.setState("order_failed");
        response.setFailCode("51004");

        AttachedAlgoOrderExternalSnapshot snapshot = mapper.integrationToSnapshot(response);

        assertThat(snapshot.getExternalStatus()).isEqualTo("order_failed");
        assertThat(snapshot.getFailCode()).isEqualTo("51004");
    }

    /** Цена самой заявки защиты доменной величины не имеет — защита ставится рыночной. */
    @Test
    @DisplayName("U9.6 — цены исполнения защиты у снапшота нет вовсе")
    void u9_6_theProtectionOrderPriceHasNoFieldAtAll() {
        assertThat(AttachedAlgoOrderExternalSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("slOrdPx", "orderPrice", "stopLossOrderPrice");
    }

    /** Та же величина, что у формы из тела родителя ({@code U8.8}): обе тропы её переносят. */
    @Test
    @DisplayName("U9.7 — объявленный размер записи переносится")
    void u9_7_theDeclaredSizeIsCarriedHere() {
        AlgoOrderOkxResponse response = OkxFixture.algoOrder();
        response.setSz("100");

        assertThat(mapper.integrationToSnapshot(response).getSize()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("U9.8 — признака «только уменьшать» у формы источника нет")
    void u9_8_theSourceFormCarriesNoReduceOnly() {
        assertThat(AlgoOrderOkxResponse.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("reduceOnly");
    }

    /** Связь с родителем держится только этим полем: сопоставляющий спрашивает `isBlank`. */
    @Test
    @DisplayName("U9.9 — пустой клиентский идентификатор: пустая строка, а не пустота")
    void u9_9_anEmptyClientIdIsAnEmptyStringNotEmptiness() {
        AlgoOrderOkxResponse response = OkxFixture.algoOrder();
        response.setAlgoClOrdId("");

        assertThat(mapper.integrationToSnapshot(response).getInternalId()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("U9.10 — пустота вместо формы источника даёт пустоту")
    void u9_10_emptinessInIsEmptinessOut() {
        assertThat(mapper.integrationToSnapshot((AlgoOrderOkxResponse) null)).isNull();
    }
}

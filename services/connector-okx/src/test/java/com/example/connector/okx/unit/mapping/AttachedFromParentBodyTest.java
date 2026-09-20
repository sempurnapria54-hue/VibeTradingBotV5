package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.integration.external.api.model.okx.response.AttachAlgoOrdOkxResponse;
import com.example.connector.okx.mapping.OrderMapper;
import com.example.connector.okx.snapshot.AttachedAlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Встроенная защита из тела родителя → снапшот — группа `U8`
 * документа `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/Order.md
 * §«`OrderOkxResponse.attachAlgoOrds[*]` →
 * `AttachedAlgoOrderExternalSnapshot`»).
 *
 * <p><b>Базовая сборка:</b> {@code AttachAlgoOrdOkxResponse} со всеми
 * девятью полями непустыми.
 *
 * <p><b>Своего статуса у формы нет, и это несущее свойство:</b> элемент
 * тела родителя статусной колонки не имеет вовсе; состояние защиты
 * выводится по набору фактов отдельным резолвером ядра, а снапшот
 * отсюда приезжает с пустым внешним статусом.
 *
 * <p>Кейс {@code U8.8} (перенос размера) в код не пошёл: дом объявляет
 * перенос, код его не делает, и ослабленный под код кейс объявил бы
 * контрактом сам дефект — `.claude/work/backlog.md` §«Размер встроенной
 * защиты из тела родителя в снапшот не переносится».
 */
class AttachedFromParentBodyTest {

    private final OrderMapper mapper = Mappers.order();

    @Test
    @DisplayName("U8.1 — базовая сборка: два идентификатора площадки, наш ключ, тип, уровень и отказ")
    void u8_1_theBaseAssemblyLandsFieldByField() {
        AttachedAlgoOrderExternalSnapshot snapshot =
                mapper.integrationToSnapshot(OkxFixture.attachedInParentBody());

        assertThat(snapshot.getExternalAttachedId()).isEqualTo("a1");
        assertThat(snapshot.getInternalId()).isEqualTo("tb-p1");
        assertThat(snapshot.getExternalId()).isEqualTo("9");
        assertThat(snapshot.getExternalType()).isEqualTo("condition");
        assertThat(snapshot.getStopLossTriggerPrice()).isEqualByComparingTo("90");
        assertThat(snapshot.getFailCode()).isEqualTo("0");
        assertThat(snapshot.getFailReason()).isEmpty();
    }

    @Test
    @DisplayName("U8.2 — своего статуса у элемента нет")
    void u8_2_theElementHasNoStatusOfItsOwn() {
        assertThat(mapper.integrationToSnapshot(OkxFixture.attachedInParentBody()).getExternalStatus())
                .isNull();
    }

    /** Операнд сверки объявленной базы. */
    @Test
    @DisplayName("U8.3 — эхо базы триггера резолвится доменным значением")
    void u8_3_theTriggerBaseEchoIsResolved() {
        assertThat(mapper.integrationToSnapshot(OkxFixture.attachedInParentBody()).getTriggerPriceType())
                .isEqualTo(AlgoOrder.TriggerPriceType.MARK);
    }

    @Test
    @DisplayName("U8.4 — пустое эхо базы: сверка не запускается")
    void u8_4_anEmptyEchoStartsNoComparison() {
        AttachAlgoOrdOkxResponse response = OkxFixture.attachedInParentBody();
        response.setSlTriggerPxType("");

        assertThat(mapper.integrationToSnapshot(response).getTriggerPriceType()).isNull();
    }

    /** Расхождение с объявленной базой видно у сверяющего, маппер его не судит. */
    @Test
    @DisplayName("U8.5 — чужая база триггера переводится, а не судится")
    void u8_5_aDifferentBaseIsTranslatedNotJudged() {
        AttachAlgoOrdOkxResponse response = OkxFixture.attachedInParentBody();
        response.setSlTriggerPxType("last");

        assertThat(mapper.integrationToSnapshot(response).getTriggerPriceType())
                .isEqualTo(AlgoOrder.TriggerPriceType.LAST);
    }

    @Test
    @DisplayName("U8.6 — код и диагностика отказа переносятся оба")
    void u8_6_bothFailureFieldsAreCarried() {
        AttachAlgoOrdOkxResponse response = OkxFixture.attachedInParentBody();
        response.setFailCode("51000");
        response.setFailReason("Parameter error");

        AttachedAlgoOrderExternalSnapshot snapshot = mapper.integrationToSnapshot(response);

        assertThat(snapshot.getFailCode()).isEqualTo("51000");
        assertThat(snapshot.getFailReason()).isEqualTo("Parameter error");
    }

    /** Два идентификатора площадки разные, и пустота одного о другом ничего не говорит. */
    @Test
    @DisplayName("U8.7 — защита ещё не материализована: пустая строка, а не пустота")
    void u8_7_anUnmaterializedProtectionCarriesAnEmptyString() {
        AttachAlgoOrdOkxResponse response = OkxFixture.attachedInParentBody();
        response.setAlgoId("");

        AttachedAlgoOrderExternalSnapshot snapshot = mapper.integrationToSnapshot(response);

        assertThat(snapshot.getExternalId()).isNotNull().isEmpty();
        assertThat(snapshot.getExternalAttachedId()).isEqualTo("a1");
    }

    @Test
    @DisplayName("U8.9 — пустота вместо формы источника даёт пустоту")
    void u8_9_emptinessInIsEmptinessOut() {
        assertThat(mapper.integrationToSnapshot((AttachAlgoOrdOkxResponse) null)).isNull();
    }

    /** Снапшот несёт встроенную защиту, а не самостоятельную условную заявку. */
    @Test
    @DisplayName("U8.10 — полей самостоятельной условной заявки у снапшота нет")
    void u8_10_theSnapshotCarriesNoStandaloneAlgoFields() {
        assertThat(AttachedAlgoOrderExternalSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("externalSize", "externalPrice", "externalTriggerTime",
                        "condition", "linkedOrderExternalIds");
    }
}

package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.integration.external.api.model.okx.request.AttachAlgoOrdOkxRequest;
import com.example.connector.okx.mapping.OrderMapper;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Встроенная защита → элемент запроса постановки — группа `U24`
 * документа `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/Order.md §«`Domain Order → OKX request`», абзац
 * о встроенной защите).
 *
 * <p><b>Базовая сборка:</b> доменная {@code AttachedAlgoOrder} с
 * клиентским идентификатором, уровнем остановки убытка и марк-ценой
 * базой триггера.
 *
 * <p><b>База цены триггера заполняется ВСЕГДА, и биржевое умолчание не
 * используется:</b> умолчание площадки — последняя цена, наш
 * объявленный базис — марк-цена, и молчание здесь меняло бы ценовой
 * домен защиты молча.
 */
class AttachedPlaceRequestTest {

    private final OrderMapper mapper = Mappers.order();

    private static AttachedAlgoOrder attached() {
        AttachedAlgoOrder attached = new AttachedAlgoOrder();
        attached.setInternalId("tb-p1");
        attached.setStopLossTriggerPrice(new BigDecimal("90"));
        attached.setTriggerPriceType(AlgoOrder.TriggerPriceType.MARK);
        return attached;
    }

    @Test
    @DisplayName("U24.1 — базовая сборка: ключ, уровень, база и флаг рыночного исполнения")
    void u24_1_theBaseAssemblyBuildsTheElement() {
        AttachAlgoOrdOkxRequest request = mapper.domainToPlaceRequest(attached());

        assertThat(request.getAttachAlgoClOrdId()).isEqualTo("tb-p1");
        assertThat(request.getSlTriggerPx()).isEqualTo("90");
        assertThat(request.getSlTriggerPxType()).isEqualTo("mark");
        assertThat(request.getSlOrdPx()).isEqualTo("-1");
    }

    /** Защита исполняется рыночной после срабатывания: это константа, а не расчёт. */
    @Test
    @DisplayName("U24.2 — флаг рыночного исполнения ставится константой")
    void u24_2_theMarketFlagIsAConstant() {
        AttachedAlgoOrder other = attached();
        other.setStopLossTriggerPrice(new BigDecimal("42"));

        assertThat(mapper.domainToPlaceRequest(other).getSlOrdPx()).isEqualTo("-1");
        assertThat(mapper.domainToPlaceRequest(attached()).getSlOrdPx()).isEqualTo("-1");
    }

    /** Источник ведёт защиту на налитый объём родителя. */
    @Test
    @DisplayName("U24.3 — размера у формы запроса нет вовсе")
    void u24_3_theRequestFormCarriesNoSize() {
        assertThat(AttachAlgoOrdOkxRequest.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("sz", "size");
    }

    /** Маппер переводит объявленное, а не судит его: сверку базы делает читатель эха. */
    @Test
    @DisplayName("U24.4 — иная база триггера переводится как объявлена")
    void u24_4_aDifferentBaseIsTranslatedAsDeclared() {
        AttachedAlgoOrder other = attached();
        other.setTriggerPriceType(AlgoOrder.TriggerPriceType.LAST);

        assertThat(mapper.domainToPlaceRequest(other).getSlTriggerPxType()).isEqualTo("last");
    }

    /**
     * Кейс охраны второго рубежа: такой вход до запроса не доезжает —
     * стратегия без базы триггера отвергается созданием, — но охрана на то и охрана.
     */
    @Test
    @DisplayName("U24.5 — пустая база триггера даёт пустое поле запроса")
    void u24_5_anEmptyBaseGivesAnEmptyField() {
        AttachedAlgoOrder other = attached();
        other.setTriggerPriceType(null);

        assertThat(mapper.domainToPlaceRequest(other).getSlTriggerPxType()).isNull();
    }

    /** Флаг рыночного исполнения ставится константой безусловно. */
    @Test
    @DisplayName("U24.6 — защита без уровня уезжает с рыночным флагом")
    void u24_6_protectionWithoutALevelStillCarriesTheMarketFlag() {
        AttachedAlgoOrder other = attached();
        other.setStopLossTriggerPrice(null);

        AttachAlgoOrdOkxRequest request = mapper.domainToPlaceRequest(other);

        assertThat(request.getSlTriggerPx()).isNull();
        assertThat(request.getSlOrdPx()).isEqualTo("-1");
    }

    @Test
    @DisplayName("U24.7 — масштаб уровня сохранён")
    void u24_7_theLevelScaleIsKept() {
        AttachedAlgoOrder other = attached();
        other.setStopLossTriggerPrice(new BigDecimal("90.10"));

        assertThat(mapper.domainToPlaceRequest(other).getSlTriggerPx()).isEqualTo("90.10");
    }

    @Test
    @DisplayName("U24.8 — пустота вместо встроенной защиты даёт пустоту")
    void u24_8_emptinessInIsEmptinessOut() {
        assertThat(mapper.domainToPlaceRequest((AttachedAlgoOrder) null)).isNull();
    }
}

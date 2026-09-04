package com.example.tradingbot.domain.model.other;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tradingbot.integration.model.okx.response.TradeFeeOkxResponse;
import com.example.tradingbot.mapping.TradeFeeRateMapper;
import com.example.tradingbot.mapping.TradeFeeRateMapperImpl;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает ставку комиссии с её домом
 * (docs/models/domain/other/TradeFeeRate.md,
 * docs/models/mapping/TradeFeeRate.md).
 *
 * <p>Несущее: знак источника снимается НА ГРАНИЦЕ — ниже маппинга ставка
 * есть издержка. Прогон контура застал источник записывающим комиссию
 * отрицательной ({@code taker = -0.0005}, наблюдение `AG12.1`), и
 * непринятый знак дал бы отрицательную комиссию в каждой формуле риска.
 */
class TradeFeeRateTest {

    private final TradeFeeRateMapper mapper = new TradeFeeRateMapperImpl();

    @Test
    @DisplayName("Знак источника снят: комиссия положительна, ребейт отрицателен")
    void sourceSignIsRemovedAtTheBoundary() {
        TradeFeeRate rate = materialize("-0.0005", "0.0001");

        assertEquals(0, rate.takerFeeRate().compareTo(new BigDecimal("0.0005")));
        assertEquals(0, rate.makerFeeRate().compareTo(new BigDecimal("-0.0001")), "ребейт остаётся отрицательным");
    }

    @Test
    @DisplayName("Ставка сравнивается числом, а не строкой: 0.0005 и 0.00050 — одно значение")
    void ratesCompareAsNumbers() {
        TradeFeeRate rate = materialize("-0.0005", "-0.0002");

        assertTrue(rate.sameValueAs("0.00050", "0.000200"), "иначе подтверждение выродилось бы в новую строку");
        assertFalse(rate.sameValueAs("0.0006", "0.0002"));
    }

    @Test
    @DisplayName("Ключ группы — пара сырых значений источника")
    void groupKeyIsRawPair() {
        TradeFeeRate rate = materialize("-0.0005", "-0.0002");

        assertTrue(rate.sameGroupAs("SWAP", "4"));
        assertFalse(rate.sameGroupAs("FUTURES", "4"), "тот же id в другом типе — другая группа");
    }

    @Test
    @DisplayName("Подтверждение растит счётчик и двигает метку источника, значения не трогая")
    void confirmationAdvancesCounter() {
        TradeFeeRate rate = materialize("-0.0005", "-0.0002");
        OffsetDateTime later = OffsetDateTime.of(2026, 9, 1, 12, 0, 0, 0, ZoneOffset.UTC);

        rate.confirm(later, "Lv2");

        assertEquals(2L, rate.getRefreshCount(), "первое подтверждение проставила материализация");
        assertEquals(later, rate.getExternalModifiedAt());
        assertEquals(0, rate.takerFeeRate().compareTo(new BigDecimal("0.0005")));
    }

    @Test
    @DisplayName("Пустая ставка числом не резолвится — прогноз комиссии невычислим")
    void blankRateDoesNotResolve() {
        TradeFeeRate rate = materialize("", "");

        assertNull(rate.takerFeeRate());
        assertFalse(rate.hasTakerFeeRate());
    }

    /** Ответ источника формы наблюдения AG12.1 → снапшот группы → доменная ставка. */
    private TradeFeeRate materialize(String taker, String maker) {
        TradeFeeOkxResponse.FeeGroupOkxResponse group = new TradeFeeOkxResponse.FeeGroupOkxResponse();
        group.setGroupId("4");
        group.setTaker(taker);
        group.setMaker(maker);
        TradeFeeOkxResponse response = new TradeFeeOkxResponse();
        response.setInstType("SWAP");
        response.setLevel("Lv1");
        response.setTs("1788294565327");
        response.setFeeGroup(List.of(group));

        TradeFeeRate rate = mapper.snapshotToDomain(mapper.integrationToSnapshot(response, group), 7L);
        rate.setRefreshCount(1L);
        return rate;
    }
}

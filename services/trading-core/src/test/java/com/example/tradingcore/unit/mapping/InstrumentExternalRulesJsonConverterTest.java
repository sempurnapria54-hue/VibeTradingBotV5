package com.example.tradingcore.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.testsupport.InstrumentRulesOverlayCopyContract;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingcore.mapping.InstrumentExternalRulesJsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Копия навеса справочных правил в дереве торгового ядра: изымает
 * идентификатор владельца и ставку комиссии — то же правило, что у копии
 * рыночных данных.
 *
 * <p>Кейсы — `U7`, `U10.3` (.claude/tests/cases/jsonb-overlay-roundtrip.md);
 * клетка `U7.4` живёт здесь, потому что адресует именно эту копию.
 */
class InstrumentExternalRulesJsonConverterTest extends InstrumentRulesOverlayCopyContract {

    private final InstrumentExternalRulesJsonConverter converter =
            new InstrumentExternalRulesJsonConverter(beanAssemblyMapper());

    @Override
    protected String writeRules(InstrumentExternalRules rules) {
        return converter.rulesToJson(rules);
    }

    @Override
    protected InstrumentExternalRules readRules(String json) {
        return converter.jsonToRules(json);
    }

    /**
     * Объект, прочитанный из навеса и гидрированный ставкой, записанный
     * обратно, ставки в навес не кладёт: она атрибут комиссионного уровня
     * счёта (docs/models/domain/other/InstrumentExternalRules.md §«Ставка
     * комиссии»).
     *
     * <p><b>Состояние недостижимо обычной тропой</b>: писатель проекции
     * берёт объект из синка каталога, а не из чтения навеса. Кейс мерит, что
     * ход, который такой объект запишет, второго носителя ставки не заведёт.
     */
    @Test
    @DisplayName("U7.4 — гидрированная ставка в навес не уезжает")
    void u7_4_aHydratedFeeRateDoesNotTravelIntoTheOverlay() {
        InstrumentExternalRules read = readRules(writeRules(rulesWithoutFee()));
        read.setExternalTakerFeeRate("0.0005");

        assertThat(keysOf(writeRules(read)))
                .as("ставка комиссии — атрибут комиссионного уровня счёта, и в навесе "
                        + "инструмента она была бы вторым носителем, расходящимся со сменой тира")
                .doesNotContain("externalTakerFeeRate");
    }

    private static InstrumentExternalRules rulesWithoutFee() {
        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setStatus(InstrumentExternalRules.Status.LIVE);
        rules.setExternalInstrumentId("BTC-USDT-SWAP");
        return rules;
    }
}

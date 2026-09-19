package com.example.tradingcore.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.testsupport.InstrumentRulesOverlayCopyContract;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingcore.mapping.InstrumentExternalRulesJsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Копия навеса справочных правил в дереве торгового ядра: изымает
 * идентификатор владельца и УДЕРЖИВАЕТ ставку комиссии.
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

    @Override
    protected String excludedField() {
        return "instrumentId";
    }

    @Override
    protected String retainedField() {
        return "externalTakerFeeRate";
    }

    /**
     * Направление дефекта, а не его наличие: объект, прочитанный из навеса и
     * гидрированный ставкой, записанный обратно кладёт в навес значение,
     * принадлежащее комиссионному уровню счёта
     * (.claude/work/backlog.md §«Изъятия двух копий конвертера навеса правил
     * инструмента асимметричны»).
     *
     * <p><b>Состояние недостижимо обычной тропой</b>: писатель проекции
     * берёт объект из синка каталога, а не из чтения навеса. Красный прогон
     * этого кейса продовым дефектом поэтому не является — он предъявляет
     * цену расхождения, если ход когда-нибудь состоится.
     */
    @Test
    @DisplayName("U7.4 — гидрированная ставка уезжает в навес второй копией значения")
    void u7_4_aHydratedFeeRateTravelsIntoTheOverlayAsASecondCarrier() {
        InstrumentExternalRules read = readRules(writeRules(rulesWithoutFee()));
        read.setExternalTakerFeeRate("0.0005");

        assertThat(keysOf(writeRules(read)))
                .as("ставка комиссии — атрибут комиссионного уровня счёта, и в навесе "
                        + "инструмента она второй носитель, расходящийся со сменой тира")
                .contains("externalTakerFeeRate");
    }

    private static InstrumentExternalRules rulesWithoutFee() {
        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setStatus(InstrumentExternalRules.Status.LIVE);
        rules.setExternalInstrumentId("BTC-USDT-SWAP");
        return rules;
    }
}

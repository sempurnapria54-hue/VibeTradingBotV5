package com.example.marketdata.unit.mapping;

import com.example.marketdata.mapping.InstrumentExternalRulesJsonConverter;
import com.example.testsupport.InstrumentRulesOverlayCopyContract;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;

/**
 * Копия навеса справочных правил в дереве рыночных данных: изымает ставку
 * комиссии и УДЕРЖИВАЕТ идентификатор владельца — правило, обратное копии
 * ядра.
 *
 * <p>Кейсы — `U7`, `U10.3` (.claude/tests/cases/jsonb-overlay-roundtrip.md).
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
        return "externalTakerFeeRate";
    }

    @Override
    protected String retainedField() {
        return "instrumentId";
    }
}

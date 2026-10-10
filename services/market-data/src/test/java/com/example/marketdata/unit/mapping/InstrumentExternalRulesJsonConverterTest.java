package com.example.marketdata.unit.mapping;

import com.example.marketdata.mapping.InstrumentExternalRulesJsonConverter;
import com.example.testsupport.InstrumentRulesOverlayCopyContract;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Копия навеса справочных правил в дереве рыночных данных: изымает
 * идентификатор владельца и ставку комиссии — то же правило, что у копии
 * ядра.
 *
 * <p>Кейсы — `U7`, `U10.3`, `U11.7`, `U13.3`, `U13.6`, `U13.7`
 * (.claude/tests/cases/jsonb-overlay-roundtrip.md).
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
    protected InstrumentExternalRules readRulesOn(ObjectMapper source, String json) {
        return new InstrumentExternalRulesJsonConverter(source).jsonToRules(json);
    }
}

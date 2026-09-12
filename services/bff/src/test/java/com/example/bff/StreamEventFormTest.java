package com.example.bff;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bff.api.model.stream.StrategyActivatedStreamApiModel;
import com.example.bff.api.model.stream.StrategyLifecycleStreamApiModel;
import com.example.bff.mapping.StreamEventMapper;
import com.example.bff.mapping.StreamEventMapperImpl;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.message.StrategyActivatedMessage;
import com.example.tradingbot.message.StrategyLifecycleMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Перевод содержимого события в форму периметра.
 *
 * <p><b>Почему это стои́т проверять.</b> Идентичности радиуса приезжают
 * компонентами <b>верхнего уровня</b> содержимого, и правил маппинга у них
 * поэтому нет — они совпадают по имени
 * (.claude/rules/codestyle.md §Маппинг). Разойдись имя у одной стороны,
 * генератор промолчит: непокрытые цели гасятся политикой маппера, и
 * браузер получил бы <b>пустое</b> поле вместо идентичности — то есть
 * правдоподобную запись без предмета.
 *
 * <p><b>Имя определения — единственное, что берётся из снимка</b>, и
 * правило для него остаётся: радиусом оно не является.
 */
class StreamEventFormTest {

    private static final String STRATEGY = "st-0001";
    private static final String ACCOUNT = "ea-0001";
    private static final String INSTRUMENT = "in-0001";
    private static final String NAME = "baseline";

    private final StreamEventMapper mapper = new StreamEventMapperImpl();

    @Test
    @DisplayName("Активация: идентичности берутся с верхнего уровня, имя — из снимка")
    void theActivationTakesItsIdentitiesFromTheTopLevelAndItsNameFromTheSnapshot() {
        StrategyActivatedStreamApiModel model = mapper.messageToApi(
                new StrategyActivatedMessage(STRATEGY, ACCOUNT, INSTRUMENT, "holder", definition()));

        assertThat(model.strategyInternalId()).isEqualTo(STRATEGY);
        assertThat(model.exchangeAccountInternalId()).isEqualTo(ACCOUNT);
        assertThat(model.instrumentInternalId()).isEqualTo(INSTRUMENT);
        assertThat(model.name())
                .as("имя определения радиусом не является и едет из снимка")
                .isEqualTo(NAME);
    }

    @Test
    @DisplayName("Деактивация и удаление: перевод 1:1, актор наружу не уходит")
    void theLifecycleFormTravelsOneToOne() {
        StrategyLifecycleStreamApiModel model = mapper.messageToApi(
                new StrategyLifecycleMessage(STRATEGY, ACCOUNT, INSTRUMENT, "holder"));

        assertThat(model.strategyInternalId()).isEqualTo(STRATEGY);
        assertThat(model.exchangeAccountInternalId()).isEqualTo(ACCOUNT);
        assertThat(model.instrumentInternalId()).isEqualTo(INSTRUMENT);
    }

    /** Снимок дерева: браузеру он не уходит, здесь нужен ради имени. */
    private Strategy definition() {
        Strategy definition = new Strategy();
        definition.setInternalId(STRATEGY);
        definition.setExchangeAccountInternalId(ACCOUNT);
        definition.setInstrumentInternalId(INSTRUMENT);
        definition.setName(NAME);
        return definition;
    }
}

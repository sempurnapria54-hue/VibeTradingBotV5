package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Окружение, для которого держатель чисел риск-аппетита не назвал, —
 * клетка {@code B4.9} группы риск-гейта ({@link DealRiskGateBoxTest}).
 *
 * <p><b>Своя конфигурация контекста, и это ВХОД клетки:</b> числа
 * риск-аппетита — оси окружения, которые ядро принимает при старте
 * (docs/rules/risk-policy.md, правило о числах риск-аппетита), и пустыми
 * их делает только положение осей контекста, а не операция поверхности.
 * Все шесть осей пусты — штатное состояние {@code stage} и {@code prod}.
 *
 * <p><b>Плечо пары здесь не назначается:</b> при непринятом пределе плеча
 * назначение отвергает поверхность (docs/rules/trading-constraints.md), и
 * предусловие клетки обходится без него — первым отказом преконтроля
 * остаётся предел серии.
 *
 * <p><b>Своя группа потребителя и своя тема владельца определений</b> —
 * довод у шапки {@link TradingCoreSubstrate}.
 */
class UnconfiguredRiskAppetiteBoxTest extends LiveDealBox {

    /** Имя одиночки: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-unconfigured-risk-appetite";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        TradingCoreSubstrate.registerOwn(registry, NAME, TradingCoreSubstrate.emptyRiskAppetite());
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }

    @Test
    @DisplayName("B4.9 — незаданные числа риск-аппетита отказывают на действии, а не на старте")
    void theUnassignedRiskAppetiteRefusesAtTheActionAndNotAtStartup() {
        // Контекст поднялся при пустых осях — приём при старте ядра не
        // роняет, и поверхность отвечает — в том числе чтением принятых
        // чисел.
        assertThat(get(HEALTH).status()).isEqualTo(200);
        assertThat(get(RISK_APPETITE).status()).isEqualTo(200);
        openGatedDealWithoutLeverage();
        Integer mark = AppLog.mark();

        tick(Tick.DEAL_ORCHESTRATOR);

        // Отказ приходит НА ДЕЙСТВИИ и называет, какое именно число пусто:
        // первой мерится охрана предела серии.
        String written = AppLog.since(mark);
        assertThat(written).contains("Risk precheck blocked action");
        assertThat(written).contains("LOSS_LIMIT_NOT_CONFIGURED");
        assertThat(written).contains("globalConsecutiveLossLimit is not accepted from the environment configuration");
        assertThat(rows.count("orders")).isEqualTo(0L);
        // Сделка в аварию не уходит: код в карв-ауте исчерпанного бюджета.
        assertThat(dealStatus()).isNotEqualTo("ERROR");
    }

    @Test
    @DisplayName("Плечо пары при непринятом пределе плеча не назначается: сверять не с чем")
    void aLeverageIsNotAssignableWhileTheLimitIsNotAccepted() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));

        Answer assigned = put(PAIR_SETTINGS + "/" + ACCOUNT + "/" + INSTRUMENT,
                Bodies.pairSettings(WORKING_LEVERAGE));

        assertThat(assigned.status()).isEqualTo(400);
        assertThat(assigned.carriesErrorDto()).isTrue();
    }

    /**
     * Сделка с траншем в предвходовой проверке, чей вход доходит до
     * преконтроля: проекции, ставка комиссии, фичи и свежий снимок средств
     * поставлены тропами ящика; плеча нет — его назначение отвергнуто бы.
     */
    private void openGatedDealWithoutLeverage() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        syncFeeRate();
        marketData.answers(featuresPath(INSTRUMENT),
                Feed.featuresWithPrice(MarketPhase.Type.BULL_TREND.name(), LAST_PRICE));
        connector.answers(balancePath(ACCOUNT), balanceBody());
        connector.answers(PEER_SERVER_TIME, Feed.serverTime(EXCHANGE_MOMENT));
        activate(workingDefinition());
        tick(Tick.ENTRY_SCANNER);
        assertThat(rows.count("deals")).isEqualTo(1L);
        // Первый тик сопровождения снимает снимок средств и работы не
        // делает: предвходовая проверка обеспечивает его ДО работы.
        tick(Tick.DEAL_ORCHESTRATOR);
        assertThat(rows.count("orders")).isEqualTo(0L);
    }
}

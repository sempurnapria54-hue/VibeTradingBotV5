package com.example.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.strategies.domain.event.OutboxWriter;
import com.example.strategies.domain.service.ActorProvider;
import com.example.strategies.domain.service.StrategyLifecycleService;
import com.example.strategies.domain.service.StrategyStatusWriter;
import com.example.strategies.domain.service.TenantRiskAppetiteReader;
import com.example.strategies.domain.validation.StrategyDefinitionValidator;
import com.example.strategies.integration.TradingCoreReadClient;
import com.example.strategies.integration.model.PairCheckCoreResponse;
import com.example.strategies.integration.model.RiskAppetiteCoreResponse;
import com.example.strategies.mapping.StrategyApiMapper;
import com.example.strategies.mapping.StrategyApiMapperImpl;
import com.example.strategies.persistence.model.StrategyEntity;
import com.example.strategies.persistence.service.StrategyDataService;
import com.example.tradingbot.domain.event.StrategyActivatedContent;
import com.example.tradingbot.domain.event.StrategyEventType;
import com.example.tradingbot.domain.event.StrategyLifecycleContent;
import com.example.tradingbot.domain.model.aggregate.strategy.PhaseEntryPolicy;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Жизненный цикл определения у его владельца.
 *
 * <p><b>Что здесь проверяется по существу.</b> Активация выдаёт
 * административное разрешение торговать, и разрешение, выданное
 * НЕПРОВЕРЕННОМУ определению, — заявление, а не разрешение
 * (docs/rules/strategy-validation.md §«Что проверяется на активации»).
 * Поэтому предметы проверки такие: не назначенные числа риск-аппетита
 * останавливают активацию (иначе неравенства не считаются вовсе); чужая
 * активная на паре останавливает её же (инвариант радиуса); переход,
 * которого нет в матрице, отвергается моделью; и <b>каждый</b> состоявшийся
 * переход оставляет строку outbox — иначе ядро о нём не узнает никогда.
 *
 * <p><b>Состояние подставляется настоящее, предикаты не подменяются</b>
 * (.claude/rules/codestyle.md §«Тесты доменных моделей»): переход
 * считает сама модель, а не мок.
 */
class StrategyLifecycleTest {

    private static final String TENANT = "tn-0001";
    private static final String ACCOUNT = "ea-0001";
    private static final String INSTRUMENT = "in-0001";
    private static final String STRATEGY = "st-0001";

    private final StrategyDataService dataService = mock(StrategyDataService.class);
    private final TradingCoreReadClient coreClient = mock(TradingCoreReadClient.class);
    private final OutboxWriter outboxWriter = mock(OutboxWriter.class);
    private final StrategyApiMapper mapper = new StrategyApiMapperImpl();
    private final StrategyDefinitionValidator validator = new StrategyDefinitionValidator();
    private final StrategyStatusWriter statusWriter = new StrategyStatusWriter(dataService, outboxWriter);

    private final TenantRiskAppetiteReader appetiteReader = new TenantRiskAppetiteReader(coreClient);

    private final StrategyLifecycleService service = new StrategyLifecycleService(
            dataService, validator, coreClient, mapper, appetiteReader, statusWriter,
            new ActorProvider());

    /** Активация состоявшаяся: статус переставлен, событие со снимком записано. */
    @Test
    void activationWritesTheStatusAndItsEventTogether() {
        Strategy definition = definition(Strategy.Status.CREATED);
        givenDefinition(definition);
        givenReferencesResolve();
        givenRiskAppetite(new BigDecimal("2"), new BigDecimal("3"));

        Strategy moved = service.applyStatus(STRATEGY, TENANT, Strategy.Status.ACTIVE);

        assertThat(moved.getStatus()).isEqualTo(Strategy.Status.ACTIVE);
        verify(outboxWriter).write(eq(TENANT), eq(StrategyEventType.STRATEGY_ACTIVATED),
                any(StrategyActivatedContent.class));
    }

    /**
     * Числа риск-аппетита не назначены — активация отвергается.
     *
     * <p>Без них неравенства создания не считаются вовсе, и разрешение
     * было бы выдано определению, которого никто не проверял.
     */
    @Test
    void activationIsRefusedWhenTheTenantRiskNumbersAreNotAssigned() {
        givenDefinition(definition(Strategy.Status.CREATED));
        givenReferencesResolve();
        givenRiskAppetite(null, null);

        assertThatThrownBy(() -> service.applyStatus(STRATEGY, TENANT, Strategy.Status.ACTIVE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("RISK_APPETITE_NOT_CONFIGURED");
        verify(outboxWriter, never()).write(any(), any(), any());
    }

    /** Чужая активная на паре останавливает активацию: инвариант радиуса пары. */
    @Test
    void activationIsRefusedWhenAnotherDefinitionIsActiveOnThePair() {
        givenDefinition(definition(Strategy.Status.INACTIVE));
        when(dataService.findActiveInternalIdOnPair(ACCOUNT, INSTRUMENT))
                .thenReturn(Optional.of("st-0002"));

        assertThatThrownBy(() -> service.applyStatus(STRATEGY, TENANT, Strategy.Status.ACTIVE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("STRATEGY_ACTIVE_ALREADY_EXISTS");
        verify(outboxWriter, never()).write(any(), any(), any());
    }

    /**
     * Ссылка определения перестала разрешаться — активация отвергается.
     *
     * <p>Счёт и инструмент живут у соседей и могли выйти из контекста
     * тенанта после создания; проекция ошибается только в запрещающую
     * сторону, и отказ повторяется следующим ходом держателя.
     */
    @Test
    void activationIsRefusedWhenTheAccountNoLongerResolves() {
        givenDefinition(definition(Strategy.Status.CREATED));
        when(coreClient.checkPair(TENANT, ACCOUNT, INSTRUMENT))
                .thenReturn(new PairCheckCoreResponse(false, false, true));

        assertThatThrownBy(() -> service.applyStatus(STRATEGY, TENANT, Strategy.Status.ACTIVE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("STRATEGY_ACCOUNT_NOT_FOUND");
        verify(outboxWriter, never()).write(any(), any(), any());
    }

    /**
     * Недопустимый переход отвергается ДО всякого чтения соседа.
     *
     * <p>Матрицу держит сама модель: {@code CREATED → INACTIVE} не
     * допускается, деактивировать можно лишь введённую в работу.
     */
    @Test
    void aTransitionOutsideTheMatrixIsRefusedWithoutTouchingThePeer() {
        givenDefinition(definition(Strategy.Status.CREATED));

        assertThatThrownBy(() -> service.applyStatus(STRATEGY, TENANT, Strategy.Status.INACTIVE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("STRATEGY_TRANSITION_NOT_ALLOWED");
        verify(coreClient, never()).checkPair(any(), any(), any());
        verify(outboxWriter, never()).write(any(), any(), any());
    }

    /** Деактивация: событие идентичностей, дерева в нём нет — оно у читателя уже лежит. */
    @Test
    void deactivationEmitsIdentitiesWithoutTheTree() {
        givenDefinition(definition(Strategy.Status.ACTIVE));

        Strategy moved = service.applyStatus(STRATEGY, TENANT, Strategy.Status.INACTIVE);

        assertThat(moved.getStatus()).isEqualTo(Strategy.Status.INACTIVE);
        verify(outboxWriter).write(eq(TENANT), eq(StrategyEventType.STRATEGY_DEACTIVATED),
                any(StrategyLifecycleContent.class));
    }

    /** Определение чужого тенанта читается как ненайденное, а не как отказ права. */
    @Test
    void aDefinitionOfAnotherTenantIsNotFound() {
        Strategy foreign = definition(Strategy.Status.CREATED);
        foreign.setTenantId("tn-0002");
        when(dataService.findByInternalId(STRATEGY)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.applyStatus(STRATEGY, TENANT, Strategy.Status.ACTIVE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Strategy not found");
    }

    private void givenDefinition(Strategy definition) {
        when(dataService.findByInternalId(STRATEGY)).thenReturn(Optional.of(definition));
        when(dataService.findByInternalIdWithTree(STRATEGY)).thenReturn(Optional.of(definition));
        when(dataService.getRequiredEntityByInternalId(STRATEGY)).thenReturn(new StrategyEntity());
    }

    private void givenReferencesResolve() {
        when(coreClient.checkPair(TENANT, ACCOUNT, INSTRUMENT))
                .thenReturn(new PairCheckCoreResponse(true, true, true));
    }

    private void givenRiskAppetite(BigDecimal simultaneous, BigDecimal catastrophic) {
        when(coreClient.getRiskAppetite(TENANT))
                .thenReturn(new RiskAppetiteCoreResponse(TENANT, simultaneous, catastrophic));
    }

    /**
     * Определение с одной НЕТОРГУЕМОЙ деталью: у неё риск-полей нет по
     * построению, и неравенства на ней не считаются. Предмет этих
     * проверок — предусловия перехода, а не содержание неравенств: их
     * считает исполнимая спецификация (docs/spec/strategy-reference.json).
     */
    private Strategy definition(Strategy.Status status) {
        StrategyDetail detail = new StrategyDetail();
        detail.setMarketPhaseType(MarketPhase.Type.UNKNOWN);
        detail.setPhaseEntryPolicy(PhaseEntryPolicy.NO_TRADE);
        Strategy definition = new Strategy();
        definition.setInternalId(STRATEGY);
        definition.setTenantId(TENANT);
        definition.setExchangeAccountInternalId(ACCOUNT);
        definition.setInstrumentInternalId(INSTRUMENT);
        definition.setName("baseline");
        definition.setStatus(status);
        definition.setDetails(List.of(detail));
        return definition;
    }
}

package com.example.tradingcore;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.MarketDataExpiredAction;
import com.example.tradingbot.domain.model.aggregate.strategy.PhaseEntryPolicy;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyMarketDataExpiredSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPositionAction;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingcore.mapping.StrategyJsonConverter;
import com.example.tradingcore.mapping.StrategyMapper;
import com.example.tradingcore.mapping.StrategyMapperImpl;
import com.example.tradingcore.persistence.model.StrategyActionEntity;
import com.example.tradingcore.persistence.model.StrategyDetailEntity;
import com.example.tradingcore.persistence.model.StrategyEntity;
import com.example.tradingcore.persistence.model.StrategyStepEntity;
import com.example.tradingcore.persistence.model.StrategyTrancheEntity;
import com.example.tradingcore.persistence.repository.StrategyDetailRepository;
import com.example.tradingcore.persistence.repository.StrategyIndicatorSettingRepository;
import com.example.tradingcore.persistence.repository.StrategyMarketStructureSettingRepository;
import com.example.tradingcore.persistence.repository.StrategyRepository;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.StrategyDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Копия дерева стратегии в базе ядра: чем читается уровень объявления и
 * по какой области резолвится цель действия.
 *
 * <p>Первое условие — <b>уровень объявления несёт родитель строки, а не
 * тип шага</b>. Строка с обоими родителями попала бы в оба уникальных
 * индекса и читалась бы двумя уровнями сразу; ограничение схемы её
 * отвергает, то есть ошибка маппера видна только на живой вставке — здесь
 * она видна на сборке.
 *
 * <p>Второе — <b>цель действия резолвится по ВСЕЙ детали</b>, обоими
 * уровнями её шагов. Сужение области до одного уровня сделало бы цель
 * нерезолвимой ровно там, где она объявлена соседним уровнем, и отказ
 * пришёл бы на приёме определения, а не на его авторинге.
 */
class StrategyTreeCopyTest {

    private final StrategyRepository repository = mock(StrategyRepository.class);
    private final StrategyDetailRepository detailRepository = mock(StrategyDetailRepository.class);
    private final StrategyMapper mapper = new StrategyMapperImpl(new StrategyJsonConverter(new ObjectMapper()));
    private final StrategyDataService dataService =
            new StrategyDataService(repository, detailRepository,
                    mock(StrategyIndicatorSettingRepository.class),
                    mock(StrategyMarketStructureSettingRepository.class), mapper,
                    mock(ExchangeAccountDataService.class), mock(InstrumentDataService.class));

    @Test
    void declarationLevelIsCarriedByTheRowParent() {
        StrategyEntity entity = mapper.domainToPersistence(strategy());

        StrategyDetailEntity detail = single(entity.getDetails());
        StrategyStepEntity dealLevelStep = single(detail.getSteps());
        assertThat(dealLevelStep.getDealStatus()).isEqualTo(Deal.Status.EXIT_PENDING.name());
        assertThat(dealLevelStep.getTrancheStatus()).isNull();
        assertThat(dealLevelStep.getDetail()).isSameAs(detail);
        assertThat(dealLevelStep.getTranche()).isNull();

        StrategyTrancheEntity tranche = single(detail.getTranches());
        StrategyStepEntity trancheStep = single(tranche.getSteps());
        assertThat(trancheStep.getTrancheStatus()).isEqualTo(DealTranche.Status.MANAGING.name());
        assertThat(trancheStep.getDealStatus()).isNull();
        assertThat(trancheStep.getTranche()).isSameAs(tranche);
        assertThat(trancheStep.getDetail()).isNull();
    }

    /** Действие обоих уровней ссылается на деталь: ключ действия уникален в её пределах. */
    @Test
    void actionsOfBothLevelsPointAtTheSameDetail() {
        StrategyEntity entity = mapper.domainToPersistence(strategy());

        StrategyDetailEntity detail = single(entity.getDetails());
        StrategyActionEntity dealLevelAction = single(single(detail.getSteps()).getActions());
        StrategyActionEntity trancheAction =
                single(single(single(detail.getTranches()).getSteps()).getActions());

        assertThat(dealLevelAction.getDetail()).isSameAs(detail);
        assertThat(trancheAction.getDetail()).isSameAs(detail);
    }

    @Test
    void targetOfDealLevelActionResolvesIntoTrancheAction() {
        StrategyEntity stored = mapper.domainToPersistence(strategyWithCrossLevelTarget());
        when(repository.save(any(StrategyEntity.class))).thenReturn(stored);

        dataService.saveTree(strategyWithCrossLevelTarget());

        StrategyDetailEntity detail = single(stored.getDetails());
        StrategyActionEntity dealLevelAction = single(single(detail.getSteps()).getActions());
        assertThat(dealLevelAction.getTargetAction()).isNotNull();
        assertThat(dealLevelAction.getTargetAction().getKey()).isEqualTo("sl");
    }

    /** Ключ, которого в детали нет, — авария сохранения, а не пустая ссылка. */
    @Test
    void unresolvableTargetKeyStopsTheSave() {
        Strategy strategy = strategyWithCrossLevelTarget();
        StrategyDetail detail = strategy.getDetails().get(0);
        algoAction(detail.getStepsByStatus().get(Deal.Status.EXIT_PENDING).get(0)).setTargetActionKey("nowhere");
        StrategyEntity stored = mapper.domainToPersistence(strategy);
        when(repository.save(any(StrategyEntity.class))).thenReturn(stored);

        assertThatThrownBy(() -> dataService.saveTree(strategy))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nowhere");
    }

    /** Строки шагов возвращаются в домен в порядке индекса, а не в порядке чтения. */
    @Test
    void stepOrderIsRestoredByIndex() {
        StrategyTrancheEntity tranche = new StrategyTrancheEntity();
        tranche.setKey("main");
        tranche.setLevelCount(1);
        tranche.setSteps(Set.of(stepRow(2L, 1, StrategyStepType.PROTECTION_ADJUSTMENT),
                stepRow(1L, 0, StrategyStepType.MAIN_PROTECTION)));

        StrategyTranche restored = mapper.persistenceToDomain(tranche);

        assertThat(restored.getStepsByStatus().get(DealTranche.Status.MANAGING))
                .extracting(StrategyStep::getStepType)
                .containsExactly(StrategyStepType.MAIN_PROTECTION, StrategyStepType.PROTECTION_ADJUSTMENT);
    }

    private StrategyStepEntity stepRow(Long id, Integer index, StrategyStepType type) {
        StrategyStepEntity row = new StrategyStepEntity();
        row.setId(id);
        row.setStepIndex(index);
        row.setStepType(type.name());
        row.setTrancheStatus(DealTranche.Status.MANAGING.name());
        return row;
    }

    private Strategy strategy() {
        return strategy(null);
    }

    private Strategy strategyWithCrossLevelTarget() {
        return strategy("sl");
    }

    /**
     * Стратегия с одной деталью: транш с шагом сопровождения, объявляющим
     * защиту, и узкая агрегатная поверхность с шагом сворачивания.
     *
     * @param dealLevelTargetKey ключ, на который ссылается действие уровня
     *                           сделки; пусто — ссылки нет
     */
    private Strategy strategy(String dealLevelTargetKey) {
        StrategyTranche tranche = new StrategyTranche();
        tranche.setKey("main");
        tranche.setLevelCount(1);
        tranche.setStepsByStatus(Map.of(DealTranche.Status.MANAGING,
                List.of(step(StrategyStepType.MAIN_PROTECTION, protectionAction()))));

        StrategyDetail detail = new StrategyDetail();
        detail.setMarketPhaseType(MarketPhase.Type.BULL_TREND);
        detail.setPhaseEntryPolicy(PhaseEntryPolicy.FOLLOW_PHASE);
        detail.setTranches(List.of(tranche));
        detail.setStepsByStatus(Map.of(Deal.Status.EXIT_PENDING,
                List.of(step(StrategyStepType.EXIT, dealLevelAction(dealLevelTargetKey)))));

        Strategy strategy = new Strategy();
        strategy.setInternalId("st-1");
        strategy.setExchangeAccountInternalId("ea-0007");
        strategy.setInstrumentInternalId("in-0042");
        strategy.setName("copy");
        strategy.setStatus(Strategy.Status.ACTIVE);
        strategy.setDetails(List.of(detail));
        return strategy;
    }

    private StrategyStep step(StrategyStepType type, StrategyAction action) {
        StrategyStep step = new StrategyStep();
        step.setStepType(type);
        step.setActions(List.of(action));
        step.setMarketDataExpiredSetting(new StrategyMarketDataExpiredSetting(
                MarketDataExpiredAction.WAIT, MarketDataExpiredAction.GRACEFUL_CLOSE));
        return step;
    }

    private StrategyAction protectionAction() {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setKey("sl");
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        return action;
    }

    private StrategyAction dealLevelAction(String targetKey) {
        if (isNull(targetKey)) {
            StrategyPositionAction action = new StrategyPositionAction();
            action.setKey("exit-all");
            action.setActionType(StrategyActionType.CANCEL_ACTION);
            return action;
        }
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setKey("drop-sl");
        action.setActionType(StrategyActionType.CANCEL_ACTION);
        action.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        action.setTargetActionKey(targetKey);
        return action;
    }

    private StrategyAlgoOrderAction algoAction(StrategyStep step) {
        return (StrategyAlgoOrderAction) step.getActions().get(0);
    }

    private <T> T single(Set<T> items) {
        assertThat(items).hasSize(1);
        return items.iterator().next();
    }
}

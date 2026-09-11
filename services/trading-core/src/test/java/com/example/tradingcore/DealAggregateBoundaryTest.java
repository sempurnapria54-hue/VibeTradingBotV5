package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.mapping.DealMapper;
import com.example.tradingcore.mapping.DealMapperImpl;
import com.example.tradingcore.mapping.DealTrancheMapper;
import com.example.tradingcore.mapping.DealTrancheMapperImpl;
import com.example.tradingcore.persistence.model.DealEntity;
import com.example.tradingcore.persistence.model.DealTrancheEntity;
import com.example.tradingcore.persistence.repository.DealRepository;
import com.example.tradingcore.persistence.repository.DealTrancheRepository;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.DealTrancheDataService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

/**
 * Границы выборки агрегата сделки: чем меряется занятость слота, что
 * входит в проход и в каком объёме читаются транши.
 *
 * <p>Проверяется не маппинг полей, а <b>решения, которые молча ломаются
 * оптимизацией</b>: радиус слота (пара, а не инструмент), состав
 * терминальных статусов (аварийная сделка проходом ведётся), объём
 * траншей (целиком, включая закрытые) и <b>форма записи существующей
 * строки</b> — точечный гардированный запрос против записи строки
 * целиком.
 */
class DealAggregateBoundaryTest {

    private static final Long ACCOUNT_ID = 7L;
    private static final Long INSTRUMENT_ID = 42L;
    private static final Long DEAL_ID = 100L;

    private final DealRepository dealRepository = mock(DealRepository.class);
    private final DealTrancheRepository trancheRepository = mock(DealTrancheRepository.class);
    private final DealMapper dealMapper = new DealMapperImpl();
    private final DealTrancheMapper trancheMapper = new DealTrancheMapperImpl();
    private final DealDataService dealDataService = new DealDataService(dealRepository, dealMapper);
    private final DealTrancheDataService trancheDataService =
            new DealTrancheDataService(trancheRepository, trancheMapper);

    /**
     * <b>Строка целиком пишется только на ЗАВЕДЕНИИ.</b> Модель с уже
     * присвоенной идентичностью отвергается: запись существующей строки
     * целиком есть {@code merge} отсоединённой сущности, откатывающий и
     * колонки соседних охраняемых запросов, и статус, переставленный
     * каскадом жёсткой ступени из соседнего потока
     * (docs/models/domain/aggregate/Deal.md §Персистентность).
     *
     * <p>Охрана нужна ровно потому, что конвенция уже действовала и
     * нарушалась: правило без отказа держится до следующего вызывающего.
     */
    @Test
    void anExistingRowIsNotWrittenWholesale() {
        Deal existing = new Deal();
        existing.setId(DEAL_ID);

        assertThatThrownBy(() -> dealDataService.create(existing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(DEAL_ID));

        verify(dealRepository, never()).save(any());
    }

    /**
     * Терминальное ребро уезжает в запрос <b>вместе с набором законных
     * исходных статусов</b>: гард и есть то, чем терминал разведён с
     * каскадом жёсткой ступени, и потерять его — значит вернуть молчаливую
     * перезапись каскада.
     */
    @Test
    void theTerminalEdgeCarriesItsFromStatusesIntoTheQuery() {
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setStatus(Deal.Status.CLOSED);
        deal.setCloseReason(Deal.CloseReason.STRATEGY_EXIT);
        when(dealRepository.applyTerminalEdge(anyLong(), any(), any(), any())).thenReturn(1);

        assertThat(dealDataService.applyTerminalEdge(deal,
                List.of(Deal.Status.ACTIVE, Deal.Status.EXIT_PENDING))).isTrue();

        verify(dealRepository).applyTerminalEdge(DEAL_ID, Deal.Status.CLOSED.name(),
                Deal.CloseReason.STRATEGY_EXIT.name(),
                List.of(Deal.Status.ACTIVE.name(), Deal.Status.EXIT_PENDING.name()));
    }

    /**
     * Ноль применённых строк — <b>не успех</b>: сделка ушла из-под
     * прохода, и звену это обязано быть видно.
     */
    @Test
    void anEdgeThatTouchedNoRowIsReportedAsNotApplied() {
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setStatus(Deal.Status.CLOSED);
        when(dealRepository.applyTerminalEdge(anyLong(), any(), any(), any())).thenReturn(0);

        assertThat(dealDataService.applyTerminalEdge(deal, List.of(Deal.Status.ACTIVE))).isFalse();
    }

    /**
     * Число и четвёрка признаков уезжают ОДНИМ запросом: признак,
     * отставший от числа, снял бы охрану от пересчёта на усечённом графе
     * (docs/spec/deal-lifecycle.json §benchmarkAvailabilityOnTerminal).
     * Перечни едут именами значений — колонка хранит {@code name()}.
     */
    @Test
    void theNumberAndItsFeaturesTravelInOneQuery() {
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setResultProfit(new BigDecimal("-12.5"));
        deal.setResultProfitCurrency("USDT");
        deal.setCloseOutcome(Deal.CloseOutcome.LIQUIDATION);
        deal.setRiskBenchmarkAvailability(Deal.RiskBenchmarkAvailability.MISSING);
        when(dealRepository.applyResultAndFeatures(anyLong(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        assertThat(dealDataService.applyResultAndFeatures(deal)).isTrue();

        verify(dealRepository).applyResultAndFeatures(DEAL_ID, new BigDecimal("-12.5"), "USDT",
                Deal.CloseOutcome.LIQUIDATION.name(), null, null,
                Deal.RiskBenchmarkAvailability.MISSING.name());
    }

    /**
     * Слот меряется ПАРОЙ «счёт, инструмент». Инструмент принадлежит
     * площадке, и у двух счетов одной площадки он один: гейт по одному
     * инструменту запретил бы второму счёту торговать то, что торгует
     * первый.
     */
    @Test
    void slotGateAsksAboutTheAccountInstrumentPair() {
        when(dealRepository.existsByExchangeAccountIdAndInstrumentIdAndStatusNotIn(anyLong(), anyLong(), any()))
                .thenReturn(true);

        assertThat(dealDataService.existsActiveOnPair(ACCOUNT_ID, INSTRUMENT_ID)).isTrue();

        verify(dealRepository).existsByExchangeAccountIdAndInstrumentIdAndStatusNotIn(
                ACCOUNT_ID, INSTRUMENT_ID, terminalStatuses());
    }

    /**
     * Терминальны только {@code CLOSED} и {@code EMERGENCY_CLOSED};
     * {@code ERROR} терминалом не является и из прохода не выпадает —
     * обработка аварийной тропы ещё идёт, и выброшенная из окна сделка
     * осталась бы с живым риском без ведущего.
     */
    @Test
    void errorDealStaysInsideTheOrchestratorWindow() {
        when(dealRepository.findByStatusNotInOrderByIdAsc(any(), any())).thenReturn(List.of(dealRow()));

        List<Deal> active = dealDataService.findActive(50);

        assertThat(active).singleElement()
                .extracting(Deal::getExchangeAccountId).isEqualTo(ACCOUNT_ID);
        ArgumentCaptor<List<String>> statuses = ArgumentCaptor.captor();
        verify(dealRepository).findByStatusNotInOrderByIdAsc(statuses.capture(), any(Pageable.class));
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                Deal.Status.CLOSED.name(), Deal.Status.EMERGENCY_CLOSED.name());
        assertThat(statuses.getValue()).doesNotContain(Deal.Status.ERROR.name());
    }

    /**
     * Транши сделки читаются ЦЕЛИКОМ, включая закрытые: ноги собираются их
     * обходом, а числа риска и экспозиция считаются по ногам всей сделки.
     * Фильтр по статусу опустошил бы обе коллекции ровно к терминалу, где
     * по ним и считается результат.
     */
    @Test
    void closedTrancheIsLoadedTogetherWithTheLiveOnes() {
        when(trancheRepository.findByDealIdOrderByIdAsc(DEAL_ID))
                .thenReturn(List.of(trancheRow(1L, DealTranche.Status.MANAGING),
                        trancheRow(2L, DealTranche.Status.CLOSED)));

        List<DealTranche> tranches = trancheDataService.findByDealId(DEAL_ID);

        assertThat(tranches).extracting(DealTranche::getStatus)
                .containsExactly(DealTranche.Status.MANAGING, DealTranche.Status.CLOSED);
    }

    /**
     * Идентичность счёта переживает круг domain → persistence → domain.
     * Поле новое, и потерянное на любой из половин оно оставило бы
     * торговую строку без радиуса — тихо, потому что читатели радиуса
     * приходят позже писателя.
     */
    @Test
    void accountRadiusSurvivesBothHalvesOfTheMapping() {
        Deal deal = new Deal();
        deal.setExchangeAccountId(ACCOUNT_ID);
        deal.setInstrumentId(INSTRUMENT_ID);
        deal.setStatus(Deal.Status.ACTIVE);
        deal.setEntryReason(Deal.EntryReason.RECOVERY);

        DealEntity entity = dealMapper.domainToPersistence(deal);

        assertThat(entity.getExchangeAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(entity.getEntryReason()).isEqualTo(Deal.EntryReason.RECOVERY.name());
        assertThat(dealMapper.persistenceToDomain(entity).getExchangeAccountId()).isEqualTo(ACCOUNT_ID);
    }

    private List<String> terminalStatuses() {
        return List.of(Deal.Status.CLOSED.name(), Deal.Status.EMERGENCY_CLOSED.name());
    }

    private DealEntity dealRow() {
        DealEntity entity = new DealEntity();
        entity.setId(DEAL_ID);
        entity.setInternalId("dl-1");
        entity.setExchangeAccountId(ACCOUNT_ID);
        entity.setInstrumentId(INSTRUMENT_ID);
        entity.setStatus(Deal.Status.ERROR.name());
        entity.setEntryReason(Deal.EntryReason.STRATEGY.name());
        return entity;
    }

    private DealTrancheEntity trancheRow(Long id, DealTranche.Status status) {
        DealTrancheEntity entity = new DealTrancheEntity();
        entity.setId(id);
        entity.setDealId(DEAL_ID);
        entity.setInternalId("tr-" + id);
        entity.setStatus(status.name());
        entity.setEpisodeSeq(1);
        return entity;
    }
}

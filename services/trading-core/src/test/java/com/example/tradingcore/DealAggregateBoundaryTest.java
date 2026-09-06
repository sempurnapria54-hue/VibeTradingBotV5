package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

/**
 * Границы выборки агрегата сделки: чем меряется занятость слота, что
 * входит в проход и в каком объёме читаются транши.
 *
 * <p>Проверяется не маппинг полей, а <b>три решения, которые молча
 * ломаются оптимизацией</b>: радиус слота (пара, а не инструмент), состав
 * терминальных статусов (аварийная сделка проходом ведётся) и объём
 * траншей (целиком, включая закрытые).
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

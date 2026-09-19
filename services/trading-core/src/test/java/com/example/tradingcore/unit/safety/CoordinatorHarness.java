package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.ACTOR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.security.ActorProvider;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.deal.DealStatusEdgeService;
import com.example.tradingcore.domain.safety.AnomalyReport;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HardRungShutdownReasonResolver;
import com.example.tradingcore.domain.safety.HoldRungEdgeService;
import com.example.tradingcore.domain.safety.KillSwitchService;
import com.example.tradingcore.domain.safety.SafetyHoldCoordinator;
import com.example.tradingcore.integration.internal.event.CoreEventWriter;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import java.util.List;

/**
 * Базовая сборка групп `U5`-`U7` документа
 * `.claude/tests/cases/trading-core-safety.md`: координатор полной
 * реакции со своими коллабораторами.
 *
 * <p><b>Ребро подъёма здесь настоящее, и это следует из перечня
 * подмен.</b> Документ называет подменёнными у координатора исполнителя
 * снятия риска и сервис отчёта, а ребра среди них нет; при этом
 * отрицательное ожидание «факта подъёма нет» (U6.2, U6.5, U7.3)
 * наблюдаемо только у писателя фактов, то есть ВНУТРИ ребра. Поэтому
 * ребро собрано настоящим над подменёнными службами персистентности,
 * поставщиком актора и писателем фактов — ровно над теми, у кого есть
 * ввод-вывод (§«Чем достаются выходы»).
 *
 * <p><b>Читатель причины тоже настоящий:</b> каскад обязан взять причину
 * у него, а не вывести её из радиуса своего сигнала, и подменённый
 * читатель это ожидание снял бы.
 */
final class CoordinatorHarness {

    /** Служба состояния пары «счёт, инструмент»: гард перехода её лестницы. */
    final AccountInstrumentStateDataService pairStates =
            mock(AccountInstrumentStateDataService.class);

    /** Служба биржевого счёта: гард перехода его лестницы. */
    final ExchangeAccountDataService accounts = mock(ExchangeAccountDataService.class);

    /** Поставщик актора хода. */
    final ActorProvider actorProvider = mock(ActorProvider.class);

    /** Писатель фактов: единственный наблюдатель «факт подъёма есть либо нет». */
    final CoreEventWriter coreEventWriter = mock(CoreEventWriter.class);

    /** Сервис отчёта — соседняя единица предмета, не предмет этих групп. */
    final AnomalyReportService reports = mock(AnomalyReportService.class);

    /** Исполнитель снятия риска — у него ввод-вывод к площадке по построению. */
    final KillSwitchService killSwitchService = mock(KillSwitchService.class);

    /** Служба сделок: популяция каскада и множества стоящих ступеней. */
    final DealDataService deals = mock(DealDataService.class);

    /** Ребро энфорсмента: что оно делает со статусом — предмет `trading-core-fsm`. */
    final DealStatusEdgeService statusEdges = mock(DealStatusEdgeService.class);

    /** Ребро подъёма — настоящее. */
    final HoldRungEdgeService rungEdge =
            new HoldRungEdgeService(pairStates, accounts, actorProvider, coreEventWriter);

    /** Читатель причины — настоящий. */
    final HardRungShutdownReasonResolver reasonResolver =
            new HardRungShutdownReasonResolver(deals);

    /** Отчёт, который отдаёт подменённый сервис: его статусы и наблюдаются. */
    final AnomalyReport report = report(501L);

    /** Предмет групп. */
    final SafetyHoldCoordinator coordinator = new SafetyHoldCoordinator(reports, killSwitchService,
            deals, statusEdges, rungEdge, reasonResolver);

    /**
     * Базовая сборка: ребро отвечает «переставилась»; снятие риска
     * подтверждает; сервис отчёта принимает все переходы; активных сделок
     * радиуса нет, пока группа не назовёт их сама.
     */
    CoordinatorHarness() {
        when(pairStates.raiseRung(anyLong(), anyLong(), any())).thenReturn(true);
        when(accounts.raiseRung(anyLong(), any())).thenReturn(true);
        when(actorProvider.currentActor()).thenReturn(ACTOR);
        when(reports.open(any(), any())).thenReturn(report);
        when(killSwitchService.fireInstrument(any())).thenReturn(true);
        when(killSwitchService.fireExchangeAccount(anyLong())).thenReturn(true);
        when(deals.findCascadeSourceOnAccount(anyLong())).thenReturn(List.of());
        when(deals.findCascadeSourceOnPair(anyLong(), anyLong())).thenReturn(List.of());
        when(deals.findIdsUnderAccountRung(any())).thenReturn(List.of());
        when(deals.findIdsUnderInstrumentRung(any())).thenReturn(List.of());
        when(statusEdges.enforceHardRung(any(), any())).thenReturn(true);
    }

    /** Популяция каскада инструментного радиуса. */
    void cascadeOnPair(List<Deal> population) {
        when(deals.findCascadeSourceOnPair(anyLong(), anyLong())).thenReturn(population);
    }

    /** Популяция каскада счётного радиуса. */
    void cascadeOnAccount(List<Deal> population) {
        when(deals.findCascadeSourceOnAccount(anyLong())).thenReturn(population);
    }

    /** Отчёт названной идентичности. */
    static AnomalyReport report(Long id) {
        AnomalyReport report = new AnomalyReport();
        report.setId(id);
        report.setInternalId("ANR-" + id);
        return report;
    }
}

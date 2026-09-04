package com.example.tradingbot.domain.command.executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.calc.DealReconciliationCalculator;
import com.example.tradingbot.domain.command.calc.DealResult;
import com.example.tradingbot.domain.command.calc.DealResultCalculator;
import com.example.tradingbot.domain.command.calc.DealTerminalFeaturesWriter;
import com.example.tradingbot.domain.deal.DealTerminalGate;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.safety.AnomalyReportService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Связывает объявленные журнальные отчёты терминалов с их писателями
 * (docs/components/MarkDealClosedExecutor.md,
 * docs/components/MarkDealEmergencyClosedExecutor.md).
 *
 * <p>Несущее для этого теста — <b>что омиссия персистентна, а не
 * залогирована</b>. Носитель наблюдаемости — запись, не лог: лог не
 * запрашивается, не агрегируется и не переживает ротацию, и сослаться на
 * него при разборе нельзя (docs/concept.md, П3). У неисчислимого итога
 * клейм сильнее: невключение неизвестного исхода в расчёт ожидаемости
 * корректно только при ИЗВЕСТНОМ числе таких случаев, а без строки оно
 * тождественно нулю — то есть систематически занижено, и дефект скрывает
 * сам себя.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TerminalOmissionReportsTest {

    private static final Long DEAL_ID = 11L;

    @Mock
    private DealDataService dealDataService;
    @Mock
    private DealActionStateDataService dealActionStateDataService;
    @Mock
    private DealResultCalculator resultCalculator;
    @Mock
    private DealTerminalFeaturesWriter featuresWriter;
    @Mock
    private DealReconciliationCalculator reconciliationCalculator;
    @Mock
    private DealTerminalGate terminalGate;
    @Mock
    private AnomalyReportService anomalyReportService;

    @InjectMocks
    private MarkDealEmergencyClosedExecutor emergencyExecutor;

    @Test
    @DisplayName("Неисчислимый итог аварийного терминала оставляет СТРОКУ, а не только лог")
    void notComputableResultLeavesReport() {
        DealContext context = context(null);
        when(terminalGate.riskProvenAbsent(any(), any(), any())).thenReturn(Boolean.TRUE);
        when(resultCalculator.calculate(any())).thenReturn(unavailableResult());

        emergencyExecutor.execute(null, null, context);

        verify(anomalyReportService).journal(any(), any());
    }

    @Test
    @DisplayName("Контроль: посчитанный итог строки не заводит — омиссии нет")
    void computedResultLeavesNoReport() {
        DealContext context = context(null);
        when(terminalGate.riskProvenAbsent(any(), any(), any())).thenReturn(Boolean.TRUE);
        when(resultCalculator.calculate(any())).thenReturn(availableResult());

        emergencyExecutor.execute(null, null, context);

        verify(anomalyReportService, never()).journal(any(), any());
    }

    private DealResult unavailableResult() {
        return new DealResult(false, null, null);
    }

    private DealResult availableResult() {
        return new DealResult(true, BigDecimal.TEN, "USDT");
    }

    private DealContext context(BigDecimal resultProfit) {
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setResultProfit(resultProfit);
        Instrument instrument = new Instrument();
        instrument.setId(7L);
        Exchange exchange = new Exchange();
        exchange.setId(1L);
        return DealContext.builder()
                .deal(deal)
                .instrument(instrument)
                .exchange(exchange)
                .build();
    }
}

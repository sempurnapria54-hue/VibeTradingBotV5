package com.example.tradingcore.unit.calc;

import static com.example.tradingcore.unit.calc.CalcFixture.contourProperties;
import static com.example.tradingcore.unit.calc.CalcFixture.workingTolerance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.tradingcore.config.ExchangeContourProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.calc.DealReconciliationCalculator;
import com.example.tradingcore.domain.command.calc.DealTerminalFeatures;
import com.example.tradingcore.domain.command.calc.DealTerminalFeaturesWriter;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldSignal;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Сборка писателя признаков терминала — групп {@code U6}-{@code U8}
 * документа `.claude/tests/cases/trading-core-calc.md`.
 *
 * <p><b>Сверка настоящая, журнал подменён.</b> Писатель зовёт
 * {@code DealReconciliationCalculator} сам, и подменять его значило бы
 * проверять писателя против сверки, которой в проде нет
 * (`.claude/rules/codestyle.md` §«Тесты доменных моделей»). Журнал
 * происшествий — единственная граница предмета: он ходит в базу.
 */
final class TerminalFeaturesHarness {

    private final AnomalyReportService reports = mock(AnomalyReportService.class);

    private final DealTerminalFeaturesWriter writer;

    /** Сборка на контуре с названными исключениями сверки. */
    TerminalFeaturesHarness(String... exclusions) {
        this(contourProperties(exclusions));
    }

    /** Сборка на названном контуре — для кейса без секции площадки. */
    TerminalFeaturesHarness(ExchangeContourProperties contour) {
        this.writer = new DealTerminalFeaturesWriter(
                new DealReconciliationCalculator(contour, workingTolerance()), contour, reports);
    }

    /** Посчитать и записать признаки терминального ребра. */
    DealTerminalFeatures apply(DealContext dealContext, Boolean resultFinalized) {
        return writer.apply(dealContext, resultFinalized);
    }

    /** Подменённый журнал — для кейсов, задающих ему поведение отказа. */
    AnomalyReportService reports() {
        return reports;
    }

    /** Коды журнальных отчётов в порядке вызовов. */
    List<String> journalledCodes() {
        ArgumentCaptor<HoldSignal> signals = ArgumentCaptor.forClass(HoldSignal.class);
        verify(reports, Mockito.atLeast(0)).journal(any(), signals.capture());
        return signals.getAllValues().stream().map(HoldSignal::getCode).toList();
    }
}

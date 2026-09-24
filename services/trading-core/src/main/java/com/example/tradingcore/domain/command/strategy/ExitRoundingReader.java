package com.example.tradingcore.domain.command.strategy;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.strategy.engine.calc.CalculatedSize;
import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.ExitOutcome;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.util.Constants;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Читатель исхода округления reduce-only выхода
 * ({@link CalculatedSize#getExitOutcome()}) у per-type исполнителей
 * действия выхода (docs/components/SizeCalculator.md, таблица исходов).
 *
 * <ul>
 *   <li>{@code SKIPPED} — действие не отправляется: строка исполнения
 *       уходит в {@code SKIPPED}, отчёт {@code PARTIAL_EXIT_BELOW_MIN_SIZE};</li>
 *   <li>{@code FULL} — отправляется экспозицией транша целиком, отчёт
 *       {@code PARTIAL_EXIT_ROUNDED_TO_FULL};</li>
 *   <li>{@code PARTIAL}, {@code FULL_BY_FRACTION} и не-выход — без отчёта.</li>
 * </ul>
 *
 * <p><b>Отчёт — строка происшествия, ключ — строка исполнения</b>: одно
 * решение по строке — один отчёт, сколько бы раз строку ни планировал
 * повтор. Калькулятор отчёт не заводит и действие не отменяет — он только
 * называет исход; решает здесь, у того, кто отправляет.
 *
 * <p><b>Отчёт решения не блокирует.</b> Сбой записи отчёта логируется, а
 * решение об отправке либо пропуске стои́т: иначе недоступность журнала
 * меняла бы торговое поведение.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExitRoundingReader {

    private static final String SUBJECT_PREFIX = "dealActionState:";

    private final AnomalyReportService anomalyReportService;
    private final DealActionStateDataService dealActionStateDataService;

    /**
     * Пропуск выхода ниже минимального размера: план без команды, строка
     * терминальна. Пусто — исход не {@code SKIPPED}, и действие идёт дальше.
     */
    public Optional<ActionPlan> skipBelowMinSize(CalculatedStrategyAction calculated, CalculationContext context,
                                                 DealActionState state, DealContext dealContext,
                                                 DealTranche tranche) {
        if (isFalse(ExitOutcome.SKIPPED.equals(outcomeOf(calculated)))) {
            return Optional.empty();
        }
        state.setStatus(DealActionStateStatus.SKIPPED);
        dealActionStateDataService.save(state);
        journal(Constants.Hold.PARTIAL_EXIT_BELOW_MIN_SIZE, calculated, context, state, dealContext, tranche);
        log.info("Exit below min size skipped dealActionStateId={} actionKey={}",
                state.getId(), context.getAction().getKey());
        return Optional.of(ActionPlan.nothing());
    }

    /** Округление частичного выхода до полного — отчёт у отправляемого действия. */
    public void journalRoundedToFull(CalculatedStrategyAction calculated, CalculationContext context,
                                     DealActionState state, DealContext dealContext, DealTranche tranche) {
        if (ExitOutcome.FULL.equals(outcomeOf(calculated))) {
            journal(Constants.Hold.PARTIAL_EXIT_ROUNDED_TO_FULL, calculated, context, state, dealContext,
                    tranche);
        }
    }

    private ExitOutcome outcomeOf(CalculatedStrategyAction calculated) {
        CalculatedSize size = calculated.getCalculatedSize();
        return isNull(size) ? null : size.getExitOutcome();
    }

    private void journal(String code, CalculatedStrategyAction calculated, CalculationContext context,
                         DealActionState state, DealContext dealContext, DealTranche tranche) {
        try {
            anomalyReportService.journalOnce(dealContext, HoldSignal.instrumentJournal(code),
                    SUBJECT_PREFIX + state.getId(), operands(calculated.getCalculatedSize(), context, tranche));
        } catch (RuntimeException e) {
            log.error("Journal {} failed dealActionStateId={}", code, state.getId(), e);
        }
    }

    /**
     * Операнды округления: из них исход выводится заново без обращения к
     * площадке (docs/spec/order-sizing.json, {@code exitOutcome}).
     */
    private Map<String, Object> operands(CalculatedSize size, CalculationContext context, DealTranche tranche) {
        Map<String, Object> rounding = new LinkedHashMap<>();
        rounding.put("exitOutcome", size.getExitOutcome());
        rounding.put("closeFraction", size.getCloseFraction());
        rounding.put("trancheExposure", isNull(tranche) ? null : tranche.exposure());
        rounding.put("exitSize", size.getExitSize());
        rounding.put("exitRemainder", size.getExitRemainder());
        rounding.put("sizeContracts", size.getSizeContracts());
        InstrumentExternalRules rules = context.getInstrumentExternalRules();
        rounding.put("instrumentMinSize", isNull(rules) ? null : rules.minSize());
        rounding.put("instrumentMinSizeSource", isNull(rules) ? null
                : "instrumentExternalRules.externalMinSize=" + rules.getExternalMinSize());
        Map<String, Object> operands = new LinkedHashMap<>();
        operands.put("exitRounding", rounding);
        return operands;
    }
}

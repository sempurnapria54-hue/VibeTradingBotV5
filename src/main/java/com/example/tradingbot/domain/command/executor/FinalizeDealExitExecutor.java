package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.calc.DealResult;
import com.example.tradingbot.domain.command.calc.DealResultCalculator;
import com.example.tradingbot.domain.command.calc.DealTerminalFeaturesWriter;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет FINALIZE_DEAL_EXIT_COMMAND — считает итоговый результат
 * сделки и записывает его вместе с четвёркой признаков отбора ОДНОЙ
 * транзакцией с продвижением своего исполнения. Опирается на уже добытые
 * факты, на биржу не ходит, преконтроль не вызывает.
 *
 * <p><b>Звено завершается только по ДОСТУПНОМУ итогу</b>
 * (docs/spec/deal-result.json §finalizationCompletes). Недоступный итог
 * даёт не один исход, а два, и различает их бюджет попыток строки: бюджет
 * жив — ожидание продолжается следующей попыткой; бюджет исчерпан —
 * ошибочная тропа. Поэтому отказ здесь классифицируется повторяемым:
 * развязку ведёт бюджет, а не тип ошибки.
 *
 * <p><b>Число и признаки едут одной транзакцией</b>, и это несущее:
 * durable-факт «число финализировано» служит охраной от перезаписи
 * признаков аварийным терминалом, приходящим на усечённом графе
 * (docs/spec/deal-lifecycle.json §benchmarkAvailabilityOnTerminal).
 *
 * <p>См. docs/components/FinalizeDealExitExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class FinalizeDealExitExecutor implements CommandExecutor {

    private final DealDataService dealDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final DealResultCalculator resultCalculator;
    private final DealTerminalFeaturesWriter featuresWriter;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.FINALIZE_DEAL_EXIT_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (nonNull(deal.getResultProfit())) {
            // Повтор по уже посчитанному числу — no-op: число write-once по построению тропы.
            return complete(actionState);
        }
        DealResult result = resultCalculator.calculate(dealContext);
        if (isFalse(result.getAvailable())) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.EXCHANGE_ERROR,
                    "итог сделки недоступен: не добыта запись закрытия эпизода, разбивка движений неполна"
                            + " либо движение чужой валюты ждёт курса (docs/spec/deal-result.json)");
        }
        deal.setResultProfit(result.getResultProfit());
        deal.setResultProfitCurrency(result.getResultProfitCurrency());
        featuresWriter.apply(dealContext, false);
        dealDataService.save(deal);
        return complete(actionState);
    }

    private ServiceCommandExecutionResult complete(DealActionState actionState) {
        if (isNull(actionState)) {
            return ServiceCommandExecutionResult.ok();
        }
        actionState.setStatus(DealActionStateStatus.COMPLETED);
        dealActionStateDataService.save(actionState);
        return ServiceCommandExecutionResult.ok();
    }
}

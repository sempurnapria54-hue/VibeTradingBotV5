package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.workingAppetite;
import static com.example.tradingcore.unit.risk.RiskFixture.workingPairState;
import static com.example.tradingcore.unit.risk.RiskFixture.workingRules;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.tenant.Tenant;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import com.example.tradingcore.domain.command.risk.RiskValidator;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.InstrumentExternalRulesDataService;
import com.example.tradingcore.persistence.service.TenantRiskAppetiteDataService;
import java.util.List;
import java.util.Optional;

/**
 * Валидатор со своими тремя границами хранилища
 * (`.claude/tests/cases/trading-core-risk.md` §«Чем достаются выходы»).
 *
 * <p><b>Подменяется ровно то, у чего есть ввод-вывод:</b> справочные
 * правила инструмента, строка пары «счёт, инструмент» и числа
 * риск-аппетита тенанта — три операнда, которые валидатор читает
 * СВОЕЙ тропой, а не аргументом. Перечни, свёртки, карта и арифметика
 * работают целиком.
 *
 * <p><b>Умолчания стоя́т в конструкторе, а не в сборке контекста:</b>
 * контекст собирается аргументом вызова, то есть ПОСЛЕ тела теста, и
 * затирал бы стабы, которыми тест как раз и задаёт свой предмет.
 */
final class RiskHarness {

    private final InstrumentExternalRulesDataService rulesDataService =
            mock(InstrumentExternalRulesDataService.class);

    private final AccountInstrumentStateDataService pairStateDataService =
            mock(AccountInstrumentStateDataService.class);

    private final TenantRiskAppetiteDataService appetiteDataService =
            mock(TenantRiskAppetiteDataService.class);

    private final RiskValidator validator =
            new RiskValidator(rulesDataService, pairStateDataService, appetiteDataService);

    RiskHarness() {
        givenRules(workingRules());
        givenPairState(workingPairState());
        givenAppetite(workingAppetite());
    }

    /** Справочные правила инструмента, которые отдаёт граница; пусто — не материализованы. */
    void givenRules(InstrumentExternalRules rules) {
        when(rulesDataService.findByInstrumentId(any(), any())).thenReturn(Optional.ofNullable(rules));
    }

    /** Строка пары, которую отдаёт граница. */
    void givenPairState(AccountInstrumentState pairState) {
        when(pairStateDataService.getRequiredByPair(any(), any())).thenReturn(pairState);
    }

    /** Чтение строки пары отказывает названным исключением. */
    void givenPairStateFails(RuntimeException failure) {
        when(pairStateDataService.getRequiredByPair(any(), any())).thenThrow(failure);
    }

    /** Строка риск-аппетита тенанта; пусто — строки нет вовсе. */
    void givenAppetite(Tenant appetite) {
        when(appetiteDataService.findByTenantInternalId(any())).thenReturn(Optional.ofNullable(appetite));
    }

    /** Преконтроль рассчитанного действия; транша у действия нет. */
    RiskValidationResult validate(CalculatedStrategyAction action, DealContext dealContext) {
        return validator.validate(action, dealContext, null);
    }

    /** Преконтроль рассчитанного действия названного транша. */
    RiskValidationResult validate(CalculatedStrategyAction action, DealContext dealContext, DealTranche tranche) {
        return validator.validate(action, dealContext, tranche);
    }

    /** Вторая точка входа: те же неравенства при нулевом акте. */
    List<RiskCheckResult> ceilingsBreachedWithoutAct(DealContext dealContext) {
        return validator.ceilingsBreachedWithoutAct(dealContext);
    }

    /** Сам валидатор — под кейсы ветки снятия защиты и узла-гейта. */
    RiskValidator validator() {
        return validator;
    }

    /** Граница справочных правил — под отрицательные ожидания «не читается». */
    InstrumentExternalRulesDataService rulesBoundary() {
        return rulesDataService;
    }

    /** Граница строки пары — под отрицательные ожидания «не читается». */
    AccountInstrumentStateDataService pairStateBoundary() {
        return pairStateDataService;
    }

    /** Граница чисел риск-аппетита — под отрицательные ожидания «не читается». */
    TenantRiskAppetiteDataService appetiteBoundary() {
        return appetiteDataService;
    }
}

package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.BALANCE_FRESHNESS;
import static com.example.tradingcore.unit.risk.RiskFixture.workingAppetite;
import static com.example.tradingcore.unit.risk.RiskFixture.workingPairState;
import static com.example.tradingcore.unit.risk.RiskFixture.workingRules;
import static java.util.Objects.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingcore.config.DealContextProperties;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import com.example.tradingcore.domain.command.risk.RiskValidator;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.model.RiskAppetite;
import com.example.tradingcore.domain.service.RiskAppetiteService;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentExternalRulesDataService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Валидатор со своими тремя границами хранилища и конфигурацией прохода
 * (`.claude/tests/cases/trading-core-risk.md` §«Чем достаются выходы»).
 *
 * <p><b>Подменяется ровно то, у чего есть ввод-вывод:</b> справочные
 * правила инструмента, строка пары «счёт, инструмент», принятые ядром
 * числа риск-аппетита и операнды потолков уровней — счета тенанта и живые
 * сделки уровня с их графами. Это операнды, которые валидатор читает СВОЕЙ
 * тропой, а не аргументом. Перечни, свёртки, карта и арифметика работают
 * целиком.
 *
 * <p><b>По умолчанию база тенанта — счёт самой проверяемой сделки, соседей
 * нет:</b> проекция реестра в проде отдаёт ту же строку счёта, что лежит в
 * контексте прохода, и база тенанта равна её базе. Стаб поэтому ставится
 * из контекста вызова, а не константой: постоянная база в 10000 расходилась
 * бы с контекстом, чья база иная (миллион у клеток оценки ликвидации), и
 * потолок тенанта отвергал бы здорового тенанта — ровно этим упали U33.5,
 * U33.6, U33.8 и U33.9. Клетка, которой нужен иной состав счетов тенанта,
 * называет его сама ({@link #givenTenantAccounts}).
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

    private final RiskAppetiteService appetiteService = mock(RiskAppetiteService.class);

    private final ExchangeAccountDataService accountDataService = mock(ExchangeAccountDataService.class);

    private final DealDataService dealDataService = mock(DealDataService.class);

    private final DealContextService dealContextService = mock(DealContextService.class);

    private final DealContextProperties properties = new DealContextProperties();

    /** Клетка назвала состав счетов тенанта сама — умолчание из контекста не ставится. */
    private boolean tenantAccountsNamed;

    private final RiskValidator validator = new RiskValidator(rulesDataService, pairStateDataService,
            appetiteService, accountDataService, dealDataService, dealContextService, properties);

    RiskHarness() {
        givenRules(workingRules());
        givenPairState(workingPairState());
        givenAppetite(workingAppetite());
        givenBalanceFreshness(BALANCE_FRESHNESS);
    }

    /**
     * Толерантность прохода к возрасту снимка средств — конфигурация, а не
     * граница хранилища: по ней преконтроль решает, мерить ли проверки
     * средств счёта. Пусто — толерантность не объявлена.
     */
    void givenBalanceFreshness(Duration freshness) {
        properties.setBalanceFreshness(freshness);
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

    /** Принятые ядром числа риск-аппетита. */
    void givenAppetite(RiskAppetite appetite) {
        when(appetiteService.getAccepted()).thenReturn(appetite);
    }

    /** Счета тенанта в статусе {@code ACTIVE}, которые отдаёт граница проекции. */
    void givenTenantAccounts(List<ExchangeAccount> accounts) {
        tenantAccountsNamed = true;
        when(accountDataService.findActiveByTenantInternalId(any())).thenReturn(accounts);
    }

    /**
     * Состав счетов тенанта по умолчанию — счёт проверяемой сделки, каким он
     * лежит в контексте; названный клеткой состав не перекрывается.
     */
    private void defaultTenantAccountsFrom(DealContext dealContext) {
        if (tenantAccountsNamed || isNull(dealContext.getExchangeAccount())) {
            return;
        }
        when(accountDataService.findActiveByTenantInternalId(any()))
                .thenReturn(List.of(dealContext.getExchangeAccount()));
    }

    /**
     * Живые сделки уровня, которые отдаёт граница сделок. Граф каждой
     * собран тестом заранее: перечитывание графа подменено и сделку не
     * трогает.
     */
    void givenLevelDeals(List<Deal> deals) {
        when(dealDataService.findNonTerminalByExchangeAccountIds(any())).thenReturn(deals);
    }

    /** Преконтроль рассчитанного действия; транша у действия нет. */
    RiskValidationResult validate(CalculatedStrategyAction action, DealContext dealContext) {
        return validate(action, dealContext, null);
    }

    /** Преконтроль рассчитанного действия названного транша. */
    RiskValidationResult validate(CalculatedStrategyAction action, DealContext dealContext, DealTranche tranche) {
        defaultTenantAccountsFrom(dealContext);
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

    /** Принимающее звено чисел риск-аппетита — под отрицательные ожидания «не читается». */
    RiskAppetiteService appetiteBoundary() {
        return appetiteService;
    }

    /** Граница живых сделок уровня — под ожидания «соседей не грузят». */
    DealDataService levelDealsBoundary() {
        return dealDataService;
    }
}

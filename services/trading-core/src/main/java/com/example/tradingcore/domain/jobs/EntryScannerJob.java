package com.example.tradingcore.domain.jobs;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingcore.config.EntryScannerProperties;
import com.example.tradingcore.domain.deal.DealOpeningService;
import com.example.tradingcore.domain.market.MarketFeatureService;
import com.example.tradingcore.domain.market.MarketFeatures;
import com.example.tradingcore.integration.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.StrategyDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ищет возможность создать новую сделку и создаёт <b>только сделку</b>:
 * заявок не выставляет, позиции не открывает, риск не проверяет
 * (docs/components/EntryScannerJob.md).
 *
 * <p><b>Выборка идёт парами «биржевой счёт, инструмент».</b> Стратегия
 * называет счёт, а инструмент принадлежит площадке, и у двух счетов одной
 * площадки он один (docs/architecture/tenant-and-exchange.md §«Торговая
 * строка называет счёт, и радиусы читаются от него»).
 *
 * <p><b>Ступени обеих лестниц энфорсятся выборкой.</b> Счёт под холдом
 * любой ступени в выборку не входит; инструмент со стоящей ступенью на
 * этом счёте отсеивается одним чтением на счёт. Живые сделки ни та, ни
 * другая ступень здесь не трогает — гасится только НОВЫЙ вход.
 *
 * <p><b>Контурная половина гейта поднята на уровень счёта.</b> Она
 * спрашивает «есть ли у счёта незакрытая сделка хоть по одной паре», то
 * есть от инструмента не зависит: проверять её внутри обхода значило бы
 * задавать один и тот же вопрос на каждый инструмент каталога. Заведённая
 * сделка закрывает счёт до следующего тика, поэтому обход по нему
 * прекращается.
 *
 * <p><b>Гейт свежести данных входа выражен раскладкой фич.</b> Владелец
 * отдаёт только свежее по сроку спрашивающей настройки, поэтому
 * отсутствующий ключ и есть ответ «данным доверять нельзя»: шаг, чьи
 * операнды не покрыты, до оценки условия не доходит.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntryScannerJob {

    private static final String JOB_NAME = "entryScannerJob";

    private final EntryScannerProperties properties;
    private final JobExecutionGuard executionGuard;
    private final ExchangeAccountDataService exchangeAccountDataService;
    private final AccountInstrumentStateDataService accountInstrumentStateDataService;
    private final InstrumentDataService instrumentDataService;
    private final StrategyDataService strategyDataService;
    private final DealDataService dealDataService;
    private final MarketFeatureService marketFeatureService;
    private final StrategyConditionEvaluator conditionEvaluator;
    private final DealOpeningService dealOpeningService;
    private final ExchangeOperationsClient exchangeOperationsClient;

    @Scheduled(cron = "${entry-scanner.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    private void run() {
        for (ExchangeAccount account : exchangeAccountDataService.findEntryEligibleAccounts()) {
            scanAccountSafely(account);
        }
    }

    /**
     * Отказ отбора по одному счёту отбор по остальным не отменяет: счета
     * независимы, и общая ветка — не выход из тика.
     */
    private void scanAccountSafely(ExchangeAccount account) {
        try {
            scanAccount(account);
        } catch (RuntimeException e) {
            log.error("Entry scan failed exchangeAccountId={}", account.getId(), e);
        }
    }

    private void scanAccount(ExchangeAccount account) {
        if (isTrue(dealDataService.existsActiveOnAccount(account.getId()))) {
            return;
        }
        Set<Long> blocked = new HashSet<>(
                accountInstrumentStateDataService.findInstrumentIdsWithStandingRung(account.getId()));
        for (Instrument instrument : instrumentDataService.findTradable(account.getExchangeCode(),
                properties.getInstrumentWindow())) {
            if (blocked.contains(instrument.getId())) {
                continue;
            }
            if (isTrue(scanPair(account, instrument))) {
                // Сделка заведена: контурная половина гейта закрывает счёт
                // до следующего тика, и обход по нему продолжать незачем.
                return;
            }
        }
    }

    /**
     * Один шаг обхода: пара «счёт, инструмент».
     *
     * <p><b>Проверки парного радиуса здесь нет, и это не пропуск гейта.</b>
     * Обход начинается ПОСЛЕ контурной проверки «нет активной сделки ни по
     * одной паре счёта», а множество активных сделок пары есть подмножество
     * множества активных сделок счёта: пройдя контурную, парная не может
     * ответить иначе как «свободно» — то есть дала бы чтение на каждый
     * инструмент окна с заранее известным ответом
     * (.claude/rules/codestyle.md §«Выборка данных»). Сам радиус пары при
     * этом энфорсится, и обоими своими носителями: защитной проверкой в
     * транзакции создания (docs/components/DealOpeningService.md) и
     * инвариантом базы {@code uk_deal_active_account_instrument}
     * (docs/components/EntryScannerJob.md §«Гейт входа»).
     *
     * @return сделка заведена
     */
    private Boolean scanPair(ExchangeAccount account, Instrument instrument) {
        Strategy strategy = strategyDataService
                .findActiveOnPairWithTree(account.getId(), instrument.getId())
                .orElse(null);
        if (isNull(strategy)) {
            return false;
        }
        MarketFeatures features = marketFeatureService.readForEntry(strategy, instrument);
        MarketPhase.Type phaseType = features.phaseType();
        if (isNull(phaseType)) {
            // Фаза не резолвится — выбирать деталь не по чему. Пустота
            // здесь консервативна: отбор молчит, а не берёт деталь наугад.
            return false;
        }
        StrategyDetail detail = strategy.detailForPhase(phaseType).orElse(null);
        if (isNull(detail) || isFalse(detail.allowsEntryFor(phaseType))) {
            return false;
        }
        return openOnFirstMatchingStep(account, instrument, detail, features, phaseType);
    }

    /**
     * Первый входной шаг детали, чьё условие выполнено, заводит сделку.
     *
     * <p>Шаг, чьи рыночные операнды не покрыты снятой раскладкой, до
     * оценки условия не доходит: устаревшее и отсутствующее ключа не
     * занимают, и вход по ним не открывается.
     *
     * <p><b>Направление читается с действия шага</b>, а не приходит
     * параметром: объявил его автор стратегии, и второй носитель
     * разошёлся бы с объявлением.
     *
     * <p><b>Биржевой момент добывается здесь</b>, потому что создание
     * сделки на биржу не ходит по своему контракту, а момент нужен
     * биржевого домена: он служит нижней границей окна линковки движений,
     * пока собственной границы ещё нет.
     */
    private Boolean openOnFirstMatchingStep(ExchangeAccount account, Instrument instrument,
                                            StrategyDetail detail, MarketFeatures features,
                                            MarketPhase.Type phaseType) {
        for (StrategyStep step : detail.entrySteps()) {
            if (isFalse(features.covers(step.getCondition()))) {
                log.debug("Entry step operands are not covered by fresh features stepId={}", step.getId());
                continue;
            }
            if (isFalse(conditionEvaluator.evaluate(step.getCondition(), entryContext(features)))) {
                continue;
            }
            StrategyOrderAction entryAction = step.firstOrderAction().orElse(null);
            if (isNull(entryAction)) {
                continue;
            }
            return dealOpeningService.openDeal(account, instrument, detail, entryAction.getDirection(),
                            phaseType, exchangeOperationsClient.getServerTime())
                    .isPresent();
        }
        return false;
    }

    /**
     * Контекст оценки входного условия: рыночная половина плюс момент
     * решения. <b>Фактов сделки у него нет ни одного</b>, и эта пустота —
     * whitelist контекста: правила, читающие эпизод, транш и фазу входа,
     * оказываются на пустом операнде и консервативно ложны
     * (docs/spec/deal-condition.json).
     */
    private ConditionEvaluationContext entryContext(MarketFeatures features) {
        return features.conditionOperands()
                .evaluationTime(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}

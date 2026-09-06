package com.example.tradingcore.domain.safety;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.config.ManualHaltProperties;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Ручное управление safety-остановкой: держатель управляет теми же
 * ступенями, что и автоматика, — тем же механизмом и через ту же точку
 * входа (docs/rules/manual-halt.md). Исполнимая форма —
 * docs/spec/manual-halt.json.
 *
 * <p><b>Собственного механизма остановки здесь нет.</b> Постановка
 * собирает сигнал и зовёт общего исполнителя блокировки. Снятие через
 * него не идёт — «снятие холда — ручная сервисная операция, не этот
 * путь», — и потому несёт идемпотентность само: её даёт <b>явно названная
 * ступень</b>, из-за которой повтор попадает в холостой ход, а не в
 * следующий шаг лестницы.
 *
 * <p><b>Отказ при запуске синхронен и виден вызывающему сразу.</b>
 * Асинхронный отказ невидим: держатель получил бы {@code 202}, ступень не
 * поднята, отчёта нет, а он считает объект остановленным — это
 * разрешающая ошибка. Полный класс отказать при запуске не может (пара
 * всегда допустима, а по статусу он не отказывает), поэтому асинхронная
 * форма у него отказа не прячет.
 *
 * <p><b>Право на доведение недоделанного есть только здесь.</b> Признак
 * происхождения сигнала несёт вызов: автоматический детектор поглощается
 * анкером, явный вызов держателя при непогашенном риске гоняет снятие
 * риска заново — это единственный выход из ступени сворачивания с
 * неподтверждённым снятием риска.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualHaltService {

    private final ExchangeAccountDataService exchangeAccountDataService;
    private final InstrumentDataService instrumentDataService;
    private final AccountInstrumentStateDataService accountInstrumentStateDataService;
    private final DealDataService dealDataService;
    private final DealContextService dealContextService;
    private final DealTerminalGate dealTerminalGate;
    private final SafetyHoldCoordinator safetyHoldCoordinator;
    private final AnomalyReportService anomalyReportService;
    private final ManualHaltProperties properties;

    /**
     * Постановка: поднять названную ступень названного радиуса.
     *
     * <p><b>Мягкий класс отказывает по статусу, полный — нет.</b>
     * Множество входа мягкой ступени — только рабочее состояние объекта
     * либо уже стоящая ступень своего радиуса; авария же застаёт объект в
     * любом состоянии.
     *
     * @param instrumentInternalId инструмент радиуса; пусто — радиус счёта
     */
    public void raise(ManualHaltClass haltClass, String accountInternalId, String instrumentInternalId) {
        HoldScope scope = scopeOf(haltClass, instrumentInternalId);
        DealContext context = objectContext(scope, accountInternalId, instrumentInternalId);
        refuseUnlessRaiseAllowed(haltClass, scope, context);
        HoldSignal signal = signalOf(haltClass, scope);
        if (isTrue(teardownRetry(haltClass, scope, context))) {
            log.warn("Holder retries the risk teardown scope={} accountInternalId={}", scope,
                    accountInternalId);
            safetyHoldCoordinator.react(signal, context, true);
            return;
        }
        if (HoldRung.HARD.equals(signal.getRung())) {
            safetyHoldCoordinator.react(signal, context, false);
            return;
        }
        raiseSoft(signal, context);
    }

    /**
     * Снятие: опустить названную ступень названного радиуса.
     *
     * <p><b>Прыжка через ступень нет:</b> вызов «снять мягкую» на объекте
     * под сворачиванием отвергается — снимать мягкую нечего, пока над ней
     * стои́т жёсткая.
     *
     * <p><b>Предусловие живого риска машинное, а не заявляемое.</b> Оно
     * стои́т только у снятия сворачивания: реакция сворачивания
     * best-effort по составу, снятие риска могло не подтвердиться, и
     * снятие холда вернуло бы вход в торговлю поверх непогашенного риска.
     *
     * <p><b>Холостое снятие строки журнала не заводит:</b> названная
     * ступень не стои́т — ничего не произошло.
     */
    public void clear(ManualHaltClass haltClass, String accountInternalId, String instrumentInternalId) {
        HoldScope scope = scopeOf(haltClass, instrumentInternalId);
        DealContext context = objectContext(scope, accountInternalId, instrumentInternalId);
        refuseUnlessClearanceAllowed(haltClass, scope, context);
        if (isFalse(clearanceApplied(haltClass, scope, context))) {
            log.debug("Manual clearance is a no-op: the named rung does not stand scope={}", scope);
            return;
        }
        journal(HoldSignal.instrumentJournal(Constants.Hold.MANUAL_HALT_CLEARED), scope, context);
    }

    // ------------------------------------------------------------------
    // Пара «радиус × класс»
    // ------------------------------------------------------------------

    /**
     * Радиус по классу и предъявленному объекту. Допустимых пар четыре, и
     * перечень задан не этой поверхностью, а лестницами: мягкий класс
     * инструментного радиуса биржевым статусом не выражается и наоборот.
     * Недопустимая пара — отказ при запуске, фабрики сигнала для неё нет.
     */
    private HoldScope scopeOf(ManualHaltClass haltClass, String instrumentInternalId) {
        boolean pairAddressed = nonNull(instrumentInternalId);
        if (ManualHaltClass.SOFT.equals(haltClass) && isFalse(pairAddressed)) {
            throw new IllegalArgumentException("SOFT is an instrument-scope class: name the instrument");
        }
        if (ManualHaltClass.FREEZE.equals(haltClass) && isTrue(pairAddressed)) {
            throw new IllegalArgumentException("FREEZE is an account-scope class: do not name an instrument");
        }
        return pairAddressed ? HoldScope.INSTRUMENT : HoldScope.EXCHANGE_ACCOUNT;
    }

    /** Сигнал постановки: та же пара, по которой собираются фабрики автоматики. */
    private HoldSignal signalOf(ManualHaltClass haltClass, HoldScope scope) {
        String code = Constants.Hold.MANUAL_HALT_REQUESTED;
        if (HoldScope.EXCHANGE_ACCOUNT.equals(scope)) {
            return ManualHaltClass.FULL.equals(haltClass)
                    ? HoldSignal.exchangeAccount(code)
                    : HoldSignal.exchangeAccountSoft(code);
        }
        return ManualHaltClass.FULL.equals(haltClass)
                ? HoldSignal.instrument(code)
                : HoldSignal.instrumentSoft(code);
    }

    // ------------------------------------------------------------------
    // Отказы при запуске
    // ------------------------------------------------------------------

    /**
     * Мягкий класс на объекте не в рабочем состоянии и без стоящей ступени
     * СВОЕГО радиуса — отказ: множество входа мягкой ступени только
     * рабочее состояние.
     *
     * <p><b>Стоящая ступень читается по радиусу, а не по имени
     * статуса.</b> {@code HOLD} — мягкая ступень только на счёте; у
     * инструмента одноимённый статус онбординговый и к лестнице отношения
     * не имеет.
     */
    private void refuseUnlessRaiseAllowed(ManualHaltClass haltClass, HoldScope scope, DealContext context) {
        if (ManualHaltClass.FULL.equals(haltClass)) {
            return;
        }
        if (isTrue(softStanding(scope, context)) || isTrue(hardStanding(scope, context))
                || isTrue(inWorkingState(scope, context))) {
            return;
        }
        throw new IllegalArgumentException(
                "Soft halt entry set is the working state only; the object is neither working nor held");
    }

    private void refuseUnlessClearanceAllowed(ManualHaltClass haltClass, HoldScope scope,
                                              DealContext context) {
        if (isFalse(ManualHaltClass.FULL.equals(haltClass))) {
            if (isTrue(hardStanding(scope, context))) {
                throw new IllegalArgumentException(
                        "The hard rung stands: clearing the soft one would jump over a step");
            }
            return;
        }
        if (isTrue(hardStanding(scope, context)) && isFalse(riskProvenAbsentOnScope(scope, context))) {
            throw new IllegalArgumentException(
                    "Live risk remains on the scope: the teardown rung is not cleared over it");
        }
    }

    // ------------------------------------------------------------------
    // Применение
    // ------------------------------------------------------------------

    /**
     * Явный вызов держателя доводит недоделанное: ступень уже стои́т той
     * же, что запрошена, а живой риск на радиусе остался.
     */
    private Boolean teardownRetry(ManualHaltClass haltClass, HoldScope scope, DealContext context) {
        return ManualHaltClass.FULL.equals(haltClass)
                && isTrue(hardStanding(scope, context))
                && isFalse(riskProvenAbsentOnScope(scope, context));
    }

    /**
     * Мягкая постановка: статус плюс строка журнала. Через координатора
     * мягкие формы не идут — снятия риска и каскада у них нет.
     *
     * <p>Строка заводится <b>по ключу отчёта</b>, а не по гарду перехода:
     * поглощение гасит смену статуса, но не отчёт.
     */
    private void raiseSoft(HoldSignal signal, DealContext context) {
        journal(signal, signal.getScope(), context);
        if (HoldScope.EXCHANGE_ACCOUNT.equals(signal.getScope())) {
            exchangeAccountDataService.raiseRung(context.getExchangeAccount().getId(),
                    ExchangeAccount.SafetyRung.HOLD);
            return;
        }
        accountInstrumentStateDataService.raiseRung(context.getExchangeAccount().getId(),
                context.getInstrument().getId(), Instrument.SafetyRung.ENTRY_BLOCKED);
    }

    /**
     * Опустить названную ступень; {@code false} — холостой ход.
     *
     * <p><b>Биржевое сворачивание снимается только в МЯГКУЮ ступень</b>, а
     * не в рабочее состояние: цель снятия объявлена лестницей, и второй
     * ход держатель делает осознанно.
     *
     * <p><b>Снятие не выдаёт права торговать, которого у объекта не было
     * до ступени:</b> инструмент, у которого сворачивание затёрло
     * незавершённый онбординг, возвращается в онбординговый {@code HOLD},
     * а не в рабочее состояние.
     */
    private Boolean clearanceApplied(ManualHaltClass haltClass, HoldScope scope, DealContext context) {
        if (HoldScope.EXCHANGE_ACCOUNT.equals(scope)) {
            return ManualHaltClass.FULL.equals(haltClass)
                    ? exchangeAccountDataService.clearRung(context.getExchangeAccount().getId(),
                            ExchangeAccount.SafetyRung.TRADE_BLOCKED, ExchangeAccount.SafetyRung.HOLD)
                    : exchangeAccountDataService.clearRung(context.getExchangeAccount().getId(),
                            ExchangeAccount.SafetyRung.HOLD, ExchangeAccount.SafetyRung.ACTIVE);
        }
        Instrument.SafetyRung standing = ManualHaltClass.FULL.equals(haltClass)
                ? Instrument.SafetyRung.TRADE_BLOCKED
                : Instrument.SafetyRung.ENTRY_BLOCKED;
        return accountInstrumentStateDataService.clearRung(context.getExchangeAccount().getId(),
                context.getInstrument().getId(), standing, Instrument.SafetyRung.ACTIVE);
    }

    /**
     * Строка журнала операции. Код различает НАПРАВЛЕНИЕ: у постановки и
     * снятия одна и та же пара «радиус × ступень», и без различителя
     * журнал не отличил бы «поднял сворачивание» от «снял».
     *
     * <p>У снятия природа факта — <b>происшествие</b>: каждое снятие
     * обязано дать свою строку, иначе холд, поднятый и снятый трижды,
     * оставил бы один след.
     */
    private void journal(HoldSignal signal, HoldScope scope, DealContext context) {
        try {
            if (Constants.Hold.MANUAL_HALT_CLEARED.equals(signal.getCode())) {
                anomalyReportService.journal(context, scopedJournalSignal(scope, signal.getCode()));
                return;
            }
            anomalyReportService.journalState(context, signal, null);
        } catch (RuntimeException e) {
            log.error("Manual halt journal failed scope={} code={}", scope, signal.getCode(), e);
        }
    }

    private HoldSignal scopedJournalSignal(HoldScope scope, String code) {
        return HoldScope.EXCHANGE_ACCOUNT.equals(scope)
                ? HoldSignal.exchangeAccountJournal(code)
                : HoldSignal.instrumentJournal(code);
    }

    // ------------------------------------------------------------------
    // Состояние объекта радиуса
    // ------------------------------------------------------------------

    /**
     * Контекст объекта радиуса: счёт всегда, инструмент — у радиуса пары.
     * Тот же носитель, каким адресуются автоматические сигналы: второй
     * способ назвать объект разошёлся бы с первым.
     */
    private DealContext objectContext(HoldScope scope, String accountInternalId,
                                      String instrumentInternalId) {
        ExchangeAccount account = exchangeAccountDataService.getRequiredByInternalId(accountInternalId);
        Instrument instrument = HoldScope.INSTRUMENT.equals(scope)
                ? instrumentDataService.getRequiredByInternalId(instrumentInternalId)
                : null;
        return DealContext.builder().exchangeAccount(account).instrument(instrument).build();
    }

    private Boolean hardStanding(HoldScope scope, DealContext context) {
        if (HoldScope.EXCHANGE_ACCOUNT.equals(scope)) {
            return ExchangeAccount.SafetyRung.TRADE_BLOCKED.equals(context.getExchangeAccount()
                    .getSafetyRung());
        }
        return Instrument.SafetyRung.TRADE_BLOCKED.equals(pairState(context).getSafetyRung());
    }

    private Boolean softStanding(HoldScope scope, DealContext context) {
        if (HoldScope.EXCHANGE_ACCOUNT.equals(scope)) {
            return ExchangeAccount.SafetyRung.HOLD.equals(context.getExchangeAccount().getSafetyRung());
        }
        return Instrument.SafetyRung.ENTRY_BLOCKED.equals(pairState(context).getSafetyRung());
    }

    /**
     * Объект в рабочем состоянии. У счёта это реестровый статус, у пары —
     * онбординговый статус инструмента: ступень пары уже прочитана
     * соседними предикатами, и читать её здесь второй раз значило бы
     * смешать две оси.
     */
    private Boolean inWorkingState(HoldScope scope, DealContext context) {
        if (HoldScope.EXCHANGE_ACCOUNT.equals(scope)) {
            return ExchangeAccount.Status.ACTIVE.equals(context.getExchangeAccount().getStatus());
        }
        return Instrument.Status.ACTIVE.equals(context.getInstrument().getStatus());
    }

    private AccountInstrumentState pairState(DealContext context) {
        return accountInstrumentStateDataService.getRequiredByPair(context.getExchangeAccount().getId(),
                context.getInstrument().getId());
    }

    /**
     * Живого риска на радиусе не осталось — предикат тот же, что гейтит
     * терминал сделки; своего поверхность не заводит.
     *
     * <p><b>Полнота названа и ограничена:</b> предикат покрывает четыре
     * признака живого риска из пяти; пятый — неизвестная живая сущность на
     * бирже — операндом прохода не выражается по построению, и его
     * носитель инструмент-скоупный. Это то же ограничение, с которым живёт
     * гейт терминала сделки.
     */
    private Boolean riskProvenAbsentOnScope(HoldScope scope, DealContext context) {
        Long instrumentId = HoldScope.INSTRUMENT.equals(scope) ? context.getInstrument().getId() : null;
        for (Deal deal : dealDataService.findRiskCandidatesOnScope(context.getExchangeAccount().getId(),
                instrumentId, properties.getClearanceTerminalWindow())) {
            DealContext dealContext = dealContextService.build(deal);
            if (isFalse(dealTerminalGate.riskProvenAbsent(deal, deal.getTranches(),
                    dealContext.getGraphComplete()))) {
                log.warn("Live risk is not proven absent dealId={} — clearance refused", deal.getId());
                return false;
            }
        }
        return true;
    }
}

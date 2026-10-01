package com.example.tradingcore.domain.safety;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.platform.security.ActorProvider;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.integration.internal.event.CoreEventWriter;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.util.Constants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ручное управление safety-остановкой: держатель управляет теми же
 * ступенями, что и автоматика, — тем же механизмом и через ту же точку
 * входа (docs/rules/manual-halt.md). Исполнимая форма —
 * docs/spec/manual-halt.json.
 *
 * <p><b>Жёсткая ступень снимается только в мягкую своего радиуса — на обоих
 * радиусах.</b> Сворачивание счёта — в {@code HOLD}, холд пары с
 * kill-switch — в {@code ENTRY_BLOCKED}; рабочее состояние возвращает
 * только второй, осознанный ход — снятие мягкой ступени. Порядок снятия
 * задаёт лестница, и дом у него один на обе (docs/rules/exchange-hold.md;
 * форма — величина {@code clearanceTarget}).
 *
 * <p><b>Предусловие «риска не осталось» — два носителя, по одному на род
 * признаков.</b> Нетерминальные сделки радиуса целиком — предикатом,
 * который гейтит терминал сделки, — и срез позиций площадки по радиусу:
 * пятый признак, неизвестная живая сущность на бирже, операндом сделки не
 * выражается, а у радиуса у него есть форма — та же, которой kill-switch
 * подтверждает радиус. Живая позиция либо не добытый срез — риск не
 * доказан. Тот же предикат гейтит и доведение недоделанного (форма —
 * величина {@code liveRiskOnScope}).
 *
 * <p><b>Собственного механизма остановки здесь нет.</b> Постановка
 * собирает сигнал и зовёт общего исполнителя блокировки — <b>обе ступени,
 * а не одну жёсткую</b>: событие подъёма обязан писать тот код, который
 * переставляет ступень, и своя мягкая ветвь оставила бы ручную постановку
 * без факта в журнале. Снятие через него не идёт — «снятие холда — ручная
 * сервисная операция, не этот путь», — и потому несёт идемпотентность
 * само: её даёт <b>явно названная ступень</b>, из-за которой повтор
 * попадает в холостой ход, а не в следующий шаг лестницы.
 *
 * <p><b>Доведение недоделанного — единственная ветвь, идущая мимо общего
 * исполнителя</b>, и события она не производит: ступень на объекте уже
 * стои́т, а право обойти анкер есть только у явного вызова держателя
 * (абзац «Право на доведение недоделанного есть только здесь» ниже). Факт
 * подъёма пишет ребро самого перехода, а перехода на этой тропе нет —
 * объявлять фактом ход, которого не было, нельзя. Исхода реакция при этом
 * не отдаёт вовсе: её единственным читателем был писатель, стоявший НАД
 * переходом (docs/rules/manual-halt.md).
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
    private final HoldService holdService;
    private final AnomalyReportService anomalyReportService;
    private final PositionSliceReader positionSliceReader;
    private final ActorProvider actorProvider;
    private final CoreEventWriter coreEventWriter;

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
        holdService.raiseManual(signal, context);
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
     * К площадке снятие ходит ровно одним чтением — срезом позиций радиуса,
     * и только когда сделки радиуса отсутствие риска уже доказали; отказ
     * этого чтения есть отказ при запуске.
     *
     * <p><b>Холостое снятие ни строки журнала, ни события не заводит:</b>
     * названная ступень не стои́т — ничего не произошло.
     *
     * <p><b>Снятие, его строка журнала и его событие — одна транзакция:</b>
     * отказ записи строки или строки outbox роняет операцию, и ступень
     * остаётся стоять. Цена разрешающая лишь по виду — при недоступном
     * журнале контур остаётся остановленным, — а исход «вернул торговлю, но
     * следа нет» закрыт (docs/rules/manual-halt.md §«Наблюдаемость: ручное
     * отличимо и от автоматики, и друг от друга»).
     *
     * <p><b>Событие снятия пишет сама тропа, а не ребро:</b> затребователь у
     * снятия один, и общий применитель, ради которого заведено ребро
     * подъёма, здесь ничего не разводит (docs/architecture/contracts.md
     * §«У каждого класса события назван писатель, и он же писатель
     * решения»).
     */
    @Transactional
    public void clear(ManualHaltClass haltClass, String accountInternalId, String instrumentInternalId) {
        HoldScope scope = scopeOf(haltClass, instrumentInternalId);
        DealContext context = objectContext(scope, accountInternalId, instrumentInternalId);
        refuseUnlessClearanceAllowed(haltClass, scope, context);
        if (isFalse(clearanceApplied(haltClass, scope, context))) {
            log.debug("Manual clearance is a no-op: the named rung does not stand scope={}", scope);
            return;
        }
        journalClearance(scope, context);
        publishReleased(haltClass, scope, context);
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
     * Опустить названную ступень; {@code false} — холостой ход.
     *
     * <p><b>Жёсткая ступень снимается только в МЯГКУЮ ступень своего
     * радиуса</b>, а не в рабочее состояние — на счёте и на паре одинаково:
     * два условия снятия лестница проверяет по одному, и второй ход
     * держатель делает осознанно (docs/rules/exchange-hold.md). Повторное
     * снятие жёсткой на объекте, уже спущенном в мягкую, холостое: названная
     * ступень не стои́т.
     *
     * <p><b>Ступень пары — поле пары</b>, и онбординговый статус
     * инструмента снятие не пишет вовсе: он другое поле в другой базе, а
     * права торговать не выдаёт отбор входа, берущий только онбординговый
     * рабочий статус (docs/rules/manual-halt.md).
     */
    private Boolean clearanceApplied(ManualHaltClass haltClass, HoldScope scope, DealContext context) {
        Long exchangeAccountId = context.getExchangeAccount().getId();
        if (HoldScope.EXCHANGE_ACCOUNT.equals(scope)) {
            return ManualHaltClass.FULL.equals(haltClass)
                    ? exchangeAccountDataService.clearRung(exchangeAccountId,
                            ExchangeAccount.SafetyRung.TRADE_BLOCKED, ExchangeAccount.SafetyRung.HOLD)
                    : exchangeAccountDataService.clearRung(exchangeAccountId,
                            ExchangeAccount.SafetyRung.HOLD, ExchangeAccount.SafetyRung.ACTIVE);
        }
        Long instrumentId = context.getInstrument().getId();
        return ManualHaltClass.FULL.equals(haltClass)
                ? accountInstrumentStateDataService.clearRung(exchangeAccountId, instrumentId,
                        Instrument.SafetyRung.TRADE_BLOCKED, Instrument.SafetyRung.ENTRY_BLOCKED)
                : accountInstrumentStateDataService.clearRung(exchangeAccountId, instrumentId,
                        Instrument.SafetyRung.ENTRY_BLOCKED, Instrument.SafetyRung.ACTIVE);
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
    private void journalClearance(HoldScope scope, DealContext context) {
        anomalyReportService.journal(context, clearanceSignal(scope));
    }

    /**
     * Факт снятия — той же транзакцией, что опустила ступень и завела
     * строку журнала. Снятая ступень — судьба принятого риска, которую
     * держала снятая ступень лестницы: полный класс снимает жёсткую, оба
     * мягких класса — мягкую ({@link ManualHaltClass}).
     *
     * <p><b>Инструмент содержимого читается по РАДИУСУ</b>, а у счётного
     * радиуса его нет по построению — тем же доводом, что у факта подъёма.
     */
    private void publishReleased(ManualHaltClass haltClass, HoldScope scope, DealContext context) {
        String instrumentInternalId = HoldScope.INSTRUMENT.equals(scope)
                ? context.getInstrument().getInternalId()
                : null;
        coreEventWriter.holdReleased(context.getExchangeAccount().getTenantId(), scope,
                releasedRung(haltClass), context.getExchangeAccount().getInternalId(),
                instrumentInternalId, actorProvider.currentActor());
    }

    /** Снятая ступень по классу вмешательства: полный — жёсткая, мягкие — мягкая. */
    private static HoldRung releasedRung(ManualHaltClass haltClass) {
        return ManualHaltClass.FULL.equals(haltClass) ? HoldRung.HARD : HoldRung.SOFT;
    }

    /** Журнальный сигнал снятия радиусом самой операции. */
    private HoldSignal clearanceSignal(HoldScope scope) {
        String code = Constants.Hold.MANUAL_HALT_CLEARED;
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
     * Живого риска на радиусе не осталось (docs/spec/manual-halt.json,
     * величина {@code liveRiskOnScope}, отрицание). Носителей два, по одному
     * на род признаков, и читаются они по порядку: второй — только когда
     * первый отсутствие риска доказал.
     *
     * <p><b>Первый — нетерминальные сделки радиуса целиком</b>, предикатом,
     * который гейтит терминал сделки; своего поверхность не заводит.
     * Терминальных выборка не берёт по построению, а не по окну: оба
     * терминальных ребра гейтятся этим же предикатом, строк терминальной
     * сделки не пишет ни одна тропа, а неподтверждённое снятие риска держит
     * сделку в {@code ERROR} — то есть в этой выборке. Так покрыты четыре
     * признака живого риска из пяти.
     *
     * <p><b>Второй — срез позиций площадки по радиусу</b>, форма пятого
     * признака. Он читается последним и одним вызовом, без повторов: сделка,
     * риска не доказавшая, решает раньше, и к площадке тогда не уходит
     * ничего.
     */
    private Boolean riskProvenAbsentOnScope(HoldScope scope, DealContext context) {
        Long exchangeAccountId = context.getExchangeAccount().getId();
        List<Deal> deals = HoldScope.INSTRUMENT.equals(scope)
                ? dealDataService.findNonTerminalOnPair(exchangeAccountId, context.getInstrument().getId())
                : dealDataService.findNonTerminalByExchangeAccountId(exchangeAccountId);
        for (Deal deal : deals) {
            DealContext dealContext = dealContextService.build(deal);
            if (isFalse(dealTerminalGate.riskProvenAbsent(deal, deal.getTranches(),
                    dealContext.getGraphComplete()))) {
                log.warn("Live risk is not proven absent dealId={} — clearance refused", deal.getId());
                return false;
            }
        }
        return positionsProvenAbsentOnScope(scope, context);
    }

    /**
     * Срез позиций радиуса пуст: счёт целиком, у пары суженный
     * инструментом.
     *
     * <p><b>Живой считается ЛЮБАЯ позиция среза</b>, а не только позиция
     * вне инструментов сделок: срез читается после того, как сделки радиуса
     * отсутствие риска доказали, и позиция, оставшаяся на инструменте
     * сделки, ни одной нетерминальной сделкой не объяснена.
     *
     * <p><b>Не добытый срез отсутствия риска не доказывает</b> — пустота
     * здесь не открывает ветви, которая снимает ступень.
     *
     * <p><b>Граница формы — позиции.</b> Заявки вне графа сделок и
     * встроенная защита в неё не входят: снятие риска их не отменяет, и
     * требование к ним оставило бы ступень без выхода повторным полным
     * вызовом (docs/rules/manual-halt.md §«Выборка и производитель
     * предусловия названы»).
     */
    private Boolean positionsProvenAbsentOnScope(HoldScope scope, DealContext context) {
        String externalInstrumentId = HoldScope.INSTRUMENT.equals(scope)
                ? context.getInstrument().getExternalId()
                : null;
        List<Position> live = positionSliceReader.livePositions(context.getExchangeAccount(),
                externalInstrumentId);
        if (isNull(live)) {
            log.warn("Position slice is not fetched exchangeAccountId={} — live risk is not proven absent",
                    context.getExchangeAccount().getId());
            return false;
        }
        if (isNotEmpty(live)) {
            log.warn("Live position remains on the scope exchangeAccountId={} instId={} — live risk is not"
                    + " proven absent", context.getExchangeAccount().getId(),
                    live.getFirst().getExternalInstrumentId());
            return false;
        }
        return true;
    }
}

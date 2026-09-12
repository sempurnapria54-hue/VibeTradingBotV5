package com.example.tradingcore.domain.safety;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.service.ActorProvider;
import com.example.tradingcore.integration.internal.event.CoreEventWriter;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Переставляет ступень объекта блокировки <b>вместе с фактом подъёма</b> —
 * одной транзакцией на каждой из четырёх троп подъёма
 * (docs/components/HoldService.md).
 *
 * <p><b>Зачем отдельный носитель.</b> Событие обязан писать тот код,
 * который пишет решение, и <b>той же транзакцией</b>
 * (docs/architecture/contracts.md §«У каждого класса события назван
 * писатель, и он же писатель решения»). Ступень поднимают две тропы —
 * мягкую ведёт сам сервис блокировки, полную координатор, — и ни одна из
 * них транзакции не открывает: без этой границы ступень коммитилась бы
 * раньше строки outbox, а между ними лежали бы внешние вызовы снятия
 * риска и каскад сделок радиуса.
 *
 * <p><b>Решений сервис не принимает.</b> Радиус, ступень и машинный код
 * приходят сигналом; здесь — перестановка и публикация.
 *
 * <p><b>Ступень лестницы выводится из пары «радиус × судьба принятого
 * риска», и дом этой пары здесь.</b> Пар четыре, и до этого носителя они
 * жили в двух местах — мягкие у сервиса блокировки, жёсткие у
 * координатора; вторая копия разошлась бы с первой
 * (.claude/rules/policy-home.md).
 *
 * <p><b>Инструмент содержимого читается по РАДИУСУ, а не по наличию его в
 * контексте.</b> Счётный сигнал приходит и из контекста сделки, у которой
 * инструмент есть, — и положенный оттуда инструмент объявил бы радиусом
 * пару, тогда как ступень поднята на всём счёте
 * (docs/architecture/contracts.md §«Содержимое несёт идентичности»).
 *
 * <p><b>Актор берётся здесь, а не приезжает параметром:</b> ручная тропа у
 * класса есть (держатель ставит ту же ступень тем же механизмом), и
 * принципал живёт в контексте хода, а не в решении затребователя
 * (docs/spec/event-actor-presence.json).
 */
@Service
@RequiredArgsConstructor
public class HoldRungEdgeService {

    private final AccountInstrumentStateDataService accountInstrumentStateDataService;
    private final ExchangeAccountDataService exchangeAccountDataService;
    private final ActorProvider actorProvider;
    private final CoreEventWriter coreEventWriter;

    /**
     * Поднять ступень объекта радиуса и, если переход применился,
     * опубликовать факт.
     *
     * <p><b>Поглощённый сигнал события не производит:</b> статус не
     * двигался, и объявлять фактом ход, которого не было, нельзя — ровно
     * тем же анкером, которым поглощение гасит и снятие риска
     * (docs/components/SafetyHoldCoordinator.md §Последовательность).
     *
     * <p><b>Отказ записи факта откатывает и ступень, и это названный
     * выбор.</b> Ступень, поднятая без своего события, оставляет
     * потребителя без факта навсегда: повторного сигнала не будет — его
     * поглотит анкер уже стоящей ступени. Ступень и строка outbox лежат в
     * одной базе, поэтому отказ, роняющий вторую, роняет и первую; отказ,
     * различающий их, детерминирован (сериализация содержимого) и
     * повторится на следующем тике, а не потеряется
     * (docs/architecture/data-ownership.md §«Outbox и доставка»).
     *
     * @return ступень ПЕРЕСТАВИЛАСЬ этим вызовом
     */
    @Transactional
    public Boolean raise(HoldSignal signal, DealContext dealContext) {
        if (isFalse(rungApplied(signal, dealContext))) {
            return false;
        }
        publishRaised(signal, dealContext);
        return true;
    }

    /**
     * Приводит объект радиуса к затребованной ступени лестницы.
     *
     * <p>Радиус выбирает исполнителя статуса, судьба принятого риска —
     * ступень его лестницы; больше ни на что они не влияют — гард
     * перехода служит анкером у обеих
     * (docs/rules/instrument-hold.md, docs/rules/exchange-hold.md).
     */
    private Boolean rungApplied(HoldSignal signal, DealContext dealContext) {
        if (HoldScope.EXCHANGE_ACCOUNT.equals(signal.getScope())) {
            return exchangeAccountDataService.raiseRung(dealContext.getExchangeAccount().getId(),
                    accountRung(signal));
        }
        return accountInstrumentStateDataService.raiseRung(dealContext.getExchangeAccount().getId(),
                dealContext.getInstrument().getId(), instrumentRung(signal));
    }

    /** Ступень лестницы счёта по судьбе принятого риска. */
    private static ExchangeAccount.SafetyRung accountRung(HoldSignal signal) {
        return isTrue(signal.tearsDownRisk())
                ? ExchangeAccount.SafetyRung.TRADE_BLOCKED
                : ExchangeAccount.SafetyRung.HOLD;
    }

    /** Ступень лестницы пары «счёт, инструмент» по той же оси. */
    private static Instrument.SafetyRung instrumentRung(HoldSignal signal) {
        return isTrue(signal.tearsDownRisk())
                ? Instrument.SafetyRung.TRADE_BLOCKED
                : Instrument.SafetyRung.ENTRY_BLOCKED;
    }

    /** Факт подъёма — той же транзакцией, что и сама перестановка. */
    private void publishRaised(HoldSignal signal, DealContext dealContext) {
        coreEventWriter.holdRaised(dealContext.getExchangeAccount().getTenantId(), signal,
                dealContext.getExchangeAccount().getInternalId(),
                instrumentInternalId(signal, dealContext), actorProvider.currentActor());
    }

    /** Инструмент радиуса; у счётного сигнала его нет по построению радиуса. */
    private static String instrumentInternalId(HoldSignal signal, DealContext dealContext) {
        return HoldScope.INSTRUMENT.equals(signal.getScope())
                ? dealContext.getInstrument().getInternalId()
                : null;
    }
}

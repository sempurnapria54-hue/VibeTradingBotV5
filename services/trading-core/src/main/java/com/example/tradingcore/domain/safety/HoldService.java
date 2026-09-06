package com.example.tradingcore.domain.safety;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.event.CoreEventType;
import com.example.tradingbot.domain.event.HoldRaisedContent;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.event.OutboxWriter;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Общий исполнитель блокировки: детектор, обнаруживший основание, зовёт
 * его самодостаточным сигналом — радиус, ступень, машинный код причины —
 * вместе с контекстом сделки, из которого берётся идентичность объекта
 * блокировки (docs/components/HoldService.md).
 *
 * <p><b>Ступень решает, кто ведёт реакцию.</b> Полную — со снятием
 * принятого риска — целиком ведёт {@link SafetyHoldCoordinator}; мягкую
 * исполняет сам этот сервис: у неё нет ни снятия риска, ни каскада
 * сделок, и координировать в ней нечего.
 *
 * <p><b>Точка входа идемпотентна по статусу объекта блокировки.</b> Анкер
 * — сам гардированный переход: повторный сигнал на объект, уже стоящий в
 * запрошенной ступени, статуса не переставляет. Монотонность держит тот
 * же гард: подъём мягкой в жёсткую проходит, понижение — нет
 * (docs/rules/exchange-hold.md §«Границы и эскалация»).
 *
 * <p><b>Мягкая ступень исполняется на ОБОИХ радиусах.</b> Составы у них
 * разные, и разводит их не этот сервис, а лестницы: у инструмента —
 * запрет входов плюс блок-сет преконтроля целиком, у счёта — только
 * выпадение из выборки входа (docs/rules/instrument-hold.md §Enforcement,
 * docs/rules/exchange-hold.md §«Ступень 1 — мягкий холд»). Общее у них
 * одно — принятый риск не трогается.
 *
 * <p><b>Названное ограничение: вызывающих у точки входа пока нет.</b>
 * Детекторы, которые её зовут, названы домом поимённо — проход
 * оркестратора, исполнители терминального ребра, поиск нарушений
 * инвариантов, ручная операция остановки, — и каждый приезжает своим
 * компонентом. Предмет сервиса от этого не меняется: последовательность
 * реакции и её идемпотентность живут здесь, а не у детектора, и проверены
 * на своих тропах.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldService {

    private final AccountInstrumentStateDataService accountInstrumentStateDataService;
    private final ExchangeAccountDataService exchangeAccountDataService;
    private final AnomalyReportService anomalyReportService;
    private final SafetyHoldCoordinator safetyHoldCoordinator;
    private final OutboxWriter outboxWriter;

    /** Поднять ступень по сигналу. Идемпотентно по статусу объекта блокировки. */
    public void raise(HoldSignal signal, DealContext dealContext) {
        if (isNull(signal)) {
            return;
        }
        if (isTrue(signal.tearsDownRisk())) {
            publishRaised(signal, dealContext, safetyHoldCoordinator.react(signal, dealContext));
            return;
        }
        raiseSoft(signal, dealContext);
    }

    /**
     * Событие подъёма ступени — <b>той же транзакцией</b>, которой статус
     * объекта переставлен (docs/architecture/contracts.md §«У каждого
     * класса события назван писатель, и он же писатель решения»).
     *
     * <p><b>Поглощённый сигнал события не производит:</b> статус не
     * двигался, и объявлять фактом ход, которого не было, нельзя — ровно
     * тем же анкером, которым поглощение гасит и снятие риска.
     */
    private void publishRaised(HoldSignal signal, DealContext dealContext, Boolean applied) {
        if (isFalse(applied)) {
            return;
        }
        outboxWriter.write(dealContext.getExchangeAccount().getTenantId(), CoreEventType.HOLD_RAISED,
                new HoldRaisedContent(dealContext.getExchangeAccount().getInternalId(),
                        instrumentInternalId(dealContext), String.valueOf(signal.getScope()),
                        String.valueOf(signal.getRung()), signal.getCode()));
    }

    /** Инструмент радиуса; пуст у счётного сигнала. */
    private String instrumentInternalId(DealContext dealContext) {
        return isNull(dealContext.getInstrument()) ? null : dealContext.getInstrument().getInternalId();
    }

    /**
     * Мягкая ступень: строка журнала плюс статус объекта блокировки.
     * Снятия риска в составе нет — принятый риск покрыт, и рвать его
     * нечем, — поэтому отчёт создаётся уже завершённым.
     *
     * <p><b>Запись идёт ДО гарда перехода.</b> Поглощение гасит смену
     * статуса и торговую реакцию, но не отчёт: гард отвечает на
     * «переставился ли статус», а отчёт — на «почему контур встал», и
     * второе основание со своим машинным кодом обязано оставить свою
     * строку (docs/rules/error-handling-policy.md §«Идемпотентность
     * реакции и идемпотентность отчёта — разные ключи»). Дедуп при этом
     * держит не гард, а ключ состояния: второй строки по тому же ключу не
     * заводится.
     *
     * <p>Журнал реакцию не гейтит: сбой записи логируется и запрета входов
     * не отменяет — ограничение риска приоритетнее журнала.
     */
    private void raiseSoft(HoldSignal signal, DealContext dealContext) {
        try {
            anomalyReportService.journalState(dealContext, signal, null);
        } catch (RuntimeException e) {
            log.error("Journal of a soft safety signal failed scope={} code={}",
                    signal.getScope(), signal.getCode(), e);
        }
        publishRaised(signal, dealContext, rungApplied(signal, dealContext));
    }

    /**
     * Приводит объект радиуса к мягкой ступени; {@code false} — переход не
     * применился, то есть состояние уже держится либо объект стои́т в
     * жёсткой ступени и мягкий запрос ею поглощается.
     *
     * <p>Радиус выбирает исполнителя статуса; больше он ни на что не
     * влияет — гард перехода служит анкером у обоих.
     */
    private Boolean rungApplied(HoldSignal signal, DealContext dealContext) {
        if (HoldScope.EXCHANGE_ACCOUNT.equals(signal.getScope())) {
            return exchangeAccountDataService.raiseRung(dealContext.getExchangeAccount().getId(),
                    ExchangeAccount.SafetyRung.HOLD);
        }
        return accountInstrumentStateDataService.raiseRung(dealContext.getExchangeAccount().getId(),
                dealContext.getInstrument().getId(), Instrument.SafetyRung.ENTRY_BLOCKED);
    }
}

package com.example.tradingbot.domain.safety;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Общий исполнитель блокировки: детектор, обнаруживший основание, зовёт
 * его самодостаточным сигналом (радиус, ступень, машинный код причины), а
 * не переписывает последовательность у себя.
 *
 * <p><b>Ступень решает, кто ведёт реакцию.</b> Жёсткую — со снятием
 * принятого риска — целиком ведёт {@link SafetyHoldCoordinator}; мягкую
 * исполняет сам этот сервис: у неё нет ни kill-switch, ни каскада сделок,
 * и координировать в ней нечего (docs/components/SafetyHoldCoordinator.md
 * §«Что делает»: мягкие формы через координатора не идут).
 *
 * <p><b>Точка входа идемпотентна по статусу объекта блокировки.</b> Анкер
 * — сам гардированный переход: повторный сигнал на объект, уже стоящий в
 * запрошенной ступени, статуса не переставляет и реакции не запускает.
 * Монотонность держит тот же гард: подъём мягкой в жёсткую проходит,
 * понижение — нет (`docs/rules/instrument-hold.md`).
 *
 * <p><b>Мягкая ступень исполняется на ОБОИХ радиусах.</b> Составы у них
 * разные, и разводит их не этот сервис, а лестницы: у инструмента — запрет
 * входов плюс блок-сет преконтроля целиком (включая ослабление защиты живой
 * сделки), у биржи — только выпадение из выборки входа, командного блок-сета
 * нет (docs/rules/instrument-hold.md, docs/rules/exchange-hold.md). Общее у
 * них одно — принятый риск не трогается.
 *
 * <p>См. docs/components/HoldService.md.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldService {

    private final InstrumentDataService instrumentDataService;
    private final ExchangeDataService exchangeDataService;
    private final AnomalyReportService anomalyReportService;
    private final SafetyHoldCoordinator safetyHoldCoordinator;

    /** Поднять ступень по сигналу. Идемпотентно по статусу объекта блокировки. */
    public void raise(HoldSignal signal, DealContext dealContext) {
        if (isNull(signal)) {
            return;
        }
        if (isTrue(signal.tearsDownRisk())) {
            safetyHoldCoordinator.react(signal, dealContext);
            return;
        }
        raiseSoft(signal, dealContext);
    }

    /**
     * Мягкая ступень: статус объекта блокировки плюс журнальный отчёт.
     * Снятия риска в составе нет — принятый риск покрыт, и рвать его
     * нечем, — поэтому отчёт создаётся уже завершённым.
     *
     * <p><b>Поглощение гасит смену статуса и торговую реакцию, но не
     * отчёт</b> (docs/rules/error-handling-policy.md §«Поглощение
     * наблюдаемо»). Поэтому запись идёт ДО гарда перехода: гард отвечает
     * на «переставился ли статус», а отчёт — на «почему контур встал», и
     * второе основание со своим машинным кодом обязано оставить свою
     * строку. Дедуп при этом держит не гард, а ключ состояния:
     * {@link AnomalyReportService#journalState} второй строки по тому же
     * ключу не заводит. Прежняя редакция полагалась на гард — и оставляла
     * биржу в холде без единой строки о причине, если первая запись не
     * прошла.
     *
     * <p>Журнал не гейтит реакцию: сбой записи отчёта логируется и запрет
     * входов не отменяет — ограничение риска приоритетнее журнала.
     */
    private void raiseSoft(HoldSignal signal, DealContext dealContext) {
        try {
            anomalyReportService.journalState(dealContext, signal, null);
        } catch (RuntimeException e) {
            log.error("Journal anomaly report failed scope={} code={}", signal.getScope(), signal.getCode(), e);
        }
        rungApplied(signal, dealContext);
    }

    /**
     * Приводит объект радиуса к мягкой ступени. Возвращает {@code false},
     * если переход не применился, — то есть состояние уже держится либо
     * объект стои́т в жёсткой ступени, и мягкий запрос ею поглощается.
     * Радиус выбирает исполнителя статуса; больше он ни на что не влияет.
     */
    private Boolean rungApplied(HoldSignal signal, DealContext dealContext) {
        return HoldScope.EXCHANGE.equals(signal.getScope())
                ? exchangeDataService.blockEntry(dealContext.getExchange().getId())
                : instrumentDataService.blockEntry(dealContext.getInstrument().getId());
    }
}

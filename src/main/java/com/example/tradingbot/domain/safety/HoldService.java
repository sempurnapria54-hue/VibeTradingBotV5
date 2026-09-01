package com.example.tradingbot.domain.safety;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
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
 * <p><b>Мягкая биржевая ступень сюда не приходит:</b> отдельного статуса
 * у неё в модели биржи нет, а её объявленные триггеры (сверка результата,
 * серия убытков, ручной вызов) не закодированы — заводить ветвь без
 * вызывающего значило бы писать мёртвый код. Названное ограничение.
 *
 * <p>См. docs/components/HoldService.md.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldService {

    private final InstrumentDataService instrumentDataService;
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
     * <p><b>Дедуп по стоящему состоянию объекта</b> выражен самим гардом:
     * не переставился статус — состояние уже держится, и второй строки по
     * тому же ключу не заводится (docs/lifecycles/AnomalyReport.md
     * §«Идемпотентность зависит от природы факта»).
     *
     * <p>Журнал не гейтит реакцию: сбой записи отчёта логируется и запрет
     * входов не отменяет — ограничение риска приоритетнее журнала.
     */
    private void raiseSoft(HoldSignal signal, DealContext dealContext) {
        if (isFalse(HoldScope.INSTRUMENT.equals(signal.getScope()))) {
            log.warn("Soft rung is not modelled for scope={} — signal code={} not raised",
                    signal.getScope(), signal.getCode());
            return;
        }
        if (isFalse(instrumentDataService.blockEntry(dealContext.getInstrument().getId()))) {
            return;
        }
        try {
            anomalyReportService.journal(dealContext, signal);
        } catch (RuntimeException e) {
            log.error("Journal anomaly report failed scope={} code={}", signal.getScope(), signal.getCode(), e);
        }
    }
}

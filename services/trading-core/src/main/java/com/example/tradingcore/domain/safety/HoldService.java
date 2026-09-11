package com.example.tradingcore.domain.safety;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingcore.domain.command.DealContext;
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
 * <p><b>Саму перестановку ступени не делает ни та, ни другая тропа.</b>
 * Её вместе с фактом подъёма применяет {@link HoldRungEdgeService} —
 * одной транзакцией, потому что событие обязан писать тот код, который
 * пишет решение (docs/architecture/contracts.md §«У каждого класса
 * события назван писатель, и он же писатель решения»).
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
 * <p><b>Вызывающие у точки входа есть, и ручная тропа среди них.</b> Её
 * зовут проход оркестратора, исполнители терминального ребра, поиск
 * нарушений инвариантов и поверхность ручной остановки: последняя приходит
 * сюда именно за тем, чтобы событие подъёма писал тот код, который
 * переставляет ступень (docs/rules/manual-halt.md §«Идемпотентность
 * наследуется, а не обходится»). Предмет сервиса от этого не
 * меняется: последовательность реакции и её идемпотентность живут здесь, а
 * не у детектора.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldService {

    private final AnomalyReportService anomalyReportService;
    private final SafetyHoldCoordinator safetyHoldCoordinator;
    private final HoldRungEdgeService holdRungEdgeService;

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
     * Мягкая ступень: строка журнала плюс переход ступени со своим фактом.
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
     * не отменяет — ограничение риска приоритетнее журнала. Дом клаузы —
     * docs/rules/error-handling-policy.md §«Отказ журнального носителя
     * реакцию не гейтит». Факт подъёма этой клаузы не наследует: он лежит в
     * транзакции самого перехода, и ступени без своего события не бывает
     * (docs/components/HoldService.md §«Что делает вызов»).
     */
    private void raiseSoft(HoldSignal signal, DealContext dealContext) {
        try {
            anomalyReportService.journalState(dealContext, signal, null);
        } catch (RuntimeException e) {
            log.error("Journal of a soft safety signal failed scope={} code={}",
                    signal.getScope(), signal.getCode(), e);
        }
        holdRungEdgeService.raise(signal, dealContext);
    }
}

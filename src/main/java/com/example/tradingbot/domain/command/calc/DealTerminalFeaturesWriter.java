package com.example.tradingbot.domain.command.calc;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.config.ExchangeContourProperties;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.safety.AnomalyReportService;
import com.example.tradingbot.domain.safety.HoldSignal;
import com.example.tradingbot.util.Constants;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Считает и записывает четыре признака отбора сделки на терминальном
 * ребре — общий носитель обязанности, а не копия в каждом исполнителе
 * терминала.
 * Исполнимые формы — docs/spec/position-close-outcome.json (торговый
 * исход), docs/spec/pnl-reconciliation.json (сверка),
 * docs/spec/deal-lifecycle.json §benchmarkAvailabilityOnTerminal
 * (доступность знаменателя); полнота разбивки — окно добычи против
 * глубины доступности движений (docs/components/RefreshBillsExecutor.md).
 *
 * <p><b>Не входившая сделка признаков не получает вовсе</b> — кроме
 * доступности знаменателя, у которой «входа не было» есть собственное
 * значение: {@code NOT_APPLICABLE} — нормальная популяция, а не аномалия.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DealTerminalFeaturesWriter {

    /** Ничего не записано: признаки уже стоя́т с финализации выхода. */
    private static final DealTerminalFeatures NOT_WRITTEN =
            new DealTerminalFeatures(null, null, null, null, false);

    /** Сырые типы закрытия источника, из которых торговый исход выводится. */
    private static final Set<String> NORMAL_EXIT_TYPES = Set.of("1", "2");
    private static final Set<String> LIQUIDATION_TYPES = Set.of("3", "4");
    private static final Set<String> FORCED_REDUCTION_TYPES = Set.of("5", "6");

    private final DealReconciliationCalculator reconciliationCalculator;
    private final ExchangeContourProperties exchangeContourProperties;
    private final AnomalyReportService anomalyReportService;

    /**
     * Посчитать признаки, положить их на сделку и отчитаться по тем, что
     * требуют журнального отчёта; возвращается ровно то, что ЗАПИСАНО.
     * Сохранение — на вызывающем: признаки едут ТОЙ ЖЕ транзакцией, что и
     * число, и своей записи не делают.
     *
     * <p>Аргумент {@code resultFinalized} есть durable-факт «число
     * записала финализация выхода»: он свидетельствует и о признаках —
     * четвёрка записана на ПОЛНОМ графе, и аварийный терминал, приходящий
     * на усечённом, пересчитывать её не вправе. Тогда не пишется ничего и
     * не считается тоже ничего: пустой результат здесь означает «значения
     * уже стоя́т», а не «признак неприменим».
     */
    public DealTerminalFeatures apply(DealContext dealContext, Boolean resultFinalized) {
        if (isTrue(resultFinalized)) {
            return NOT_WRITTEN;
        }
        DealTerminalFeatures features = resolve(dealContext);
        Deal deal = dealContext.getDeal();
        deal.setCloseOutcome(features.getCloseOutcome());
        deal.setReconciliationStatus(features.getReconciliationStatus());
        deal.setBreakdownIncomplete(features.getBreakdownIncomplete());
        deal.setRiskBenchmarkAvailability(features.getRiskBenchmarkAvailability());
        report(dealContext, features);
        return features;
    }

    /**
     * Журнальные отчёты-события по признакам: потерянный знаменатель и
     * нераспознанный тип закрытия. Оба — событие, своя строка на каждую
     * такую сделку, дедупа нет. Журнал реакцию не гейтит: сбой записи
     * логируется и терминала не отменяет.
     */
    private void report(DealContext dealContext, DealTerminalFeatures features) {
        if (Deal.RiskBenchmarkAvailability.MISSING.equals(features.getRiskBenchmarkAvailability())) {
            journal(dealContext, Constants.Hold.RISK_BENCHMARK_MISSING);
        }
        if (isTrue(features.getUnrecognizedCloseTypeReported())) {
            journal(dealContext, Constants.Hold.UNRECOGNIZED_CLOSE_TYPE);
        }
    }

    private void journal(DealContext dealContext, String code) {
        try {
            anomalyReportService.journal(dealContext, HoldSignal.instrumentJournal(code));
        } catch (RuntimeException e) {
            log.error("Journal anomaly report failed code={} dealId={}", code,
                    dealContext.getDeal().getId(), e);
        }
    }

    private DealTerminalFeatures resolve(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        boolean hadEntry = isTrue(deal.positionObserved());
        List<Position> episodes = emptyIfNull(deal.getPositions()).stream().toList();
        return new DealTerminalFeatures(
                closeOutcome(dealContext, hadEntry, episodes),
                hadEntry ? reconciliationCalculator.reconcile(dealContext) : null,
                hadEntry ? breakdownCompleteness(dealContext) : null,
                benchmarkAvailability(deal, hadEntry),
                unrecognizedCloseTypeReported(episodes));
    }

    // ------------------------------------------------------------------
    // Торговый исход
    // ------------------------------------------------------------------

    /**
     * Старшинство LIQUIDATION &gt; FORCED_REDUCTION &gt; UNDETERMINED &gt;
     * NORMAL_EXIT. Неизвестное выше штатного намеренно: обратный порядок
     * скрывал бы ликвидацию соседнего эпизода за штатным выходом —
     * благоприятное умолчание.
     *
     * <p>Неполный граф даёт UNDETERMINED: агрегат «есть ли ликвидация» по
     * недогруженному списку эпизодов ложен молча, и сделка с
     * ликвидированным, но не загруженным эпизодом получила бы durable
     * NORMAL_EXIT — единственное значение, которое входит в выборку R как
     * штатное решение.
     */
    private Deal.CloseOutcome closeOutcome(DealContext dealContext, boolean hadEntry, List<Position> episodes) {
        if (isFalse(hadEntry)) {
            return null;
        }
        if (isFalse(dealContext.getGraphComplete())) {
            return Deal.CloseOutcome.UNDETERMINED;
        }
        List<Deal.CloseOutcome> outcomes = episodes.stream().map(this::episodeOutcome).toList();
        if (outcomes.contains(Deal.CloseOutcome.LIQUIDATION)) {
            return Deal.CloseOutcome.LIQUIDATION;
        }
        if (outcomes.contains(Deal.CloseOutcome.FORCED_REDUCTION)) {
            return Deal.CloseOutcome.FORCED_REDUCTION;
        }
        if (outcomes.contains(Deal.CloseOutcome.UNDETERMINED) || isEmpty(outcomes)) {
            return Deal.CloseOutcome.UNDETERMINED;
        }
        return Deal.CloseOutcome.NORMAL_EXIT;
    }

    /**
     * Исход одного эпизода. Непригодный операнд — пустой тип либо тип вне
     * перечня — даёт UNDETERMINED наравне с недобытой записью: корзина
     * одна, различает их отчёт, а не пятое значение перечня.
     */
    private Deal.CloseOutcome episodeOutcome(Position episode) {
        if (isNull(episode.getExternalRealizedProfit())) {
            return Deal.CloseOutcome.UNDETERMINED;
        }
        String closeType = episode.getExternalCloseType();
        if (NORMAL_EXIT_TYPES.contains(closeType)) {
            return Deal.CloseOutcome.NORMAL_EXIT;
        }
        if (LIQUIDATION_TYPES.contains(closeType)) {
            return Deal.CloseOutcome.LIQUIDATION;
        }
        if (FORCED_REDUCTION_TYPES.contains(closeType)) {
            return Deal.CloseOutcome.FORCED_REDUCTION;
        }
        return Deal.CloseOutcome.UNDETERMINED;
    }

    /**
     * Запись ДОБЫТА, а исход из её типа не выводится. Недобытая запись
     * сюда не попадает: она наблюдаема тропой добычи и отчёта не требует.
     */
    private Boolean unrecognizedCloseTypeReported(List<Position> episodes) {
        return episodes.stream()
                .filter(episode -> nonNull(episode.getExternalRealizedProfit()))
                .anyMatch(episode -> isFalse(closeTypeRecognized(episode.getExternalCloseType())));
    }

    private Boolean closeTypeRecognized(String closeType) {
        return isFalse(isBlank(closeType))
                && (NORMAL_EXIT_TYPES.contains(closeType)
                || LIQUIDATION_TYPES.contains(closeType)
                || FORCED_REDUCTION_TYPES.contains(closeType));
    }

    // ------------------------------------------------------------------
    // Полнота разбивки
    // ------------------------------------------------------------------

    /**
     * Накрыло ли окно добычи всю жизнь сделки: возраст нижней границы
     * окна на момент добычи против глубины доступности движений у
     * источника. Добыча не выполнялась — сравнивать нечего, и это
     * единственный триггер значения «не оценивалось».
     *
     * <p>Нижняя граница берётся тем же двухместным операндом, что у
     * конвейера добычи: у сделки, заведённой восстановлением, входной
     * заявки не отправлялось никогда, и границу даёт биржевое время
     * открытия наблюдённой позиции
     * (docs/models/domain/aggregate/Deal.md §«Окно линковки движений»).
     */
    private Deal.BreakdownCompleteness breakdownCompleteness(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        OffsetDateTime fetchedThrough = deal.getBillsFetchedThrough();
        if (isNull(fetchedThrough)) {
            return Deal.BreakdownCompleteness.NOT_ASSESSED;
        }
        OffsetDateTime lowerBound = nonNull(deal.getBillsWindowBegin())
                ? deal.getBillsWindowBegin()
                : deal.getExternalCreatedAt();
        if (isNull(lowerBound)) {
            return Deal.BreakdownCompleteness.NOT_ASSESSED;
        }
        Duration availableDepth = Duration.ofDays(exchangeContourProperties
                .forExchange(dealContext.getExchange().getName())
                .getBillsArchiveDepthDays());
        return Duration.between(lowerBound, fetchedThrough).compareTo(availableDepth) > 0
                ? Deal.BreakdownCompleteness.INCOMPLETE_BY_WINDOW
                : Deal.BreakdownCompleteness.COMPLETE;
    }

    // ------------------------------------------------------------------
    // Доступность знаменателя
    // ------------------------------------------------------------------

    /**
     * Значение определяют два различителя, и первый — финализация: на
     * сделке с уже финализированным числом терминал признаков не пишет
     * вовсе, и этот различитель стои́т выше — во входной ветви
     * {@link #apply}. Здесь остаётся второй: «входа не было» даёт
     * NOT_APPLICABLE, иначе AVAILABLE либо MISSING по факту знаменателя.
     *
     * <p>Вырожденный знаменатель читается как отсутствующий: нулём
     * R-мультипликатор не делится, и обозвать такую сделку AVAILABLE
     * значило бы объявить пригодным то, что пригодным не является.
     */
    private Deal.RiskBenchmarkAvailability benchmarkAvailability(Deal deal, boolean hadEntry) {
        if (isFalse(hadEntry)) {
            return Deal.RiskBenchmarkAvailability.NOT_APPLICABLE;
        }
        return nonNull(deal.getPlannedRiskAmount()) && deal.getPlannedRiskAmount().signum() > 0
                ? Deal.RiskBenchmarkAvailability.AVAILABLE
                : Deal.RiskBenchmarkAvailability.MISSING;
    }
}

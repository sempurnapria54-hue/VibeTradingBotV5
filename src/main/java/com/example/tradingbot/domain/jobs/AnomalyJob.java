package com.example.tradingbot.domain.jobs;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.config.AnomalyJobProperties;
import com.example.tradingbot.domain.deal.DealOpeningService;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.domain.safety.AccountingDetectors;
import com.example.tradingbot.domain.safety.AnomalyPassGate;
import com.example.tradingbot.domain.safety.AnomalyScan;
import com.example.tradingbot.domain.safety.AnomalyScanReader;
import com.example.tradingbot.domain.safety.DealInvariantDetectors;
import com.example.tradingbot.domain.safety.ExchangeSideDetectors;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Сравнивает живые факты биржи с доменными сущностями и ищет нарушения
 * инвариантов. Сделку по FSM не ведёт.
 *
 * <p><b>В этом классе построен один детектор — активная позиция без
 * сделки, объясняющей её появление;</b> прочие живут у коллабораторов
 * (перечень ниже). Реакция на него — вызов
 * ВОССТАНОВИТЕЛЬНОЙ тропы создателя сделки тем же тиком
 * (docs/components/DealOpeningService.md — дом тропы): заведение сделки
 * <b>и есть</b> реакция на эту аномалию. Без этого вызова значение
 * {@code EntryReason.RECOVERY} не производит никто, а найденный вне
 * приложения живой риск остаётся вне модели — невидимым всем механизмам,
 * считающим по сделке.
 *
 * <p><b>Ступень сворачивания поднимает не эта джоба и не создатель
 * сделки, а инвариант экспозиции.</b> Экспозиция транша производна от его
 * заявок, а у восстановленного транша заявок нет — сумма экспозиций
 * расходится с нетто-размером живого эпизода с первого же прохода, и
 * реакцию даёт лестница инварианта
 * (docs/models/domain/aggregate/Deal.md). Собственной тропы реакции
 * восстановление не заводит.
 *
 * <p><b>Перечень построен: одиннадцать детекторов</b> из двенадцати
 * живых (docs/components/AnomalyJob.md §«Что ищет»). Кода нет только у
 * A13 — переоценки инварианта ликвидации: её такт, гистерезис и выбор
 * между ремоделом и выходом остаются предметом шага сопровождения.
 * Ступень и радиус у каждого детектора ВЫВОДЯТСЯ тремя ратифицированными
 * осями; реакции проход не заводит — зовёт лестницу.
 *
 * <p>Детекторы разложены по источнику операнда: биржевые признаки —
 * {@link com.example.tradingbot.domain.safety.ExchangeSideDetectors},
 * сверка наших строк с биржей —
 * {@link com.example.tradingbot.domain.safety.AccountingDetectors},
 * инварианты живой сделки —
 * {@link com.example.tradingbot.domain.safety.DealInvariantDetectors},
 * полнота прохода —
 * {@link com.example.tradingbot.domain.safety.AnomalyPassGate}.
 *
 * <p>Concurrency — in-memory {@link JobExecutionGuard}; создание дубля
 * предотвращает тот же gatekeeper, что и на входной тропе. CRON/enabled
 * — конвенция джоб (.claude/rules/codestyle.md §Джобы). См.
 * docs/components/AnomalyJob.md.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyJob {

    private static final String JOB_NAME = "anomalyJob";

    /**
     * Окно чтения контура: инструменты и биржи читаются ограниченной
     * выборкой, а упор в окно засчитывается неполнотой прохода — тем же
     * ходом, что и неполученный срез. Число — потолок реестра контура, не
     * калибровка наблюдения: онбординг заводит инструменты десятками.
     */
    private static final int CONTOUR_WINDOW = 500;

    private final AnomalyJobProperties properties;
    private final JobExecutionGuard executionGuard;
    private final InstrumentDataService instrumentDataService;
    private final ExchangeDataService exchangeDataService;
    private final DealDataService dealDataService;
    private final AnomalyScanReader scanReader;
    private final AnomalyPassGate passGate;
    private final ExchangeSideDetectors exchangeSideDetectors;
    private final AccountingDetectors accountingDetectors;
    private final DealInvariantDetectors dealInvariantDetectors;
    private final DealOpeningService dealOpeningService;

    @Scheduled(cron = "${anomaly-job.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    /**
     * Проход: срез счёта + контур → детекция → отметка прохода.
     *
     * <p><b>Статусные ворота входной тропы здесь НЕ стоят:</b> риск уже
     * живой, и пропуск заблокированного инструмента оставил бы его вне
     * модели — ровно тем зависшим живым риском, которого не бывает.
     * Блокировка гасит новые входы, а не учёт уже существующего. Поэтому
     * контур читается ЦЕЛИКОМ: обход по одному статусу делал бы `A6`
     * недостижимым (его популяция — как раз заблокированный инструмент),
     * а `A2` — ложно срабатывающим на нашем собственном инструменте под
     * мягкой ступенью, то есть замыкал бы петлю «мягкий холд → через тик
     * жёсткая биржевая ступень со сносом по рынку».
     *
     * <p><b>На неполном проходе прочие детекторы МОЛЧАТ.</b> Признак
     * всякого из них — расхождение БД с биржей, и на неполном срезе
     * расхождение производит сама неполнота; ложный триггер снял бы риск,
     * которого нет (docs/components/AnomalyJob.md §«Гейт полноты среза»).
     *
     * <p><b>Проход отмечается ПОСЛЕ детекции, а не до неё.</b> Отметка
     * «полон» до обхода сбрасывала бы счёт слепоты и на том проходе, где
     * детекция упала: «не смотрели» стало бы неотличимо от «ничего не
     * нашли» — ровно то различение, ради которого `A10` и заведён (П3).
     */
    private void run() {
        AnomalyScan scan = scanReader.read();
        List<Instrument> contour = instrumentDataService.findContourWithin(CONTOUR_WINDOW);
        List<Exchange> exchanges = exchangeDataService.findContourWithin(CONTOUR_WINDOW);
        Boolean contourComplete = withinWindow("инструменты", contour.size())
                && withinWindow("биржи", exchanges.size());
        Boolean sliceComplete = isTrue(scan.getComplete()) && isTrue(contourComplete);
        Set<String> contourNames = contourNames(contour);
        for (Exchange exchange : exchanges) {
            Boolean observed = false;
            try {
                if (isTrue(sliceComplete)) {
                    detect(scan, exchange, contour, contourNames);
                    observed = true;
                }
            } catch (RuntimeException e) {
                log.error("Anomaly detection failed exchangeId={}", exchange.getId(), e);
            }
            try {
                passGate.apply(observed, exchange);
            } catch (RuntimeException e) {
                log.error("Anomaly pass gate failed exchangeId={}", exchange.getId(), e);
            }
        }
    }

    /** Детекторы прохода. Каждый молчит на неполном срезе — гейт выше. */
    private void detect(AnomalyScan scan, Exchange exchange, List<Instrument> contour, Set<String> contourNames) {
        exchangeSideDetectors.detect(scan, exchange, contourNames);
        dealInvariantDetectors.detect(exchange);
        for (Instrument instrument : contour) {
            try {
                Boolean dealExplains = dealDataService.existsActiveByInstrumentId(instrument.getId());
                detectUnexplainedPosition(instrument, first(scan.positionsOf(instrument.getExternalId())),
                        dealExplains);
                accountingDetectors.detect(scan, exchange, instrument, dealExplains);
            } catch (RuntimeException e) {
                log.error("Anomaly detection failed instrumentId={}", instrument.getId(), e);
            }
        }
    }

    /**
     * Выборка контура уложилась в окно. Упор в окно означает «возможно,
     * есть ещё», и засчитывается неполнотой прохода — тем же ходом, что и
     * неполученный срез: обход по усечённому контуру объявил бы чужими
     * строки среза, которым не хватило места в выборке.
     */
    private Boolean withinWindow(String name, int size) {
        if (size < CONTOUR_WINDOW) {
            return true;
        }
        log.warn("[anomaly] выборка «{}» упёрлась в окно ({}) — проход считается неполным", name, CONTOUR_WINDOW);
        return false;
    }

    /** Биржевые имена инструментов контура — граница «модель против счёта». */
    private Set<String> contourNames(List<Instrument> contour) {
        return contour.stream()
                .map(Instrument::getExternalId)
                .filter(Objects::nonNull)
                .collect(toSet());
    }

    /**
     * Первая запись среза по инструменту. Больше одной живой позиции
     * модель не допускает; расхождение — предмет своего детектора, и
     * здесь оно не гасится молча.
     */
    private PositionExternalSnapshot first(List<PositionExternalSnapshot> rows) {
        return isEmpty(rows) ? null : rows.getFirst();
    }

    /**
     * Активная позиция без сделки, объясняющей её появление. Определение
     * обнаруженного и есть защитная проверка отсутствия активной сделки:
     * позицию, которую сделка объясняет, восстанавливать не надо.
     *
     * <p>Дешёвый гард идёт первым: в штатном режиме позиции по
     * инструменту нет, и признак снимается без обращения к БД.
     */
    private void detectUnexplainedPosition(Instrument instrument, PositionExternalSnapshot snapshot,
                                           Boolean dealExplains) {
        if (isFalse(carriesLiveRisk(snapshot))) {
            return;
        }
        if (isTrue(dealExplains)) {
            return;
        }
        StrategyTradeDirection direction = directionOf(snapshot);
        if (isNull(direction)) {
            // Знак размера не определён: направление позиции неизвестно, а
            // сделка заводится ВОКРУГ наблюдённого факта — подставлять
            // сторону нечем. Наблюдение остаётся в логе, следующий тик
            // перечитает.
            log.warn("Unexplained position with undetermined direction instrumentId={} externalId={}",
                    instrument.getId(), snapshot.getExternalId());
            return;
        }
        log.warn("Unexplained live position instrumentId={} externalId={} size={} — recovering deal",
                instrument.getId(), snapshot.getExternalId(), snapshot.getExternalSize());
        // Биржевой момент — время ОТКРЫТИЯ наблюдённой позиции: своей
        // входной заявки такая сделка не отправит никогда, и это поле —
        // единственный операнд нижней границы окна линковки движений.
        dealOpeningService.recoverDeal(instrument.getId(), direction, snapshot.getExternalCreatedAt());
    }

    /** Снапшот несёт живой риск: позиция найдена и её размер положителен. */
    private Boolean carriesLiveRisk(PositionExternalSnapshot snapshot) {
        return nonNull(snapshot) && nonNull(snapshot.getExternalSize())
                && snapshot.getExternalSize().compareTo(BigDecimal.ZERO) > 0;
    }

    /** Направление наблюдённой позиции в словаре сделки; пусто — знак не определён. */
    private StrategyTradeDirection directionOf(PositionExternalSnapshot snapshot) {
        if (Position.Direction.LONG.equals(snapshot.getDirection())) {
            return StrategyTradeDirection.LONG;
        }
        return Position.Direction.SHORT.equals(snapshot.getDirection())
                ? StrategyTradeDirection.SHORT
                : null;
    }
}

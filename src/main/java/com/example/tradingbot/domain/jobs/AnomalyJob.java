package com.example.tradingbot.domain.jobs;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
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
import com.example.tradingbot.domain.safety.AnomalyPassGate;
import com.example.tradingbot.domain.safety.AnomalyScan;
import com.example.tradingbot.domain.safety.AnomalyScanReader;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Сравнивает живые факты биржи с доменными сущностями и ищет нарушения
 * инвариантов. Сделку по FSM не ведёт.
 *
 * <p><b>Что построено здесь — один детектор: активная позиция без сделки,
 * объясняющей её появление.</b> Реакция на него — вызов
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
 * <p><b>Названное ограничение — неполнота КОДОВАЯ, не концептуальная.</b>
 * Перечень детекторов объявлен целиком (docs/components/AnomalyJob.md
 * §«Что ищет»), <b>ступень и радиус у каждого выводятся</b> тремя
 * ратифицированными осями, а такт и гистерезис объявлены там же. Здесь
 * построен один детектор из тринадцати; остальные — CODE-дельта шага 8
 * (`.claude/work/backlog.md` §«Шаг 8 (safety / AnomalyJob)»). У A7 в
 * дельту входит и обязательство слоя команд: ни одна наша заявка не
 * уходит на биржу без распознаваемого clOrdId — по нему и только по нему
 * опознаётся чужая.
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

    private final AnomalyJobProperties properties;
    private final JobExecutionGuard executionGuard;
    private final InstrumentDataService instrumentDataService;
    private final ExchangeDataService exchangeDataService;
    private final DealDataService dealDataService;
    private final AnomalyScanReader scanReader;
    private final AnomalyPassGate passGate;
    private final DealOpeningService dealOpeningService;

    @Scheduled(cron = "${anomaly-job.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    /**
     * Проход: срез счёта → гейт полноты → обход управляемых инструментов.
     *
     * <p>Статусные ворота входной тропы здесь НЕ стоят: риск уже живой, и
     * пропуск заблокированного инструмента оставил бы его вне модели —
     * ровно тем зависшим живым риском, которого не бывает. Блокировка
     * гасит новые входы, а не учёт уже существующего.
     *
     * <p><b>На неполном проходе прочие детекторы МОЛЧАТ.</b> Признак
     * всякого из них — расхождение БД с биржей, и на неполном срезе
     * расхождение производит сама неполнота; ложный триггер снял бы риск,
     * которого нет (docs/components/AnomalyJob.md §«Гейт полноты среза»).
     */
    private void run() {
        AnomalyScan scan = scanReader.read();
        for (Exchange exchange : exchangeDataService.findAllActive()) {
            try {
                passGate.apply(scan, exchange);
                if (isTrue(scan.getComplete())) {
                    detect(scan, exchange);
                }
            } catch (RuntimeException e) {
                log.error("Anomaly pass failed exchangeId={}", exchange.getId(), e);
            }
        }
    }

    /** Детекторы прохода. Каждый молчит на неполном срезе — гейт выше. */
    private void detect(AnomalyScan scan, Exchange exchange) {
        for (Instrument instrument : instrumentDataService.findByStatus(Instrument.Status.ACTIVE)) {
            try {
                detectUnexplainedPosition(instrument, first(scan.positionsOf(instrument.getExternalId())));
            } catch (RuntimeException e) {
                log.error("Anomaly detection failed instrumentId={}", instrument.getId(), e);
            }
        }
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
     */
    private void detectUnexplainedPosition(Instrument instrument, PositionExternalSnapshot snapshot) {
        if (isTrue(dealDataService.existsActiveByInstrumentId(instrument.getId()))) {
            return;
        }
        if (isFalse(carriesLiveRisk(snapshot))) {
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

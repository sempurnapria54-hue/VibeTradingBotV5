package com.example.tradingcore.domain.jobs;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.config.AnomalyJobProperties;
import com.example.tradingcore.domain.deal.DealOpeningService;
import com.example.tradingcore.domain.safety.AccountingDetectors;
import com.example.tradingcore.domain.safety.AnomalyPassGate;
import com.example.tradingcore.domain.safety.AnomalyScan;
import com.example.tradingcore.domain.safety.AnomalyScanReader;
import com.example.tradingcore.domain.safety.DealInvariantDetectors;
import com.example.tradingcore.domain.safety.ExchangeSideDetectors;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Сравнивает живые факты биржи с доменными сущностями и ищет <b>нарушения
 * инвариантов</b>, а не штатные runtime-ситуации
 * (docs/components/AnomalyJob.md). Сделку по FSM не ведёт.
 *
 * <p><b>Проход идёт по биржевым СЧЕТАМ, а не по площадкам.</b> Срезы
 * читаются ключами счёта, и каждый счёт наблюдается своим проходом: у двух
 * счетов одной площадки живые сущности независимы, и срез одного о другом
 * ничего не говорит.
 *
 * <p><b>Здесь построен один детектор — активная позиция без сделки,
 * объясняющей её появление;</b> прочие живут у коллабораторов. Реакция на
 * него — вызов ВОССТАНОВИТЕЛЬНОЙ тропы создателя сделки тем же тиком:
 * заведение сделки <b>и есть</b> реакция на эту аномалию. Без этого вызова
 * найденный вне приложения живой риск остаётся вне модели — невидимым всем
 * механизмам, считающим по сделке.
 *
 * <p><b>Ступень сворачивания поднимает не эта джоба и не создатель
 * сделки, а инвариант экспозиции.</b> У восстановленного транша заявок
 * нет — сумма экспозиций расходится с нетто-размером живого эпизода с
 * первого же прохода, и реакцию даёт лестница инварианта.
 *
 * <p><b>Статусные ворота отбора входа здесь НЕ стоят:</b> риск уже живой,
 * и пропуск заблокированного инструмента оставил бы его вне модели.
 * Блокировка гасит новые входы, а не учёт уже существующего — поэтому
 * контур читается ЦЕЛИКОМ.
 *
 * <p><b>На неполном проходе прочие детекторы МОЛЧАТ</b>, а проход
 * отмечается ПОСЛЕ детекции: отметка «полон» до обхода сбрасывала бы счёт
 * слепоты и на том проходе, где детекция упала.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyJob {

    private static final String JOB_NAME = "anomalyJob";

    private final AnomalyJobProperties properties;
    private final JobExecutionGuard executionGuard;
    private final ExchangeAccountDataService exchangeAccountDataService;
    private final AccountInstrumentStateDataService accountInstrumentStateDataService;
    private final InstrumentDataService instrumentDataService;
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
     * Проход по счетам контура: срез счёта плюс контур площадки →
     * детекция → отметка прохода.
     *
     * <p>Популяция — счета <b>реестрового</b> статуса, ступенью не сужая:
     * счёт под ступенью наблюдается наравне с прочими, и именно он —
     * популяция детектора непроэнфорсенной блокировки.
     */
    private void run() {
        for (ExchangeAccount account : exchangeAccountDataService.findTradingAccounts()) {
            Boolean observed = false;
            try {
                observed = observe(account);
            } catch (RuntimeException e) {
                log.error("Anomaly detection failed exchangeAccountId={}", account.getId(), e);
            }
            try {
                passGate.apply(observed, account);
            } catch (RuntimeException e) {
                log.error("Anomaly pass gate failed exchangeAccountId={}", account.getId(), e);
            }
        }
    }

    /**
     * Наблюдение одного счёта; {@code false} — проход по нему неполон, и
     * детекторы промолчали.
     */
    private Boolean observe(ExchangeAccount account) {
        AnomalyScan scan = scanReader.read(account.getInternalId());
        List<Instrument> contour = instrumentDataService.findContourWithin(account.getExchangeCode(),
                properties.getContourWindow());
        if (isFalse(scan.getComplete()) || isFalse(withinWindow(contour.size()))) {
            return false;
        }
        detect(scan, account, contour);
        return true;
    }

    /** Детекторы прохода. Каждый молчит на неполном срезе — гейт выше. */
    private void detect(AnomalyScan scan, ExchangeAccount account, List<Instrument> contour) {
        exchangeSideDetectors.detect(scan, account, contourNames(contour));
        dealInvariantDetectors.detect(account);
        Boolean accountHardRung = ExchangeAccount.SafetyRung.TRADE_BLOCKED.equals(account.getSafetyRung());
        Set<Long> hardRungPairs = new HashSet<>(
                accountInstrumentStateDataService.findInstrumentIdsUnderHardRung(account.getId()));
        // Объяснённость парного слота — операнд КАЖДОГО инструмента контура,
        // поэтому читается пачкой на счёт, а не запросом на итерацию
        // (.claude/rules/codestyle.md §«Выборка данных»).
        Set<Long> explainedPairs = dealDataService.findInstrumentIdsWithActiveDeal(account.getId());
        for (Instrument instrument : contour) {
            try {
                Boolean dealExplains = explainedPairs.contains(instrument.getId());
                detectUnexplainedPosition(account, instrument,
                        first(scan.positionsOf(instrument.getExternalId())), dealExplains);
                accountingDetectors.detect(scan, account, instrument, accountHardRung, hardRungPairs,
                        dealExplains);
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
    private Boolean withinWindow(int size) {
        if (size < properties.getContourWindow()) {
            return true;
        }
        log.warn("Contour selection hit the window ({}): the pass counts as incomplete",
                properties.getContourWindow());
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
    private Position first(List<Position> rows) {
        return isEmpty(rows) ? null : rows.getFirst();
    }

    /**
     * Активная позиция без сделки, объясняющей её появление. Определение
     * обнаруженного и есть защитная проверка отсутствия активной сделки:
     * позицию, которую сделка объясняет, восстанавливать не надо.
     *
     * <p>Дешёвый гард идёт первым: в штатном режиме позиции по инструменту
     * нет, и признак снимается без обращения к БД.
     */
    private void detectUnexplainedPosition(ExchangeAccount account, Instrument instrument,
                                           Position position, Boolean dealExplains) {
        if (isFalse(carriesLiveRisk(position)) || isTrue(dealExplains)) {
            return;
        }
        StrategyTradeDirection direction = directionOf(position);
        if (isNull(direction)) {
            // Знак размера не определён: направление позиции неизвестно, а
            // сделка заводится ВОКРУГ наблюдённого факта — подставлять
            // сторону нечем. Наблюдение остаётся в логе, следующий тик
            // перечитает.
            log.warn("Unexplained position with undetermined direction instrumentId={} externalId={}",
                    instrument.getId(), position.getExternalId());
            return;
        }
        log.warn("Unexplained live position instrumentId={} externalId={} size={} — recovering the deal",
                instrument.getId(), position.getExternalId(), position.getExternalSize());
        // Биржевой момент — время ОТКРЫТИЯ наблюдённой позиции: своей
        // входной заявки такая сделка не отправит никогда, и это поле —
        // единственный операнд нижней границы окна линковки движений.
        dealOpeningService.recoverDeal(account, instrument, direction, position.getExternalCreatedAt());
    }

    /** Запись среза несёт живой риск: позиция найдена и её размер положителен. */
    private Boolean carriesLiveRisk(Position position) {
        return nonNull(position) && nonNull(position.getExternalSize())
                && position.getExternalSize().compareTo(BigDecimal.ZERO) > 0;
    }

    /** Направление наблюдённой позиции в словаре сделки; пусто — знак не определён. */
    private StrategyTradeDirection directionOf(Position position) {
        if (Position.Direction.LONG.equals(position.getDirection())) {
            return StrategyTradeDirection.LONG;
        }
        return Position.Direction.SHORT.equals(position.getDirection())
                ? StrategyTradeDirection.SHORT
                : null;
    }
}

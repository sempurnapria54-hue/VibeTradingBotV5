package com.example.marketdata.domain.service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.marketdata.config.CandleLoadingProperties;
import com.example.marketdata.integration.internal.api.ExchangeReadClient;
import com.example.marketdata.persistence.service.CandleDataService;
import com.example.marketdata.persistence.service.CandleGroupDataService;
import com.example.marketdata.persistence.service.InstrumentDataService;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ведёт одну {@link CandleGroup} по жизненному циклу загрузки свечей
 * (docs/lifecycles/CandleGroup.md): BACKFILL (выкачка истории в глубину
 * до заказанного горизонта либо пустого ответа) → CHECK (проверка
 * целостности по count) → REPAIR (докачка дыр бинарным поиском) → ACTIVE;
 * из ACTIVE — SYNC (докачка хвоста вместе с проверкой) → ACTIVE либо
 * REPAIR. Идемпотентность держит {@link CandleDataService}
 * (естественный ключ группы и открытия бара).
 *
 * <p><b>Горизонт бэкфилла берётся у ГРУППЫ, а не у инструмента:</b>
 * глубину называет требование потребителя, и у 1m и 1D одного
 * инструмента она разная (docs/processes/candle-loading.md §«Кто заводит
 * группу»).
 *
 * <p><b>Незакрытые бары сюда не доходят:</b> признак закрытия виден
 * коннектору, и наружу он отдаёт только закрытые
 * (docs/models/domain/other/Candle.md). Второй фильтр по признаку,
 * которого в ответе нет, был бы фикцией.
 *
 * <p><b>Счётчик попыток докачки лежит на группе и едет её же записью</b>
 * (docs/models/domain/other/CandleGroup.md §Структура): гарантия
 * «исчерпаны попытки — {@code ERROR}» переживает рестарт. Попытка
 * засчитывается состоявшимся проходом починки — тем, что записал группу;
 * проход, упавший на чтении у площадки, группу не пишет и бюджета не
 * расходует: неустранимую дыру от временного отказа отличает ответ
 * площадки, а не его отсутствие.
 *
 * <p><b>Проход, продвинувший ряд к плотности, бюджет возвращает.</b>
 * Предел попыток отличает неустранимую дыру, а не широкую: хвост,
 * отросший за долгий бэкфилл шире нескольких страниц, латается починкой
 * постранично, и без возврата бюджета исчерпал бы его на заведомо
 * устранимой дыре (docs/lifecycles/CandleGroup.md §«Докачка дыр
 * (`REPAIR`)»). Прогресс меряется недостачей до плотности, а не числом
 * вставленных свечей: бары ниже нижней границы ряда её не сокращают.
 *
 * <p><b>Докачка хвоста проверяет целостность тем же шагом.</b> Отдельный
 * шаг {@code CHECK} после {@code SYNC} держал бы готовую группу вне
 * {@code ACTIVE} на пересчёте готовности каждого тика с новым баром, и
 * инструмент терял бы готовность на штатной догонке
 * (docs/lifecycles/Instrument.md §«Штатная докачка хвоста готовности не
 * снимает»).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandleLoader {

    private final ExchangeReadClient readClient;
    private final CandleDataService candleDataService;
    private final CandleGroupDataService candleGroupDataService;
    private final InstrumentDataService instrumentDataService;
    private final CandleLoadingProperties properties;

    /**
     * Продвигает группу на один шаг согласно её статусу.
     *
     * <p><b>Итог шага ложится точечной записью, и только если группу за
     * время шага никто не переписал.</b> Шаг идёт с чтением площадки, а
     * углублённое требование потребителя пишет ту же строку в любой момент
     * (docs/lifecycles/CandleGroup.md §«Возврат к `BACKFILL` по углублённому
     * требованию»). Запись группы целиком вернула бы статус и горизонт к
     * снимку начала шага, и группа дошла бы до {@code ACTIVE} с непокрытым
     * горизонтом. Поэтому горизонт шаг не пишет вовсе, а свой итог
     * записывает лишь при статусе и горизонте, застанных в начале шага;
     * иначе итог отбрасывается, и следующий тик ведёт группу из того, что
     * написало требование. Свечи, записанные шагом, остаются: ряд идемпотентен
     * по ключу, а счёт и границы следующий шаг берёт из ряда заново.
     *
     * <p><b>Переход в {@code ERROR} журналируется после записи, а не до:</b>
     * отброшенный итог перехода не совершил, и лог не говорит больше
     * записанного. Терминал, который пишет этот цикл, один — {@code ERROR}
     * исчерпанной докачки дыр.
     */
    public void advance(CandleGroup group) {
        CandleGroup.Status loadedStatus = group.getStatus();
        Long loadedHorizon = group.getPlannedFirstUtcMillis();
        switch (loadedStatus) {
            case CREATED -> startBackfill(group);
            case BACKFILL -> backfill(group);
            case SYNC -> sync(group);
            case CHECK -> check(group);
            case REPAIR -> repair(group);
            default -> {
                // ACTIVE / ERROR / DELETED — в этом цикле не ведутся.
                return;
            }
        }
        if (isFalse(candleGroupDataService.saveLoadingStep(group, loadedStatus, loadedHorizon))) {
            log.info("CandleGroup {} was rewritten during its {} step; the step outcome is dropped",
                    group.getId(), loadedStatus);
            return;
        }
        if (isTrue(group.isTerminal())) {
            log.error("CandleGroup {} exceeded {} repair attempts -> ERROR",
                    group.getId(), properties.getMaxRepairAttempts());
        }
    }

    private void startBackfill(CandleGroup group) {
        group.setStatus(CandleGroup.Status.BACKFILL);
    }

    private void backfill(CandleGroup group) {
        Instrument instrument = instrumentDataService.getRequiredById(group.getInstrumentId());
        List<Candle> page = readClient.getHistoryCandles(instrument.getExternalId(), group.getTimeframe(),
                group.getActualFirstUtcMillis(), properties.getPageSize());
        persist(group, page);
        reconcile(group);
        if (isBackfillComplete(group, page)) {
            group.setStatus(CandleGroup.Status.CHECK);
        }
    }

    private void sync(CandleGroup group) {
        Instrument instrument = instrumentDataService.getRequiredById(group.getInstrumentId());
        List<Candle> page = readClient.getLatestCandles(instrument.getExternalId(), group.getTimeframe(),
                properties.getPageSize());
        persist(group, page);
        reconcile(group);
        settleIntegrity(group);
    }

    private void check(CandleGroup group) {
        reconcile(group);
        settleIntegrity(group);
    }

    /**
     * Проверка целостности по count: плотный ряд — {@code ACTIVE} с
     * обнулённым бюджетом докачки, дефицит — {@code REPAIR}. Границы и
     * {@code count} к этому моменту сведены с рядом в базе.
     */
    private void settleIntegrity(CandleGroup group) {
        if (group.isDense()) {
            group.resetRepairAttempts();
            group.setStatus(CandleGroup.Status.ACTIVE);
            return;
        }
        group.setStatus(CandleGroup.Status.REPAIR);
    }

    private void repair(CandleGroup group) {
        group.registerRepairAttempt();
        if (group.hasExceededRepairAttempts(properties.getMaxRepairAttempts())) {
            group.setStatus(CandleGroup.Status.ERROR);
            return;
        }
        HoleWindow window = locateHole(group);
        if (isNull(window)) {
            group.setStatus(CandleGroup.Status.CHECK);
            return;
        }
        Instrument instrument = instrumentDataService.getRequiredById(group.getInstrumentId());
        long step = group.getTimeframe().getDurationMillis();
        Long deficitBefore = group.deficit();
        List<Candle> page = readClient.getHistoryCandles(instrument.getExternalId(), group.getTimeframe(),
                window.toMillis() + step, properties.getPageSize());
        persist(group, page);
        reconcile(group);
        if (group.deficit() < deficitBefore) {
            group.resetRepairAttempts();
        }
        group.setStatus(CandleGroup.Status.CHECK);
    }

    /**
     * Бэкфилл закончен, когда площадка отдала пусто (начало её истории)
     * либо нижняя граница загруженного достигла заказанного горизонта.
     *
     * <p>Пустой горизонт означает «глубина не заказана», и тогда бэкфилл
     * идёт до конца истории площадки: требование без глубины — требование
     * всей доступной истории, а не отсутствие требования.
     */
    private boolean isBackfillComplete(CandleGroup group, List<Candle> page) {
        if (isEmpty(page)) {
            return true;
        }
        Long first = group.getActualFirstUtcMillis();
        Long horizon = group.getPlannedFirstUtcMillis();
        return nonNull(first) && nonNull(horizon) && first <= horizon;
    }

    /**
     * Локализует окно с дырой бинарным поиском по count: делит
     * [actualFirst, actualLast], в дефицитную половину спускается, пока
     * окно не сузится до размера страницы.
     */
    private HoleWindow locateHole(CandleGroup group) {
        Long first = group.getActualFirstUtcMillis();
        Long last = group.getActualLastUtcMillis();
        if (isNull(first) || isNull(last)) {
            return null;
        }
        long step = group.getTimeframe().getDurationMillis();
        long windowSpan = step * properties.getPageSize();
        long lo = first;
        long hi = last;
        while (hi - lo > windowSpan) {
            long mid = lo + alignDown((hi - lo) / 2, step);
            long expectedLeft = (mid - lo) / step + 1L;
            long actualLeft = candleDataService.countInRange(group.getId(), lo, mid);
            if (actualLeft < expectedLeft) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return new HoleWindow(lo, hi);
    }

    private void persist(CandleGroup group, List<Candle> candles) {
        candleDataService.saveCandles(group.getId(), candles);
    }

    private void reconcile(CandleGroup group) {
        Long groupId = group.getId();
        group.setCount(candleDataService.count(groupId));
        group.setActualFirstUtcMillis(candleDataService.findMinOpenTimestamp(groupId));
        group.setActualLastUtcMillis(candleDataService.findMaxOpenTimestamp(groupId));
    }

    private long alignDown(long value, long step) {
        return (value / step) * step;
    }
}

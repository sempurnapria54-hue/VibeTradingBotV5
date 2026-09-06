package com.example.tradingcore.domain.safety;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.util.InternalIdFactory;
import com.example.tradingcore.util.Constants;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Детекторы, чей признак читается <b>целиком со стороны биржи</b>: живой
 * риск по инструменту вне контура, больше одной позиции на инструмент,
 * живая заявка без нашего маркера (docs/components/AnomalyJob.md §«Что
 * ищет»).
 *
 * <p><b>Первым двум гистерезис не нужен, и это не послабление.</b> Гонка
 * чтения — срез, прочитанный между отправкой нашей команды и её
 * появлением на бирже — их признака не производит: вторая позиция по
 * одному инструменту нашей командой не создаётся никогда, а строка
 * инструмента нашим ходом не исчезает.
 *
 * <p><b>Третьему гистерезис нужен, и посылка обратного опровергнута.</b>
 * Закрытие позиции идёт эндпоинтом, у которого клиентского идентификатора
 * нет в контракте вовсе, поэтому наша рыночная закрывающая заявка висит в
 * срезе БЕЗ маркера. По признаку с гистерезисом в один тик детектор снёс
 * бы счёт по рынку за наш штатный выход — тот самый класс, ради
 * исключения которого маркер и введён, только входом в него служит не
 * рестарт, а эндпоинт без поля.
 *
 * <p><b>Радиус у всех трёх счётный.</b> У первого строки инструмента нет
 * вовсе — инструментной реакции нечем адресоваться; у второго под
 * сомнением режим позиций СЧЁТА; у третьего — распоряжение счётом.
 * Ступень жёсткая: счёт принадлежит системе единолично, и сущность,
 * которую мы не создавали, означает, что им распоряжается кто-то ещё.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeSideDetectors {

    /** Признак читается из одного среза: подтверждения следующим тиком не требует. */
    private static final Integer WITHOUT_HYSTERESIS = 1;

    /** Признак производит наш собственный незавершённый ход: подтверждается следующим тиком. */
    private static final Integer CONFIRMED_NEXT_TICK = 2;

    private final AnomalyReaction reaction;

    /**
     * Проход по трём биржевым признакам.
     *
     * @param contour биржевые имена <b>всех</b> инструментов модели,
     *                независимо от их статуса: операнд первого детектора —
     *                «строки инструмента нет вовсе», и сужение его
     *                статусом объявляло бы чужим наш собственный
     *                заблокированный инструмент
     */
    public void detect(AnomalyScan scan, ExchangeAccount account, Set<String> contour) {
        for (String externalInstrumentId : scan.instrumentsWithLiveEntities()) {
            try {
                if (isFalse(contour.contains(externalInstrumentId))) {
                    outsideContour(externalInstrumentId, account);
                    continue;
                }
                duplicatePosition(scan, externalInstrumentId, account);
                foreignOrders(scan, externalInstrumentId, account);
            } catch (RuntimeException e) {
                // Отказ на одном имени обход не обрывает: остальные живые
                // сущности этого тика обязаны быть просмотрены, иначе
                // частичная детекция выглядела бы чистым проходом.
                log.error("Exchange-side detection failed externalInstrumentId={}", externalInstrumentId, e);
            }
        }
    }

    /**
     * Срез несёт живую сущность по инструменту, которого в модели нет
     * вовсе. Восстановление здесь недостижимо — строки инструмента нет, а
     * тропы «дотянуть спецификацию и допустить инструмент» не
     * существует, — поэтому риск не может быть приписан ничему.
     */
    private void outsideContour(String externalInstrumentId, ExchangeAccount account) {
        log.warn("Live entity on an instrument outside the contour externalInstrumentId={}",
                externalInstrumentId);
        reaction.apply(AnomalyFinding.builder()
                .scope(HoldScope.EXCHANGE_ACCOUNT)
                .rung(HoldRung.HARD)
                .code(Constants.Hold.EXCHANGE_FOREIGN_INSTRUMENT_RISK)
                .hysteresisTicks(WITHOUT_HYSTERESIS)
                .journalOnly(false)
                .build(), account);
    }

    /**
     * Позиций по одному инструменту больше одной. Модель допускает не
     * больше одной живой, и наблюдение обратного означает, что режим
     * позиций счёта не тот, который объявлен adapter-константой.
     */
    private void duplicatePosition(AnomalyScan scan, String externalInstrumentId, ExchangeAccount account) {
        int positions = scan.positionsOf(externalInstrumentId).size();
        if (positions <= 1) {
            return;
        }
        log.warn("More than one live position on the instrument externalInstrumentId={} positions={}",
                externalInstrumentId, positions);
        reaction.apply(AnomalyFinding.builder()
                .scope(HoldScope.EXCHANGE_ACCOUNT)
                .rung(HoldRung.HARD)
                .code(Constants.Hold.EXCHANGE_POSITION_MODE_VIOLATION)
                .hysteresisTicks(WITHOUT_HYSTERESIS)
                .journalOnly(false)
                .build(), account);
    }

    /**
     * Живая заявка либо отдельная условная без нашего маркера. БД в
     * признаке не участвует: «строки в БД нет» безопасным дискриминатором
     * не является — заявка, ушедшая на биржу до коммита своей строки,
     * переживает рестарт, а рестарт штатен.
     *
     * <p><b>Встроенная защита сюда не попадает по построению:</b> в
     * перечень живых ОТДЕЛЬНЫХ заявок она не входит и собственного
     * клиентского идентификатора не несёт. Отсюда две стороны: своей
     * встроенной защиты детектор чужой не объявит, и чужую не заметит.
     */
    private void foreignOrders(AnomalyScan scan, String externalInstrumentId, ExchangeAccount account) {
        boolean foreign = scan.ordersOf(externalInstrumentId).stream()
                .anyMatch(order -> isFalse(InternalIdFactory.isOurs(order.getInternalId())))
                || scan.algoOrdersOf(externalInstrumentId).stream()
                .anyMatch(algoOrder -> isFalse(InternalIdFactory.isOurs(algoOrder.getInternalId())));
        if (isFalse(foreign)) {
            return;
        }
        log.warn("Live order without the contour marker externalInstrumentId={}", externalInstrumentId);
        reaction.apply(AnomalyFinding.builder()
                .scope(HoldScope.EXCHANGE_ACCOUNT)
                .rung(HoldRung.HARD)
                .code(Constants.Hold.EXCHANGE_FOREIGN_ORDER)
                .hysteresisTicks(CONFIRMED_NEXT_TICK)
                .journalOnly(false)
                .build(), account);
    }
}

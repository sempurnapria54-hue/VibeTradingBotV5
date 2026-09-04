package com.example.tradingbot.domain.safety;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.util.ClientIdGenerator;
import com.example.tradingbot.util.Constants;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Детекторы, чей признак читается <b>целиком со стороны биржи</b>: `A2`
 * (живой риск по инструменту вне контура), `A3` (больше одной позиции на
 * инструмент), `A7` (живая заявка без нашего маркера).
 *
 * <p><b>`A2` и `A3` гистерезиса не требуют, и это не послабление.</b>
 * Гонка чтения — срез, прочитанный между отправкой нашей команды и её
 * появлением на бирже — их признака не производит: вторая позиция по
 * одному инструменту нашей командой не создаётся никогда, а строка
 * инструмента нашим ходом не исчезает (docs/components/AnomalyJob.md
 * §«Такт и гистерезис»).
 *
 * <p><b>`A7` гистерезис требует, и посылка обратного опровергнута.</b>
 * Наш собственный ход его признак производит: закрытие позиции идёт
 * эндпоинтом {@code close-position}, у которого клиентского
 * идентификатора нет в контракте вовсе
 * (docs/integrations/okx/contracts/position.md), поэтому наша рыночная
 * закрывающая заявка висит в срезе БЕЗ маркера. По прежнему признаку
 * детектор сносил бы биржу по рынку за наш штатный выход — тот самый
 * класс, ради исключения которого маркер и введён, только входом в него
 * служит не рестарт, а эндпоинт без поля.
 *
 * <p><b>Радиус у всех трёх биржевой.</b> У `A2` строки инструмента нет
 * вовсе — инструментной реакции нечем адресоваться; у `A3` под сомнением
 * режим позиций СЧЁТА; у `A7` — распоряжение счётом. Ступень жёсткая:
 * счёт принадлежит системе единолично (`docs/concept.md` §«Счёт
 * принадлежит системе единолично»), и сущность, которую мы не создавали,
 * означает, что им распоряжается кто-то ещё.
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
     * Проход по трём биржевым признакам. {@code contour} — биржевые имена
     * <b>всех</b> инструментов модели, независимо от их статуса: операнд
     * `A2` — «строки инструмента нет вовсе», и сужение его статусом
     * объявляло бы чужим наш собственный заблокированный инструмент.
     *
     * <p>Отказ на одном имени обход не обрывает: остальные живые сущности
     * этого тика обязаны быть просмотрены, иначе частичная детекция
     * выглядела бы чистым проходом.
     */
    public void detect(AnomalyScan scan, Exchange exchange, Set<String> contour) {
        for (String externalInstrumentId : scan.instrumentsWithLiveEntities()) {
            try {
                if (isFalse(contour.contains(externalInstrumentId))) {
                    outsideContour(externalInstrumentId, exchange);
                    continue;
                }
                duplicatePosition(scan, externalInstrumentId, exchange);
                foreignOrders(scan, externalInstrumentId, exchange);
            } catch (RuntimeException e) {
                log.error("Anomaly exchange-side detection failed instId={}", externalInstrumentId, e);
            }
        }
    }

    /**
     * `A2`: срез несёт живую сущность по инструменту, которого в модели
     * нет вовсе. Восстановление здесь недостижимо — строки инструмента
     * нет, а тропы «дотянуть спецификацию и допустить инструмент» не
     * существует, — поэтому риск не может быть приписан ничему.
     */
    private void outsideContour(String externalInstrumentId, Exchange exchange) {
        log.warn("[anomaly] живая сущность по инструменту вне контура instId={}", externalInstrumentId);
        reaction.apply(AnomalyFinding.builder()
                .scope(HoldScope.EXCHANGE)
                .rung(HoldRung.HARD)
                .code(Constants.Hold.EXCHANGE_FOREIGN_INSTRUMENT_RISK)
                .hysteresisTicks(WITHOUT_HYSTERESIS)
                .journalOnly(false)
                .build(), exchange);
    }

    /**
     * `A3`: позиций по одному инструменту больше одной. Модель допускает
     * не больше одной живой, и наблюдение обратного означает, что режим
     * позиций счёта не тот, который объявлен adapter-константой
     * {@code posSide=net}.
     */
    private void duplicatePosition(AnomalyScan scan, String externalInstrumentId, Exchange exchange) {
        int positions = scan.positionsOf(externalInstrumentId).size();
        if (positions <= 1) {
            return;
        }
        log.warn("[anomaly] больше одной позиции на инструменте instId={} позиций={}",
                externalInstrumentId, positions);
        reaction.apply(AnomalyFinding.builder()
                .scope(HoldScope.EXCHANGE)
                .rung(HoldRung.HARD)
                .code(Constants.Hold.EXCHANGE_POSITION_MODE_VIOLATION)
                .hysteresisTicks(WITHOUT_HYSTERESIS)
                .journalOnly(false)
                .build(), exchange);
    }

    /**
     * `A7`: живая заявка либо algo-заявка без нашего маркера. БД в
     * признаке не участвует — разбор в доме перечня.
     *
     * <p><b>Встроенная защита сюда не попадает по построению:</b> в
     * перечень живых ОТДЕЛЬНЫХ заявок она не входит, и собственного
     * клиентского идентификатора не несёт. Отсюда две стороны: своей
     * встроенной защиты детектор чужой не объявит, и чужую не заметит.
     *
     * <p><b>Гистерезис — два тика</b>, потому что наша собственная
     * закрывающая заявка маркера не несёт (разбор в шапке класса).
     */
    private void foreignOrders(AnomalyScan scan, String externalInstrumentId, Exchange exchange) {
        boolean foreign = scan.ordersOf(externalInstrumentId).stream()
                .anyMatch(order -> isFalse(ClientIdGenerator.isOurs(order.getInternalId())))
                || scan.algoOrdersOf(externalInstrumentId).stream()
                .anyMatch(algo -> isFalse(ClientIdGenerator.isOurs(algo.getInternalId())));
        if (isFalse(foreign)) {
            return;
        }
        log.warn("[anomaly] живая заявка без маркера контура instId={}", externalInstrumentId);
        reaction.apply(AnomalyFinding.builder()
                .scope(HoldScope.EXCHANGE)
                .rung(HoldRung.HARD)
                .code(Constants.Hold.EXCHANGE_FOREIGN_ORDER)
                .hysteresisTicks(CONFIRMED_NEXT_TICK)
                .journalOnly(false)
                .build(), exchange);
    }
}

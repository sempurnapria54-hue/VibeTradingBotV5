package com.example.tradingcore.integration.internal.event;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;

import com.example.tradingcore.config.BrokerProperties;
import com.example.tradingcore.exception.PoisonStrategyFactException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Обработчик отказа контейнера у группы определений ядра — исход отказа
 * применения <b>объявлен, а не взят умолчанием</b>
 * (docs/architecture/data-ownership.md §«Копии чужих данных»).
 *
 * <p><b>Умолчание клиента давало обратное объявленному:</b> десять
 * немедленных попыток, затем пропуск записи и продвижение смещения. У копии
 * определения это значило потерю активации без следа, а СЛЕДУЮЩИЕ факты о
 * том же определении применялись к копии, которой нет, — расхождение с
 * владельцем, которое не лечит ничто, кроме повторной активации руками.
 *
 * <p><b>Исходов два, и различает их класс отказа:</b>
 *
 * <ul>
 *   <li><b>всякий отказ, кроме отравленной записи, — отложенное
 *       применение.</b> Смещение не продвигается, запись повторяется с
 *       паузой без ограничения числа попыток; носитель отложенного — сама
 *       партия темы, второго не заводится. Штатный случай — проекции счёта
 *       либо инструмента ещё нет: её ставит свой проход по расписанию, и
 *       порядок между ним и приёмом не гарантирован ничем;</li>
 *   <li><b>отравленная запись</b> ({@link PoisonStrategyFactException})
 *       пропускается сразу, со строкой журнала, несущей координаты записи и
 *       первопричину: повтор её не исправит, а остановленная на ней партия
 *       не применила бы уже ни одного факта.</li>
 * </ul>
 *
 * <p><b>Пауза — величина конфигурации, число попыток — нет.</b> Пауза
 * управляет тем, как часто повтор бьётся в базу; сдаться отложенное
 * применение не может по построению — сдача и есть пропуск.
 */
@Slf4j
@Component
public class StrategyFactErrorHandler extends DefaultErrorHandler {

    public StrategyFactErrorHandler(BrokerProperties properties) {
        super(StrategyFactErrorHandler::skipPoison,
                new FixedBackOff(properties.getIntakeRetryInterval().toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS));
        addNotRetryableExceptions(PoisonStrategyFactException.class);
        setRetryListeners((record, failure, deliveryAttempt) -> log.warn(
                "Strategy fact application is deferred topic={} partition={} offset={} attempt={} cause={}",
                record.topic(), record.partition(), record.offset(), deliveryAttempt,
                getRootCauseMessage(failure)));
    }

    /** След пропуска отравленной записи: координаты и первопричина целиком. */
    private static void skipPoison(ConsumerRecord<?, ?> record, Exception failure) {
        log.error("Strategy fact is poison and is skipped topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset(), failure);
    }
}

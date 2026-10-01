package com.example.tradingcore.integration.internal.event;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;
import static org.apache.commons.lang3.exception.ExceptionUtils.getThrowableList;

import com.example.tradingcore.config.BrokerProperties;
import com.example.tradingcore.exception.PoisonStrategyFactException;
import com.example.tradingcore.persistence.service.ReceptionSkipDataService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.ConsumerAwareRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
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
 *       пропускается сразу, <b>со следом пропуска</b> — строкой
 *       {@code reception_skips} в базе ядра, несущей группу, координаты
 *       записи, идентичность и класс из конверта, ключ и первопричину
 *       (docs/rules/durable-consumer-reception.md §«След пропуска — таблица
 *       `reception_skips`»): повтор её не исправит, а остановленная на ней
 *       партия не применила бы уже ни одного факта.</li>
 * </ul>
 *
 * <p><b>След не записан — запись не пропущена.</b> Строка ложится отдельной
 * транзакцией до того, как смещение продвинется; отказ записи пробрасывается
 * из восстановления, и каркас возвращает запись в партию, не продвигая
 * смещения. Повторная доставка уже пропущенной записи (фиксация смещения
 * после записи следа не состоялась) строку не удваивает: вставка поглощает
 * конфликт по ключу координат.
 *
 * <p><b>Перед возвратом в повтор — та же пауза, что у отложенного
 * применения.</b> Путь отравленной записи идёт мимо политики пауз: каркас
 * возвращает её в партию и доставляет снова немедленно, и без паузы
 * недоступная база превращала бы восстановление в цикл без передышки,
 * бьющийся в неё и в журнал приложения.
 *
 * <p><b>Пауза — величина конфигурации, число попыток — нет.</b> Пауза
 * управляет тем, как часто повтор бьётся в базу; сдаться отложенное
 * применение не может по построению — сдача и есть пропуск.
 */
@Slf4j
@Component
public class StrategyFactErrorHandler extends DefaultErrorHandler {

    /** Разделитель звеньев цепочки причин в первопричине следа. */
    private static final String CAUSE_LINK = " <- ";

    public StrategyFactErrorHandler(BrokerProperties properties, ReceptionSkipDataService skips) {
        super(skipRecorder(skips, properties.getIntakeRetryInterval()),
                new FixedBackOff(properties.getIntakeRetryInterval().toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS));
        addNotRetryableExceptions(PoisonStrategyFactException.class);
        setRetryListeners((record, failure, deliveryAttempt) -> log.warn(
                "Strategy fact application is deferred topic={} partition={} offset={} attempt={} cause={}",
                record.topic(), record.partition(), record.offset(), deliveryAttempt,
                getRootCauseMessage(failure)));
    }

    /**
     * Восстановление отравленной записи — запись следа пропуска. Группа
     * берётся у потребителя, доставившего запись, а не из конфигурации:
     * след называет того, кто пропустил, а не того, кто должен был.
     */
    private static ConsumerAwareRecordRecoverer skipRecorder(ReceptionSkipDataService skips, Duration pause) {
        return (record, consumer, failure) -> recordSkip(skips, pause, record, consumer, failure);
    }

    private static void recordSkip(ReceptionSkipDataService skips, Duration pause, ConsumerRecord<?, ?> record,
                                   Consumer<?, ?> consumer, Exception failure) {
        try {
            skips.recordSkipIfAbsent(consumer.groupMetadata().groupId(), record.topic(), record.partition(),
                    record.offset(), header(record, StrategyDefinitionConsumer.HEADER_EVENT_ID),
                    header(record, StrategyDefinitionConsumer.HEADER_EVENT_TYPE), key(record), cause(failure));
        } catch (RuntimeException refusal) {
            log.error("Strategy fact skip trail is not written, the record is retried"
                    + " topic={} partition={} offset={}", record.topic(), record.partition(), record.offset(),
                    refusal);
            pauseBeforeRetry(pause);
            throw refusal;
        }
        log.error("Strategy fact is poison and is skipped topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset(), failure);
    }

    /**
     * Первопричина следа: класс и сообщение каждого звена цепочки причин,
     * от отказа слушателя вглубь. Обёртка каркаса слушателя предмета не
     * несёт и в первопричину не входит; звено отравленной записи называет,
     * ЧТО не так с записью, а глубинное — почему (отказ разбора содержимого).
     */
    private static String cause(Exception failure) {
        return getThrowableList(failure).stream()
                .filter(link -> isFalse(link instanceof ListenerExecutionFailedException))
                .map(ExceptionUtils::getMessage)
                .collect(Collectors.joining(CAUSE_LINK));
    }

    /**
     * Пустой заголовок означает «значения не было», а не пустую строку.
     * Заголовок без значения — тот же случай: отказ на нём ронял бы запись
     * следа каждым повтором.
     */
    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return isNull(header) || isNull(header.value()) ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /** Ключ записи — тенант; пусто, когда запись его не несёт. */
    private static String key(ConsumerRecord<?, ?> record) {
        return isNull(record.key()) ? null : String.valueOf(record.key());
    }

    /**
     * Пауза перед возвратом записи в повтор. Прерывание паузу обрывает и
     * восстанавливает флаг потока: остановку контейнера пауза не держит.
     */
    private static void pauseBeforeRetry(Duration pause) {
        try {
            Thread.sleep(pause);
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
        }
    }
}

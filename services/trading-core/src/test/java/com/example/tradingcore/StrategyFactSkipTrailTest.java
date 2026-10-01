package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingcore.config.BrokerProperties;
import com.example.tradingcore.exception.PoisonStrategyFactException;
import com.example.tradingcore.integration.internal.event.StrategyFactErrorHandler;
import com.example.tradingcore.persistence.repository.ReceptionSkipRepository;
import com.example.tradingcore.persistence.service.ReceptionSkipDataService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * След пропуска отравленной записи у группы определений ядра
 * (docs/rules/durable-consumer-reception.md §«След пропуска — таблица
 * `reception_skips`»).
 *
 * <p><b>Что здесь проверяется по существу.</b> Пропуск без следа — ветвь
 * «смещение продвинулось, следствия нет», у которой нет ни одного
 * наблюдателя; поэтому три вещи, каждая из которых, будучи нарушенной,
 * делает пропуск бесшумным:
 *
 * <ul>
 *   <li><b>отравленная запись оставляет строку</b> с группой, координатами,
 *       конвертом, ключом и первопричиной — а отложенное применение строки не
 *       оставляет вовсе;</li>
 *   <li><b>отказ записи следа НЕ поглощается</b>: запись возвращается в
 *       партию, и смещение не продвигается;</li>
 *   <li><b>значение с провода, которое база не примет, не роняет запись
 *       следа каждым повтором</b> — иначе отравленная запись остановила бы
 *       партию навсегда.</li>
 * </ul>
 *
 * <p>Поглощение конфликта по ключу координат держит база и мерит чёрный
 * ящик (клетка повторной доставки группы {@code B8}).
 */
class StrategyFactSkipTrailTest {

    private static final String GROUP = "trading-core.strategy-facts";
    private static final String TOPIC = "strategies.facts";
    private static final Integer PARTITION = 0;
    private static final Long OFFSET = 42L;
    private static final String EVENT = "ev-0001";
    private static final String EVENT_TYPE = "STRATEGY_ACTIVATED";
    private static final String TENANT = "tn-0001";

    private final ReceptionSkipDataService skips = mock(ReceptionSkipDataService.class);
    private final ReceptionSkipRepository repository = mock(ReceptionSkipRepository.class);
    @SuppressWarnings("unchecked")
    private final Consumer<String, String> consumer = mock(Consumer.class);
    private final MessageListenerContainer container = mock(MessageListenerContainer.class);

    private final StrategyFactErrorHandler handler = new StrategyFactErrorHandler(properties(), skips);

    // --- обработчик отказа --------------------------------------------------

    /** Отравленная запись пропускается со строкой следа, смещение не возвращается. */
    @Test
    void aPoisonRecordIsSkippedWithATrailRow() {
        when(consumer.groupMetadata()).thenReturn(new ConsumerGroupMetadata(GROUP));

        handler.handleRemaining(listenerFailure(new PoisonStrategyFactException("Strategy fact payload is empty",
                new IllegalArgumentException("nothing to read"))), records(), consumer, container);

        ArgumentCaptor<String> cause = ArgumentCaptor.forClass(String.class);
        verify(skips).recordSkipIfAbsent(eq(GROUP), eq(TOPIC), eq(PARTITION), eq(OFFSET), eq(EVENT),
                eq(EVENT_TYPE), eq(TENANT), cause.capture());
        // Первопричина — звено отравленной записи и глубинный отказ; обёртка
        // каркаса слушателя в неё не входит.
        assertThat(cause.getValue())
                .isEqualTo("PoisonStrategyFactException: Strategy fact payload is empty"
                        + " <- IllegalArgumentException: nothing to read");
        verify(consumer, never()).seek(any(TopicPartition.class), anyLong());
    }

    /** Отказ записи следа возвращает запись в партию: смещение не продвигается. */
    @Test
    void aRefusedTrailReturnsTheRecordToThePartition() {
        when(consumer.groupMetadata()).thenReturn(new ConsumerGroupMetadata(GROUP));
        when(skips.recordSkipIfAbsent(anyString(), anyString(), anyInt(), anyLong(), any(), any(), any(),
                anyString())).thenThrow(new IllegalStateException("database is down"));

        assertThatThrownBy(() -> handler.handleRemaining(
                listenerFailure(new PoisonStrategyFactException("Strategy fact payload is empty")), records(),
                consumer, container))
                .isInstanceOf(RuntimeException.class);

        verify(consumer).seek(new TopicPartition(TOPIC, PARTITION), OFFSET);
    }

    /** Отложенное применение следа не оставляет: запись не пропускается. */
    @Test
    void aDeferredApplicationLeavesNoTrail() {
        assertThatThrownBy(() -> handler.handleRemaining(
                listenerFailure(new IllegalStateException("ExchangeAccount not found")), records(),
                consumer, container))
                .isInstanceOf(RuntimeException.class);

        verify(skips, never()).recordSkipIfAbsent(any(), any(), any(), any(), any(), any(), any(), any());
        verify(consumer).seek(new TopicPartition(TOPIC, PARTITION), OFFSET);
    }

    // --- граница персистентности ---------------------------------------------

    /**
     * Значения с провода усекаются по ширине колонки и очищаются от нулевого
     * символа; пустое остаётся пустым, координаты не трогаются.
     */
    @Test
    void wireValuesAreFittedToTheColumnsAndCoordinatesAreKept() {
        when(repository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        Boolean written = new ReceptionSkipDataService(repository).recordSkipIfAbsent(GROUP, TOPIC, PARTITION,
                OFFSET, "e".repeat(70), null, "tn\u0000-1", "cause\u0000tail");

        assertThat(written).isTrue();
        verify(repository).insertIfAbsent(eq(GROUP), eq(TOPIC), eq(PARTITION), eq(OFFSET), eq("e".repeat(64)),
                isNull(), eq("tn-1"), eq("causetail"), any());
    }

    /** Ноль вставленных строк — след этой записи уже лежал. */
    @Test
    void anAbsorbedConflictReportsThatTheTrailWasThere() {
        when(repository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        Boolean written = new ReceptionSkipDataService(repository).recordSkipIfAbsent(GROUP, TOPIC, PARTITION,
                OFFSET, EVENT, EVENT_TYPE, TENANT, "cause");

        assertThat(written).isFalse();
    }

    private static BrokerProperties properties() {
        BrokerProperties properties = new BrokerProperties();
        // Пауза перед повтором — миллисекунда: предмет теста исход, а не такт.
        properties.setIntakeRetryInterval(Duration.ofMillis(1));
        return properties;
    }

    private static ListenerExecutionFailedException listenerFailure(Exception cause) {
        return new ListenerExecutionFailedException("Listener method threw exception", cause);
    }

    private static List<ConsumerRecord<?, ?>> records() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, PARTITION, OFFSET, TENANT, "{}");
        record.headers().add("eventId", EVENT.getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", EVENT_TYPE.getBytes(StandardCharsets.UTF_8));
        return List.of(record);
    }
}

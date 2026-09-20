package com.example.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.statistics.config.ReceptionProperties;
import com.example.statistics.domain.service.StatisticsReceptionService;
import com.example.statistics.exception.IncompleteEventException;
import com.example.statistics.integration.internal.event.ReceptionErrorHandler;
import com.example.statistics.integration.internal.event.ReceptionHaltMarker;
import java.time.Duration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * Остановка приёма: обработчик ошибок <b>не сдаётся</b>, а флаг остановки
 * ставится отдельно от транзакции обработки
 * (docs/rules/durable-consumer-reception.md §«Обработчик отказа — часть
 * конструкции, а не настройка»).
 *
 * <p><b>Что здесь на самом деле проверяется.</b> Умолчание контейнера —
 * конечное число попыток, затем восстановление логированием и продвижение
 * смещения; при нём ветвь «смещение продвинулось, строки нет» становится
 * штатным исходом. Клейм о её несуществовании держится <b>на этом бине</b>,
 * поэтому мерится он сам: отравленное сообщение прогоняется через
 * обработчик много раз подряд, и ни на одном из них он не объявляет запись
 * восстановленной — то есть смещение не продвигается никогда.
 *
 * <p><b>Предмет здесь один — ОБРАБОТЧИК; маркер остановки в него не
 * входит.</b> Его ветви живут группой `U11` документа
 * `.claude/tests/cases/durable-reception.md` и прогоняются контрактом
 * копий ({@code ReceptionHaltMarkerContract}) в обоих деревьях; маркер
 * остаётся здесь настоящим, потому что «флаг записан ровно один раз на
 * двенадцати доставках» есть утверждение о ПАРЕ, а не о нём одном.
 */
class ReceptionHaltTest {

    private static final String TOPIC = "trading-core.facts";

    /** Заведомо больше любого конечного умолчания библиотеки. */
    private static final int DELIVERIES = 12;

    private final StatisticsReceptionService receptionService = mock(StatisticsReceptionService.class);
    private final ReceptionHaltMarker haltMarker = new ReceptionHaltMarker(receptionService);

    @Test
    @DisplayName("Отравленное сообщение не объявляется восстановленным ни на какой попытке")
    void theJournalGroupNeverGivesUpOnAPoisonedMessage() {
        ReceptionErrorHandler handler = new ReceptionErrorHandler(properties(), haltMarker);
        Consumer<?, ?> consumer = mock(Consumer.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);

        for (int delivery = 0; delivery < DELIVERIES; delivery++) {
            assertThat(handler.handleOne(new IncompleteEventException("неполный вход"), record(), consumer, container))
                    .as("объявленная восстановленной запись продвинула бы смещение и потеряла бы строку молча")
                    .isFalse();
        }

        verify(receptionService, times(1))
                .noteHalt(TOPIC);
    }

    private ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>(TOPIC, 0, 12L, "tenant-1", "{}");
    }

    /**
     * Пауза здесь короткая намеренно: предмет пробы — число попыток, а
     * настоящая пауза окружения растянула бы прогон на минуты, ничего не
     * измерив сверх.
     */
    private ReceptionProperties properties() {
        ReceptionProperties properties = new ReceptionProperties();
        properties.setRetryInterval(Duration.ofMillis(1));
        return properties;
    }
}

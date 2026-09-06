package com.example.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import com.example.bff.config.PerimeterProperties;
import com.example.bff.domain.SubscriptionTicketService;
import com.example.bff.domain.jobs.StreamPulseJob;
import com.example.bff.domain.stream.StreamRegistry;
import com.example.bff.util.Constants;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Пульс отражает живость ПОТРЕБИТЕЛЯ, а не планировщика
 * (docs/architecture/contracts.md §«Поток несёт пульс, и молчание без
 * пульса — наблюдаемый отказ»).
 *
 * <p><b>Почему это охраняется прогоном.</b> Пульс, бьющийся по
 * расписанию независимо от подписки на темы, доказывал бы ровно то, что
 * в периметре тикает таймер: потребитель мёртв, событий нет, а картина
 * выглядит живой — то самое состояние, против которого пульс и заведён.
 * Читая код, эту разницу не увидеть: в обеих редакциях тик выглядит
 * одинаково, и различает их только состояние слушателя.
 *
 * <p>Проверяется то, что реально уходит на провод, — текст SSE-ответа.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = PerimeterSurfaceContext.class)
class StreamPulseTest {

    private static final String STREAM_PATH = "/api/v1/bff/stream";
    private static final String SUBJECT = "user-42";
    private static final String PULSE_EVENT = "event:" + Constants.StreamRecords.PULSE;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SubscriptionTicketService ticketService;

    @Autowired
    private StreamRegistry streamRegistry;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("Потребитель жив — пульс доходит до подписки")
    void aLiveConsumerLetsThePulseThrough() throws Exception {
        MvcResult result = openStream();

        pulseOver(containerThat(true, Set.of(new TopicPartition("trading-core.facts", 0))), true).beat();

        assertThat(result.getResponse().getContentAsString()).contains(PULSE_EVENT);
    }

    /**
     * Слушатель остановлен — молчание честно: показывать «жив», когда
     * читать события некому, значит обманывать смотрящего.
     */
    @Test
    @DisplayName("Слушатель остановлен — пульса нет")
    void aStoppedListenerSilencesThePulse() throws Exception {
        MvcResult result = openStream();

        pulseOver(containerThat(false, Set.of(new TopicPartition("trading-core.facts", 0))), true).beat();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(PULSE_EVENT);
    }

    /**
     * Назначенных партиций нет — связь с брокером потеряна либо группа
     * развалилась; событий не будет, и пульс молчит.
     */
    @Test
    @DisplayName("Слушатель без назначенных партиций — пульса нет")
    void aListenerWithoutAssignmentsSilencesThePulse() throws Exception {
        MvcResult result = openStream();

        pulseOver(containerThat(true, Set.of()), true).beat();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(PULSE_EVENT);
    }

    /** Слушателей нет вовсе — потреблять некому, и пульс молчит. */
    @Test
    @DisplayName("Слушателей нет вовсе — пульса нет")
    void anAbsentListenerSilencesThePulse() throws Exception {
        MvcResult result = openStream();
        KafkaListenerEndpointRegistry empty = mock(KafkaListenerEndpointRegistry.class);
        when(empty.getListenerContainers()).thenReturn(List.of());

        new StreamPulseJob(streamRegistry, empty, propertiesWithPulse(true)).beat();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(PULSE_EVENT);
    }

    /** Выключатель тика — как у всякой джобы. */
    @Test
    @DisplayName("Выключенный тик ничего не делает")
    void aDisabledPulseDoesNothing() throws Exception {
        MvcResult result = openStream();

        pulseOver(containerThat(true, Set.of(new TopicPartition("trading-core.facts", 0))), false).beat();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(PULSE_EVENT);
    }

    private MvcResult openStream() throws Exception {
        return mockMvc.perform(get(STREAM_PATH)
                        .param("ticket", ticketService.issue(SUBJECT, PerimeterSurfaceContext.TENANT)))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private StreamPulseJob pulseOver(MessageListenerContainer container, Boolean pulseEnabled) {
        KafkaListenerEndpointRegistry listeners = mock(KafkaListenerEndpointRegistry.class);
        when(listeners.getListenerContainers()).thenReturn(List.of(container));
        return new StreamPulseJob(streamRegistry, listeners, propertiesWithPulse(pulseEnabled));
    }

    private MessageListenerContainer containerThat(Boolean running, Set<TopicPartition> assignments) {
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.isRunning()).thenReturn(running);
        when(container.getAssignedPartitions()).thenReturn(assignments);
        return container;
    }

    /**
     * Свойства тика отдельные от контекстных: предмет проверки —
     * поведение самого тика, и трогать общий бин ради одной пробы
     * значило бы менять условия соседним.
     */
    private PerimeterProperties propertiesWithPulse(Boolean pulseEnabled) {
        PerimeterProperties properties = new PerimeterProperties();
        properties.getStream().setPulseInterval(Duration.ofSeconds(15));
        properties.getStream().setPulseEnabled(pulseEnabled);
        return properties;
    }
}

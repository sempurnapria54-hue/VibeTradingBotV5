package com.example.bff;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import com.example.bff.api.model.StreamRecordApiModel;
import com.example.bff.domain.SubscriptionTicketService;
import com.example.bff.domain.stream.StreamRegistry;
import com.example.bff.util.Constants;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Тропа потока целиком: билет открывает подписку, окно переигрывания
 * продолжает её с названной позиции, а ненайденная позиция даёт ЯВНЫЙ
 * разрыв (docs/architecture/contracts.md §«Живые данные в браузер»).
 *
 * <p><b>Проверяется то, что реально уходит на провод</b> — текст
 * SSE-ответа, а не внутренние структуры реестра: клиент видит именно
 * его, и клейм о позиции чтения держится на нём.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = PerimeterSurfaceContext.class)
class StreamSubscriptionTest {

    private static final String STREAM_PATH = "/api/v1/bff/stream";
    private static final String SUBJECT = "user-42";
    private static final String LAST_EVENT_ID_HEADER = "Last-Event-ID";

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
    @DisplayName("Годный билет открывает подписку, и факты тенанта доходят до провода")
    void aValidTicketOpensAStreamThatCarriesTheTenantFacts() throws Exception {
        MvcResult result = openStream(null);

        streamRegistry.publish(PerimeterSurfaceContext.TENANT, fact("e-1"));

        assertThat(bodyOf(result))
                .contains("id:e-1")
                .contains("event:DEAL_OPENED");
    }

    /** Чужой тенант в поток не идёт: фильтр стои́т на ключе записи. */
    @Test
    @DisplayName("Факт чужого тенанта на провод не выходит")
    void aForeignTenantFactNeverReachesTheWire() throws Exception {
        MvcResult result = openStream(null);

        streamRegistry.publish("tenant-other", fact("e-1"));

        assertThat(bodyOf(result)).doesNotContain("e-1");
    }

    @Test
    @DisplayName("Названная позиция нашлась в окне — поток продолжается с неё")
    void aKnownPositionContinuesTheStream() throws Exception {
        streamRegistry.publish(PerimeterSurfaceContext.TENANT, fact("e-1"));
        streamRegistry.publish(PerimeterSurfaceContext.TENANT, fact("e-2"));

        MvcResult result = openStream("e-1");

        assertThat(bodyOf(result))
                .as("хвост после названной позиции переигрывается")
                .contains("id:e-2")
                .doesNotContain(Constants.StreamRecords.GAP);
    }

    /**
     * Ненайденная позиция даёт явный разрыв. Молчаливого продолжения не
     * бывает: иначе фронт показал бы связную картину поверх дыры.
     */
    @Test
    @DisplayName("Названная позиция не нашлась — явный разрыв")
    void anUnknownPositionYieldsAnExplicitGap() throws Exception {
        streamRegistry.publish(PerimeterSurfaceContext.TENANT, fact("e-1"));

        MvcResult result = openStream("invented");

        assertThat(bodyOf(result)).contains("event:" + Constants.StreamRecords.GAP);
    }

    /** Первое подключение разрыва не получает: терять ему нечего. */
    @Test
    @DisplayName("Первое подключение разрыва не получает")
    void aFirstConnectionGetsNoGap() throws Exception {
        MvcResult result = openStream(null);

        assertThat(bodyOf(result)).doesNotContain(Constants.StreamRecords.GAP);
    }

    /**
     * Пульс доходит до подписки и идентичности не несёт: он не факт, и
     * просить продолжения с него нельзя.
     */
    @Test
    @DisplayName("Пульс доходит до подписки без идентичности")
    void aPulseReachesTheStreamWithoutAnIdentity() throws Exception {
        MvcResult result = openStream(null);

        streamRegistry.broadcast(new StreamRecordApiModel(null, Constants.StreamRecords.PULSE,
                OffsetDateTime.now(ZoneOffset.UTC), null));

        assertThat(bodyOf(result))
                .contains("event:" + Constants.StreamRecords.PULSE)
                .doesNotContain("id:");
    }

    private MvcResult openStream(String lastEventId) throws Exception {
        MockHttpServletRequestBuilder call = get(STREAM_PATH)
                .param("ticket", ticketService.issue(SUBJECT, PerimeterSurfaceContext.TENANT));
        if (nonNull(lastEventId)) {
            call = call.header(LAST_EVENT_ID_HEADER, lastEventId);
        }
        return mockMvc.perform(call).andExpect(request().asyncStarted()).andReturn();
    }

    private String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private StreamRecordApiModel fact(String eventId) {
        return new StreamRecordApiModel(eventId, "DEAL_OPENED", OffsetDateTime.now(ZoneOffset.UTC), null);
    }
}

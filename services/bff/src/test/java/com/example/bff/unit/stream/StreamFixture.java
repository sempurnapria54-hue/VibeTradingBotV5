package com.example.bff.unit.stream;

import com.example.bff.api.model.StreamRecordApiModel;
import com.example.bff.config.PerimeterProperties;
import com.example.bff.domain.stream.StreamRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.RecordingEmitterChannel;

/**
 * Базовая сборка групп `U5`-`U9` документа
 * `.claude/tests/cases/bff-perimeter-logic.md`: реестр подписок, его оси
 * окружения и записи, которые в него кладут.
 *
 * <p><b>Сборка собирается тестом как обычный объект:</b> ни контейнера,
 * ни контекста каркаса у предмета нет — аннотации на классе суть деталь
 * потребителя.
 */
final class StreamFixture {

    /** Класс факта: любой, лишь бы не запись периметра. */
    static final String FACT_TYPE = "DEAL_OPENED";

    private StreamFixture() {
    }

    /**
     * Оси окружения реестра.
     *
     * @param replayWindow предел окна переигрывания
     * @param ceiling      потолок одновременных подписок тенанта
     * @return конфигурация периметра
     */
    static PerimeterProperties properties(Integer replayWindow, Integer ceiling) {
        PerimeterProperties properties = new PerimeterProperties();
        properties.getStream().setReplayWindow(replayWindow);
        properties.getStream().setMaxSubscriptionsPerTenant(ceiling);
        properties.getStream().setConnectionTimeout(Duration.ofMinutes(1));
        return properties;
    }

    /** Реестр базовой сборки: окно на три записи, потолок на две подписки. */
    static StreamRegistry registry() {
        return new StreamRegistry(properties(3, 2));
    }

    /**
     * Открыть подписку и подключить к ней наблюдателя.
     *
     * @param registry    реестр
     * @param tenantId    тенант сессии
     * @param lastEventId названная позиция; пусто — первое подключение
     * @return наблюдатель, собирающий отданное подписке
     */
    static RecordingEmitterChannel subscribe(StreamRegistry registry, String tenantId, String lastEventId) {
        return RecordingEmitterChannel.attachedTo(registry.open(tenantId, lastEventId));
    }

    /** Факт с непустой идентичностью — то, что кладётся в окно. */
    static StreamRecordApiModel fact(String id) {
        return new StreamRecordApiModel(id, FACT_TYPE, OffsetDateTime.now(ZoneOffset.UTC), "content-" + id);
    }

    /** Запись периметра: идентичности не несёт. */
    static StreamRecordApiModel perimeterRecord(String type) {
        return new StreamRecordApiModel(null, type, OffsetDateTime.now(ZoneOffset.UTC), null);
    }

    /** Записи, ушедшие в подписку, в порядке отдачи. */
    static List<SseFrame> framesOf(RecordingEmitterChannel channel) {
        return SseFrame.decode(channel.written());
    }
}

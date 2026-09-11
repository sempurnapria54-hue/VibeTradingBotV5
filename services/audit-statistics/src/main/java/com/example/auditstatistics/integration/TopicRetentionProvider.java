package com.example.auditstatistics.integration;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.stereotype.Component;

/**
 * Добывает у брокера срок хранения темы — операнд порога алерта на лаг
 * пары (docs/architecture/data-ownership.md §«Outbox и доставка»).
 *
 * <p><b>Почему у брокера, а не из своей конфигурации.</b> Срок лежит в
 * манифесте темы <b>чужого</b> производителя и калибруется им; копия у
 * потребителя разошлась бы при первой же калибровке, и разошлась бы в
 * разрешающую сторону — срок, уменьшенный владельцем, оставил бы порог
 * выше себя, а алерт молчал бы до самой потери. Носитель числа остаётся
 * один: манифест применяет владелец темы, брокер отдаёт применённое,
 * потребитель читает у брокера.
 *
 * <p><b>Читается ПРИМЕНЁННОЕ значение, а не только явно заданное в
 * манифесте.</b> Дом называет тропу «брокер отдаёт применённое», и
 * унаследованное от умолчания брокера есть ровно тот срок, по которому
 * брокер тему и чистит: отвергнуть его значило бы гасить алерт на теме,
 * которая на самом деле имеет конечный срок.
 *
 * <p><b>Не добыл — отдаёт пустое, а не ноль и не умолчание</b>
 * (docs/components/ReceptionStateJob.md §«Не добыл — отдаёт пустое, а не
 * прежнее»). Пустой срок не читается как «алерта нет»: порог не
 * выводится, и алерт на паре <b>срабатывает</b>, потому что измеритель не
 * мерит (docs/concept.md, П1 — умолчание не бывает благоприятным).
 * Исходов пустоты три, и все три означают одно — применимого срока у темы
 * нет: вызов отказал или не уложился в срок; брокер значения не отдал;
 * отданное не выражает конечного срока (пустая строка, {@code -1} —
 * хранение без предела, нечисловое значение).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TopicRetentionProvider {

    /**
     * Срок ожидания ответа брокера.
     *
     * <p>Локальная техническая константа: величина осмысленна только
     * внутри этого вызова. Она заведомо меньше такта тика — иначе
     * затянувшийся ответ съедал бы такт целиком, — а её истечение есть
     * та же ветвь «срок не добыт», что и отказ.
     */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);

    private final Admin admin;

    /**
     * Применённый срок хранения темы в миллисекундах; пусто — конечного
     * срока у темы нет либо он не добыт.
     */
    public Optional<Long> retentionMs(String topic) {
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
        try {
            Config config = admin.describeConfigs(List.of(resource))
                    .values()
                    .get(resource)
                    .get(CALL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return finiteRetention(topic, config.get(TopicConfig.RETENTION_MS_CONFIG));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Ожидание срока хранения темы прервано: порог алерта не выводится topic={}", topic, e);
            return Optional.empty();
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            log.warn("Срок хранения темы у брокера не добыт: порог алерта не выводится topic={}", topic, e);
            return Optional.empty();
        }
    }

    /**
     * Отданное брокером значение — конечным сроком, если оно им является.
     *
     * <p>{@code -1} у Kafka означает хранение без предела: доля от него
     * порогом не бывает, и ветвь та же, что у неотданного значения.
     */
    private Optional<Long> finiteRetention(String topic, ConfigEntry entry) {
        if (isNull(entry) || isBlank(entry.value())) {
            log.warn("Брокер не отдал срока хранения темы: порог алерта не выводится topic={}", topic);
            return Optional.empty();
        }
        Long retentionMs = Long.valueOf(entry.value());
        if (retentionMs <= 0) {
            log.info("Тема хранится без конечного срока: порог алерта не выводится topic={} retentionMs={}",
                    topic, retentionMs);
            return Optional.empty();
        }
        return Optional.of(retentionMs);
    }
}

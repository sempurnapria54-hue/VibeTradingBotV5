package com.example.bff.box;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Провод прогона к брокеру субстрата: вход ящика и наблюдатель его
 * молчания.
 *
 * <p><b>У ЭТОГО предмета брокер нужен ВХОДОМ, а не выходом, и в этом
 * отличие от соседних ящиков.</b> Периметр только потребляет: запись
 * темы есть вход раздачи в браузер, а собственных публикаций у него нет
 * ни одной (docs/architecture/services/bff.md §«Что потребляет и что
 * публикует»). Поэтому здесь две стороны и обе несущие — писатель
 * события и счётчик записей, которым предъявляется ОТСУТСТВИЕ
 * публикаций периметра.
 *
 * <p><b>Конверт кладётся ЗАГОЛОВКАМИ, тенант — КЛЮЧОМ.</b> Так его
 * кладёт реле производителя (docs/architecture/data-ownership.md
 * §«Outbox и доставка»), и второй формы конверта прогон не заводит:
 * иначе кейс проверял бы разбор того, чего на проводе не бывает.
 *
 * <p><b>Клиенты живут на прогон, а не на клетку.</b> Соединение с
 * брокером стои́т сотни миллисекунд, и платить их каждой клеткой незачем;
 * состояния между клетками они не несут — тему и смещения держит сам
 * брокер.
 */
final class Wire {

    /** Тема фактов торгового ядра: одна из двух, на которые подписан периметр. */
    static final String CORE_TOPIC = "trading-core.facts";

    /** Тема фактов владельца определений: вторая из двух. */
    static final String STRATEGIES_TOPIC = "strategies.facts";

    /** Заголовок конверта: идентичность события. */
    static final String EVENT_ID = "eventId";

    /** Заголовок конверта: класс события. */
    static final String EVENT_TYPE = "eventType";

    /** Заголовок конверта: момент происшествия. */
    static final String OCCURRED_AT = "occurredAt";

    /** Потолок ожидания ответа брокера: «не упало» проверкой не является. */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);

    private static final Wire INSTANCE = new Wire();

    private final Admin admin;
    private final Producer<String, String> producer;

    private Wire() {
        Map<String, Object> adminProperties = new HashMap<>();
        adminProperties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BffSubstrate.brokerAddress());
        this.admin = Admin.create(adminProperties);
        Map<String, Object> producerProperties = new HashMap<>();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BffSubstrate.brokerAddress());
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");
        this.producer = new KafkaProducer<>(producerProperties);
    }

    static Wire wire() {
        return INSTANCE;
    }

    /**
     * Кладёт в тему запись полного конверта и дожидается подтверждения
     * брокером.
     *
     * <p>Дожидается намеренно: кейс, положивший запись «в никуда», ждал
     * бы её появления в проводе по таймауту и краснел бы по чужой
     * причине.
     *
     * @param topic      тема
     * @param tenantId   тенант — ключ партиции; пусто означает отсутствие ключа
     * @param eventId    идентичность события
     * @param eventType  класс события
     * @param occurredAt момент происшествия; пусто — заголовка нет
     * @param payload    содержимое дословно
     */
    void publish(String topic, String tenantId, String eventId, String eventType,
                 String occurredAt, String payload) {
        Map<String, String> headers = new HashMap<>();
        headers.put(EVENT_ID, eventId);
        headers.put(EVENT_TYPE, eventType);
        headers.put(OCCURRED_AT, occurredAt);
        publish(topic, tenantId, headers, payload);
    }

    /**
     * Кладёт в тему запись с НАЗВАННЫМ набором заголовков.
     *
     * <p>Набор подаётся целиком, потому что предмет части клеток — именно
     * неполный конверт: заголовка нет вовсе, а не пуст.
     *
     * @param topic    тема
     * @param tenantId ключ записи; пусто — ключа нет
     * @param headers  заголовки конверта; пустое значение означает, что
     *                 заголовок не кладётся вовсе
     * @param payload  содержимое дословно
     */
    void publish(String topic, String tenantId, Map<String, String> headers, String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, tenantId, payload);
        headers.forEach((name, value) -> {
            if (Objects.nonNull(value)) {
                record.headers().add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8)));
            }
        });
        try {
            producer.send(record).get(CALL_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание подтверждения брокера прервано", failure);
        } catch (Exception failure) {
            throw new IllegalStateException("Запись не легла в тему " + topic, failure);
        }
    }

    /** Заводит тему названного имени; уже существующая остаётся как есть. */
    void createTopic(String topic) {
        try {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                    .all().get(CALL_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание заведения темы прервано", failure);
        } catch (ExecutionException alreadyThere) {
            // Тема, заведённая прежней клеткой, предмета не меняет: имя её
            // то же самое, и заводится она ради присутствия, а не ради
            // момента заведения.
        } catch (Exception failure) {
            throw new IllegalStateException("Тема " + topic + " не заведена", failure);
        }
    }

    /** Имена тем брокера, кроме служебных тем самого брокера. */
    Set<String> topics() {
        try {
            return admin.listTopics().names().get(CALL_TIMEOUT.toSeconds(), TimeUnit.SECONDS).stream()
                    .filter(name -> BooleanUtils.isFalse(name.startsWith("__")))
                    .collect(Collectors.toSet());
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание перечня тем прервано", failure);
        } catch (Exception failure) {
            throw new IllegalStateException("Перечень тем брокера не прочитан", failure);
        }
    }

    /**
     * Сколько записей лежит в теме — по верхним смещениям её партиций.
     *
     * <p>Темы, которой нет, ноль записей: отсутствующая тема и пустая для
     * этого утверждения неразличимы, и обе означают «сюда не писали».
     *
     * @param topic имя темы
     */
    Long recordsIn(String topic) {
        if (BooleanUtils.isFalse(topics().contains(topic))) {
            return 0L;
        }
        try {
            Map<TopicPartition, OffsetSpec> request = new HashMap<>();
            admin.describeTopics(List.of(topic)).allTopicNames()
                    .get(CALL_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    .get(topic).partitions()
                    .forEach(partition -> request.put(
                            new TopicPartition(topic, partition.partition()), OffsetSpec.latest()));
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> offsets =
                    admin.listOffsets(request).all().get(CALL_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            return offsets.values().stream().mapToLong(ListOffsetsResult.ListOffsetsResultInfo::offset).sum();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание смещений темы прервано", failure);
        } catch (Exception failure) {
            throw new IllegalStateException("Смещения темы " + topic + " не прочитаны", failure);
        }
    }

    /**
     * Сколько записей лежит во ВСЕХ темах брокера.
     *
     * <p>Им предъявляется отрицание «периметр не публикует ничего»:
     * счёт по названным темам был бы у́же утверждения — публикация в тему,
     * которой кейс не назвал, осталась бы невидимой.
     */
    Long totalRecords() {
        return topics().stream().mapToLong(this::recordsIn).sum();
    }

    /** Имена групп потребителей, известных брокеру. */
    List<String> consumerGroups() {
        try {
            List<String> names = new ArrayList<>();
            for (GroupListing group : admin.listGroups().all()
                    .get(CALL_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                names.add(group.groupId());
            }
            return names;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание перечня групп прервано", failure);
        } catch (Exception failure) {
            throw new IllegalStateException("Перечень групп брокера не прочитан", failure);
        }
    }
}

package com.example.strategies.box;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Провод субстрата — и у ЭТОГО предмета он односторонний.
 *
 * <p><b>Сторона производителя здесь есть РОВНО ОДНА, и она отрицательная.</b>
 * Владелец определений ничего не потребляет: вход его решения — запрос
 * пользователя через периметр, а не сообщение
 * (docs/architecture/services/strategies.md §«Что потребляет и что
 * публикует»). Поэтому основной клиент здесь ЧИТАЮЩИЙ — он наблюдает
 * выход реле, — а пишущий существует ради единственной клетки
 * {@code B10.1}: она кладёт записи в темы и утверждает, что сервис на
 * них не реагирует ничем. Положенное ею — не вход ящика, а предъявление
 * того, что входа не существует.
 *
 * <p><b>Клиент свой, а не бин сервиса.</b> {@code KafkaTemplate} контекста
 * есть внутренность ящика — им сервис и публикует; читать им же значило
 * бы наблюдать предмет изнутри.
 *
 * <p><b>Группы у читателя нет.</b> Партии назначаются ({@code assign}), а
 * не подписываются: подписка завела бы группу, а группа есть состояние на
 * брокере, которое переживает и клетку, и контекст. Смещений читатель не
 * фиксирует — окно задаёт отметка клетки.
 *
 * <p><b>Читается ОКНО от отметки, а не тема целиком.</b> Тема клетку
 * переживает — опустошение базы её не касается, — и утверждение «в теме
 * ровно одна запись» без отметки говорило бы о прогоне, а не о клетке.
 */
final class Wire {

    /** Потолок вычитывания окна: оно уже опубликовано, ждать нечего. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /** Такт вычитывания окна. */
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private static final Consumer<String, String> READER = newReader();

    /** Пишущий клиент отрицательной клетки — см. шапку класса. */
    private static final Producer<String, String> WRITER = newWriter();

    private Wire() {
    }

    /**
     * Отметка конца темы производителя: с неё клетка читает
     * опубликованное.
     *
     * <p><b>Отсутствие темы — законный исход отметки, а не отказ.</b>
     * Тему заводит ПУБЛИКАЦИЯ, а отметка снимается до неё: у ящика,
     * который ещё ничего не публиковал, темы не существует, и окно клетки
     * начинается с нуля каждой партии, которая появится. Наблюдатель темы
     * не заводит намеренно — заведённая им, она означала бы, что
     * публикация состоялась.
     */
    static Mark mark() {
        Set<TopicPartition> partitions = knownPartitions();
        if (partitions.isEmpty()) {
            return new Mark(Map.of());
        }
        READER.assign(partitions);
        return new Mark(new LinkedHashMap<>(READER.endOffsets(partitions)));
    }

    /**
     * Опубликованное после отметки — в порядке партий и смещений.
     *
     * <p><b>Порядок читается ВНУТРИ партии.</b> Ключ партиции — тенант, и
     * порядок событий одного тенанта наблюдаем именно так; межпартийного
     * порядка у брокера нет вовсе, и клетка о нём не утверждает.
     *
     * @param mark отметка, снятая до наблюдаемого хода
     */
    static List<Published> publishedSince(Mark mark) {
        Set<TopicPartition> partitions = factsPartitions();
        READER.assign(partitions);
        Map<TopicPartition, Long> end = READER.endOffsets(partitions);
        partitions.forEach(partition -> READER.seek(partition, mark.offsetOf(partition)));
        Map<TopicPartition, List<Published>> collected = new LinkedHashMap<>();
        partitions.forEach(partition -> collected.put(partition, new ArrayList<>()));
        long deadline = System.currentTimeMillis() + READ_TIMEOUT.toMillis();
        while (isBehind(partitions, end) && System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> polled = READER.poll(POLL_INTERVAL);
            for (ConsumerRecord<String, String> record : polled) {
                collected.get(new TopicPartition(record.topic(), record.partition()))
                        .add(published(record));
            }
        }
        List<Published> ordered = new ArrayList<>();
        collected.values().forEach(ordered::addAll);
        return ordered;
    }

    /**
     * Опубликованное после отметки — либо пусто, когда темы ещё нет.
     *
     * <p><b>Ею читают клетки, чьё ожидание есть ОТСУТСТВИЕ записи.</b>
     * Отсутствие темы — сильнейшая форма «не публиковалось ничего»: тему
     * заводит публикация, и у ящика, который не публиковал, её нет вовсе.
     * Клетка, ожидающая ЗАПИСЬ, берёт {@link #publishedSince}: там
     * отсутствие темы есть отказ ожидания, а не его исполнение.
     *
     * @param mark отметка, снятая до наблюдаемого хода
     */
    static List<Published> publishedSinceOrNone(Mark mark) {
        return knownPartitions().isEmpty() ? List.of() : publishedSince(mark);
    }

    private static Boolean isBehind(Set<TopicPartition> partitions, Map<TopicPartition, Long> end) {
        return partitions.stream().anyMatch(partition -> READER.position(partition) < end.get(partition));
    }

    private static Published published(ConsumerRecord<String, String> record) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Header header : record.headers()) {
            headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
        }
        return new Published(record.key(), headers, record.value());
    }

    /**
     * Партии темы фактов; спрос повторяется до потолка, и это не
     * перестраховка.
     *
     * <p>Тему заводит публикация, а лидер её партии выбирается не в тот
     * же миг: отказ по первому пустому ответу объявлял бы «сервис не
     * публиковал ничего» там, где тема просто моложе спроса.
     */
    private static Set<TopicPartition> factsPartitions() {
        long deadline = System.currentTimeMillis() + READ_TIMEOUT.toMillis();
        Set<TopicPartition> partitions = knownPartitions();
        while (partitions.isEmpty() && System.currentTimeMillis() < deadline) {
            partitions = knownPartitions();
        }
        if (partitions.isEmpty()) {
            throw new IllegalStateException("Раскладки темы фактов брокер не отдал: читать нечего");
        }
        return partitions;
    }

    /**
     * Раскладка темы, какой её знает брокер сейчас; пусто — темы ещё нет.
     *
     * <p>Спрос у несуществующей темы отвечает истечением потолка, и это
     * ОТВЕТ, а не сбой: он и означает «публикации не было».
     */
    private static Set<TopicPartition> knownPartitions() {
        Set<TopicPartition> partitions = new LinkedHashSet<>();
        try {
            List<PartitionInfo> known =
                    READER.partitionsFor(StrategiesSubstrate.FACTS_TOPIC, POLL_INTERVAL);
            known.forEach(info -> partitions.add(new TopicPartition(info.topic(), info.partition())));
        } catch (TimeoutException absent) {
            return Set.of();
        }
        return partitions;
    }

    /**
     * Кладёт запись в названную тему — вход ОТРИЦАТЕЛЬНОЙ клетки.
     *
     * <p><b>Тема заводится здесь явно, а не автосозданием.</b>
     * Наблюдатель темы не заводит по построению, и положить запись в
     * несуществующую тему нечем; клетка, кладущая её, заводит тему СВОИМ
     * ходом — и тем отделяет «тема есть, потому что положили мы» от
     * «тема есть, потому что сервис опубликовал».
     *
     * @param topic   тема, в которую кладётся запись
     * @param key     ключ партиции
     * @param payload содержимое записи
     */
    static void put(String topic, String key, String payload) {
        ensureTopic(topic);
        try {
            WRITER.send(new ProducerRecord<>(topic, key, payload)).get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запись в тему прервана", interrupted);
        } catch (ExecutionException failure) {
            throw new IllegalStateException("Брокер не принял запись в тему " + topic, failure);
        }
    }

    /**
     * Имена групп потребителей, известных брокеру.
     *
     * <p>Ими наблюдается ОТСУТСТВИЕ потребителя: группа есть состояние
     * на брокере, и сервис, подписавшийся хоть на одну тему, её заводит.
     */
    static List<String> consumerGroups() {
        try (Admin admin = newAdmin()) {
            return admin.listGroups().all().get().stream()
                    .map(GroupListing::groupId)
                    .toList();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Спрос групп потребителя прерван", interrupted);
        } catch (ExecutionException failure) {
            throw new IllegalStateException("Брокер не отдал перечня групп потребителя", failure);
        }
    }

    private static void ensureTopic(String topic) {
        try (Admin admin = newAdmin()) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Заведение темы прервано", interrupted);
        } catch (ExecutionException existing) {
            // Тема уже есть — законный исход: её мог завести и сам сервис.
        }
    }

    private static Admin newAdmin() {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, StrategiesSubstrate.brokerAddress());
        return Admin.create(settings);
    }

    private static Producer<String, String> newWriter() {
        Map<String, Object> settings = new HashMap<>();
        settings.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, StrategiesSubstrate.brokerAddress());
        settings.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        settings.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaProducer<>(settings);
    }

    private static Consumer<String, String> newReader() {
        Map<String, Object> settings = new HashMap<>();
        settings.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, StrategiesSubstrate.brokerAddress());
        settings.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        settings.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        settings.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.FALSE);
        // Тему заводит публикация, а не наблюдатель: заведённая спросом,
        // она означала бы, что публикация состоялась.
        settings.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, Boolean.FALSE);
        return new KafkaConsumer<>(settings);
    }

    /**
     * Отметка окна темы: смещение конца каждой партии на момент снятия.
     *
     * @param offsets смещения конца по партиям
     */
    record Mark(Map<TopicPartition, Long> offsets) {

        /** Смещение партии; ноль — партии на момент отметки не было. */
        Long offsetOf(TopicPartition partition) {
            return offsets.getOrDefault(partition, 0L);
        }
    }

    /**
     * Опубликованная запись: ключ, конверт заголовками, содержимое телом.
     *
     * @param key     ключ партиции — тенант
     * @param headers заголовки конверта по именам
     * @param payload содержимое дословно
     */
    record Published(String key, Map<String, String> headers, String payload) {

        /** Класс события из конверта. */
        String eventType() {
            return headers.get("eventType");
        }

        /** Идентичность события из конверта. */
        String eventId() {
            return headers.get("eventId");
        }
    }
}

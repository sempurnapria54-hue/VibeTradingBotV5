package com.example.tradingcore.box;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Провод субстрата: сторона ПРОИЗВОДИТЕЛЯ чужих фактов.
 *
 * <p><b>Копия определения ставится сообщением, а не вставкой дерева в
 * базу</b> (.claude/tests/cases/trading-core.md §«Где живёт код кейсов»).
 * Строки копии сервис производит сам — слушателем темы владельца
 * определений, — и вставка опиралась бы на строку, которой он не
 * производил: зелёный прогон говорил бы о нашей вставке, а не о его
 * поведении.
 *
 * <p><b>Конверт кладётся ЗАГОЛОВКАМИ, содержимое — телом.</b> Так его
 * кладёт реле производителя (docs/architecture/contracts.md §«Конверт
 * события»), и второго носителя формы конверта здесь не заводится: кейс,
 * собравший конверт иначе, мерил бы собственную выдумку.
 *
 * <p><b>Клиент свой, а не бин сервиса.</b> {@code KafkaTemplate} контекста
 * есть ВНУТРЕННОСТЬ ящика — им ядро публикует собственные факты; подать
 * им чужое сообщение значило бы наблюдать предмет изнутри.
 *
 * <p><b>Подтверждение от всех синхронных реплик и синхронная отправка.</b>
 * Кейс ставит предусловие, а не измеряет задержку: отправка, чьё
 * подтверждение не дождались, сделала бы следующий ассерт гонкой.
 *
 * <p><b>Сторона ПОТРЕБИТЕЛЯ здесь тоже своя, и группы у неё нет.</b>
 * Опубликованное ядром читается НАЗНАЧЕННЫМИ партиями ({@code assign}), а
 * не подпиской: подписка завела бы группу, а группа есть состояние на
 * брокере, которое переживает клетку и контекст.
 *
 * <p><b>Читается ОКНО от отметки, а не тема целиком.</b> Тема клетку
 * переживает — опустошение базы её не касается, — и утверждение «в теме
 * ровно два сообщения» без отметки говорило бы о прогоне, а не о клетке.
 * Форма та же, что у журнала приложения ({@link AppLog#mark()}).
 *
 * <p><b>Порядок читается ВНУТРИ партии.</b> Ключ партиции — тенант, и
 * порядок событий одного тенанта наблюдаем именно так; межпартийного
 * порядка у брокера нет вовсе, и клетка о нём не утверждает.
 */
final class Wire {

    /** Имя заголовка с идентичностью события. */
    private static final String HEADER_EVENT_ID = "eventId";

    /** Имя заголовка с классом события. */
    private static final String HEADER_EVENT_TYPE = "eventType";

    /** Потолок ожидания подтверждения брокером. */
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(30);

    /** Потолок вычитывания окна темы: оно уже опубликовано, ждать нечего. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /** Такт вычитывания окна. */
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private static final Producer<String, String> PRODUCER = newProducer();

    private static final Consumer<String, String> READER = newReader();

    private Wire() {
    }

    /**
     * Кладёт в тему владельца определений сообщение с полным конвертом.
     *
     * <p><b>Тема приходит параметром, а не берётся константой.</b> Класс
     * со своим контекстом читает СВОЮ тему
     * ({@link TradingCoreSubstrate#ownStrategyTopic}), и положенное
     * общей темой соседями прогона его не касается.
     *
     * @param topic     тема владельца определений у этого читателя
     * @param eventId   идентичность события: по ней идёт дедуп у читателя
     * @param eventType класс события
     * @param payload   содержимое дословно
     * @param key       ключ партиции — тенант
     */
    static void publishStrategyFact(String topic, String eventId, String eventType, String payload,
                                    String key) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        record.headers().add(new RecordHeader(HEADER_EVENT_ID, bytes(eventId)));
        record.headers().add(new RecordHeader(HEADER_EVENT_TYPE, bytes(eventType)));
        send(record);
    }

    /**
     * Кладёт в ту же тему сообщение с НЕПОЛНЫМ конвертом: вход клеток о
     * пропуске.
     *
     * @param topic   тема владельца определений у этого читателя
     * @param headers заголовки конверта, которые есть; прочих нет
     * @param payload содержимое дословно
     */
    static void publishStrategyFact(String topic, Map<String, String> headers, String payload) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, TradingCoreBox.TENANT, payload);
        headers.forEach((name, value) -> record.headers().add(new RecordHeader(name, bytes(value))));
        send(record);
    }

    /**
     * Отметка конца темы ядра: с неё клетка читает опубликованное.
     *
     * <p>Тема заводится этим же ходом, если её ещё нет: раскладку
     * спрашивает наблюдатель, и отсутствие темы у него означает «ядро не
     * публиковало ничего», а не отказ.
     */
    static Mark mark() {
        Set<TopicPartition> partitions = corePartitions();
        READER.assign(partitions);
        return new Mark(new LinkedHashMap<>(READER.endOffsets(partitions)));
    }

    /**
     * Опубликованное ядром после отметки — в порядке партий и смещений.
     *
     * @param mark отметка, снятая до наблюдаемого хода
     */
    static List<Published> publishedSince(Mark mark) {
        Set<TopicPartition> partitions = corePartitions();
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
     * Партии темы ядра, заводя тему, если её ещё нет.
     *
     * <p><b>Спрос раскладки повторяется, и это не перестраховка.</b>
     * Первый спрос у несуществующей темы её и ЗАВОДИТ, но раскладку
     * отдаёт пустой: лидер партии на этот момент ещё не выбран. Отказ по
     * первому пустому ответу объявлял бы «ядро не публиковало ничего» там,
     * где тема просто моложе спроса.
     */
    private static Set<TopicPartition> corePartitions() {
        long deadline = System.currentTimeMillis() + READ_TIMEOUT.toMillis();
        Set<TopicPartition> partitions = new LinkedHashSet<>();
        while (partitions.isEmpty() && System.currentTimeMillis() < deadline) {
            List<PartitionInfo> known = READER.partitionsFor(TradingCoreSubstrate.CORE_TOPIC,
                    POLL_INTERVAL);
            known.forEach(info -> partitions.add(new TopicPartition(info.topic(), info.partition())));
        }
        if (partitions.isEmpty()) {
            throw new IllegalStateException("Раскладки темы ядра брокер не отдал: читать нечего");
        }
        return partitions;
    }

    private static void send(ProducerRecord<String, String> record) {
        try {
            PRODUCER.send(record).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Отправка в тему субстрата прервана", failure);
        } catch (Exception failure) {
            throw new IllegalStateException("Брокер субстрата не принял сообщение", failure);
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static Producer<String, String> newProducer() {
        Map<String, Object> settings = new HashMap<>();
        settings.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                TradingCoreSubstrate.broker().getBootstrapServers());
        settings.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        settings.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        settings.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(settings);
    }

    /**
     * Наблюдатель темы ядра. Группы у него нет: партии назначаются, и
     * смещений он не фиксирует — окно задаёт отметка клетки.
     */
    private static Consumer<String, String> newReader() {
        Map<String, Object> settings = new HashMap<>();
        settings.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                TradingCoreSubstrate.broker().getBootstrapServers());
        settings.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        settings.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        settings.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.FALSE);
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
     * Опубликованная ядром запись: ключ, конверт заголовками, содержимое
     * телом.
     *
     * @param key      ключ партиции
     * @param headers  заголовки конверта по именам
     * @param payload  содержимое дословно
     */
    record Published(String key, Map<String, String> headers, String payload) {

        /** Класс события из конверта. */
        String eventType() {
            return headers.get(HEADER_EVENT_TYPE);
        }

        /** Идентичность события из конверта. */
        String eventId() {
            return headers.get(HEADER_EVENT_ID);
        }
    }
}

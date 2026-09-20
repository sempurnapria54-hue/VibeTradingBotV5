package com.example.audit.box;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.RangeAssignor;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Провод субстрата: сторона ПРОИЗВОДИТЕЛЯ события и наблюдатель смещений
 * группы журнала.
 *
 * <p><b>Вход ящика есть ЗАПИСЬ БРОКЕРА, а не запрос</b>
 * (.claude/tests/cases/audit.md §«Новая ось формы — событие как ВХОД»), и
 * собирает её тест: конверт заголовками, тенант ключом записи, содержимое
 * телом (docs/architecture/contracts.md §«Как конверт лежит на проводе —
 * поимённо»). Имена лежат приватной копией у потребителя, и кейс
 * проверяет именно их — второго носителя формы конверта здесь не
 * заводится.
 *
 * <p><b>Клиент свой, а не бин сервиса.</b> Потребитель контекста есть
 * ВНУТРЕННОСТЬ ящика; подать сообщение его средствами значило бы
 * наблюдать предмет изнутри.
 *
 * <p><b>Подтверждение от всех синхронных реплик и синхронная отправка.</b>
 * Кейс ставит вход, а не измеряет задержку: отправка, чьё подтверждение не
 * дождались, сделала бы следующее ожидание гонкой.
 *
 * <p><b>Смещения читаются АДМИНИСТРАТИВНЫМ клиентом, а не вторым
 * потребителем той же группы.</b> Второй потребитель вошёл бы в живую
 * группу ящика и отнял бы у неё партицию — то есть наблюдатель поменял бы
 * наблюдаемое. Административный спрос группу не трогает.
 */
final class Wire {

    /** Потолок ожидания подтверждения брокером. */
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(30);

    /** Потолок административного спроса. */
    private static final Duration ADMIN_TIMEOUT = Duration.ofSeconds(30);

    /** Единственная партиция темы субстрата: её заводит сам субстрат. */
    private static final Integer ONLY_PARTITION = 0;

    /** Потолок ожидания вступления чужого участника в группу. */
    private static final Duration JOIN_TIMEOUT = Duration.ofSeconds(60);

    /** Шаг опроса, которым чужой участник дожидается своего назначения. */
    private static final Duration JOIN_POLL = Duration.ofMillis(200);

    /**
     * Производитель, кладущий запись транзакцией; заводится по первому
     * спросу ({@link #publishInTransaction}).
     */
    private static final AtomicReference<Producer<String, String>> TRANSACTIONAL_PRODUCER =
            new AtomicReference<>();

    private static final Producer<String, String> PRODUCER = newProducer();

    private static final Admin ADMIN = newAdmin();

    private Wire() {
    }

    /**
     * Кладёт запись в названную тему.
     *
     * <p><b>Заголовки подаются перечнем, а не полным набором</b>: вход
     * клеток о неполном конверте есть ОТСУТСТВИЕ заголовка, и набор,
     * собираемый по умолчанию, такой вход выразить не дал бы.
     *
     * @param topic   тема производителя
     * @param key     ключ записи — тенант; пусто означает «ключа нет вовсе»
     * @param headers заголовки конверта, которые есть; прочих нет
     * @param payload содержимое дословно; пусто означает «тела нет вовсе»
     */
    static void publish(String topic, String key, Map<String, String> headers, String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        headers.forEach((name, value) -> record.headers().add(new RecordHeader(name, bytes(value))));
        send(record);
    }

    /**
     * Зафиксированное группой смещение по теме; пусто — группа не
     * фиксировала по ней ничего.
     *
     * @param consumerGroup имя группы потребителя
     * @param topic         тема производителя
     */
    static Long committedOffset(String consumerGroup, String topic) {
        Map<TopicPartition, OffsetAndMetadata> committed = await(
                ADMIN.listConsumerGroupOffsets(consumerGroup).partitionsToOffsetAndMetadata());
        OffsetAndMetadata offset = committed.get(new TopicPartition(topic, ONLY_PARTITION));
        return Objects.isNull(offset) ? null : offset.offset();
    }

    /**
     * Смещение конца темы: столько записей в ней лежит.
     *
     * <p>Вместе с зафиксированным оно и есть остаток непринятого: разность
     * двух чисел, а не показание чужого ряда.
     *
     * @param topic тема производителя
     */
    static Long endOffset(String topic) {
        TopicPartition partition = new TopicPartition(topic, ONLY_PARTITION);
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ends =
                await(ADMIN.listOffsets(Map.of(partition, OffsetSpec.latest())).all());
        return ends.get(partition).offset();
    }

    /**
     * Имена всех тем брокера субстрата.
     *
     * <p>Ими проверяется ОТРИЦАНИЕ: темы мёртвых писем у группы журнала не
     * заводится, и отравленное сообщение никуда не перекладывается
     * (docs/rules/durable-consumer-reception.md §«Обработчик отказа — часть
     * конструкции, а не настройка»).
     */
    static Set<String> topicNames() {
        return await(ADMIN.listTopics().names());
    }

    /**
     * Снимает названные темы у брокера.
     *
     * <p><b>Этим клетка отнимает НАЗНАЧЕНИЕ у живой подписки</b> — вход,
     * который иначе не поставить: остановить контейнер значило бы
     * коснуться второго бина, а решение 6 называет единственной такой
     * точкой тик (.claude/decisions/test-contour-design-pass.md). Тема,
     * которой у брокера не стало, партиций не отдаёт, и назначение
     * пустеет само — ровно то состояние, которое дом называет «группа
     * развалилась либо связи с брокером нет»
     * (docs/components/ReceptionStateJob.md §«Тик молчит, когда приём не
     * жив»).
     *
     * <p><b>Обратно тема не появляется:</b> брокер субстрата тем не
     * заводит ({@link AuditSubstrate}), поэтому снятие окончательно, а
     * контекст такой клетки закрывается вместе с ней.
     *
     * @param names темы, которых у брокера не станет
     */
    static void deleteTopics(Collection<String> names) {
        await(ADMIN.deleteTopics(names).all());
    }

    /**
     * Наименьшее доступное смещение темы: ниже него в ней не лежит ничего.
     *
     * <p>Вместе с зафиксированным оно и есть вход ветви «брокер удалил
     * непрочитанное, пока потребителя не было»
     * (docs/rules/durable-consumer-reception.md §«Обнаружение разрыва —
     * сравнение смещений, и моментов у него два»).
     *
     * @param topic тема производителя
     */
    static Long earliestOffset(String topic) {
        TopicPartition partition = new TopicPartition(topic, ONLY_PARTITION);
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> starts =
                await(ADMIN.listOffsets(Map.of(partition, OffsetSpec.earliest())).all());
        return starts.get(partition).offset();
    }

    /**
     * Удаляет записи темы ниже названного смещения — то есть поднимает
     * наименьшее доступное.
     *
     * <p><b>Этим ставится состояние «непрочитанное удалено», и срок
     * хранения для него не нужен.</b> Срок — величина ОКРУЖЕНИЯ: ждать его
     * истечения в прогоне значило бы ждать минутами то, что брокер умеет
     * сделать одним административным ходом. Наблюдаемое у обоих поводов
     * одно — наименьшее доступное смещение выше зафиксированного группой.
     *
     * @param topic  тема производителя
     * @param offset смещение, ниже которого записей не останется
     */
    static void deleteRecordsBefore(String topic, Long offset) {
        TopicPartition partition = new TopicPartition(topic, ONLY_PARTITION);
        await(ADMIN.deleteRecords(Map.of(partition, RecordsToDelete.beforeOffset(offset))).all());
    }

    /**
     * Кладёт запись В ТРАНЗАКЦИИ: её коммит добавляет к теме
     * <b>управляющую</b> запись, которой потребитель не видит.
     *
     * <p><b>Так ставится вход ветви «смещение доставленной записи больше
     * ожидаемого».</b> Управляющая запись занимает своё смещение, поэтому
     * следующая за ней обычная приезжает на единицу дальше, чем ждёт
     * потребитель — ровно то состояние, которое дом называет разрывом на
     * доставке (docs/rules/durable-consumer-reception.md §«Обнаружение
     * разрыва — сравнение смещений, и моментов у него два»).
     *
     * <p><b>Замена повода объявлена, а не умолчана.</b> В проде повод у
     * такого состояния один — удаление: дом прямо говорит, что компакция
     * темам фактов запрещена, а транзакционных маркеров производители не
     * пишут. Удаление же на ходу гонкой с ЖИВЫМ потребителем не
     * выражается: он вычитывает положенное раньше, чем административный
     * ход успевает отнять. Наблюдаемое у клетки — смещение доставленной
     * записи, и повод его роста ей безразличен.
     *
     * <p><b>Производитель заводится по первому спросу.</b>
     * {@code initTransactions} поднимает координатора транзакций и его
     * тему; платят за это клетки, которым маркер нужен, а не каждый класс
     * прогона.
     *
     * @param topic   тема производителя
     * @param key     ключ записи — тенант
     * @param headers заголовки конверта, которые есть; прочих нет
     * @param payload содержимое дословно
     */
    static void publishInTransaction(String topic, String key, Map<String, String> headers, String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        headers.forEach((name, value) -> record.headers().add(new RecordHeader(name, bytes(value))));
        Producer<String, String> producer = transactionalProducer();
        producer.beginTransaction();
        producer.send(record);
        producer.commitTransaction();
    }

    /**
     * Подаёт НАЗНАЧЕНИЕ ПАРТИЦИЙ живому контейнеру: чужой участник входит
     * в его группу и выходит из неё.
     *
     * <p><b>Это единственный вход, которым назначение подаётся СНАРУЖИ.</b>
     * Первый момент обнаружения разрыва — назначение партиций
     * (docs/rules/durable-consumer-reception.md §«Обнаружение разрыва —
     * сравнение смещений, и моментов у него два»), а у поднятого контекста
     * оно случается один раз — до того, как тик заведёт строки пар, то
     * есть тогда, когда писать ещё некуда. Остановить и поднять контейнер
     * значило бы коснуться второго бина, а решение 6 называет единственной
     * такой точкой тик (.claude/decisions/test-contour-design-pass.md).
     * Вступление чужого участника не касается ни одного бина: оно
     * происходит на брокере.
     *
     * <p><b>Ребалансировка пинится жадной стратегией, и это несущее.</b>
     * Стратегию назначения группа выбирает общую для всех участников;
     * чужой участник объявляет ровно {@code RangeAssignor}, и тогда всякая
     * ребалансировка отнимает партиции у всех и раздаёт заново — то есть
     * назначение приходит контейнеру ЦЕЛИКОМ. При кооперативной стратегии
     * он удержал бы часть партиций через ребалансировку, и сравнение по
     * ним не состоялось бы вовсе.
     *
     * <p><b>Ждётся ВСТУПЛЕНИЕ, а не назначение, и это не послабление.</b>
     * Диапазонная стратегия делит партиции потемно, и теме с единственной
     * партицией достаётся один участник из двух: чужому может не достаться
     * ничего. Ребалансировку при этом вызывает само вступление, а жадность
     * делает её полной у ВСЕХ участников — ожидание непустого назначения у
     * чужого истекало бы там, где вход уже подан.
     *
     * <p><b>Смещений чужой участник не двигает:</b> автоматическая фиксация
     * у него снята, а своей он не делает — прочитанное им остаётся
     * непринятым, и контейнер дочитывает его сам.
     *
     * @param consumerGroup имя группы, которую нужно перетряхнуть
     * @param topics        темы её объявленной подписки
     */
    static void reassignPartitions(String consumerGroup, Collection<String> topics) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, AuditSubstrate.brokerAddress());
        settings.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
        settings.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        settings.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        settings.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.FALSE);
        settings.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, RangeAssignor.class.getName());
        Instant deadline = Instant.now().plus(JOIN_TIMEOUT);
        try (Consumer<String, String> member = new KafkaConsumer<>(settings)) {
            member.subscribe(new ArrayList<>(topics));
            while (isBlank(member.groupMetadata().memberId())) {
                if (Instant.now().isAfter(deadline)) {
                    throw new IllegalStateException("Чужой участник не вошёл в группу " + consumerGroup);
                }
                member.poll(JOIN_POLL);
            }
        }
    }

    private static synchronized Producer<String, String> transactionalProducer() {
        if (Objects.isNull(TRANSACTIONAL_PRODUCER.get())) {
            Map<String, Object> settings = new HashMap<>(producerSettings());
            settings.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "audit-box-tx");
            Producer<String, String> producer = new KafkaProducer<>(settings);
            producer.initTransactions();
            TRANSACTIONAL_PRODUCER.set(producer);
        }
        return TRANSACTIONAL_PRODUCER.get();
    }

    private static <T> T await(KafkaFuture<T> future) {
        try {
            return future.get(ADMIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Спрос к брокеру субстрата прерван", failure);
        } catch (Exception failure) {
            throw new IllegalStateException("Брокер субстрата не ответил на спрос", failure);
        }
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
        return new KafkaProducer<>(producerSettings());
    }

    /** Общее обоим производителям: адрес, форма значений и подтверждение. */
    private static Map<String, Object> producerSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, AuditSubstrate.brokerAddress());
        settings.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        settings.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        settings.put(ProducerConfig.ACKS_CONFIG, "all");
        return settings;
    }

    private static Admin newAdmin() {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, AuditSubstrate.brokerAddress());
        return Admin.create(settings);
    }
}

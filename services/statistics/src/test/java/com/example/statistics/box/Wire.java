package com.example.statistics.box;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.RangeAssignor;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Провод субстрата: сторона ПРОИЗВОДИТЕЛЯ события и наблюдатель смещений
 * группы статистики.
 *
 * <p><b>Вход ящика есть ЗАПИСЬ БРОКЕРА, а не запрос</b>
 * (.claude/tests/cases/statistics.md §«Новая ось формы — ПЕРЕСЧЁТ
 * ПРОЕКЦИИ»), и собирает её тест: конверт заголовками, тенант ключом записи,
 * содержимое телом (docs/architecture/contracts.md §«Как конверт лежит на
 * проводе — поимённо»). Имена лежат приватной копией у потребителя, и кейс
 * проверяет именно их — второго носителя формы конверта здесь не заводится.
 *
 * <p><b>Клиент свой, а не бин сервиса.</b> Потребитель контекста есть
 * ВНУТРЕННОСТЬ ящика; подать сообщение его средствами значило бы наблюдать
 * предмет изнутри.
 *
 * <p><b>Подтверждение от всех синхронных реплик и синхронная отправка.</b>
 * Кейс ставит вход, а не измеряет задержку: отправка, чьё подтверждение не
 * дождались, сделала бы следующее ожидание гонкой.
 *
 * <p><b>Смещения читаются АДМИНИСТРАТИВНЫМ клиентом, а не вторым
 * потребителем той же группы.</b> Второй потребитель вошёл бы в живую группу
 * ящика и отнял бы у неё партицию — то есть наблюдатель поменял бы
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

    /** Шаг опроса, которым чужой участник дожидается вступления. */
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
     * <p><b>Заголовки подаются перечнем, а не полным набором</b>: вход клеток
     * о неполном конверте есть ОТСУТСТВИЕ заголовка, и набор, собираемый по
     * умолчанию, такой вход выразить не дал бы.
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
     * Зафиксированное группой смещение по теме; пусто — группа не фиксировала
     * по ней ничего.
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
     * Заводит тему, на которую не подписан никто.
     *
     * <p><b>Ею ставится предусловие клетки об отсутствии публикаций.</b> «Ни
     * в свои темы, ни в чужие» на одних темах подписки не выразимо: тема,
     * которую потребитель читает, от чужой отличается ровно тем, что он её
     * читает, — и её неизменившийся конец говорит заодно, что прочитанное он
     * не дописывает обратно. Чужая тема предъявляет второе: адрес, которого
     * процессу никто не называл, остаётся пустым.
     *
     * <p><b>Заведение идемпотентно:</b> брокер субстрата общий на прогон, и
     * второй экземпляр того же класса застал бы тему уже заведённой.
     *
     * @param name имя темы, которой у брокера станет
     */
    static void createTopic(String name) {
        if (topicNames().contains(name)) {
            return;
        }
        await(ADMIN.createTopics(List.of(new NewTopic(name, 1, (short) 1))).all());
    }

    /**
     * Имена всех групп, известных брокеру субстрата.
     *
     * <p><b>Ими читается отрицание «второй durable-группы у процесса нет».</b>
     * Группа есть состояние НА БРОКЕРЕ, а не свойство конфигурации: имя,
     * которого процессу не называли, могло бы появиться там только его
     * собственным вступлением.
     *
     * <p><b>Перечень при этом накрывает весь прогон, а не одну клетку</b> —
     * брокер общий всем контекстам, и каждый класс со своим положением осей
     * несёт свою группу ({@link StatisticsSubstrate#registerOwn}). Отсюда
     * форма клейма у клетки: она сверяет не состав перечня, а то, у скольких
     * его членов есть ПОЗИЦИЯ ЧТЕНИЯ на теме фактов.
     */
    static Set<String> consumerGroups() {
        return await(ADMIN.listGroups().all()).stream()
                .map(GroupListing::groupId)
                .collect(Collectors.toSet());
    }

    /**
     * Наименьшее доступное смещение темы: записи ниже него брокер уже удалил.
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
     * Удаляет записи темы ниже названного смещения — безвозвратно.
     *
     * <p><b>Этим ставится ИСТЕЧЕНИЕ СРОКА ХРАНЕНИЯ, которого прогон ждать не
     * может.</b> Срок — величина окружения, отмеряемая часами брокера;
     * наблюдаемое же у обоих поводов одно: наименьшее доступное смещение
     * поднялось над записью, которую потребитель не прочитал.
     *
     * @param topic  тема производителя
     * @param offset смещение, ниже которого записей не остаётся
     */
    static void deleteRecordsBefore(String topic, Long offset) {
        TopicPartition partition = new TopicPartition(topic, ONLY_PARTITION);
        await(ADMIN.deleteRecords(Map.of(partition, RecordsToDelete.beforeOffset(offset))).all());
    }

    /**
     * Снимает названные темы у брокера — безвозвратно.
     *
     * <p><b>Этим отнимается НАЗНАЧЕНИЕ, а не останавливается контейнер.</b>
     * Второй повод неживого приёма, названный кейсом, — остановленный
     * слушатель — потребовал бы коснуться второго бина, а решение 6
     * называет единственной такой точкой тик
     * (.claude/decisions/test-contour-design-pass.md). Темы, которой у
     * брокера нет, партиций нет тоже, и назначение пустеет само — ровно то
     * состояние, которое дом называет «группа развалилась либо связи с
     * брокером нет» (docs/components/ReceptionStateJob.md §«Тик молчит,
     * когда приём не жив»).
     *
     * <p><b>Обратно тема не появляется:</b> брокер субстрата тем не заводит
     * ({@link StatisticsSubstrate}), поэтому снятие окончательно, а контекст
     * такой клетки закрывается вместе с ней.
     *
     * @param names темы, которых у брокера не станет
     */
    static void deleteTopics(Collection<String> names) {
        await(ADMIN.deleteTopics(names).all());
    }

    /**
     * Кладёт запись ТРАНЗАКЦИЕЙ: её коммит занимает в теме своё смещение.
     *
     * <p><b>Этим поднимается СМЕЩЕНИЕ ДОСТАВЛЕННОЙ ЗАПИСИ над ожидаемым</b> —
     * вход второго момента обнаружения разрыва
     * (docs/rules/durable-consumer-reception.md §«Обнаружение разрыва —
     * сравнение смещений, и моментов у него два»). Управляющая запись
     * потребителю не отдаётся, поэтому следующая обычная приезжает на единицу
     * дальше ожидаемого.
     *
     * <p><b>Замена повода объявлена, а не умолчана.</b> В проде повод у такого
     * состояния один — удаление непрочитанного на ходу, — и дом говорит это
     * прямо: компакция темам фактов запрещена, а транзакционных маркеров
     * производители не пишут. Но удаление на ходу гонкой с ЖИВЫМ потребителем
     * не выражается: он вычитывает положенное раньше, чем административный ход
     * успевает отнять. Наблюдаемое у клетки есть смещение записи, и повод его
     * роста ей безразличен.
     *
     * <p><b>Конца обработки такая тема смещением не предъявляет:</b> равенство
     * «конец темы = зафиксированное смещение» на ней не наступает вовсе, и
     * клетка ждёт строки факта ({@code StatisticsBox#awaitDealFactCount}).
     *
     * <p><b>Производитель заводится по первому спросу.</b>
     * {@code initTransactions} поднимает координатора транзакций и его тему;
     * платят за это клетки, которым маркер нужен, а не каждый класс прогона.
     *
     * @param topic   тема производителя
     * @param key     ключ записи — тенант
     * @param headers заголовки конверта, которые есть; прочих нет
     * @param payload содержимое дословно
     */
    static void publishInTransaction(String topic, String key, Map<String, String> headers,
                                     String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        headers.forEach((name, value) -> record.headers().add(new RecordHeader(name, bytes(value))));
        Producer<String, String> producer = transactionalProducer();
        producer.beginTransaction();
        producer.send(record);
        producer.commitTransaction();
    }

    /**
     * Подаёт НАЗНАЧЕНИЕ ПАРТИЦИЙ живому контейнеру: чужой участник входит в
     * его группу и выходит из неё.
     *
     * <p><b>Это единственный вход, которым назначение подаётся СНАРУЖИ.</b>
     * Первый момент обнаружения разрыва — назначение партиций
     * (docs/rules/durable-consumer-reception.md §«Обнаружение разрыва —
     * сравнение смещений, и моментов у него два»), а у поднятого контекста оно
     * случается один раз — до того, как тик заведёт строки пар, то есть тогда,
     * когда писать ещё некуда. Остановить и поднять контейнер значило бы
     * коснуться второго бина, а решение 6 называет единственной такой точкой
     * тик (.claude/decisions/test-contour-design-pass.md). Вступление чужого
     * участника не касается ни одного бина: оно происходит на брокере.
     *
     * <p><b>Ребалансировка пинится жадной стратегией, и это несущее.</b>
     * Стратегию назначения группа выбирает общую для всех участников; чужой
     * участник объявляет ровно {@code RangeAssignor}, и тогда всякая
     * ребалансировка отнимает партиции у всех и раздаёт заново — то есть
     * назначение приходит контейнеру ЦЕЛИКОМ. При кооперативной стратегии он
     * удержал бы часть партиций через ребалансировку, и сравнения по ним не
     * состоялось бы вовсе.
     *
     * <p><b>Ждётся ВСТУПЛЕНИЕ, а не назначение, и это не послабление.</b>
     * Диапазонная стратегия делит партиции потемно, и теме с единственной
     * партицией достаётся один участник из двух: чужому может не достаться
     * ничего. Ребалансировку при этом вызывает само вступление, а жадность
     * делает её полной у ВСЕХ участников — ожидание непустого назначения у
     * чужого истекало бы там, где вход уже подан.
     *
     * <p><b>Смещений чужой участник не двигает:</b> автоматическая фиксация у
     * него снята, а своей он не делает — прочитанное им остаётся непринятым, и
     * контейнер дочитывает его сам.
     *
     * @param consumerGroup имя группы, которую нужно перетряхнуть
     * @param topics        темы её объявленной подписки
     */
    static void reassignPartitions(String consumerGroup, Collection<String> topics) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, StatisticsSubstrate.brokerAddress());
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

    /**
     * Назначает теме срок хранения у БРОКЕРА.
     *
     * <p><b>Им ставится вход клетки о пороге алерта.</b> Порог выводится
     * долей от срока хранения темы, а сам срок в конфигурации сервиса не
     * хранится — его добывает у брокера тик
     * (docs/components/ReceptionStateJob.md §«Срок хранения темы добывается
     * тем же обходом»). Единственный способ предъявить это — сменить срок
     * ТАМ, не тронув ни одной оси сервиса, и увидеть, что порог поехал.
     *
     * @param topic      тема, чей срок назначается
     * @param retention  срок хранения
     */
    static void setRetention(String topic, Duration retention) {
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
        AlterConfigOp operation = new AlterConfigOp(
                new ConfigEntry(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(retention.toMillis())),
                AlterConfigOp.OpType.SET);
        await(ADMIN.incrementalAlterConfigs(Map.of(resource, List.of(operation))).all());
    }

    /**
     * Читает тему ЧУЖОЙ группой от самого начала и отдаёт идентичности
     * прочитанных записей.
     *
     * <p><b>Ею предъявляется независимость смещений двух групп.</b> Группа,
     * названная иначе, ведёт своё смещение, и часть темы непрочитанной у неё
     * не остаётся: она читает всё лежащее, сколько бы ни прочла соседняя
     * (docs/models/domain/other/StatisticsFact.md §«Подписка, группа и
     * позиция чтения»).
     *
     * <p><b>Смещений чужая группа НЕ фиксирует</b>: автоматическая фиксация у
     * неё снята, своей она не делает. Иначе наблюдатель оставлял бы на
     * брокере след, которого предмет не производит.
     *
     * <p><b>Партиция назначается ЯВНО, без вступления в группу.</b> Имя
     * группы здесь нужно только затем, чтобы оно отличалось от имени группы
     * сервиса; подписка вызвала бы ребалансировку и ожидание назначения там,
     * где читать нужно ровно одну известную партицию.
     *
     * @param consumerGroup имя чужой группы
     * @param topic         тема, которую она читает
     * @return идентичности событий в порядке смещений
     */
    static List<String> readAllAsGroup(String consumerGroup, String topic) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, StatisticsSubstrate.brokerAddress());
        settings.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
        settings.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        settings.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        settings.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.FALSE);
        TopicPartition partition = new TopicPartition(topic, ONLY_PARTITION);
        List<String> identities = new ArrayList<>();
        Long end = endOffset(topic);
        try (Consumer<String, String> reader = new KafkaConsumer<>(settings)) {
            reader.assign(List.of(partition));
            reader.seekToBeginning(List.of(partition));
            Instant deadline = Instant.now().plus(JOIN_TIMEOUT);
            while (identities.size() < end) {
                if (Instant.now().isAfter(deadline)) {
                    throw new IllegalStateException("Чужая группа не дочитала тему " + topic);
                }
                reader.poll(JOIN_POLL).forEach(record -> identities.add(headerOf(record)));
            }
        }
        return identities;
    }

    /**
     * Имена всех тем брокера субстрата.
     *
     * <p>Ими проверяется ОТРИЦАНИЕ: темы мёртвых писем у группы статистики не
     * заводится, и отравленное сообщение никуда не перекладывается
     * (docs/rules/durable-consumer-reception.md §«Обработчик отказа — часть
     * конструкции, а не настройка»).
     */
    static Set<String> topicNames() {
        return await(ADMIN.listTopics().names());
    }

    /** Идентичность события, приехавшая заголовком записи. */
    private static String headerOf(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("eventId");
        return Objects.isNull(header) ? null : new String(header.value(), StandardCharsets.UTF_8);
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
        settings.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, StatisticsSubstrate.brokerAddress());
        settings.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        settings.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        settings.put(ProducerConfig.ACKS_CONFIG, "all");
        return settings;
    }

    private static synchronized Producer<String, String> transactionalProducer() {
        if (Objects.isNull(TRANSACTIONAL_PRODUCER.get())) {
            Map<String, Object> settings = new HashMap<>(producerSettings());
            settings.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "statistics-box-tx");
            Producer<String, String> producer = new KafkaProducer<>(settings);
            producer.initTransactions();
            TRANSACTIONAL_PRODUCER.set(producer);
        }
        return TRANSACTIONAL_PRODUCER.get();
    }

    private static Admin newAdmin() {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, StatisticsSubstrate.brokerAddress());
        return Admin.create(settings);
    }
}

package com.example.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statistics.config.ReceptionKafkaConfig;
import com.example.statistics.config.ReceptionProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;

/**
 * Подписка группы статистики и позиция её чтения.
 *
 * <p><b>Подписка мерится ПО ДОМУ ТЕМ, а не по списку рядом.</b> Тема
 * заводится манифестом сервиса-производителя
 * (docs/architecture/contracts.md §«Событие → тема»), и требование
 * контракта — тема и подписка durable-потребителя не должны разъезжаться
 * (§«Тема и подписка durable-потребителя на неё не должны разъезжаться
 * дольше срока хранения темы»). Энфорсера у требования нет, и это
 * названо там же; здесь он появляется у одной стороны — журнала: набор тем
 * подписки сверяется с набором тем, заведённых манифестами.
 *
 * <p><b>Что тест НЕ мерит.</b> Он не спрашивает у брокера, существует ли
 * тема: манифест — источник состояния кластера, и до развёртывания
 * спрашивать нечего.
 */
class ReceptionSubscriptionTest {

    /** Дом тем: манифесты сервисов-производителей. */
    private static final Path MANIFESTS = Path.of("..", "..", "deploy", "base", "services");

    /** Имя темы, заводимой манифестом. */
    private static final Pattern DECLARED_TOPIC = Pattern.compile(
            "kind:\\s*KafkaTopic\\s*\\nmetadata:\\s*\\n\\s*name:\\s*([A-Za-z0-9._-]+)");

    /** Умолчание подписки в конфигурации сервиса. */
    private static final Pattern SUBSCRIBED_TOPICS =
            Pattern.compile("topics:\\s*\\$\\{RECEPTION_TOPICS:([^}]*)}");

    private static final Path SERVICE_CONFIG =
            Path.of("src", "main", "resources", "application.yaml");

    /**
     * Подписка у́же перечня заведённых тем, и это следствие признака, а не
     * пропуск: класс несом статистикой ровно тогда, когда его содержимое
     * несёт операнд объявленного зерна
     * (docs/models/domain/other/StatisticsFact.md §«Признак несомого
     * класса»). Перечень тем производен от зёрен.
     *
     * <p><b>Проверяется поэтому ВКЛЮЧЕНИЕ, а не равенство:</b> всякая тема
     * подписки обязана быть заведена манифестом — подписка на незаведённую
     * тему молча не даёт ничего, — но не всякая заведённая тема обязана
     * быть в подписке.
     */
    @Test
    @DisplayName("Каждая тема подписки заведена манифестом, а подписка у́же перечня по признаку")
    void everySubscribedTopicIsDeclaredByAManifest() throws IOException {
        List<String> declared = declaredTopics();
        List<String> subscribed = subscribedTopics();

        assertThat(declared)
                .as("тем в манифестах не найдено — мерить нечего, а не «всё сошлось»")
                .isNotEmpty();
        assertThat(subscribed)
                .as("подписки не найдено — мерить нечего")
                .isNotEmpty();
        assertThat(declared)
                .as("подписка на тему, которой манифест не заводит, молча не даёт ничего")
                .containsAll(subscribed);
    }

    @Test
    @DisplayName("Позиция чтения — с начала темы, автоматическая фиксация смещений выключена")
    void theGroupReadsFromTheEarliestOffsetAndNeverAutoCommits() {
        ConsumerFactory<String, String> factory =
                new ReceptionKafkaConfig().journalConsumerFactory(properties());

        Map<String, Object> settings = factory.getConfigurationProperties();

        assertThat(settings.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG))
                .as("«с текущего момента» теряло бы произведённое, пока потребителя не было")
                .isEqualTo("earliest");
        assertThat(settings.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG))
                .as("таймер клиента продвинул бы смещение независимо от того, легла ли строка")
                .isEqualTo(Boolean.FALSE);
        assertThat(settings.get(ConsumerConfig.GROUP_ID_CONFIG))
                .as("имя группы — операнд ключа строки состояния приёма")
                .isEqualTo("statistics.facts");
        assertThat(settings.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG))
                .isEqualTo(StringDeserializer.class);
    }

    /** Темы, заведённые манифестами производителей. */
    private List<String> declaredTopics() throws IOException {
        List<String> topics = new ArrayList<>();
        try (Stream<Path> manifests = Files.list(MANIFESTS)) {
            for (Path manifest : manifests.toList()) {
                Matcher matcher = DECLARED_TOPIC.matcher(
                        Files.readString(manifest, StandardCharsets.UTF_8).replace("\r\n", "\n"));
                while (matcher.find()) {
                    topics.add(matcher.group(1));
                }
            }
        }
        return topics;
    }

    /** Темы подписки — умолчанием конфигурации сервиса. */
    private List<String> subscribedTopics() throws IOException {
        Matcher matcher = SUBSCRIBED_TOPICS.matcher(Files.readString(SERVICE_CONFIG, StandardCharsets.UTF_8));
        assertThat(matcher.find())
                .as("умолчания подписки в конфигурации сервиса не найдено — мерить нечего")
                .isTrue();
        return Arrays.stream(matcher.group(1).split(",")).map(String::trim).toList();
    }

    private ReceptionProperties properties() {
        ReceptionProperties properties = new ReceptionProperties();
        properties.setBootstrapServers("platform-kafka-kafka-bootstrap:9092");
        properties.setGroupId("statistics.facts");
        return properties;
    }
}

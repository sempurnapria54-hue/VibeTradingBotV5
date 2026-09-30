package com.example.bff.unit.stream;

import static com.example.bff.unit.stream.StreamFixture.FACT_TYPE;
import static com.example.bff.unit.stream.StreamFixture.fact;
import static com.example.bff.unit.stream.StreamFixture.framesOf;
import static com.example.bff.unit.stream.StreamFixture.perimeterRecord;
import static com.example.bff.unit.stream.StreamFixture.properties;
import static com.example.bff.unit.stream.StreamFixture.registry;
import static com.example.bff.unit.stream.StreamFixture.subscribe;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.bff.api.model.StreamRecordApiModel;
import com.example.bff.config.PerimeterProperties;
import com.example.bff.domain.stream.StreamRegistry;
import com.example.bff.util.Constants;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.RecordingEmitterChannel;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Окно переигрывания и явный разрыв — группы `U6` и `U7` документа
 * `.claude/tests/cases/bff-perimeter-logic.md`
 * (docs/architecture/contracts.md §«Позиция чтения в браузер не уходит»).
 *
 * <p><b>Почему это стои́т проверять.</b> Обе ошибки окна молчаливы и
 * правдоподобны: продолжение не с той позиции даёт браузеру связную
 * картину поверх дыры, а разрыв вместо хвоста заставляет его перечитывать
 * историю там, где терять было нечего. Отличить одно от другого по самому
 * потоку нельзя — различает их ровно эта пара исходов.
 *
 * <p><b>Переигрывание наблюдается подключением наблюдателя ПОСЛЕ открытия:</b>
 * записи, отданные внутри {@code open}, каркас держит в своей очереди и
 * выталкивает на подключении. Порядок при этом сохраняется — он и есть
 * предмет половины кейсов.
 */
class StreamReplayTest {

    private static final String TENANT = "tenant-7";
    private static final String OTHER_TENANT = "tenant-8";

    /** Потолок реестра с держащей подпиской: она не отнимает места у клетки. */
    private static final Integer HELD_CEILING = 8;

    /** Кругов у клетки одновременности: точки между двумя ходами у предмета нет. */
    private static final Integer CONCURRENT_ROUNDS = 500;

    /** Единственный факт в окне: позиция на нём даёт пустой хвост, а не разрыв. */
    @Test
    @DisplayName("U6.1 — позиция на единственном факте окна даёт пустой хвост")
    void u6_1_aPositionOnTheOnlyFactYieldsAnEmptyTail() {
        StreamRegistry registry = heldRegistry(TENANT);
        registry.publish(TENANT, fact("E1"));

        RecordingEmitterChannel channel = subscribe(registry, TENANT, "E1");

        assertThat(framesOf(channel))
                .as("позиция нашлась — терять нечего, и разрыва нет")
                .isEmpty();
    }

    /** Окно держит последние записи: вытесненная позиция даёт разрыв. */
    @Test
    @DisplayName("U6.2 — окно держит последние записи, вытесненная позиция даёт разрыв")
    void u6_2_theWindowKeepsTheLastRecordsAndAnEvictedPositionYieldsAGap() {
        StreamRegistry registry = heldRegistry(TENANT);
        publishFacts(registry, TENANT, "E1", "E2", "E3", "E4");

        RecordingEmitterChannel survived = subscribe(registry, TENANT, "E2");
        RecordingEmitterChannel evicted = subscribe(registry, TENANT, "E1");

        assertThat(idsOf(survived))
                .as("окно на три записи: после вытеснения в нём E2, E3, E4 в порядке доставки")
                .containsExactly("E3", "E4");
        assertThat(framesOf(evicted)).singleElement()
                .extracting(SseFrame::eventName)
                .as("самая ранняя вытеснена — её позиция больше не находится")
                .isEqualTo(Constants.StreamRecords.GAP);
    }

    /** Окна тенантов раздельны, и порядок внутри каждого — порядок доставки. */
    @Test
    @DisplayName("U6.3 — окна двух тенантов раздельны и каждое хранит свой порядок")
    void u6_3_theWindowsOfTwoTenantsAreSeparate() {
        StreamRegistry registry = heldRegistry(TENANT, OTHER_TENANT);
        registry.publish(TENANT, fact("A1"));
        registry.publish(OTHER_TENANT, fact("B1"));
        registry.publish(TENANT, fact("A2"));
        registry.publish(OTHER_TENANT, fact("B2"));

        RecordingEmitterChannel first = subscribe(registry, TENANT, "A1");
        RecordingEmitterChannel second = subscribe(registry, OTHER_TENANT, "B1");

        assertThat(idsOf(first)).as("в окне тенанта только его записи").containsExactly("A2");
        assertThat(idsOf(second)).as("и у соседа — только его").containsExactly("B2");
    }

    /**
     * Окно заводит подписка, а не публикация: тенанту, у которого подписки
     * нет и не было, помнить нечего, и позиция на его факте даёт разрыв.
     */
    @Test
    @DisplayName("U6.4 — факт тенанта без подписок окна не заводит")
    void u6_4_aFactOfATenantWithoutSubscriptionsOpensNoWindow() {
        StreamRegistry registry = registry();

        registry.publish(TENANT, fact("E1"));
        registry.publish(TENANT, fact("E2"));

        RecordingEmitterChannel late = subscribe(registry, TENANT, "E1");
        assertThat(framesOf(late)).singleElement()
                .extracting(SseFrame::eventName)
                .as("окна не было — позиции в нём нет, и поток сообщает разрыв")
                .isEqualTo(Constants.StreamRecords.GAP);
    }

    /**
     * Последнюю подписку окно переживает: пересоздание подписки клиентом
     * продолжает поток с названной позиции.
     */
    @Test
    @DisplayName("U6.9 — окно переживает последнюю подписку в пределах срока билета")
    void u6_9_theWindowOutlivesTheLastSubscriptionWithinTheTicketTerm() {
        StreamRegistry registry = registry();
        RecordingEmitterChannel gone = subscribe(registry, TENANT, null);
        publishFacts(registry, TENANT, "E1", "E2");
        gone.fireCompletion();
        registry.publish(TENANT, fact("E3"));

        RecordingEmitterChannel recreated = subscribe(registry, TENANT, "E1");

        assertThat(idsOf(recreated))
                .as("факт, доехавший между подписками, окно сохранило")
                .containsExactly("E2", "E3");
    }

    /**
     * Срок окна без подписок — срок билета: по его истечении окно уходит, и
     * позиция в нём больше не находится.
     */
    @Test
    @DisplayName("U6.10 — окно без подписок уходит по сроку билета")
    void u6_10_aWindowWithoutSubscriptionsIsEvictedAfterTheTicketTerm() {
        PerimeterProperties expiring = properties(3, 2);
        expiring.getTicket().setTtl(Duration.ZERO);
        StreamRegistry registry = new StreamRegistry(expiring);
        RecordingEmitterChannel gone = subscribe(registry, TENANT, null);
        registry.publish(TENANT, fact("E1"));
        gone.fireCompletion();

        registry.publish(TENANT, fact("E2"));
        RecordingEmitterChannel late = subscribe(registry, TENANT, "E1");

        assertThat(framesOf(late)).singleElement()
                .extracting(SseFrame::eventName)
                .as("окно ушло со сроком — ни позиции, ни факта после неё")
                .isEqualTo(Constants.StreamRecords.GAP);
    }

    /** В окно кладёт публикация факта, а не рассылка записи периметра. */
    @Test
    @DisplayName("U6.5 — запись периметра окна не меняет")
    void u6_5_aPerimeterRecordDoesNotTouchTheWindow() {
        StreamRegistry registry = heldRegistry(TENANT);
        registry.publish(TENANT, fact("E1"));

        registry.broadcast(perimeterRecord(Constants.StreamRecords.PULSE));

        RecordingEmitterChannel channel = subscribe(registry, TENANT, "E1");
        assertThat(framesOf(channel))
                .as("окно держит факты, по которым клиент просит продолжения")
                .isEmpty();
    }

    /** Разрыв, отданный открытию, в окно не попадает. */
    @Test
    @DisplayName("U6.6 — запись разрыва в окно не кладётся")
    void u6_6_theGapRecordIsNotPutIntoTheWindow() {
        StreamRegistry registry = heldRegistry(TENANT);
        registry.publish(TENANT, fact("E1"));

        subscribe(registry, TENANT, "нет-такой-позиции");

        RecordingEmitterChannel afterGap = subscribe(registry, TENANT, "E1");
        assertThat(framesOf(afterGap))
                .as("окно наполняет публикация, а не открытие")
                .isEmpty();
    }

    /**
     * Нулевой предел окна: недостижим умолчанием своей конфигурации, но
     * окружение, задавшее ноль, состояние открывает.
     */
    @Test
    @DisplayName("U6.7 — при нулевом пределе окно всегда пусто, и всякая позиция даёт разрыв")
    void u6_7_aZeroWindowLeavesEveryPositionWithAGap() {
        StreamRegistry registry = new StreamRegistry(properties(0, 2));
        registry.publish(TENANT, fact("E1"));

        RecordingEmitterChannel channel = subscribe(registry, TENANT, "E1");

        assertThat(framesOf(channel)).singleElement()
                .extracting(SseFrame::eventName)
                .as("записи не остаётся ни одной — продолжать не с чего")
                .isEqualTo(Constants.StreamRecords.GAP);
    }

    /** Первому подключению терять нечего: ни хвоста, ни разрыва. */
    @Test
    @DisplayName("U7.1 — неназванная позиция не даёт ни хвоста, ни разрыва")
    void u7_1_anAbsentPositionYieldsNeitherTailNorGap() {
        StreamRegistry registry = filledRegistry();

        RecordingEmitterChannel channel = subscribe(registry, TENANT, null);

        assertThat(framesOf(channel))
                .as("до первого нового факта в провод не уходит ничего")
                .isEmpty();
    }

    /** Пустое и незаполненное неразличимы. */
    @Test
    @DisplayName("U7.2 — пустая строка и пробелы читаются как неназванная позиция")
    void u7_2_aBlankPositionReadsAsAbsent() {
        StreamRegistry registry = filledRegistry();

        RecordingEmitterChannel empty = subscribe(registry, TENANT, "");
        RecordingEmitterChannel spaces = subscribe(registry, TENANT, "   ");

        assertThat(framesOf(empty)).as("пустая строка — та же неназванность").isEmpty();
        assertThat(framesOf(spaces)).as("и строка из пробелов тоже").isEmpty();
    }

    /** Поток продолжается С названной позиции: сама она не повторяется. */
    @Test
    @DisplayName("U7.3 — позиция в середине окна даёт хвост после неё")
    void u7_3_aPositionInsideTheWindowYieldsTheTailAfterIt() {
        StreamRegistry registry = filledRegistry();

        RecordingEmitterChannel channel = subscribe(registry, TENANT, "E1");

        assertThat(idsOf(channel))
                .as("продолжение идёт с названной позиции, а не включая её")
                .containsExactly("E2", "E3");
    }

    /** Пустой хвост — свой исход, отличный от «не нашлось». */
    @Test
    @DisplayName("U7.4 — позиция на последней записи окна не даёт ни записи, ни разрыва")
    void u7_4_aPositionOnTheLastRecordYieldsNothing() {
        StreamRegistry registry = filledRegistry();

        RecordingEmitterChannel channel = subscribe(registry, TENANT, "E3");

        assertThat(framesOf(channel))
                .as("нашлась последней — терять нечего")
                .isEmpty();
    }

    /** Сторона границы, противоположная вытеснению: первая уцелевшая находится. */
    @Test
    @DisplayName("U7.5 — первая невытесненная позиция даёт хвост, а не разрыв")
    void u7_5_theFirstSurvivingPositionYieldsATail() {
        StreamRegistry registry = heldRegistry(TENANT);
        publishFacts(registry, TENANT, "E1", "E2", "E3", "E4");

        RecordingEmitterChannel channel = subscribe(registry, TENANT, "E2");

        assertThat(idsOf(channel))
                .as("E2 — самая ранняя из уцелевших, и хвост после неё цел")
                .containsExactly("E3", "E4");
    }

    /** Вытесненная позиция — разрыв, и ни одного факта вместе с ним. */
    @Test
    @DisplayName("U7.6 — вытесненная позиция даёт разрыв и ни одного факта")
    void u7_6_anEvictedPositionYieldsAGapAlone() {
        StreamRegistry registry = registry();
        publishFacts(registry, TENANT, "E1", "E2", "E3", "E4");

        RecordingEmitterChannel channel = subscribe(registry, TENANT, "E1");

        assertThat(framesOf(channel)).singleElement()
                .extracting(SseFrame::eventName)
                .as("молчаливого продолжения не бывает")
                .isEqualTo(Constants.StreamRecords.GAP);
    }

    /** Выдуманная позиция ведёт к тому же исходу, что и вытесненная. */
    @Test
    @DisplayName("U7.7 — выдуманная позиция даёт разрыв")
    void u7_7_anInventedPositionYieldsAGap() {
        StreamRegistry registry = filledRegistry();

        RecordingEmitterChannel channel = subscribe(registry, TENANT, "выдуманная-позиция");

        assertThat(framesOf(channel)).singleElement()
                .extracting(SseFrame::eventName)
                .isEqualTo(Constants.StreamRecords.GAP);
    }

    /** «Окна нет» и «не нашлось» отдают одно и то же. */
    @Test
    @DisplayName("U7.8 — названная позиция без единой публикации даёт разрыв")
    void u7_8_aNamedPositionWithoutAnyWindowYieldsAGap() {
        StreamRegistry registry = registry();

        RecordingEmitterChannel channel = subscribe(registry, TENANT, "E1");

        assertThat(framesOf(channel)).singleElement()
                .extracting(SseFrame::eventName)
                .isEqualTo(Constants.StreamRecords.GAP);
    }

    /** Заведённым и пустым окно бывает только при нулевом пределе — исход тот же. */
    @Test
    @DisplayName("U7.9 — заведённое, но пустое окно даёт разрыв")
    void u7_9_anEmptyButExistingWindowYieldsAGap() {
        StreamRegistry registry = new StreamRegistry(properties(0, 2));
        registry.publish(TENANT, fact("E1"));

        RecordingEmitterChannel channel = subscribe(registry, TENANT, "E1");

        assertThat(framesOf(channel)).singleElement()
                .extracting(SseFrame::eventName)
                .isEqualTo(Constants.StreamRecords.GAP);
    }

    /** Чужая идентичность не находится ни при каком совпадении значений. */
    @Test
    @DisplayName("U7.10 — позиция чужого тенанта даёт разрыв")
    void u7_10_aPositionOfAnotherTenantYieldsAGap() {
        StreamRegistry registry = registry();
        publishFacts(registry, OTHER_TENANT, "E1", "E2");
        publishFacts(registry, TENANT, "A1");

        RecordingEmitterChannel channel = subscribe(registry, TENANT, "E1");

        assertThat(framesOf(channel)).singleElement()
                .extracting(SseFrame::eventName)
                .as("окна раздельны — чужая позиция в своём не ищется")
                .isEqualTo(Constants.StreamRecords.GAP);
    }

    /** Сравнение идентичностей дословное: нормализации нет. */
    @Test
    @DisplayName("U7.11 — позиция, отличающаяся регистром или пробелом, даёт разрыв")
    void u7_11_aPositionDifferingInCaseOrSpaceYieldsAGap() {
        StreamRegistry registry = filledRegistry();

        RecordingEmitterChannel byCase = subscribe(registry, TENANT, "e2");
        RecordingEmitterChannel bySpace = subscribe(registry, TENANT, " E2 ");

        assertThat(framesOf(byCase)).singleElement()
                .extracting(SseFrame::eventName).isEqualTo(Constants.StreamRecords.GAP);
        assertThat(framesOf(bySpace)).singleElement()
                .extracting(SseFrame::eventName).isEqualTo(Constants.StreamRecords.GAP);
    }

    /**
     * Состав записи разрыва. Момент у неё — момент ОТПРАВКИ, а не
     * происшествия, и это решение, а не долг: нижнюю границу дочитывания
     * строит клиент от последнего показанного ему непустого момента
     * происшествия, и поля границы у записи разрыва нет
     * (docs/architecture/contracts.md, раздел «Живые данные в браузер»).
     */
    @Test
    @DisplayName("U7.12 — запись разрыва несёт пустую идентичность, пустое содержимое и момент отправки в UTC")
    void u7_12_theGapRecordCarriesNoIdentityAndTheSendingMoment() {
        StreamRegistry registry = filledRegistry();
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        RecordingEmitterChannel channel = subscribe(registry, TENANT, "выдуманная-позиция");

        SseFrame frame = framesOf(channel).get(0);
        assertThat(frame.carriesId()).as("поля идентичности у разрыва нет").isFalse();
        StreamRecordApiModel record = (StreamRecordApiModel) frame.payload();
        assertThat(record.id()).as("и в самой записи она пуста").isNull();
        assertThat(record.type()).isEqualTo(Constants.StreamRecords.GAP);
        assertThat(record.content()).as("факта разрыв не несёт ни одного").isNull();
        assertThat(record.occurredAt().getOffset()).as("время UTC").isEqualTo(ZoneOffset.UTC);
        assertThat(record.occurredAt()).as("момент отправки — не раньше начала прогона").isAfterOrEqualTo(before);
    }

    /**
     * Открытие с позицией и публикация факта идут одновременно. Точки между
     * регистрацией подписки и переигрыванием у предмета нет, поэтому вход
     * повторён; при любом порядке двух ходов провод обязан нести хвост и
     * новый факт в порядке доставки и ровно по разу. Наблюдатель
     * подключается после обоих ходов: записи, отданные до подключения,
     * каркас держит в своей очереди в порядке отдачи.
     */
    @Test
    @DisplayName("U7.13 — факт, принятый во время открытия, не обгоняет хвоста окна")
    void u7_13_aFactAcceptedDuringTheOpeningDoesNotOvertakeTheTail() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < CONCURRENT_ROUNDS; round++) {
                StreamRegistry registry = held(new StreamRegistry(properties(5, 2)), TENANT);
                publishFacts(registry, TENANT, "E1", "E2", "E3");
                CyclicBarrier start = new CyclicBarrier(2);

                Future<SseEmitter> opening = pool.submit(() -> {
                    await(start);
                    return registry.open(TENANT, "E1");
                });
                Future<?> publication = pool.submit(() -> {
                    await(start);
                    registry.publish(TENANT, fact("E4"));
                });
                publication.get();
                RecordingEmitterChannel channel = RecordingEmitterChannel.attachedTo(opening.get());

                assertThat(idsOf(channel))
                        .as("круг %s: хвост окна, затем новый факт, каждый по разу", round)
                        .containsExactly("E2", "E3", "E4");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** Окно тенанта, наполненное фактами `E1`, `E2`, `E3` в этом порядке. */
    private StreamRegistry filledRegistry() {
        StreamRegistry registry = heldRegistry(TENANT);
        publishFacts(registry, TENANT, "E1", "E2", "E3");
        return registry;
    }

    /**
     * Реестр базовой сборки, у названных тенантов которого открыто по
     * подписке: окно заводит подписка, а не запись, и наполняется оно,
     * пока тенанту есть кому показывать. Потолок шире базового — держащая
     * подписка места у клетки не занимает.
     */
    private StreamRegistry heldRegistry(String... tenantIds) {
        return held(new StreamRegistry(properties(3, HELD_CEILING)), tenantIds);
    }

    /** Открыть у названных тенантов по подписке первого подключения. */
    private StreamRegistry held(StreamRegistry registry, String... tenantIds) {
        for (String tenantId : tenantIds) {
            registry.open(tenantId, null);
        }
        return registry;
    }

    private void publishFacts(StreamRegistry registry, String tenantId, String... ids) {
        for (String id : ids) {
            registry.publish(tenantId, fact(id));
        }
    }

    private List<String> idsOf(RecordingEmitterChannel channel) {
        return framesOf(channel).stream()
                .peek(frame -> assertThat(frame.eventName())
                        .as("хвост несёт факты, а не записи периметра")
                        .isEqualTo(FACT_TYPE))
                .map(SseFrame::id)
                .toList();
    }

    /** Встать у барьера; прерывание прогона — отказ клетки, а не её исход. */
    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException | BrokenBarrierException failure) {
            throw new IllegalStateException("Барьер клетки не пройден", failure);
        }
    }
}

package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.attached;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.order;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.orderWith;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.standaloneStop;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheOf;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheWithExposure;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPositionAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Отсутствие выходов: чего слой не делает — группа `U20` документа
 * `.claude/tests/cases/domain-model-predicates.md`
 * (docs/architecture/services.md §«Что в библиотеку НЕ уезжает»,
 * docs/architecture/data-ownership.md).
 *
 * <p><b>Базовая сборка:</b> любая из групп выше; наблюдается
 * ОТСУТСТВИЕ эффектов за пределами возвращённого значения и изменённого
 * получателя.
 *
 * <p><b>Клейм отсутствия читается по исполняемому телу исходников
 * артефакта</b> — за вычетом комментариев, — а не по прогону: прогон
 * показал бы отсутствие вызова только на той тропе, которую он прошёл, а
 * утверждение здесь о ВСЕЙ библиотеке.
 */
class LayerAbsenceTest {

    /** Исполняемые тела всех исходников артефакта, за вычетом комментариев. */
    private static final String EXECUTABLE_BODY = readExecutableBody();

    @Test
    @DisplayName("U20.1 — в базу не пишется ничего")
    void u20_1_theArtefactNeverTouchesTheDatabase() {
        assertThat(EXECUTABLE_BODY).doesNotContain("Repository", "DataService", "EntityManager",
                "jakarta.persistence", "javax.persistence", "jdbc");
    }

    @Test
    @DisplayName("U20.2 — соседу не звонят, в брокер не публикуют")
    void u20_2_theArtefactNeverCallsAnybody() {
        assertThat(EXECUTABLE_BODY).doesNotContain("RestClient", "WebClient", "HttpClient",
                "KafkaTemplate", "ApplicationEventPublisher");
    }

    @Test
    @DisplayName("U20.3 — в лог не пишется ничего")
    void u20_3_noClassHoldsALogger() {
        assertThat(EXECUTABLE_BODY).doesNotContain("Logger", "slf4j", "Slf4j");
    }

    /** Момент приезжает аргументом либо полем модели. */
    @Test
    @DisplayName("U20.4 — часов не читают")
    void u20_4_noClassReadsTheClock() {
        assertThat(EXECUTABLE_BODY).doesNotContain("Instant.now", "OffsetDateTime.now",
                "LocalDate.now", "LocalDateTime.now", "currentTimeMillis", "Clock");
    }

    /** Тот же объект отвечает одинаково на повторный вызов. */
    @Test
    @DisplayName("U20.5 — предикат-вопрос состояния получателя не меняет")
    void u20_5_aQuestionPredicateIsIdempotent() {
        DealTranche subject = trancheOf(trancheWithExposure("10"), List.of(),
                List.of(standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", "90")));

        Boolean first = subject.isCovered();
        Boolean second = subject.isCovered();

        assertThat(second).isEqualTo(first);
        assertThat(subject.exposure()).isEqualByComparingTo("10");
        assertThat(subject.getStatus()).isNull();
        assertThat(subject.getCloseReason()).isNull();
    }

    /** Кросс-траншевых слагаемых нет по построению. */
    @Test
    @DisplayName("U20.6 — защиты соседних траншей в сумму не входят")
    void u20_6_coverageNeverCrossesTrancheBorders() {
        DealTranche uncovered = trancheOf(trancheWithExposure("10"), List.of(), List.of());
        DealTranche neighbour = trancheOf(trancheWithExposure("10"), List.of(),
                List.of(standaloneStop(1L, AlgoOrder.Status.ACTIVE, "40", "90")));
        Deal deal = new Deal();
        deal.setTranches(List.of(uncovered, neighbour));

        assertThat(uncovered.isCovered()).isFalse();
        assertThat(deal.allTranchesCovered()).isFalse();
    }

    /** Транш отвечает по своим данным, а направление приезжает аргументом. */
    @Test
    @DisplayName("U20.7 — предикат транша сделку не читает")
    void u20_7_theTrancheHoldsNoReferenceToItsDeal() {
        assertThat(Stream.of(DealTranche.class.getDeclaredFields()).map(Field::getType))
                .doesNotContain(Deal.class);
        assertThat(Stream.of(DealTranche.class.getDeclaredMethods())
                .flatMap(method -> Stream.of(method.getParameterTypes()))
                .filter(Deal.class::equals)).isEmpty();
    }

    /** Торгового решения не принимается, команды не строятся. */
    @Test
    @DisplayName("U20.8 — предикат сделки решения не принимает")
    void u20_8_theArtefactDeclaresNoExecutors() {
        List<String> executors = sourceNames().stream()
                .filter(name -> name.endsWith("Command") || name.endsWith("Handler")
                        || name.endsWith("Executor") || name.endsWith("Orchestrator")
                        || name.endsWith("Job") || name.endsWith("Service"))
                .toList();

        assertThat(executors).isEmpty();
    }

    /** Перевод заявки не меняет ни её встроенных защит, ни транша. */
    @Test
    @DisplayName("U20.9 — ребро статуса смежных объектов не трогает")
    void u20_9_anEdgeLeavesTheNeighboursAlone() {
        AttachedAlgoOrder protection = attached(AttachedAlgoOrder.Status.ACTIVE, "10", "90");
        Order parent = orderWith(order(1L, Order.Status.ACTIVE, "10", false), protection);
        DealTranche tranche = trancheOf(trancheWithExposure("10"), List.of(parent), List.of());

        parent.toCancel(Order.CloseReason.KILL_SWITCH);

        assertThat(protection.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ACTIVE);
        assertThat(protection.getCloseReason()).isNull();
        assertThat(tranche.getStatus()).isNull();
        assertThat(tranche.exposure()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("U20.10 — ребро, закончившееся отказом, изменений не оставляет")
    void u20_10_aRefusedEdgeLeavesNothingBehind() {
        AlgoOrder subject = standaloneStop(1L, AlgoOrder.Status.COMPLETED, "10", "90");
        subject.setCloseReason(AlgoOrder.CloseReason.TRIGGERED);

        assertThatThrownBy(() -> subject.toCancel(AlgoOrder.CloseReason.KILL_SWITCH))
                .isInstanceOf(IllegalStateException.class);

        assertThat(subject.getStatus()).isEqualTo(AlgoOrder.Status.COMPLETED);
        assertThat(subject.getCloseReason()).isEqualTo(AlgoOrder.CloseReason.TRIGGERED);
        assertThat(subject.getSize()).isEqualByComparingTo("10");
    }

    /** Технический ключ строки и audit-поля ставит персистентность. */
    @Test
    @DisplayName("U20.11 — модель ключа строки и audit-полей не проставляет")
    void u20_11_theModelNeverStampsItsOwnAuditFields() {
        DealTranche subject = new DealTranche();

        subject.isRiskBearing();
        subject.exposure();
        subject.isCovered();

        assertThat(subject.getId()).isNull();
        assertThat(subject.getCreatedAt()).isNull();
        assertThat(subject.getModifiedAt()).isNull();
        assertThat(subject.getCreatedBy()).isNull();
        assertThat(subject.getModifiedBy()).isNull();
    }

    /**
     * Отрицание ограничено сериализацией: КОНТРАКТ полиморфного разбора
     * дерева действий модель несёт — дискриминатор объявлен на
     * {@code StrategyAction}.
     */
    @Test
    @DisplayName("U20.12 — формы провода модель не строит и сериализацию не исполняет")
    void u20_12_theArtefactNeverSerialisesAnything() {
        assertThat(EXECUTABLE_BODY).doesNotContain("ObjectMapper", "writeValueAsString", "readValue");
        assertThat(StrategyAction.class.getAnnotation(JsonTypeInfo.class)).isNotNull();
    }

    /**
     * Дискриминатор {@code actionKind} и три его имени (пробел `G3`
     * документа, добран под-шагом 3): читатель имён — ПРОВОД, тело
     * команды приёма у `strategies` и снимок дерева в событии активации у
     * ядра.
     */
    @Test
    @DisplayName("U20.13 — дискриминатор дерева действий и три его имени")
    void u20_13_theActionKindDiscriminatorNamesThreeSubtypes() {
        JsonTypeInfo typeInfo = StrategyAction.class.getAnnotation(JsonTypeInfo.class);
        JsonSubTypes subTypes = StrategyAction.class.getAnnotation(JsonSubTypes.class);

        assertThat(typeInfo.property()).isEqualTo("actionKind");
        assertThat(typeInfo.use()).isEqualTo(JsonTypeInfo.Id.NAME);
        assertThat(Arrays.stream(subTypes.value()).map(JsonSubTypes.Type::name))
                .containsExactlyInAnyOrder("ORDER", "ALGO_ORDER", "POSITION");
        assertThat(Arrays.stream(subTypes.value()).map(JsonSubTypes.Type::value))
                .containsExactlyInAnyOrder(StrategyOrderAction.class, StrategyAlgoOrderAction.class,
                        StrategyPositionAction.class);
        assertThat(new StrategyOrderAction().levelSource()).isNotNull();
        assertThat(StrategyTradeDirection.values()).hasSize(2);
    }

    private static List<String> sourceNames() {
        return sources().map(path -> path.getFileName().toString().replace(".java", "")).toList();
    }

    /**
     * Базовый гейт клейма отсутствия: дерево исходников разобрано и
     * непусто. Пустой перечень сделал бы все утверждения группы
     * тождественно истинными — то есть подменил бы предмет измерения.
     */
    private static Stream<Path> sources() {
        try (Stream<Path> tree = Files.walk(Path.of("src", "main", "java"))) {
            List<Path> found = tree.filter(path -> path.toString().endsWith(".java")).toList();
            assertThat(found).as("исходники артефакта разобраны").isNotEmpty();
            return found.stream();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Все исходники артефакта одной строкой, за вычетом комментариев. */
    private static String readExecutableBody() {
        return sources().map(LayerAbsenceTest::stripComments).collect(Collectors.joining("\n"));
    }

    private static String stripComments(Path source) {
        try {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^\\s*//.*$", "");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}

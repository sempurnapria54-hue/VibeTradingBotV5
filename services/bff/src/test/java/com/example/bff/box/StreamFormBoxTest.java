package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bff.api.model.stream.AnomalyReportedStreamApiModel;
import com.example.bff.api.model.stream.DealClosedStreamApiModel;
import com.example.bff.api.model.stream.DealOpenedStreamApiModel;
import com.example.bff.api.model.stream.DealShutdownInitiatedStreamApiModel;
import com.example.bff.api.model.stream.HoldRaisedStreamApiModel;
import com.example.bff.api.model.stream.OrderDecidedStreamApiModel;
import com.example.bff.api.model.stream.StrategyActivatedStreamApiModel;
import com.example.bff.api.model.stream.StrategyLifecycleStreamApiModel;
import com.example.tradingbot.domain.event.CoreEventType;
import com.example.tradingbot.domain.event.StrategyEventType;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B7} документа кейсов: форма на проводе к браузеру
 * (.claude/tests/cases/bff.md §«B7 — Форма на проводе к браузеру»).
 *
 * <p><b>Ожидаемый состав записи — объявленная форма периметра, и читается
 * он с её объявления.</b> Форма своя у периметра и объявляется им самим
 * (docs/architecture/contracts.md §«Состав своей формы периметр объявляет
 * сам»): компоненты записи {@code *StreamApiModel} и есть обещание браузеру.
 * Какому классу события какая форма отвечает — тоже объявление периметра, и
 * оно стои́т здесь константой.
 *
 * <p><b>Содержимое, которое кладёт клетка, — ПОЛНАЯ форма владельца</b>
 * ({@link Bodies#fullMessage}): компоненты, не объявленные формой периметра,
 * в нём есть, и «не уехали» утверждается о решении формы, а не о бедности
 * входа.
 *
 * <p><b>Перечень классов выводится из перечней производителей</b>
 * ({@link CoreEventType}, {@link StrategyEventType}): списанный руками, он
 * устарел бы при первом новом классе.
 */
class StreamFormBoxTest extends SharedBffBox {

    /** Объявленная форма периметра по классу события. */
    private static final Map<String, Class<? extends Record>> FORMS = Map.of(
            "ORDER_DECIDED", OrderDecidedStreamApiModel.class,
            "DEAL_OPENED", DealOpenedStreamApiModel.class,
            "DEAL_SHUTDOWN_INITIATED", DealShutdownInitiatedStreamApiModel.class,
            "DEAL_CLOSED", DealClosedStreamApiModel.class,
            "HOLD_RAISED", HoldRaisedStreamApiModel.class,
            "ANOMALY_REPORTED", AnomalyReportedStreamApiModel.class,
            "STRATEGY_ACTIVATED", StrategyActivatedStreamApiModel.class,
            "STRATEGY_DEACTIVATED", StrategyLifecycleStreamApiModel.class,
            "STRATEGY_DELETED", StrategyLifecycleStreamApiModel.class);

    @Test
    @DisplayName("B7.1 — Доменного класса в проводе нет ни в одном поле")
    void b7_1_noDomainClassOnTheWire() {
        List<Subscription.Frame> frames = everyClassArrived("TF1", "b7-1");

        assertThat(frames).hasSize(FORMS.size());
        // У каждой записи содержимое — ровно объявленная форма периметра:
        // ни компонентов владельца сверх неё, ни производных полей,
        // порождённых предикатами доменной модели.
        assertThat(frames).allSatisfy(frame ->
                assertThat(contentOf(frame).keySet()).isEqualTo(declaredComponents(frame.type())));
    }

    @Test
    @DisplayName("B7.2 — Активация везёт идентичности и имя, а дерева определения не везёт")
    void b7_2_activationCarriesIdentitiesAndNameButNoTree() {
        String tenant = "TF2";
        String ticket = ticketOf(tenant);

        try (Subscription stream = openedStreamOf(tenant, ticket, "e-b7-2-open")) {
            wire.publish(Wire.STRATEGIES_TOPIC, tenant, "e-b7-2", "STRATEGY_ACTIVATED",
                    "2026-09-20T11:00:00Z", Bodies.strategyActivated("b7-2"));
            stream.awaitFrames(2);

            Subscription.Frame frame = stream.frames().get(1);
            // Идентичности — с ВЕРХНЕГО уровня содержимого (у снимка они
            // другие), имя — из снимка.
            assertThat(contentOf(frame))
                    .containsEntry("strategyInternalId", "S-b7-2")
                    .containsEntry("exchangeAccountInternalId", "EA-b7-2")
                    .containsEntry("instrumentInternalId", "I-b7-2")
                    .containsEntry("name", "Definition b7-2");
            // Самого дерева нет ни одного узла.
            assertThat(frame.data()).doesNotContain("nested", "details", "tranches",
                    "riskPerActionPercent", "targetRiskRewardRatio", "definition");
        }
    }

    @Test
    @DisplayName("B7.3 — Классы жизненного цикла определения едут одной формой")
    void b7_3_lifecycleClassesTravelInOneForm() {
        String tenant = "TF3";
        String ticket = ticketOf(tenant);

        try (Subscription stream = openedStreamOf(tenant, ticket, "e-b7-3-open")) {
            wire.publish(Wire.STRATEGIES_TOPIC, tenant, "e-b7-3-off", "STRATEGY_DEACTIVATED",
                    "2026-09-20T11:00:00Z", Bodies.fullMessage("STRATEGY_DEACTIVATED", "b7-3"));
            wire.publish(Wire.STRATEGIES_TOPIC, tenant, "e-b7-3-gone", "STRATEGY_DELETED",
                    "2026-09-20T11:00:01Z", Bodies.fullMessage("STRATEGY_DELETED", "b7-3"));
            stream.awaitFrames(3);

            Subscription.Frame deactivated = stream.frames().get(1);
            Subscription.Frame deleted = stream.frames().get(2);
            // Класс различается ИМЕНЕМ записи, а не формой содержимого.
            assertThat(deactivated.type()).isEqualTo("STRATEGY_DEACTIVATED");
            assertThat(deleted.type()).isEqualTo("STRATEGY_DELETED");
            assertThat(contentOf(deactivated)).isEqualTo(contentOf(deleted));
            assertThat(contentOf(deleted).keySet())
                    .isEqualTo(componentsOf(StrategyLifecycleStreamApiModel.class));
        }
    }

    @Test
    @DisplayName("B7.4 — Компонент, формой не объявленный, не едет")
    void b7_4_anUndeclaredComponentDoesNotTravel() {
        String tenant = "TF4";
        String ticket = ticketOf(tenant);

        try (Subscription stream = openedStreamOf(tenant, ticket, "e-b7-4-open")) {
            wire.publish(Wire.CORE_TOPIC, tenant, "e-b7-4", "DEAL_OPENED", "2026-09-20T11:00:00Z",
                    Bodies.fullMessage("DEAL_OPENED", "b7-4")
                            .replaceFirst("\\{", "{\"shadowComponent\": \"shadow-b7-4\", "));
            stream.awaitFrames(2);

            // Отказа разбора нет — запись доехала, а компонента в ней нет.
            Subscription.Frame frame = stream.frames().get(1);
            assertThat(frame.id()).isEqualTo("e-b7-4");
            assertThat(frame.data()).doesNotContain("shadowComponent", "shadow-b7-4");
            assertThat(contentOf(frame)).containsEntry("dealInternalId", "D-b7-4");
        }
    }

    @Test
    @DisplayName("B7.6 — Девять несомых классов переводятся восемью формами, и это не описка")
    void b7_6_nineClassesTravelInEightForms() {
        List<Subscription.Frame> frames = everyClassArrived("TF6", "b7-6");
        Set<String> producerClasses = Stream.concat(
                        Arrays.stream(CoreEventType.values()).map(Enum::name),
                        Arrays.stream(StrategyEventType.values()).map(Enum::name))
                .collect(Collectors.toSet());

        // Доезжают все девять, и класс записи — имя значения перечня.
        assertThat(frames.stream().map(Subscription.Frame::type))
                .containsExactlyInAnyOrderElementsOf(producerClasses);
        assertThat(producerClasses).hasSize(9);
        // Форм содержимого восемь: деактивация и удаление едут одной.
        assertThat(frames.stream().map(frame -> contentOf(frame).keySet()).distinct()).hasSize(8);
        // Пустым содержимым не приезжает ни одна.
        assertThat(frames).allSatisfy(frame -> assertThat(contentOf(frame)).isNotEmpty());
    }

    /**
     * Кладёт по записи каждого класса обоих перечней производителей и
     * отдаёт доехавшие записи — без барьерной.
     */
    private List<Subscription.Frame> everyClassArrived(String tenant, String mark) {
        String ticket = ticketOf(tenant);
        try (Subscription stream = openedStreamOf(tenant, ticket, "e-" + mark + "-open")) {
            Arrays.stream(CoreEventType.values()).map(Enum::name).forEach(type ->
                    wire.publish(Wire.CORE_TOPIC, tenant, "e-" + mark + "-" + type, type,
                            "2026-09-20T11:00:00Z", Bodies.fullMessage(type, mark)));
            Arrays.stream(StrategyEventType.values()).map(Enum::name).forEach(type ->
                    wire.publish(Wire.STRATEGIES_TOPIC, tenant, "e-" + mark + "-" + type, type,
                            "2026-09-20T11:00:00Z", Bodies.fullMessage(type, mark)));
            stream.awaitFrames(1 + FORMS.size());
            return stream.frames().subList(1, stream.frames().size());
        }
    }

    /** Билет субъекта клетки, чьё единственное членство — названный тенант. */
    private String ticketOf(String tenant) {
        authAnswers(Bodies.memberships(tenant, ROLE));
        return issuedTicket();
    }

    private static Set<String> declaredComponents(String eventType) {
        assertThat(FORMS).as("форма класса %s не объявлена", eventType).containsKey(eventType);
        return componentsOf(FORMS.get(eventType));
    }

    private static Set<String> componentsOf(Class<? extends Record> form) {
        return Arrays.stream(form.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }

    /** Содержимое записи в форме периметра. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> contentOf(Subscription.Frame frame) {
        return (Map<String, Object>) frame.content().get("content");
    }
}

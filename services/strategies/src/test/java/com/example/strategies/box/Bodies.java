package com.example.strategies.box;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Тела вызовов поверхности владельца определений — вход ящика.
 *
 * <p><b>Основа одна — эталон репозитория</b>
 * ({@code strategy-examples/trend-following-ema.json}, исполнимая форма —
 * docs/spec/strategy-reference.json). Дерево определения — сотни строк, и
 * собранное в тесте руками оно было бы ВТОРЫМ эталоном: разойдясь с
 * первым, оно меняло бы предмет каждой клетки создания молча.
 *
 * <p><b>Тела — строки, а не собранные api-модели.</b> Часть кейсов подаёт
 * форму, которой в моделях сервиса нет вовсе: поле вне контракта
 * ({@code tenantId}, {@code internalId} в теле), ОПУЩЕННОЕ обязательное
 * поле. Типизованная сборка такой вход выразить не даёт, а сборка из
 * модели сервиса к тому же брала бы его внутренность.
 *
 * <p><b>Правка идёт по ПРИЗНАКУ узла, а не по индексу пути.</b> Входное
 * действие опознаётся родом ордера — тем же признаком, которым его
 * опознаёт охрана создания; путь вида «деталь 0, транш 0, шаг 0» молча
 * разъехался бы с эталоном при первой его правке.
 */
final class Bodies {

    /** Эталонное определение репозитория: годное тело команды создания. */
    private static final String REFERENCE = "strategy-examples/trend-following-ema.json";

    /** Рода ордера, занимающие нотинал: ими опознаётся входное действие. */
    private static final List<String> ENTRY_ORDER_TYPES = List.of("ENTRY", "ENTRY_ATTACHED_STOP_LOSS");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Bodies() {
    }

    /** Эталон дословно. */
    static String reference() {
        return text(tree());
    }

    /**
     * Эталон с добавленным полем верхнего уровня: вход клеток о полях,
     * которых контракт команды не несёт.
     *
     * @param field имя поля
     * @param value его значение
     */
    static String referenceWith(String field, String value) {
        ObjectNode tree = tree();
        tree.put(field, value);
        return text(tree);
    }

    /**
     * Эталон, переставленный на другой инструмент того же счёта.
     *
     * <p>Радиус инварианта «одна активная» — ПАРА «счёт, инструмент», и
     * наблюдается он только определением, у которого совпадает счёт и
     * расходится инструмент.
     *
     * @param instrumentInternalId инструмент, на который переставляется определение
     */
    static String onInstrument(String instrumentInternalId) {
        ObjectNode tree = tree();
        tree.put("instrumentInternalId", instrumentInternalId);
        return text(tree);
    }

    /**
     * Эталон, у которого доля аллокации входного действия заменена
     * названным числом.
     *
     * @param allocationPercents доля аллокации входа в процентах
     */
    static String withEntryAllocation(BigDecimal allocationPercents) {
        ObjectNode tree = tree();
        forEachEntryAction(tree, action -> action.put("allocationPercents", allocationPercents));
        return text(tree);
    }

    /**
     * Эталон, у которого поле доли аллокации входного действия ОПУЩЕНО.
     *
     * <p>Отсутствие поля и его негодное значение — две разные проверки, и
     * подмена одного другим стёрла бы предмет клетки
     * (docs/rules/absent-value-semantics.md).
     */
    static String withoutEntryAllocation() {
        ObjectNode tree = tree();
        forEachEntryAction(tree, action -> action.remove("allocationPercents"));
        return text(tree);
    }

    /**
     * Эталон, у которого риск-число заменено у каждой ТОРГУЕМОЙ детали.
     *
     * <p>Неторгуемая деталь риск-чисел не несёт вовсе, и проставленное ей
     * число завело бы у клетки второй предмет.
     *
     * @param field имя риск-числа детали
     * @param value его значение
     */
    static String withDetailNumber(String field, BigDecimal value) {
        ObjectNode tree = tree();
        for (JsonNode detail : elementsOf(tree, "details")) {
            if (detail.hasNonNull(field)) {
                ((ObjectNode) detail).put(field, value);
            }
        }
        return text(tree);
    }

    /**
     * Эталон, у действия которого поле ОПУЩЕНО — на обоих уровнях
     * объявления: шагах траншей и агрегатных шагах детали.
     *
     * @param actionKey стабильный ключ действия
     * @param field     имя опускаемого поля
     */
    static String withoutActionField(String actionKey, String field) {
        ObjectNode tree = tree();
        for (JsonNode detail : elementsOf(tree, "details")) {
            List<JsonNode> byStatus = new ArrayList<>();
            elementsOf(detail, "tranches").forEach(tranche -> byStatus.add(tranche.get("stepsByStatus")));
            byStatus.add(detail.get("stepsByStatus"));
            byStatus.stream()
                    .filter(Objects::nonNull)
                    .forEach(steps -> steps.forEach(list -> list.forEach(step -> {
                        for (JsonNode action : elementsOf(step, "actions")) {
                            if (Objects.equals(actionKey, action.path("key").asText(null))) {
                                ((ObjectNode) action).remove(field);
                            }
                        }
                    })));
        }
        return text(tree);
    }

    private static void forEachEntryAction(ObjectNode tree, Consumer<ObjectNode> change) {
        for (JsonNode detail : elementsOf(tree, "details")) {
            for (JsonNode tranche : elementsOf(detail, "tranches")) {
                JsonNode stepsByStatus = tranche.get("stepsByStatus");
                if (Objects.isNull(stepsByStatus)) {
                    continue;
                }
                stepsByStatus.forEach(steps -> steps.forEach(step -> changeEntryActions(step, change)));
            }
        }
    }

    private static void changeEntryActions(JsonNode step, Consumer<ObjectNode> change) {
        for (JsonNode action : elementsOf(step, "actions")) {
            if (ENTRY_ORDER_TYPES.contains(action.path("orderType").asText(""))) {
                change.accept((ObjectNode) action);
            }
        }
    }

    /** Элементы массива по имени поля; пусто, когда поля нет. */
    private static Iterable<JsonNode> elementsOf(JsonNode node, String field) {
        JsonNode found = node.get(field);
        return found instanceof ArrayNode array ? array : MAPPER.createArrayNode();
    }

    private static ObjectNode tree() {
        try (InputStream source = Bodies.class.getClassLoader().getResourceAsStream(REFERENCE)) {
            if (Objects.isNull(source)) {
                throw new IllegalStateException("Эталона определения нет на пути прогона: " + REFERENCE);
            }
            return (ObjectNode) MAPPER.readTree(source);
        } catch (IOException failure) {
            throw new IllegalStateException("Эталон определения не прочитался: " + REFERENCE, failure);
        }
    }

    private static String text(ObjectNode tree) {
        try {
            return MAPPER.writeValueAsString(tree);
        } catch (IOException failure) {
            throw new IllegalStateException("Тело команды не собралось", failure);
        }
    }
}

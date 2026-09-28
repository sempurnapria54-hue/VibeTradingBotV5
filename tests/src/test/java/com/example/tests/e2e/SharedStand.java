package com.example.tests.e2e;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

/**
 * Держатель общего стенда сквозного набора: один живой стенд на JVM прогона и
 * группу классов (.claude/skills/test-code.md §«Уровень 3 — сквозной набор»).
 *
 * <p><b>Группа — пакет набора</b> ({@code strategytodeal}, {@code perimeterread},
 * {@code exitandclose}, {@code safetyteardown}): у пакета одна раскладка тропы,
 * и его классы идут подряд — порядок прогона алфавитный по полному имени
 * класса ({@code runOrder} в {@code tests/pom.xml}).
 *
 * <p><b>Стенд поднимается заново в двух случаях:</b> класс другой группы и
 * порча, найденная при отпускании ({@link Trail#spoilage()}). Сторону,
 * оставленную остановленной либо с перекрытым ключом, отпускание чинит её
 * перезапуском ({@link Trail#restoreSides()}). Иначе следующий класс получает
 * поднятый стенд переданным ({@link Trail#handOver()}): ходы классов
 * изолированы парой «тенант, счёт», а не пересозданием сторон.
 *
 * <p><b>Класс, отпуская стенд, снимает свои активные определения:</b> иначе
 * сканер следующего класса открыл бы по ним сделку посреди чужого кейса.
 *
 * <p><b>Подъёмы и портящие классы печатаются поимённо</b> — строкой
 * {@code [общий стенд]} в выводе прогона.
 */
public final class SharedStand {

    private static Trail held;

    private static String heldGroup;

    private static Integer raises = 0;

    private static String spoiler;

    private SharedStand() {
    }

    /**
     * Стенд тропы сделки для класса.
     *
     * @param owner класс, берущий стенд
     * @return поднятый либо переданный стенд
     */
    public static synchronized Trail dealPath(Class<?> owner) {
        return acquire(owner, Boolean.FALSE);
    }

    /**
     * Стенд тропы периметра для класса.
     *
     * @param owner класс, берущий стенд
     * @return поднятый либо переданный стенд
     */
    public static synchronized Trail perimeter(Class<?> owner) {
        return acquire(owner, Boolean.TRUE);
    }

    /**
     * Класс отпускает стенд: порча сверена, стороны с перекрытой конфигурацией
     * подняты заново, активные определения тенанта ходов сняты; испорченный
     * стенд закрыт, и следующий класс поднимет свой.
     *
     * @param owner класс, отпускающий стенд
     */
    public static synchronized void release(Class<?> owner) {
        if (isNull(held)) {
            return;
        }
        List<String> reasons = new ArrayList<>(held.spoilage());
        if (reasons.isEmpty()) {
            List<String> restored = held.restoreSides();
            if (isFalse(restored.isEmpty())) {
                System.out.println("[общий стенд] после " + owner.getSimpleName() + " подняты заново с конфигурацией"
                        + " подъёма: " + String.join("; ", restored));
            }
            try {
                held.retireActiveDefinitions();
            } catch (RuntimeException failure) {
                reasons.add("активные определения не сняты: " + failure.getMessage());
            }
        }
        if (isFalse(reasons.isEmpty())) {
            System.out.println("[общий стенд] " + owner.getSimpleName() + " испортил стенд " + held.name() + ": "
                    + String.join("; ", reasons));
            close();
            spoiler = owner.getSimpleName();
        }
    }

    private static Trail acquire(Class<?> owner, Boolean perimeter) {
        String group = owner.getPackageName() + (isTrue(perimeter) ? "/perimeter" : "/deal");
        if (nonNull(held) && Objects.equals(group, heldGroup)) {
            held.handOver();
            return held;
        }
        String cause = nonNull(held) ? "граница группы"
                : nonNull(spoiler) ? "стенд испорчен классом " + spoiler
                : "первый подъём";
        close();
        raises++;
        String name = owner.getPackageName().substring(owner.getPackageName().lastIndexOf('.') + 1) + "_" + raises;
        System.out.println("[общий стенд] подъём " + raises + ": " + name + " для " + owner.getSimpleName() + " — "
                + cause);
        held = isTrue(perimeter) ? Trail.openPerimeter(name) : Trail.open(name);
        heldGroup = group;
        spoiler = null;
        return held;
    }

    private static void close() {
        if (nonNull(held)) {
            held.close();
        }
        held = null;
        heldGroup = null;
    }
}

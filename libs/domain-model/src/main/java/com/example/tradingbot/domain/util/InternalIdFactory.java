package com.example.tradingbot.domain.util;

import java.security.SecureRandom;
import java.util.UUID;
import lombok.experimental.UtilityClass;

/**
 * Производство {@code internalId} — единственного идентификатора,
 * пересекающего границу сервиса.
 *
 * <p><b>Форм две, и различает их не тип сущности, а вопрос: уезжает ли она
 * на площадку</b> ({@code docs/architecture/data-ownership.md}
 * §Идентификаторы). Фабрика живёт здесь, а не у каждого сервиса, потому
 * что иначе одинаковость формы держалась бы на дисциплине восьми
 * сервисов, а знание о потолке поля площадки — на памяти того, кто пишет
 * исполнителя заявки.
 */
@UtilityClass
public class InternalIdFactory {

    /**
     * Маркер контура в клиентском идентификаторе заявки. Дом правила —
     * {@code docs/integrations/okx/rules/client-id-marker.md}: маркер
     * делает нашу заявку опознаваемой на стороне площадки.
     */
    private static final String CONTOUR_MARKER = "vtb";

    /**
     * Потолок поля клиентского идентификатора у источника. Именно из-за
     * него уезжающая форма не может быть UUID: строка UUID — 36 символов,
     * с маркером 39, а поместиться обязаны 32.
     */
    private static final int EXCHANGE_ID_LIMIT = 32;

    /** Алфавит случайной части: только то, что площадка принимает без оговорок. */
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Идентификатор внутренней сущности — тенанта, сделки, транша,
     * стратегии, отчёта аномалии.
     *
     * <p>Опознавать её вовне некому, поэтому здесь максимум энтропии и
     * ничего не стоит.
     */
    public static String forInternalEntity() {
        return UUID.randomUUID().toString();
    }

    /**
     * Идентификатор сущности, уезжающей на площадку, — заявки,
     * algo-заявки, встроенной защиты.
     *
     * <p>Форму диктует площадка, а не мы: маркер контура плюс случайная
     * часть, вместе не длиннее потолка поля источника. Случайная часть
     * занимает весь остаток потолка — обрезать её сверх необходимого
     * значило бы отдавать энтропию даром.
     */
    public static String forExchangeBoundEntity() {
        StringBuilder id = new StringBuilder(EXCHANGE_ID_LIMIT).append(CONTOUR_MARKER);
        while (id.length() < EXCHANGE_ID_LIMIT) {
            id.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return id.toString();
    }
}

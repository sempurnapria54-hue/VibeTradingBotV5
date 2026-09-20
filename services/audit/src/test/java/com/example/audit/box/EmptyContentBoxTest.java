package com.example.audit.box;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.6}, второе звено — тело, заданное пустой строкой
 * (.claude/tests/cases/audit.md).
 *
 * <p><b>Исход совпал с первым звеном, а звено другое, и это не
 * педантизм.</b> Предикат полноты пустую строку ПРОПУСКАЕТ — значение
 * непусто как ссылка, — и останавливает приём приведение к документу на
 * стороне базы. Ровно этот зазор и есть находка {@code F-1}: обязательность
 * входа мерится ненулевой ссылкой, а не непустым значением, и там, где
 * колонка типа не отвергает, исхода не остаётся вовсе
 * ({@link EmptyMandatoryValuesBoxTest}).
 *
 * <p><b>Свой класс у звена потому, что оно — второе отравленное
 * сообщение</b>, а два таких в одном контексте не уживаются
 * ({@link PoisonedReceptionBox}).
 *
 * <p>Исход общий всей группе, и он собран в базовом классе
 * ({@link PoisonedReceptionBox#assertReceptionHalted()}): строки журнала
 * нет, смещение группы не продвинулось, флаг остановки лежит у своей пары,
 * непрерывность не утверждаема, наружу не ушло ничего.
 */
class EmptyContentBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, "b2-6b", Map.of());
    }

    @Test
    @DisplayName("B2.6 (второе звено) — Содержимое пустой строкой")
    void anEmptyBodyStopsReceptionAtTheDatabaseInstead() {
        givenReceptionStateRows();

        poison(envelope(POISON_EVENT, occurredAt), TENANT, "");

        assertReceptionHalted();
    }
}

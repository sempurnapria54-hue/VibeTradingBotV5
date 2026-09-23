package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Клетка {@code B10.1} — незаданная точка провайдера идентичности контекст
 * не поднимает (.claude/tests/cases/bff.md §«B10 — Конфигурация как вход»).
 *
 * <p><b>Без {@code @SpringBootTest}, и это следствие предмета:</b> ожидание
 * клетки есть НЕподъём контекста, а поднятый контекст есть предусловие
 * всякого кейса ящика. Поэтому подъём здесь — вход, и производит его сам
 * кейс той же формой, что и реплики ящика ({@link Replica#launch}).
 *
 * <p><b>Пусто ровно ОДНО, и прочие оси настроены:</b> иначе отказ подъёма
 * сошёлся бы по соседнему поводу — адресу брокера, секрету билета — и
 * клетка предъявляла бы не свой предмет.
 *
 * <p><b>«Поверхность не открыта ни на одном пути» предъявляется САМИМ
 * отказом подъёма:</b> процесса, у которого можно было бы спросить путь, не
 * возникает, и вызов по порту упавшего процесса мерил бы отсутствие
 * слушателя сокета, а не закрытость поверхности.
 */
class UnconfiguredAccessContourTest {

    @Test
    @DisplayName("B10.1 — Незаданная точка провайдера идентичности контекст не поднимает")
    void b10_1_anUnsetIdentityProviderPointRaisesNoContext() {
        assertThatThrownBy(() -> Replica.launch(Map.of(BffSubstrate.ISSUER_KEY, "")).close())
                .as("пустое означает, что контур не настроен, и это отказ при старте, а не открытая поверхность")
                .isInstanceOf(Exception.class);
    }
}

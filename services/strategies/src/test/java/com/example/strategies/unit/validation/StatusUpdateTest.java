package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.statusRequest;
import static com.example.strategies.unit.validation.ValidationFixture.validator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Третья точка входа: разбор целевого статуса — группа {@code U31}
 * документа `.claude/tests/cases/strategy-definition-validation.md` (дом
 * — docs/lifecycles/Strategy.md; перечень статусов —
 * docs/models/domain/aggregate/Strategy.md §«Strategy (корень)»).
 *
 * <p><b>Здесь проверяется только РАЗБОР целевого значения.</b>
 * Допустимость самого перехода мерит владелец строки над базой, и её
 * отсутствие в этой точке — утверждение о предмете, а не пропуск кейса.
 *
 * <p><b>Начальный статус системный:</b> он принадлежит перечню и
 * отвергается отдельной клаузой — разводит их только она.
 */
class StatusUpdateTest {

    private static final String REJECTION = "Target status must be one of ACTIVE/INACTIVE/DELETED";

    @Test
    @DisplayName("U31.1 — целевой статус «активна»: возвращается значение перечня")
    void u31_1_theActiveStatusParses() {
        assertThat(validator().validateStatusUpdate(statusRequest("ACTIVE")))
                .isEqualTo(Strategy.Status.ACTIVE);
    }

    @Test
    @DisplayName("U31.2 — целевой статус «неактивна»")
    void u31_2_theInactiveStatusParses() {
        assertThat(validator().validateStatusUpdate(statusRequest("INACTIVE")))
                .isEqualTo(Strategy.Status.INACTIVE);
    }

    @Test
    @DisplayName("U31.3 — целевой статус «удалена»")
    void u31_3_theDeletedStatusParses() {
        assertThat(validator().validateStatusUpdate(statusRequest("DELETED")))
                .isEqualTo(Strategy.Status.DELETED);
    }

    @Test
    @DisplayName("U31.4 — целевой статус «создана»: начальный статус системный и руками не ставится")
    void u31_4_theInitialStatusIsRejectedThoughItBelongsToTheEnum() {
        assertThatThrownBy(() -> validator().validateStatusUpdate(statusRequest("CREATED")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(REJECTION);
    }

    @Test
    @DisplayName("U31.5 — целевой статус — неизвестная строка: в тексте перечень допустимых")
    void u31_5_anUnknownStatusIsRejectedWithTheAllowedSet() {
        assertThatThrownBy(() -> validator().validateStatusUpdate(statusRequest("ARCHIVED")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(REJECTION)
                .hasMessageContaining("ARCHIVED");
    }

    @Test
    @DisplayName("U31.6 — целевой статус опущен: пустое значение перечню не принадлежит")
    void u31_6_anAbsentStatusIsRejectedToo() {
        assertThatThrownBy(() -> validator().validateStatusUpdate(statusRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(REJECTION);
    }

    @Test
    @DisplayName("U31.7 — целевой статус в нижнем регистре: сверка чувствительна к регистру")
    void u31_7_theStatusMatchIsCaseSensitive() {
        assertThatThrownBy(() -> validator().validateStatusUpdate(statusRequest("active")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(REJECTION);
    }

    @Test
    @DisplayName("U31.8 — статус отказа 400: допустимость ПЕРЕХОДА здесь не мерится вовсе")
    void u31_8_theRejectionStatusIsBadRequest() {
        ResponseStatusException rejected = rejectionOf("ARCHIVED");

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(validator().validateStatusUpdate(statusRequest("DELETED")))
                .as("переход из любого состояния здесь проходит: матрицу держит владелец строки")
                .isEqualTo(Strategy.Status.DELETED);
    }

    private ResponseStatusException rejectionOf(String status) {
        try {
            validator().validateStatusUpdate(statusRequest(status));
            throw new IllegalStateException("отказа нет — кейс мерил бы пустоту");
        } catch (ResponseStatusException rejected) {
            return rejected;
        }
    }
}

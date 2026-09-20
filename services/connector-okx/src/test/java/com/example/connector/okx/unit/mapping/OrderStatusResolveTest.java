package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.connector.okx.exception.ExternalStatusException;
import com.example.connector.okx.resolve.OkxOrderExternalStatusResolver;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import com.example.tradingbot.domain.resolve.StatusResolveResult;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Резолв статуса обычной заявки — группа `U28` документа
 * `.claude/tests/cases/okx-mapping.md`
 * (docs/spec/external-status-resolution.json, величины
 * `orderStatus`, `refusalReason`, `closeReasonCandidate`;
 * docs/rules/external-status-resolution.md).
 *
 * <p><b>Базовая сборка:</b> резолвер собран конструктором; на вход —
 * сырое значение статуса площадки строкой. Выход — пара «доменный
 * статус × причина закрытия» либо контролируемый отказ.
 *
 * <p><b>Перечень значений закрыт, и «иначе» у него ОТКАЗ, а не
 * благоприятное умолчание:</b> ошибочное состояние результатом перевода
 * не бывает (docs/rules/absent-value-semantics.md §«Благоприятное
 * умолчание запрещено»).
 *
 * <p><b>Причину отмены резолвер не ставит, и это разделение, а не
 * пропуск:</b> операнда намерения у него в сигнатуре нет вовсе, и
 * подстановку делает исполнитель ядра — предмет `trading-core-fsm`.
 */
class OrderStatusResolveTest {

    private final OkxOrderExternalStatusResolver resolver = new OkxOrderExternalStatusResolver();

    @Test
    @DisplayName("U28.1 — живая заявка: активна, причины нет")
    void u28_1_aLiveOrderIsActive() {
        StatusResolveResult<Order.Status, Order.CloseReason> resolved = resolver.resolve("live");

        assertThat(resolved.getStatus()).isEqualTo(Order.Status.ACTIVE);
        assertThat(resolved.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U28.2 — частично исполненная: частичное завершение, причины нет")
    void u28_2_aPartiallyFilledOrderIsPartiallyCompleted() {
        StatusResolveResult<Order.Status, Order.CloseReason> resolved =
                resolver.resolve("partially_filled");

        assertThat(resolved.getStatus()).isEqualTo(Order.Status.PARTIALLY_COMPLETED);
        assertThat(resolved.getCloseReason()).isNull();
    }

    /** Единственное значение, которое резолвер ставит сам. */
    @Test
    @DisplayName("U28.3 — исполненная: завершена с причиной исполнения")
    void u28_3_aFilledOrderCarriesItsOnlyReason() {
        StatusResolveResult<Order.Status, Order.CloseReason> resolved = resolver.resolve("filled");

        assertThat(resolved.getStatus()).isEqualTo(Order.Status.COMPLETED);
        assertThat(resolved.getCloseReason()).isEqualTo(Order.CloseReason.FILLED);
    }

    /** Причина контекстно-зависима и приходит из намерения. */
    @Test
    @DisplayName("U28.4 — снятая: отменена, причины нет")
    void u28_4_aCanceledOrderCarriesNoReason() {
        StatusResolveResult<Order.Status, Order.CloseReason> resolved = resolver.resolve("canceled");

        assertThat(resolved.getStatus()).isEqualTo(Order.Status.CANCELED);
        assertThat(resolved.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U28.5 — снятая защитным механизмом площадки резолвится как обычная")
    void u28_5_anMmpCancellationIsAnOrdinaryCancellation() {
        StatusResolveResult<Order.Status, Order.CloseReason> resolved = resolver.resolve("mmp_canceled");

        assertThat(resolved.getStatus()).isEqualTo(Order.Status.CANCELED);
        assertThat(resolved.getCloseReason()).isNull();
    }

    /** Эти значения живут только в перечне условной заявки. */
    @ParameterizedTest
    @ValueSource(strings = {"order_failed", "partially_failed", "pause"})
    @DisplayName("U28.6-U28.8 — значения словаря условной заявки у обычной отказывают")
    void u28_6_to_8_theAlgoDictionaryIsForeignHere(String externalStatus) {
        assertThatThrownBy(() -> resolver.resolve(externalStatus))
                .isInstanceOf(ExternalStatusException.class)
                .extracting(failure -> ((ExternalStatusException) failure).getReasonCode())
                .isEqualTo(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS);
    }

    /** Словарь площадки строчный, регистр не подбирается. */
    @Test
    @DisplayName("U28.9 — верхний регистр статуса отказывает")
    void u28_9_theStatusCaseIsNotGuessed() {
        assertThatThrownBy(() -> resolver.resolve("LIVE"))
                .isInstanceOf(ExternalStatusException.class);
    }

    /** Пустой статус есть присутствующее значение вне перечня. */
    @Test
    @DisplayName("U28.10 — пустой статус отказывает")
    void u28_10_anEmptyStatusRefuses() {
        assertThatThrownBy(() -> resolver.resolve(""))
                .isInstanceOf(ExternalStatusException.class)
                .extracting(failure -> ((ExternalStatusException) failure).getReasonCode())
                .isEqualTo(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS);
    }

    /** Названное свойство: резолвер зовётся на добытом снапшоте, у которого сырой статус есть. */
    @Test
    @DisplayName("U28.11 — охраны пустого входа у резолвера нет")
    void u28_11_thereIsNoGuardForEmptiness() {
        assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(NullPointerException.class);
    }

    /** Полей у класса нет: состояния резолвер не держит. */
    @Test
    @DisplayName("U28.12 — два разбора подряд на одном экземпляре независимы")
    void u28_12_theResolverIsStateless() {
        assertThat(resolver.resolve("live").getStatus()).isEqualTo(Order.Status.ACTIVE);
        assertThat(resolver.resolve("filled").getStatus()).isEqualTo(Order.Status.COMPLETED);
        assertThat(resolver.resolve("live").getCloseReason()).isNull();
        assertThat(OkxOrderExternalStatusResolver.class.getDeclaredFields())
                .allSatisfy(field -> assertThat(java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                        .isTrue());
    }

    /** Отказ выражается броском, а не значением перечня. */
    @Test
    @DisplayName("U28.13 — значения отказа в доменном перечне статусов нет вовсе")
    void u28_13_refusalIsNotAStatusValue() {
        assertThat(Arrays.stream(Order.Status.values()).map(Enum::name).toList())
                .doesNotContain("REFUSED");
        assertThat(java.util.stream.Stream
                .of("live", "partially_filled", "filled", "canceled", "mmp_canceled")
                .map(resolver::resolve)
                .map(StatusResolveResult::getStatus))
                .containsExactly(Order.Status.ACTIVE, Order.Status.PARTIALLY_COMPLETED,
                        Order.Status.COMPLETED, Order.Status.CANCELED, Order.Status.CANCELED);
    }
}

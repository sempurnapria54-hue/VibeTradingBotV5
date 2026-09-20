package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.connector.okx.exception.ExternalStatusException;
import com.example.connector.okx.mapping.OrderMapper;
import com.example.connector.okx.resolve.OkxAlgoOrderExternalStatusResolver;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import com.example.tradingbot.domain.resolve.StatusResolveResult;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Резолв статуса условной заявки — группа `U29` документа
 * `.claude/tests/cases/okx-mapping.md`
 * (docs/spec/external-status-resolution.json, величина `algoStatus`;
 * docs/models/mapping/AlgoOrder.md §«Резолв статуса»).
 *
 * <p><b>Базовая сборка:</b> та же, что у `U28`; резолвер свой.
 *
 * <p><b>Отличий от обычной заявки три, и каждое объявлено:</b>
 * приостановленная считается активной — она ещё существует и влияет на
 * риск; сработавшая получает причину срабатывания, а не «исполнена»; у
 * двух отказных состояний есть свои причины отказа, и живут они только
 * в её перечне.
 */
class AlgoOrderStatusResolveTest {

    private final OkxAlgoOrderExternalStatusResolver resolver = new OkxAlgoOrderExternalStatusResolver();

    @Test
    @DisplayName("U29.1 — живая: активна, причины нет")
    void u29_1_aLiveAlgoOrderIsActive() {
        StatusResolveResult<AlgoOrder.Status, AlgoOrder.CloseReason> resolved = resolver.resolve("live");

        assertThat(resolved.getStatus()).isEqualTo(AlgoOrder.Status.ACTIVE);
        assertThat(resolved.getCloseReason()).isNull();
    }

    /** Приостановленная защита влияет на риск: трактовка её снятой обнулила бы покрытие. */
    @Test
    @DisplayName("U29.2 — приостановленная считается активной")
    void u29_2_aPausedAlgoOrderIsStillActive() {
        StatusResolveResult<AlgoOrder.Status, AlgoOrder.CloseReason> resolved = resolver.resolve("pause");

        assertThat(resolved.getStatus()).isEqualTo(AlgoOrder.Status.ACTIVE);
        assertThat(resolved.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U29.3 — частично сработавшая: частичное завершение")
    void u29_3_aPartiallyEffectiveAlgoOrderIsPartiallyCompleted() {
        StatusResolveResult<AlgoOrder.Status, AlgoOrder.CloseReason> resolved =
                resolver.resolve("partially_effective");

        assertThat(resolved.getStatus()).isEqualTo(AlgoOrder.Status.PARTIALLY_COMPLETED);
        assertThat(resolved.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U29.4 — сработавшая: завершена с причиной срабатывания")
    void u29_4_anEffectiveAlgoOrderIsTriggered() {
        StatusResolveResult<AlgoOrder.Status, AlgoOrder.CloseReason> resolved =
                resolver.resolve("effective");

        assertThat(resolved.getStatus()).isEqualTo(AlgoOrder.Status.COMPLETED);
        assertThat(resolved.getCloseReason()).isEqualTo(AlgoOrder.CloseReason.TRIGGERED);
    }

    @Test
    @DisplayName("U29.5 — снятая: причина приходит из намерения")
    void u29_5_aCanceledAlgoOrderCarriesNoReason() {
        StatusResolveResult<AlgoOrder.Status, AlgoOrder.CloseReason> resolved =
                resolver.resolve("canceled");

        assertThat(resolved.getStatus()).isEqualTo(AlgoOrder.Status.CANCELED);
        assertThat(resolved.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U29.6 — отказ постановки: своя причина отказа")
    void u29_6_anOrderFailureCarriesItsOwnReason() {
        assertThatThrownBy(() -> resolver.resolve("order_failed"))
                .isInstanceOf(ExternalStatusException.class)
                .extracting(failure -> ((ExternalStatusException) failure).getReasonCode())
                .isEqualTo(ExternalStatusReason.ORDER_FAILED);
    }

    @Test
    @DisplayName("U29.7 — частичный отказ: своя причина отказа")
    void u29_7_aPartialFailureCarriesItsOwnReason() {
        assertThatThrownBy(() -> resolver.resolve("partially_failed"))
                .isInstanceOf(ExternalStatusException.class)
                .extracting(failure -> ((ExternalStatusException) failure).getReasonCode())
                .isEqualTo(ExternalStatusReason.PARTIALLY_FAILED);
    }

    @Test
    @DisplayName("U29.8 — неизвестное значение отказывает общей причиной")
    void u29_8_anUnknownValueRefusesWithTheCommonReason() {
        assertThatThrownBy(() -> resolver.resolve("unknown"))
                .isInstanceOf(ExternalStatusException.class)
                .extracting(failure -> ((ExternalStatusException) failure).getReasonCode())
                .isEqualTo(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS);
    }

    /** Асимметрия со словарём обычной заявки полная и намеренная. */
    @Test
    @DisplayName("U29.9 — значение словаря обычной заявки у условной отказывает")
    void u29_9_theOrdinaryDictionaryIsForeignHere() {
        assertThatThrownBy(() -> resolver.resolve("filled"))
                .isInstanceOf(ExternalStatusException.class)
                .extracting(failure -> ((ExternalStatusException) failure).getReasonCode())
                .isEqualTo(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS);
    }

    @Test
    @DisplayName("U29.10 — пустой статус отказывает")
    void u29_10_anEmptyStatusRefuses() {
        assertThatThrownBy(() -> resolver.resolve(""))
                .isInstanceOf(ExternalStatusException.class)
                .extracting(failure -> ((ExternalStatusException) failure).getReasonCode())
                .isEqualTo(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS);
    }

    @Test
    @DisplayName("U29.11 — охраны пустого входа нет по тому же доводу")
    void u29_11_thereIsNoGuardForEmptiness() {
        assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(NullPointerException.class);
    }

    /**
     * Исход кодирует нога, нашедшая запись, а сырой статус остаётся диагностикой:
     * переход в снапшот встроенной защиты резолвера не зовёт и статус несёт строкой.
     */
    @Test
    @DisplayName("U29.12 — к записи цикла добычи встроенной защиты резолвер не применяется")
    void u29_12_theResolverIsNotAppliedToTheAttachedProtectionRecord() {
        OrderMapper orderMapper = Mappers.order();
        var source = OkxFixture.algoOrder();
        source.setState("effective");

        var snapshot = orderMapper.integrationToSnapshot(source);

        assertThat(snapshot.getExternalStatus()).isEqualTo("effective");
        assertThat(com.example.connector.okx.snapshot.AttachedAlgoOrderExternalSnapshot.class
                .getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("status", "closeReason");
    }
}

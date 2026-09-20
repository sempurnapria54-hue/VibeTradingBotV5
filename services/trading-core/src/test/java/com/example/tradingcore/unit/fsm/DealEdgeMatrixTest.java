package com.example.tradingcore.unit.fsm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.fsm.DealTransitionGate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Матрица объявленных рёбер сделки — группа {@code U1} документа
 * `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/spec/deal-lifecycle.json, величина {@code edgeDeclared}; прозой —
 * docs/lifecycles/Deal.md §«Инварианты переходов»).
 *
 * <p><b>Базовая сборка.</b> Гейт сконструирован с настоящим гейтом
 * терминала; операнды пары — два значения статуса, состояние сделки
 * предикат ребра не читает.
 */
class DealEdgeMatrixTest {

    private final DealTransitionGate gate = new DealTransitionGate(new DealTerminalGate());

    @Test
    @DisplayName("U1.1 — ACTIVE → EXIT_PENDING: ребро объявлено")
    void u1_1_activeToExitPendingIsDeclared() {
        assertThat(gate.edgeDeclared(Deal.Status.ACTIVE, Deal.Status.EXIT_PENDING)).isTrue();
    }

    @Test
    @DisplayName("U1.2 — ACTIVE → CLOSED: ребро объявлено")
    void u1_2_activeToClosedIsDeclared() {
        assertThat(gate.edgeDeclared(Deal.Status.ACTIVE, Deal.Status.CLOSED)).isTrue();
    }

    @Test
    @DisplayName("U1.3 — ACTIVE → ERROR: ребро объявлено")
    void u1_3_activeToErrorIsDeclared() {
        assertThat(gate.edgeDeclared(Deal.Status.ACTIVE, Deal.Status.ERROR)).isTrue();
    }

    @Test
    @DisplayName("U1.4 — EXIT_PENDING → CLOSED: ребро объявлено")
    void u1_4_exitPendingToClosedIsDeclared() {
        assertThat(gate.edgeDeclared(Deal.Status.EXIT_PENDING, Deal.Status.CLOSED)).isTrue();
    }

    @Test
    @DisplayName("U1.5 — EXIT_PENDING → ERROR: ребро объявлено")
    void u1_5_exitPendingToErrorIsDeclared() {
        assertThat(gate.edgeDeclared(Deal.Status.EXIT_PENDING, Deal.Status.ERROR)).isTrue();
    }

    @Test
    @DisplayName("U1.6 — ERROR → EMERGENCY_CLOSED: ребро объявлено")
    void u1_6_errorToEmergencyClosedIsDeclared() {
        assertThat(gate.edgeDeclared(Deal.Status.ERROR, Deal.Status.EMERGENCY_CLOSED)).isTrue();
    }

    @Test
    @DisplayName("U1.7 — ERROR → CLOSED: из ошибочного состояния в штатный терминал ребра нет")
    void u1_7_errorToCleanTerminalIsNotDeclared() {
        assertThat(gate.edgeDeclared(Deal.Status.ERROR, Deal.Status.CLOSED)).isFalse();
    }

    @Test
    @DisplayName("U1.8 — EXIT_PENDING → EMERGENCY_CLOSED: аварийный терминал достижим только из ошибочного")
    void u1_8_exitPendingToEmergencyClosedIsNotDeclared() {
        assertThat(gate.edgeDeclared(Deal.Status.EXIT_PENDING, Deal.Status.EMERGENCY_CLOSED)).isFalse();
    }

    @Test
    @DisplayName("U1.9 — ACTIVE → EMERGENCY_CLOSED: ребро не объявлено")
    void u1_9_activeToEmergencyClosedIsNotDeclared() {
        assertThat(gate.edgeDeclared(Deal.Status.ACTIVE, Deal.Status.EMERGENCY_CLOSED)).isFalse();
    }

    @Test
    @DisplayName("U1.10 — CLOSED → любой: терминал исходящих рёбер не имеет")
    void u1_10_cleanTerminalHasNoOutgoingEdges() {
        for (Deal.Status target : Deal.Status.values()) {
            assertThat(gate.edgeDeclared(Deal.Status.CLOSED, target)).isFalse();
        }
    }

    @Test
    @DisplayName("U1.11 — EMERGENCY_CLOSED → любой: исходящих рёбер нет")
    void u1_11_emergencyTerminalHasNoOutgoingEdges() {
        for (Deal.Status target : Deal.Status.values()) {
            assertThat(gate.edgeDeclared(Deal.Status.EMERGENCY_CLOSED, target)).isFalse();
        }
    }

    @Test
    @DisplayName("U1.12 — ACTIVE → ACTIVE: петли на себя в матрице нет")
    void u1_12_selfLoopIsNotDeclared() {
        assertThat(gate.edgeDeclared(Deal.Status.ACTIVE, Deal.Status.ACTIVE)).isFalse();
    }

    @Test
    @DisplayName("U1.13 — исходный статус пуст: ребро не объявлено, исключения нет")
    void u1_13_anAbsentSourceStatusIsTotal() {
        assertThat(gate.edgeDeclared(null, Deal.Status.CLOSED)).isFalse();
    }

    @Test
    @DisplayName("U1.14 — целевой статус пуст: ребро не объявлено, отказа нет")
    void u1_14_anAbsentTargetStatusIsTotal() {
        assertThat(gate.edgeDeclared(Deal.Status.ACTIVE, null)).isFalse();
    }

    @Test
    @DisplayName("U1.15 — перечень значений обойдён целиком: объявленных рёбер ровно шесть")
    void u1_15_theWholeMatrixDeclaresExactlySixEdges() {
        List<String> declared = new ArrayList<>();
        for (Deal.Status from : Deal.Status.values()) {
            for (Deal.Status to : Deal.Status.values()) {
                if (Boolean.TRUE.equals(gate.edgeDeclared(from, to))) {
                    declared.add(from + "->" + to);
                }
            }
        }

        assertThat(declared).containsExactlyInAnyOrder(
                "ACTIVE->EXIT_PENDING", "ACTIVE->CLOSED", "ACTIVE->ERROR",
                "EXIT_PENDING->CLOSED", "EXIT_PENDING->ERROR",
                "ERROR->EMERGENCY_CLOSED");
    }
}

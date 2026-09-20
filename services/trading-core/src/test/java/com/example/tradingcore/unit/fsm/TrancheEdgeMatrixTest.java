package com.example.tradingcore.unit.fsm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.fsm.TrancheTransitionGate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Матрица объявленных рёбер транша — группа {@code U12} документа
 * `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/spec/deal-tranche-lifecycle.json, величина
 * {@code trancheEdgeDeclared}; прозой — docs/lifecycles/DealTranche.md
 * §«Матрица переходов»).
 *
 * <p><b>Базовая сборка.</b> Гейт сконструирован без коллабораторов;
 * операнды — два значения статуса транша.
 */
class TrancheEdgeMatrixTest {

    private final TrancheTransitionGate gate = new TrancheTransitionGate();

    @Test
    @DisplayName("U12.1 — PRECHECK → ENTRY_SUBMITTED: объявлено")
    void u12_1_precheckToEntrySubmittedIsDeclared() {
        assertDeclared(DealTranche.Status.PRECHECK, DealTranche.Status.ENTRY_SUBMITTED);
    }

    @Test
    @DisplayName("U12.2 — PRECHECK → CLOSED: объявлено")
    void u12_2_precheckToClosedIsDeclared() {
        assertDeclared(DealTranche.Status.PRECHECK, DealTranche.Status.CLOSED);
    }

    @Test
    @DisplayName("U12.3 — ENTRY_SUBMITTED → ENTRY_FINALIZED: объявлено")
    void u12_3_entrySubmittedToEntryFinalizedIsDeclared() {
        assertDeclared(DealTranche.Status.ENTRY_SUBMITTED, DealTranche.Status.ENTRY_FINALIZED);
    }

    @Test
    @DisplayName("U12.4 — ENTRY_SUBMITTED → EXIT_PENDING: объявлено")
    void u12_4_entrySubmittedToExitPendingIsDeclared() {
        assertDeclared(DealTranche.Status.ENTRY_SUBMITTED, DealTranche.Status.EXIT_PENDING);
    }

    @Test
    @DisplayName("U12.5 — ENTRY_SUBMITTED → CLOSED: объявлено")
    void u12_5_entrySubmittedToClosedIsDeclared() {
        assertDeclared(DealTranche.Status.ENTRY_SUBMITTED, DealTranche.Status.CLOSED);
    }

    @Test
    @DisplayName("U12.6 — ENTRY_FINALIZED → PROTECTION_SWITCHED: объявлено")
    void u12_6_entryFinalizedToProtectionSwitchedIsDeclared() {
        assertDeclared(DealTranche.Status.ENTRY_FINALIZED, DealTranche.Status.PROTECTION_SWITCHED);
    }

    @Test
    @DisplayName("U12.7 — ENTRY_FINALIZED → MANAGING: объявлено")
    void u12_7_entryFinalizedToManagingIsDeclared() {
        assertDeclared(DealTranche.Status.ENTRY_FINALIZED, DealTranche.Status.MANAGING);
    }

    @Test
    @DisplayName("U12.8 — PROTECTION_SWITCHED → MANAGING: объявлено")
    void u12_8_protectionSwitchedToManagingIsDeclared() {
        assertDeclared(DealTranche.Status.PROTECTION_SWITCHED, DealTranche.Status.MANAGING);
    }

    @Test
    @DisplayName("U12.9 — MANAGING → ENTRY_SUBMITTED: объявлено, это ребро переоткрытия")
    void u12_9_managingToEntrySubmittedIsTheReopenEdge() {
        assertDeclared(DealTranche.Status.MANAGING, DealTranche.Status.ENTRY_SUBMITTED);
        assertThat(gate.reopenEdge(DealTranche.Status.MANAGING, DealTranche.Status.ENTRY_SUBMITTED)).isTrue();
    }

    @Test
    @DisplayName("U12.10 — MANAGING → EXIT_PENDING: объявлено")
    void u12_10_managingToExitPendingIsDeclared() {
        assertDeclared(DealTranche.Status.MANAGING, DealTranche.Status.EXIT_PENDING);
    }

    @Test
    @DisplayName("U12.11 — EXIT_PENDING → CLOSED: объявлено")
    void u12_11_exitPendingToClosedIsDeclared() {
        assertDeclared(DealTranche.Status.EXIT_PENDING, DealTranche.Status.CLOSED);
    }

    @Test
    @DisplayName("U12.12 — PRECHECK → MANAGING: не объявлено")
    void u12_12_precheckToManagingIsNotDeclared() {
        assertNotDeclared(DealTranche.Status.PRECHECK, DealTranche.Status.MANAGING);
    }

    @Test
    @DisplayName("U12.13 — ENTRY_FINALIZED → EXIT_PENDING: выход идёт через сопровождение")
    void u12_13_entryFinalizedToExitPendingIsNotDeclared() {
        assertNotDeclared(DealTranche.Status.ENTRY_FINALIZED, DealTranche.Status.EXIT_PENDING);
    }

    @Test
    @DisplayName("U12.14 — MANAGING → CLOSED: терминал достижим только из выхода либо ранних статусов")
    void u12_14_managingToClosedIsNotDeclared() {
        assertNotDeclared(DealTranche.Status.MANAGING, DealTranche.Status.CLOSED);
    }

    @Test
    @DisplayName("U12.15 — PROTECTION_SWITCHED → EXIT_PENDING: не объявлено")
    void u12_15_protectionSwitchedToExitPendingIsNotDeclared() {
        assertNotDeclared(DealTranche.Status.PROTECTION_SWITCHED, DealTranche.Status.EXIT_PENDING);
    }

    @Test
    @DisplayName("U12.16 — CLOSED → любой: исходящих рёбер у терминала нет")
    void u12_16_theTerminalHasNoOutgoingEdges() {
        for (DealTranche.Status target : DealTranche.Status.values()) {
            assertNotDeclared(DealTranche.Status.CLOSED, target);
        }
    }

    @Test
    @DisplayName("U12.17 — исходный либо целевой статус пуст: исключения нет")
    void u12_17_anAbsentStatusIsTotal() {
        assertThat(gate.edgeDeclared(null, DealTranche.Status.CLOSED)).isFalse();
        assertThat(gate.edgeDeclared(DealTranche.Status.MANAGING, null)).isFalse();
    }

    @Test
    @DisplayName("U12.18 — перечень обойдён целиком: объявленных рёбер ровно одиннадцать")
    void u12_18_theWholeMatrixDeclaresExactlyElevenEdges() {
        List<String> declared = new ArrayList<>();
        for (DealTranche.Status from : DealTranche.Status.values()) {
            for (DealTranche.Status to : DealTranche.Status.values()) {
                if (Boolean.TRUE.equals(gate.edgeDeclared(from, to))) {
                    declared.add(from + "->" + to);
                }
            }
        }

        assertThat(declared).containsExactlyInAnyOrder(
                "PRECHECK->ENTRY_SUBMITTED", "PRECHECK->CLOSED",
                "ENTRY_SUBMITTED->ENTRY_FINALIZED", "ENTRY_SUBMITTED->EXIT_PENDING",
                "ENTRY_SUBMITTED->CLOSED",
                "ENTRY_FINALIZED->PROTECTION_SWITCHED", "ENTRY_FINALIZED->MANAGING",
                "PROTECTION_SWITCHED->MANAGING",
                "MANAGING->ENTRY_SUBMITTED", "MANAGING->EXIT_PENDING",
                "EXIT_PENDING->CLOSED");
        assertThat(List.of(DealTranche.Status.values()))
                .noneMatch(status -> status.name().contains("ERROR") || status.name().contains("EMERGENCY"));
    }

    private void assertDeclared(DealTranche.Status from, DealTranche.Status to) {
        assertThat(gate.edgeDeclared(from, to)).isTrue();
    }

    private void assertNotDeclared(DealTranche.Status from, DealTranche.Status to) {
        assertThat(gate.edgeDeclared(from, to)).isFalse();
    }
}

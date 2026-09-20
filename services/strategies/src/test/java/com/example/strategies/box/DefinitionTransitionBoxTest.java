package com.example.strategies.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B4} документа кейсов: матрица переходов статуса
 * (.claude/tests/cases/strategies.md).
 *
 * <p><b>Предмет группы — матрица, а не предусловия.</b> Недопустимость
 * перехода читается ДО чужих операндов, и потому у всякой её клетки
 * стои́т одно и то же отрицание: к стабу соседа не ушло ни одного
 * запроса. Предусловия готовности — предмет соседней группы {@code B5}.
 *
 * <p><b>Строки outbox считаются ПРИРОСТОМ, а не абсолютным числом.</b>
 * Предусловия части клеток сами суть переходы — определение доводится до
 * {@code ACTIVE} либо до {@code DELETED} тропой ящика, — и каждый из них
 * пишет свою строку. Утверждение «строки нет» относится к отвергнутому
 * переходу, а не к прогону, поэтому клетка снимает счёт до входа.
 *
 * <p><b>Ожидание берёт ПАРУ «класс отказа плюс реджект-код текста», а не
 * HTTP-число</b> (там же, §«Число ответа и класс отказа — разные
 * ожидания»). Исключение — {@code 200} у состоявшегося перехода: это
 * контракт успеха самой точки.
 */
class DefinitionTransitionBoxTest extends SharedStrategiesBox {

    /** Класс отказа, которым отвечают все броски поверхности владельца. */
    private static final String REJECTED = "STRATEGY_REQUEST_REJECTED";

    /** Реджект-код недопустимого ребра матрицы. */
    private static final String NOT_ALLOWED = "STRATEGY_TRANSITION_NOT_ALLOWED";

    @Test
    @DisplayName("B4.1 — Черновик деактивировать нельзя")
    void b4_1_aDraftCannotBeDeactivated() {
        peerResolvesEverything();
        String internalId = given(TENANT);
        Long eventsBefore = rows.count(OUTBOX_TABLE);

        Answer answer = moveTo(internalId, TENANT, "INACTIVE");

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo(REJECTED);
        assertThat(answer.errorMessage())
                .as("отказ называет обе стороны ребра, а не «нельзя»")
                .contains(NOT_ALLOWED)
                .contains("CREATED")
                .contains("INACTIVE");
        assertThat(statusOf(internalId, TENANT)).isEqualTo("CREATED");
        assertThat(rows.count(OUTBOX_TABLE)).as("отвергнутый переход события не пишет")
                .isEqualTo(eventsBefore);
        assertThat(peer.count())
                .as("недопустимость ребра читается до чужих операндов")
                .isZero();
    }

    @Test
    @DisplayName("B4.2 — Удалённое определение терминально")
    void b4_2_aDeletedDefinitionIsTerminal() {
        peerResolvesEverything();
        String internalId = given(TENANT);
        assertThat(moveTo(internalId, TENANT, "DELETED").status()).isEqualTo(200);
        peer.forgetRequests();
        Long eventsBefore = rows.count(OUTBOX_TABLE);

        Answer answer = moveTo(internalId, TENANT, "ACTIVE");

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorMessage()).contains(NOT_ALLOWED).contains("DELETED");
        assertThat(statusOf(internalId, TENANT))
                .as("из терминала не выходит ни одно ребро")
                .isEqualTo("DELETED");
        assertThat(rows.count(OUTBOX_TABLE)).isEqualTo(eventsBefore);
        assertThat(peer.count()).isZero();
    }

    @Test
    @DisplayName("B4.3 — `CREATED` целью перехода не бывает")
    void b4_3_createdIsNeverATransitionTarget() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);
        Long eventsBefore = rows.count(OUTBOX_TABLE);

        Answer answer = moveTo(internalId, TENANT, "CREATED");

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorMessage())
                .as("текст называет допустимый перечень целей")
                .contains("ACTIVE")
                .contains("INACTIVE")
                .contains("DELETED");
        assertThat(statusOf(internalId, TENANT)).isEqualTo("ACTIVE");
        assertThat(rows.count(OUTBOX_TABLE)).isEqualTo(eventsBefore);
        assertThat(peer.count())
                .as("отказ поднимается ДО резолва определения")
                .isZero();
    }

    @Test
    @DisplayName("B4.4 — Значение вне перечня статусов")
    void b4_4_aValueOutsideTheStatusSet() {
        peerResolvesEverything();
        String internalId = given(TENANT);
        Long eventsBefore = rows.count(OUTBOX_TABLE);

        Answer answer = moveTo(internalId, TENANT, "SUSPENDED");

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorMessage())
                .as("значение на границе — строка, и разбор делает граница")
                .contains("SUSPENDED");
        assertThat(statusOf(internalId, TENANT)).isEqualTo("CREATED");
        assertThat(rows.count(OUTBOX_TABLE)).isEqualTo(eventsBefore);
    }

    @Test
    @DisplayName("B4.5 — Деактивация активного определения")
    void b4_5_theDeactivationOfAnActiveDefinition() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);

        Answer answer = moveTo(internalId, TENANT, "INACTIVE");

        assertThat(answer.status()).as("контракт успеха точки перехода").isEqualTo(200);
        assertThat(answer.asObject()).containsEntry("status", "INACTIVE");
        assertThat(statusOf(internalId, TENANT)).isEqualTo("INACTIVE");
        assertThat(events(DEACTIVATED))
                .as("переход пишет ровно одну строку своего класса")
                .hasSize(1);
        assertThat(peer.count())
                .as("предусловий готовности у этого ребра нет")
                .isZero();
    }

    @Test
    @DisplayName("B4.6 — Повторная активация после деактивации")
    void b4_6_aSecondActivationAfterDeactivation() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);
        assertThat(moveTo(internalId, TENANT, "INACTIVE").status()).isEqualTo(200);

        Answer answer = moveTo(internalId, TENANT, "ACTIVE");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject()).containsEntry("status", "ACTIVE");
        List<Map<String, Object>> activations = events(ACTIVATED);
        assertThat(activations).as("вторая активация — своя строка, а не правка первой").hasSize(2);
        assertThat(activations.stream().map(row -> row.get("event_id")).toList())
                .as("идентичности событий различны")
                .doesNotHaveDuplicates();
        assertThat(contentOf(activations.getLast()))
                .as("содержимое снова несёт снимок дерева целиком")
                .containsKey("definition");
    }

    @Test
    @DisplayName("B4.7 — Удаление возможно из любого нетерминального статуса")
    void b4_7_deletionIsPossibleFromEveryNonTerminalStatus() {
        peerResolvesEverything();
        // Три нетерминальных статуса на одной паре: активной в каждый
        // момент остаётся ровно одна — иначе предусловие само нарушало бы
        // инвариант, ради которого стои́т частичный уникальный индекс.
        String draft = given(TENANT);
        String inactive = givenActive(TENANT);
        assertThat(moveTo(inactive, TENANT, "INACTIVE").status()).isEqualTo(200);
        String active = givenActive(TENANT);
        peer.forgetRequests();

        List<Answer> answers = List.of(moveTo(draft, TENANT, "DELETED"),
                moveTo(inactive, TENANT, "DELETED"),
                moveTo(active, TENANT, "DELETED"));

        assertThat(answers.stream().map(Answer::status).toList()).containsExactly(200, 200, 200);
        assertThat(answers.stream().map(answer -> answer.asObject().get("status")).toList())
                .containsExactly("DELETED", "DELETED", "DELETED");
        List<Map<String, Object>> deletions = events(DELETED);
        assertThat(deletions).hasSize(3);
        assertThat(deletions.stream().map(StrategiesBox::contentOf).map(Map::keySet).toList())
                .as("форма содержимого одна у всех трёх: идентичности и актор, без дерева")
                .allSatisfy(keys -> assertThat(keys).containsExactlyInAnyOrder("strategyInternalId",
                        "exchangeAccountInternalId", "instrumentInternalId", "actor"));
        assertThat(peer.count())
                .as("предусловий готовности ни одно удаление не проходило")
                .isZero();
    }

    @Test
    @DisplayName("B4.8 — Переход чужого определения не начинается")
    void b4_8_aForeignDefinitionsTransitionNeverStarts() {
        peerResolvesEverything();
        String internalId = given(TENANT);
        Long eventsBefore = rows.count(OUTBOX_TABLE);

        Answer foreign = moveTo(internalId, SECOND_TENANT, "ACTIVE");
        Answer absent = get(STRATEGIES + "/st-absent-b4-8", SECOND_TENANT);

        assertThat(foreign.carriesErrorDto()).isTrue();
        assertThat(foreign.status())
                .as("исход тот же, что у несуществующего: о чужой сущности поверхность не отвечает")
                .isEqualTo(absent.status());
        assertThat(foreign.errorMessage())
                .as("отказ ненайденности, а не отказ ребра: матрицы дело не дошло")
                .doesNotContain(NOT_ALLOWED);
        assertThat(statusOf(internalId, TENANT)).isEqualTo("CREATED");
        assertThat(rows.count(OUTBOX_TABLE)).isEqualTo(eventsBefore);
        assertThat(peer.count()).isZero();
    }
}

package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.resolve.OkxCredentialsRejectionResolver;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Резолв класса отказа по коду ответа — группа `U30` документа
 * `.claude/tests/cases/okx-mapping.md`
 * (docs/integrations/okx/rules/auth-rejection-codes.md).
 *
 * <p><b>Базовая сборка:</b> резолвер собран конструктором; на вход —
 * код ответа площадки строкой. Два независимых вопроса, а не отрицание
 * одного.
 *
 * <p><b>Классов ТРИ, и третий существует:</b> «отверг наши креды», «наш
 * собственный дефект сборки запроса» и «прочий отказ источника» — у
 * каждого своя тропа. Опознание идёт по коду, а не по статусу ответа: у
 * всего семейства статус один, и по нему два первых класса неразличимы
 * при противоположных реакциях.
 */
class CredentialsRejectionResolveTest {

    private static final List<String> CREDENTIALS_REJECTED =
            List.of("50101", "50105", "50111", "50113", "50119");

    private static final List<String> OWN_REQUEST_DEFECT = List.of("50102", "50103");

    private final OkxCredentialsRejectionResolver resolver = new OkxCredentialsRejectionResolver();

    @ParameterizedTest
    @ValueSource(strings = {"50101", "50105", "50111", "50113", "50119"})
    @DisplayName("U30.1-U30.5 — пять наблюдённых кодов отказа в кредах")
    void u30_1_to_5_theFiveObservedRejectionCodes(String code) {
        assertThat(resolver.isCredentialsRejected(code)).isTrue();
        assertThat(resolver.isOwnRequestDefect(code)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"50102", "50103"})
    @DisplayName("U30.6-U30.7 — два кода нашего собственного дефекта сборки запроса")
    void u30_6_to_7_theTwoOwnDefectCodes(String code) {
        assertThat(resolver.isCredentialsRejected(code)).isFalse();
        assertThat(resolver.isOwnRequestDefect(code)).isTrue();
    }

    /** Перечень закрыт над наблюдёнными формами; направление консервативное. */
    @Test
    @DisplayName("U30.8 — код того же семейства вне обоих перечней: третий класс")
    void u30_8_aFamilyCodeOutsideBothListsIsTheThirdClass() {
        assertThat(resolver.isCredentialsRejected("50104")).isFalse();
        assertThat(resolver.isOwnRequestDefect("50104")).isFalse();
    }

    @Test
    @DisplayName("U30.9 — код успеха ни в одном перечне")
    void u30_9_theSuccessCodeIsInNeitherList() {
        assertThat(resolver.isCredentialsRejected("0")).isFalse();
        assertThat(resolver.isOwnRequestDefect("0")).isFalse();
    }

    /** Ответ без поля кода достижим: падение здесь увело бы тропу в непредвиденную ошибку. */
    @Test
    @DisplayName("U30.10 — пустой код: обе ложь, отказа вычисления нет")
    void u30_10_anEmptyCodeComputesToFalse() {
        assertThat(resolver.isCredentialsRejected("")).isFalse();
        assertThat(resolver.isOwnRequestDefect("")).isFalse();
    }

    @Test
    @DisplayName("U30.11 — отсутствие кода: обе ложь по тому же доводу")
    void u30_11_anAbsentCodeComputesToFalse() {
        assertThat(resolver.isCredentialsRejected(null)).isFalse();
        assertThat(resolver.isOwnRequestDefect(null)).isFalse();
    }

    /** Кейс закрепляет наблюдаемое: код сравнивается дословно, обрамления в ответе не наблюдалось. */
    @Test
    @DisplayName("U30.12 — обрамлённый пробелами код в перечень не попадает")
    void u30_12_aPaddedCodeIsComparedVerbatim() {
        assertThat(resolver.isCredentialsRejected(" 50101 ")).isFalse();
        assertThat(resolver.isOwnRequestDefect(" 50101 ")).isFalse();
    }

    @Test
    @DisplayName("U30.13 — пересечения двух перечней нет")
    void u30_13_theTwoListsDoNotIntersect() {
        assertThat(CREDENTIALS_REJECTED).doesNotContainAnyElementsOf(OWN_REQUEST_DEFECT);
        assertThat(CREDENTIALS_REJECTED)
                .allSatisfy(code -> assertThat(resolver.isOwnRequestDefect(code)).isFalse());
        assertThat(OWN_REQUEST_DEFECT)
                .allSatisfy(code -> assertThat(resolver.isCredentialsRejected(code)).isFalse());
    }
}

package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.resolve.OkxCredentialsRejectionResolver;
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
 * код ответа площадки строкой. Вопрос один — «отверг ли источник наши
 * креды».
 *
 * <p><b>Коды нашего собственного дефекта сборки запроса отказом кредов не
 * считаются:</b> они выглядят так же — тот же статус, то же семейство, — но
 * биржевой ступени не поднимают: {@code 50103} уезжает общей тропой отказа
 * границы, {@code 50102} граница отправки исправляет перемером смещения часов
 * площадки (группа {@code U2} и клетки {@code B7.9}-{@code B7.12} документа
 * `.claude/tests/cases/connector-okx.md`).
 * Опознание идёт по коду, а не по статусу ответа: у всего семейства статус
 * один, и по нему два класса неразличимы при противоположных реакциях.
 */
class CredentialsRejectionResolveTest {

    private final OkxCredentialsRejectionResolver resolver = new OkxCredentialsRejectionResolver();

    @ParameterizedTest
    @ValueSource(strings = {"50101", "50105", "50111", "50113", "50119"})
    @DisplayName("U30.1-U30.5 — пять наблюдённых кодов отказа в кредах")
    void u30_1_to_5_theFiveObservedRejectionCodes(String code) {
        assertThat(resolver.isCredentialsRejected(code)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"50102", "50103"})
    @DisplayName("U30.6-U30.7 — два кода нашего собственного дефекта сборки запроса отказом кредов не считаются")
    void u30_6_to_7_theTwoOwnDefectCodesAreNotARejection(String code) {
        assertThat(resolver.isCredentialsRejected(code)).isFalse();
    }

    /** Перечень закрыт над наблюдёнными формами; направление консервативное. */
    @Test
    @DisplayName("U30.8 — код того же семейства вне перечня отказом кредов не считается")
    void u30_8_aFamilyCodeOutsideTheListIsNotARejection() {
        assertThat(resolver.isCredentialsRejected("50104")).isFalse();
    }

    @Test
    @DisplayName("U30.9 — код успеха в перечне не стоит")
    void u30_9_theSuccessCodeIsNotInTheList() {
        assertThat(resolver.isCredentialsRejected("0")).isFalse();
    }

    /** Ответ без поля кода достижим: падение здесь увело бы тропу в непредвиденную ошибку. */
    @Test
    @DisplayName("U30.10 — пустой код: ложь, отказа вычисления нет")
    void u30_10_anEmptyCodeComputesToFalse() {
        assertThat(resolver.isCredentialsRejected("")).isFalse();
    }

    @Test
    @DisplayName("U30.11 — отсутствие кода: ложь по тому же доводу")
    void u30_11_anAbsentCodeComputesToFalse() {
        assertThat(resolver.isCredentialsRejected(null)).isFalse();
    }

    /** Кейс закрепляет наблюдаемое: код сравнивается дословно, обрамления в ответе не наблюдалось. */
    @Test
    @DisplayName("U30.12 — обрамлённый пробелами код в перечень не попадает")
    void u30_12_aPaddedCodeIsComparedVerbatim() {
        assertThat(resolver.isCredentialsRejected(" 50101 ")).isFalse();
    }
}

package com.example.testsupport;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Свёртка полноты durable-потребителя: предикат непрерывности, совокупная
 * выдача и то, что именно свёртка спрашивает у источника — группы `U3`,
 * `U4`, `U6`, клетки `U15.9`, `U16.4`, `U16.5` документа
 * `.claude/tests/cases/durable-reception.md`.
 *
 * <p><b>Ожидание объявлено один раз и прогоняется каждым деревом своей
 * копии.</b> Форма приёма объявлена сквозной
 * (docs/rules/durable-consumer-reception.md §«Форма исполнителей приёма»),
 * а кода два — по дереву на потребителя, — и сличить их на одном classpath
 * нечем: деревья сервисов друг от друга не зависят. Красный прогон здесь
 * называет дерево именем модуля, и это и есть имя потерявшей охрану копии.
 *
 * <p><b>Ветви НИЖНЕЙ ГРАНИЦЫ сюда не входят, и это объявленное
 * расхождение:</b> у журнала операндов два, у статистики один
 * (docs/models/domain/other/StatisticsFact.md §«Состояние приёма и полнота
 * чисел статистики»). Их кейсы — группы `U1` и `U2`, по одной на дерево.
 * Общей осталась клетка `U15.9`: на состоянии «подписанных пар ноль» обе
 * копии обязаны сказать одно и то же.
 */
public abstract class ReceptionCompletenessContract {

    /** Имя группы потребителя: сквозь свёртку оно проходит как получено. */
    protected static final String GROUP = "reception-group";

    /** Позднейший момент наблюдения, который отдаёт источник. */
    protected static final OffsetDateTime OBSERVED =
            OffsetDateTime.of(2026, 9, 10, 3, 0, 0, 0, ZoneOffset.UTC);

    /** Самый ранний момент приёма следствия — операнд, который есть только у журнала. */
    protected static final OffsetDateTime RECORDED =
            OffsetDateTime.of(2026, 9, 9, 3, 0, 0, 0, ZoneOffset.UTC);

    private static final OffsetDateTime STALE_BEFORE =
            OffsetDateTime.of(2026, 9, 12, 12, 0, 0, 0, ZoneOffset.UTC);

    /** Что источник отвечает свёртке; ветвь, которой нет, остаётся неспрошенной. */
    public interface SourceAnswers {

        Long countSubscribedPairs(String consumerGroup);

        Long countSubscribedPairsWithBreak(String consumerGroup, OffsetDateTime staleBefore);

        OffsetDateTime latestObservedSince(String consumerGroup);

        OffsetDateTime earliestRecordedAt();
    }

    /** Обе величины полноты, как их отдала копия своего дерева. */
    public record Completeness(OffsetDateTime lowerBound, Boolean continuityClaimable) { }

    // --- порты к своей копии ---------------------------------------------

    protected abstract Optional<OffsetDateTime> lowerBound(SourceAnswers answers, String consumerGroup);

    protected abstract Boolean continuityClaimable(SourceAnswers answers, String consumerGroup,
                                                   OffsetDateTime staleBefore);

    protected abstract Completeness completeness(SourceAnswers answers, String consumerGroup,
                                                 OffsetDateTime staleBefore);

    /** Класс свёртки своего дерева — вход клеток, наблюдаемых отражением. */
    protected abstract Class<?> completenessServiceType();

    /** Класс источника операндов своего дерева. */
    protected abstract Class<?> completenessSourceType();

    /** Класс формы выдачи своего дерева. */
    protected abstract Class<?> completenessFormType();

    // --- U3: предикат непрерывности ---------------------------------------

    @Test
    @DisplayName("U3.1 — подписано 3 пары, с дырой ноль: непрерывность утверждаема")
    void u3_1_noPairWithABreakMakesContinuityClaimable() {
        assertThat(continuityClaimable(answers(3L, 0L), GROUP, STALE_BEFORE)).isTrue();
    }

    @Test
    @DisplayName("U3.2 — дыра хоть на одной паре снимает непрерывность")
    void u3_2_oneBrokenPairIsEnoughToDenyContinuity() {
        assertThat(continuityClaimable(answers(3L, 1L), GROUP, STALE_BEFORE))
                .as("дыра хоть на одной есть дыра в принятом")
                .isFalse();
    }

    @Test
    @DisplayName("U3.3 — все три пары с дырой: исход тот же, величина булева")
    void u3_3_theValueIsBooleanAndNotACount() {
        assertThat(continuityClaimable(answers(3L, 3L), GROUP, STALE_BEFORE))
                .isEqualTo(continuityClaimable(answers(3L, 1L), GROUP, STALE_BEFORE));
    }

    @Test
    @DisplayName("U3.4 — подписанных пар ноль: не утверждаема, о дырах не спрошено")
    void u3_4_anEmptyQuantifierDomainDeniesContinuityWithoutAskingForBreaks() {
        Recording recording = answers(0L, 0L);

        assertThat(continuityClaimable(recording, GROUP, STALE_BEFORE)).isFalse();
        assertThat(recording.asked("countSubscribedPairsWithBreak"))
                .as("второй конъюнкт короткозамкнут: спрашивать о дырах в пустой области нечего")
                .isZero();
    }

    @Test
    @DisplayName("U3.5 — пустая область: без конъюнкта непустоты ответ был бы «дыры нет»")
    void u3_5_theEmptinessConjunctIsLoadBearing() {
        assertThat(continuityClaimable(answers(0L, 0L), GROUP, STALE_BEFORE))
                .as("свёртка «все» по пустой коллекции истинна — ошибка была бы в разрешающую сторону")
                .isFalse();
    }

    @Test
    @DisplayName("U3.6 — момент устаревания уезжает в источник как получен")
    void u3_6_theStalenessMomentTravelsUnchanged() {
        Recording recording = answers(2L, 0L);

        continuityClaimable(recording, GROUP, STALE_BEFORE);

        assertThat(recording.seenStaleBefore())
                .as("ни подмены, ни пересчёта своими часами свёртка не делает")
                .isEqualTo(STALE_BEFORE);
    }

    @Test
    @DisplayName("U3.7 — ложь означает «не утверждаема», а не «дыра есть»")
    void u3_7_falsehoodHasTwoIndistinguishableCauses() {
        assertThat(continuityClaimable(answers(0L, 0L), GROUP, STALE_BEFORE))
                .as("поводов два — пустая область и найденная дыра, — и по ответу они не различимы")
                .isEqualTo(continuityClaimable(answers(3L, 1L), GROUP, STALE_BEFORE));
    }

    // --- U4: две величины одной выдачей -----------------------------------

    @Test
    @DisplayName("U4.1 — форма несёт обе величины: границу и истинный предикат")
    void u4_1_theFormCarriesBothValues() {
        Completeness completeness = completeness(answers(2L, 0L), GROUP, STALE_BEFORE);

        assertThat(completeness.lowerBound())
                .as("момент наблюдения позже момента приёма — у обеих копий границей служит он")
                .isEqualTo(OBSERVED);
        assertThat(completeness.continuityClaimable()).isTrue();
    }

    @Test
    @DisplayName("U4.2 — подписанных пар ноль: пустая граница и ложный предикат")
    void u4_2_anEmptyDomainMakesBothValuesSayNothingIsPromised() {
        Completeness completeness = completeness(answers(0L, 0L), GROUP, STALE_BEFORE);

        assertThat(completeness.lowerBound())
                .as("обе величины обязаны говорить об одном состоянии одно и то же")
                .isNull();
        assertThat(completeness.continuityClaimable()).isFalse();
    }

    @Test
    @DisplayName("U4.3 — граница есть, предикат ложен: величины независимы")
    void u4_3_theBoundAndThePredicateAreIndependent() {
        Completeness completeness = completeness(answers(2L, 1L), GROUP, STALE_BEFORE);

        assertThat(completeness.lowerBound())
                .as("граница выражает начало ряда, а не отсутствие дыры в нём")
                .isEqualTo(OBSERVED);
        assertThat(completeness.continuityClaimable()).isFalse();
    }

    @Test
    @DisplayName("U4.4 — пустая граница едет отсутствием, а не нулевым моментом")
    void u4_4_anAbsentBoundIsNotAnEpoch() {
        Completeness completeness = completeness(answers(0L, 0L), GROUP, STALE_BEFORE);

        assertThat(completeness.lowerBound())
                .as("эпоха на месте пустоты обещала бы полноту с начала времён")
                .isNull();
    }

    @Test
    @DisplayName("U4.5 — момент устаревания уходит только в предикат, границу не трогает")
    void u4_5_theStalenessMomentReachesOnlyThePredicate() {
        Recording early = answers(2L, 0L);
        Recording late = answers(2L, 0L);

        Completeness first = completeness(early, GROUP, STALE_BEFORE);
        Completeness second = completeness(late, GROUP, STALE_BEFORE.plusDays(30L));

        assertThat(first.lowerBound())
                .as("операндов свежести у границы нет вовсе")
                .isEqualTo(second.lowerBound());
        assertThat(early.seenStaleBefore()).isEqualTo(STALE_BEFORE);
        assertThat(late.seenStaleBefore()).isEqualTo(STALE_BEFORE.plusDays(30L));
    }

    @Test
    @DisplayName("U4.6 — форма выдачи несёт ровно две величины и ни одной сверх")
    void u4_6_theFormHasExactlyTwoValues() {
        List<String> fields = new ArrayList<>();
        for (Field field : completenessFormType().getDeclaredFields()) {
            if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) {
                fields.add(field.getName());
            }
        }

        assertThat(fields)
                .as("состав формы у двух копий одинаков — расхождение объявлено только у операндов границы")
                .containsExactlyInAnyOrder("lowerBound", "continuityClaimable");
    }

    // --- U6: что именно спрашивается у источника ---------------------------

    @Test
    @DisplayName("U6.1 — пустая область: за границей источник спрошен ровно об одном")
    void u6_1_anEmptyDomainShortCircuitsTheBound() {
        Recording recording = answers(0L, 0L);

        lowerBound(recording, GROUP);

        assertThat(recording.askedValues())
                .as("внешний `if` короткозамкнут: о моментах спрашивать нечего")
                .containsExactly("countSubscribedPairs");
    }

    @Test
    @DisplayName("U6.2 — пустая область: за предикатом источник спрошен ровно об одном")
    void u6_2_anEmptyDomainShortCircuitsThePredicate() {
        Recording recording = answers(0L, 0L);

        continuityClaimable(recording, GROUP, STALE_BEFORE);

        assertThat(recording.askedValues()).containsExactly("countSubscribedPairs");
    }

    @Test
    @DisplayName("U6.3 — имя группы уходит в источник как получено, во все спрошенные величины")
    void u6_3_theGroupNameTravelsUnchangedIntoEveryQuestion() {
        Recording recording = answers(2L, 0L);

        completeness(recording, GROUP, STALE_BEFORE);

        assertThat(recording.seenGroups())
                .as("ключ строки состояния — пара «группа × тема», и половину ключа свёртка не трогает")
                .isNotEmpty()
                .allMatch(GROUP::equals);
    }

    @Test
    @DisplayName("U6.4 — у источника спрашиваются числа и моменты, а не коллекции")
    void u6_4_theSourceIsAskedForScalarsAndNotForRows() {
        List<String> collectionReturning = new ArrayList<>();
        for (Method method : completenessSourceType().getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && collectionLike(method.getReturnType())) {
                collectionReturning.add(method.getName());
            }
        }

        assertThat(collectionReturning)
                .as("тянуть строки ради одного поля запрещено (.claude/rules/codestyle.md §«Выборка данных: "
                        + "не тянем сущность ради одного поля»)")
                .isEmpty();
    }

    @Test
    @DisplayName("U6.5 — отказ источника уходит наружу как есть")
    void u6_5_aFailingSourceIsNotSwallowed() {
        IllegalStateException failure = new IllegalStateException("подключение к базе потеряно");

        assertThatThrownBy(() -> lowerBound(throwing(failure), GROUP))
                .as("своей ветви «неизвестно» свёртка не заводит: умолчание не бывает благоприятным")
                .isSameAs(failure);
    }

    // --- U15.9: согласие копий на пустом состоянии ------------------------

    @Test
    @DisplayName("U15.9 — на «подписанных пар ноль» обе копии отдают пустую границу и ложный предикат")
    void u15_9_bothCopiesAgreeOnTheEmptyState() {
        Completeness completeness = completeness(answers(0L, 0L), GROUP, STALE_BEFORE);

        assertThat(completeness.lowerBound())
                .as("расхождение копий объявлено ТОЛЬКО в числе операндов границы")
                .isNull();
        assertThat(completeness.continuityClaimable()).isFalse();
    }

    // --- U16: чего свёртка не делает --------------------------------------

    @Test
    @DisplayName("U16.4 — у свёртки нет ни одного пишущего метода")
    void u16_4_theFoldHasNoWritingMethod() {
        List<String> writing = new ArrayList<>();
        for (Method method : completenessServiceType().getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())
                    && (void.class.equals(method.getReturnType()) || writingName(method.getName()))) {
                writing.add(method.getName());
            }
        }

        assertThat(writing)
                .as("писатель величин назван ролью, и свёртка в перечень писателей не входит")
                .isEmpty();
    }

    @Test
    @DisplayName("U16.5 — своей транзакции свёртка не открывает")
    void u16_5_theFoldOpensNoTransactionOfItsOwn() {
        assertThat(transactionalNames(completenessServiceType()))
                .as("границу называет вызывающий — у чистки она общая со снятием момента разрыва")
                .isEmpty();
    }

    // --- оснастка ---------------------------------------------------------

    /** Источник, отвечающий заданными числами и двумя фиксированными моментами. */
    protected static Recording answers(Long subscribedPairs, Long pairsWithBreak) {
        return new Recording(subscribedPairs, pairsWithBreak, null);
    }

    private static Recording throwing(RuntimeException failure) {
        return new Recording(0L, 0L, failure);
    }

    private static Boolean collectionLike(Class<?> type) {
        return Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type) || type.isArray();
    }

    private static Boolean writingName(String name) {
        return name.startsWith("save") || name.startsWith("write") || name.startsWith("note")
                || name.startsWith("update") || name.startsWith("delete") || name.startsWith("mark");
    }

    private static List<String> transactionalNames(Class<?> type) {
        List<String> found = new ArrayList<>();
        if (annotatedTransactional(type.getAnnotations())) {
            found.add(type.getSimpleName());
        }
        for (Method method : type.getDeclaredMethods()) {
            if (annotatedTransactional(method.getAnnotations())) {
                found.add(method.getName());
            }
        }
        return found;
    }

    private static Boolean annotatedTransactional(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getSimpleName().contains("Transactional")) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    /** Источник операндов, считающий вызовы и запоминающий аргументы. */
    protected static final class Recording implements SourceAnswers {

        private final Long subscribedPairs;
        private final Long pairsWithBreak;
        private final RuntimeException failure;
        private final Map<String, Integer> calls = new LinkedHashMap<>();
        private final List<String> groups = new ArrayList<>();
        private OffsetDateTime staleBefore;

        private Recording(Long subscribedPairs, Long pairsWithBreak, RuntimeException failure) {
            this.subscribedPairs = subscribedPairs;
            this.pairsWithBreak = pairsWithBreak;
            this.failure = failure;
        }

        /** Сколько раз спрошена названная величина. */
        public Integer asked(String value) {
            return calls.getOrDefault(value, 0);
        }

        /** Величины, о которых источник спрошен, в порядке первого обращения. */
        public List<String> askedValues() {
            return new ArrayList<>(calls.keySet());
        }

        /** Имена групп, с которыми свёртка обратилась к источнику. */
        public List<String> seenGroups() {
            return groups;
        }

        /** Момент устаревания, дошедший до источника. */
        public OffsetDateTime seenStaleBefore() {
            return staleBefore;
        }

        @Override
        public Long countSubscribedPairs(String consumerGroup) {
            seen("countSubscribedPairs", consumerGroup);
            if (nonNull(failure)) {
                throw failure;
            }
            return subscribedPairs;
        }

        @Override
        public Long countSubscribedPairsWithBreak(String consumerGroup, OffsetDateTime moment) {
            seen("countSubscribedPairsWithBreak", consumerGroup);
            this.staleBefore = moment;
            return pairsWithBreak;
        }

        @Override
        public OffsetDateTime latestObservedSince(String consumerGroup) {
            seen("latestObservedSince", consumerGroup);
            return OBSERVED;
        }

        @Override
        public OffsetDateTime earliestRecordedAt() {
            seen("earliestRecordedAt", null);
            return RECORDED;
        }

        private void seen(String value, String consumerGroup) {
            calls.merge(value, 1, Integer::sum);
            if (nonNull(consumerGroup)) {
                groups.add(consumerGroup);
            }
        }
    }
}

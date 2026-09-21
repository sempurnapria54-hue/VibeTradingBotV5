package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B4.1} и {@code B4.6} — штатный исход сравнения смещений и
 * записи, разрывом не являющиеся (.claude/tests/cases/statistics.md §«B4 —
 * Обнаружение разрыва: три исхода сравнения смещений»).
 *
 * <p><b>Обе стоя́т на ОДНОМ положении осей и на одном состоянии группы</b>,
 * поэтому живут одним классом: штатная конфигурация плюс своя пара «группа ×
 * темы». Своя группа здесь обязательна не ради порядка: клетка кладёт запись и
 * смотрит ЗАФИКСИРОВАННОЕ группой смещение, а оно есть состояние на брокере,
 * общее всем контекстам одного имени.
 *
 * <p><b>Назначение подаётся вступлением чужого участника в группу</b>
 * ({@link StatisticsBox#reassignPartitions()}): у поднятого контекста своё
 * назначение случается раньше, чем тик заведёт строки пар, то есть тогда,
 * когда писать ещё некуда.
 *
 * <p><b>Возврат назначения предъявляется ВТОРОЙ темой, а не своей.</b> Исход
 * {@code B4.1} — «не пишется ничего», и отрицание сошлось бы и у контейнера,
 * который назначения обратно не получил вовсе. Принятая после ребалансировки
 * запись второй темы предъявляет возврат: чужой участник закрыт, и принять её
 * больше некому. Своя тема при этом остаётся нетронутой — иначе приём двигал бы
 * ровно те величины, о неподвижности которых клетка и утверждает.
 *
 * <p><b>Вторая тема — ось КОНФИГУРАЦИИ, а не второй производитель</b>
 * ({@link StatisticsSubstrate#ownSecondTopic}): форма ключа подписки есть
 * скаляр через запятую, и сколько тем в нём названо, решает окружение.
 *
 * <p><b>Повторная доставка ставится ОТКАЗОМ обработки</b>
 * ({@link Rows#withoutTable}): это единственная тропа, на которой брокер отдаёт
 * потребителю ТУ ЖЕ запись с тем же смещением. Вторая годная запись приехала бы
 * другим смещением, то есть другим предметом.
 */
class AssignmentOffsetsBoxTest extends StatisticsBox {

    /** Краткое имя клеток: из него строятся их группа и их темы. */
    private static final String SLUG = "b4-1";

    /** Биржевой счёт — обязательный ключ сделочного зерна. */
    private static final String ACCOUNT = "ACCOUNT-1";

    /** Определение стратегии — второй компонент ключа зерна. */
    private static final String STRATEGY = "S-1";

    /** Возраст события, которым клетки ходят в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        List<String> both = List.of(
                StatisticsSubstrate.ownTopic(SLUG), StatisticsSubstrate.ownSecondTopic(SLUG));
        StatisticsSubstrate.registerOwn(registry, SLUG, both, both, Map.of());
    }

    @Test
    @DisplayName("B4.1 — Смещение есть и не ниже наименьшего доступного: не пишется ничего")
    void anOffsetAtOrAboveTheEarliestAvailableWritesNothingAtAll() {
        givenReceptionStateRows();
        publish(subject(), "E-1", DEAL_CLOSED, momentsAgo(EVENT_AGE),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed(subject());
        Object observedBefore = pair(subject()).get(OBSERVED_COLUMN);
        String versionBefore = pairVersion(subject());
        Long committedBefore = Wire.committedOffset(consumerGroup(), subject());
        assertThat(committedBefore)
                .as("вход поставлен: группа зафиксировала смещение, и оно не ниже наименьшего доступного")
                .isNotNull()
                .isGreaterThanOrEqualTo(Wire.earliestOffset(subject()));

        reassignPartitions();
        givenPartitionsBack();

        assertThat(pair(subject()).get(GAP_COLUMN)).as("момент разрыва пуст").isNull();
        assertThat(pair(subject()).get(OBSERVED_COLUMN))
                .as("момент наблюдения не переписан").isEqualTo(observedBefore);
        assertThat(continuityClaimable()).as("непрерывность утверждаема").isEqualTo(Boolean.TRUE);
        assertThat(Wire.committedOffset(consumerGroup(), subject()))
                .as("чтение продолжается с зафиксированного смещения").isEqualTo(committedBefore);
        assertThat(pairVersion(subject()))
                .as("уже принятое событие повторно не принималось: строка пары не переписана")
                .isEqualTo(versionBefore);
        assertThat(dealFacts()).as("фактов ровно два: по одному на событие").hasSize(2);
    }

    @Test
    @DisplayName("B4.6 — Первая запись после назначения и повторно доставленная разрывом не считаются")
    void neitherTheFirstRecordAfterAnAssignmentNorARedeliveredOneCountsAsAGap() {
        givenReceptionStateRows();

        reassignPartitions();
        publish(subject(), "E-FIRST", DEAL_CLOSED, momentsAgo(EVENT_AGE),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed(subject());

        assertThat(pair(subject()).get(GAP_COLUMN))
                .as("ожидание посажено самим назначением, и первая запись приходит ровно с него")
                .isNull();
        assertThat(dealFacts()).as("факт первой записи лёг").hasSize(1);
        assertThat(continuityClaimable()).isEqualTo(Boolean.TRUE);

        rows.withoutTable(DEAL_FACTS, () -> {
            publish(subject(), "E-AGAIN", DEAL_CLOSED, momentsAgo(Duration.ofMinutes(1)),
                    Bodies.dealClosed(ACCOUNT, STRATEGY));
            awaitHalted(subject());
        });
        awaitConsumed(subject());

        assertThat(pair(subject()).get(GAP_COLUMN))
                .as("смещение повтора не больше ожидаемого, и разрывом он не объявляется").isNull();
        assertThat(dealFacts()).as("факт повторённой записи лёг с первой же удачной попытки").hasSize(2);
        assertThat(pair(subject()).get(HALTED_COLUMN))
                .as("приём возобновился, и флаг снят принятой записью").isEqualTo(Boolean.FALSE);
        assertThat(continuityClaimable()).as("предикат непрерывности истинен").isEqualTo(Boolean.TRUE);
    }

    /** Тема, о которой утверждают обе клетки. */
    private String subject() {
        return StatisticsSubstrate.ownTopic(SLUG);
    }

    /** Вторая тема подписки: ею предъявляется возврат назначения. */
    private String witness() {
        return StatisticsSubstrate.ownSecondTopic(SLUG);
    }

    /**
     * Ждёт, пока контейнер получит партиции обратно: принятая запись второй
     * темы есть предъявление назначения — чужой участник закрыт, и принять её
     * больше некому.
     */
    private void givenPartitionsBack() {
        publish(witness(), "E-BACK", DEAL_CLOSED, momentsAgo(Duration.ofMinutes(1)),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed(witness());
    }
}

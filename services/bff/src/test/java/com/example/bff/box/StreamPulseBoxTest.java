package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bff.domain.jobs.StreamPulseJob;
import com.example.testsupport.SchedulerCapacityContract;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Группа {@code B5} документа кейсов: пульс потока
 * (.claude/tests/cases/bff.md §«B5 — Пульс потока»).
 *
 * <p><b>Здесь клетки, которым тик общего контекста не нужен:</b> отсутствие
 * ручного запуска, вывод числа потоков планировщика и реплика без слушателя
 * ({@code B5.4}) — у неё тик свой. Клетки, подающие тик
 * рассылкой, живут на своих репликах ({@link FreshReplicaPulseBoxTest});
 * выключенный тик, слушатель без назначения и реплика без подписок — своими
 * контекстами ({@link PulseSwitchedOffBoxTest},
 * {@link ConsumerWithoutAssignmentBoxTest},
 * {@link PulseWithoutSubscriptionsBoxTest}).
 *
 * <p><b>Отсутствие пульса предъявляется барьерной записью факта</b>: пульс
 * пишется в провод синхронно внутри тика, записи одного провода
 * упорядочены, и доехавший факт означает, что всё предшествовавшее ему в
 * проводе уже лежит. У реплики без слушателя барьеру не доехать, и там
 * отсутствие предъявляется выдержанной тишиной после синхронного тика.
 */
class StreamPulseBoxTest extends SharedBffBox {

    private static final String FACT = "DEAL_OPENED";

    /**
     * Слушателя нет вовсе: пустой перечень тем его не заводит, и пустой
     * реестр слушателей читается как «не живо». Реплика своя — ось тем
     * сдвинута ею; тик зовётся руками, как у соседних клеток группы, и пишет
     * синхронно, поэтому выдержанная тишина после него есть исход, а не
     * запаздывание.
     */
    @Test
    @DisplayName("B5.4 — Слушателя нет вовсе — пульса нет")
    void b5_4_withoutAnyListenerThereIsNoPulse() {
        try (ConfigurableApplicationContext silent = Replica.launch(Map.of(BffSubstrate.STREAM_TOPICS_KEY, ""))) {
            authAnswersOneMembership();
            Integer replicaPort = Replica.portOf(silent);
            String ticket = issuedTicketAt(replicaPort, token());

            try (Subscription stream = subscribeAt(replicaPort, ticket, null)) {
                assertThat(stream.status()).isEqualTo(200);
                silent.getBean(StreamPulseJob.class).beat();

                Awaitility.await("тишина после тика")
                        .during(Duration.ofSeconds(1))
                        .atMost(Duration.ofSeconds(5))
                        .until(() -> stream.frames().isEmpty());
            }
        }
    }

    @Test
    @DisplayName("B5.9 — Ручного запуска у тика нет, и это не пропуск")
    void b5_9_theTickHasNoManualTrigger() {
        String tenant = "TP9";
        authAnswers(Bodies.memberships(tenant, ROLE));
        String ticket = issuedTicket();

        try (Subscription stream = openedStreamOf(tenant, ticket, "e-b5-9-1")) {
            // Обход поверхности: всякий POST под собственным корнем
            // периметра, кроме выдачи билета, отвечает отказом, а не
            // запуском — в том числе по адресам, какими ручной запуск
            // называется у соседей.
            List<String> candidates = List.of(PERIMETER + "/jobs/stream-pulse",
                    PERIMETER + "/jobs/stream-pulse/trigger", PERIMETER + "/stream-pulse",
                    PERIMETER + "/pulse", STREAM, CONTEXT);
            assertThat(candidates).allSatisfy(path ->
                    assertThat(post(path, "").status()).isGreaterThanOrEqualTo(400));

            publishDealOpened(tenant, "e-b5-9-2");
            stream.awaitFrames(2);
            // Ни одно из обращений тика не подало: пульса в проводе нет.
            assertThat(stream.types()).containsExactly(FACT, FACT);
        }

        // Защиты от перекрытия у тика нет тоже — и это читается перечнем
        // полей джобы, а не прогоном, на котором перекрытия не случилось:
        // охрана перекрытия есть поле-исполнитель, и у джобы его нет.
        assertThat(Arrays.stream(StreamPulseJob.class.getDeclaredFields())
                .map(Field::getType).map(Class::getSimpleName))
                .noneMatch(type -> type.contains("Guard"));
    }

    @Test
    @DisplayName("B5.10 — Размер пула планировщика выведен из числа @Scheduled-методов")
    void b5_10_theSchedulerPoolSizeIsDerivedFromTheScheduledMethodCount() throws IOException {
        // Счёт и чтение объявленного — ТЕ ЖЕ, что у пробы вместимости
        // модуля: второй носитель счёта разошёлся бы с первым.
        String declared = SchedulerCapacityContract.declaredPoolSizeValue();

        // Плейсхолдера окружения размер не несёт: число выводится из
        // дерева, а не калибруется манифестом.
        assertThat(declared).doesNotContain("${");
        assertThat(SchedulerCapacityContract.declaredPoolSize())
                .isEqualTo(SchedulerCapacityContract.scheduledDeclarationCount());
    }
}

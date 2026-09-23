package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Клетки, подающие ТИК ПУЛЬСА рассылкой всем тенантам
 * (.claude/tests/cases/bff.md, {@code B4.6}, {@code B5.1}, {@code B5.7},
 * {@code B5.8}).
 *
 * <p><b>Реплика у каждой клетки своя, и довод — брошенные подписки.</b>
 * Закрытая клиентом подписка остаётся в наборе сервера до первой записи в
 * неё, а эта запись у построенного есть ГОНКА с контейнером: тропа
 * восстановления сама бросает, и отказ уходит из тика (находка F-14
 * документа кейсов). Пульс пишет всем тенантам реплики, то есть первым
 * делом — во все подписки, брошенные прежними клетками общего контекста, и
 * исход клетки зависел бы от того, сколько их и кто выиграл гонку. Своя
 * реплика с ОТКРЫТЫМ зондом назначения брошенных подписок не держит ни
 * одной; предмет F-14 держат клетки {@code B3.7} и {@code B3.8}.
 *
 * <p><b>Тик подаётся прямым вызовом метода джобы реплики, и это ВХОД</b>
 * (.claude/decisions/test-contour-design-pass.md, решение 6). Пульс пишется
 * в провод синхронно внутри тика, поэтому отсутствие пульса предъявляется
 * барьерной записью факта, положенной после тика.
 */
class FreshReplicaPulseBoxTest extends SharedBffBox {

    private static final String PULSE = "PERIMETER_PULSE";

    private static final String GAP = "PERIMETER_GAP";

    private static final String FACT = "DEAL_OPENED";

    /** Ширина окна клетки {@code B5.8}: окно наполняется ровно до неё. */
    private static final Integer NARROW_WINDOW = 3;

    /** Сколько пульсов клетка {@code B5.8} подаёт в узкое окно: втрое больше его ширины. */
    private static final Integer PULSES = 10;

    @Test
    @DisplayName("B4.6 — Записи периметра идентичности не несут и в окно не кладутся")
    void b4_6_perimeterRecordsCarryNoIdentityAndStayOutOfTheWindow() {
        String tenant = "TR6";
        try (Replica replica = Replica.delivering(this, Map.of())) {
            String ticket = ticketAt(replica, tenant);

            // Подписка с фактами остаётся ОТКРЫТОЙ до конца клетки, а после
            // последнего факта записей тенанта не кладётся ни одной: иначе
            // закрытые подписки клетки стали бы брошенными в рассылке (F-14).
            try (Subscription stream = openedStreamAt(replica.port(), tenant, ticket, "e-b4-6-1")) {
                replica.pulse().beat();
                stream.awaitType(PULSE);
                publishDealOpened(tenant, "e-b4-6-2");
                stream.awaitFrames(3);

                Subscription.Frame beat = frameOf(stream, PULSE);
                assertThat(beat.carriesId()).isFalse();
                // В ТЕЛЕ компонент идентичности есть и пуст — умолчание
                // включения не переопределено ни одним ключом; это
                // названная цена, а не дефект.
                assertThat(beat.content()).containsKey("id");
                assertThat(beat.content().get("id")).isNull();
                assertThat(beat.content().get("content")).isNull();

                try (Subscription gapped = subscribeAt(replica.port(), ticket, "e-b4-6-made-up")) {
                    gapped.awaitFrames(1);
                    Subscription.Frame gap = frameOf(gapped, GAP);
                    assertThat(gap.carriesId()).isFalse();
                    assertThat(gap.content()).containsKey("id");
                    assertThat(gap.content().get("id")).isNull();
                }

                // Хвост после первого факта несёт только следующий факт: ни
                // пульс, ни разрыв — оба случились раньше — в окно не легли.
                try (Subscription resumed = subscribeAt(replica.port(), ticket, "e-b4-6-1")) {
                    resumed.awaitFrames(1);
                    assertThat(resumed.ids()).containsExactly("e-b4-6-2");
                    assertThat(resumed.types()).containsOnly(FACT);
                }

                // Назвать позицией класс записи периметра нечем: окно его не
                // держит, и такое значение даёт разрыв, а не продолжение.
                try (Subscription byClass = subscribeAt(replica.port(), ticket, PULSE)) {
                    byClass.awaitFrames(1);
                    assertThat(byClass.types()).containsExactly(GAP);
                }
            }
        }
    }

    @Test
    @DisplayName("B5.1 — Живой потребитель пропускает пульс")
    void b5_1_aLiveConsumerLetsThePulseThrough() {
        String tenant = "TP1";
        try (Replica replica = Replica.delivering(this, Map.of())) {
            String ticket = ticketAt(replica, tenant);
            Long before = wire.totalRecords();

            try (Subscription stream = openedStreamAt(replica.port(), tenant, ticket, "e-b5-1-1")) {
                OffsetDateTime beforeBeat = OffsetDateTime.now();
                replica.pulse().beat();
                OffsetDateTime afterBeat = OffsetDateTime.now();
                publishDealOpened(tenant, "e-b5-1-2");
                stream.awaitFrames(3);

                assertThat(stream.types()).containsExactly(FACT, PULSE, FACT);
                Subscription.Frame beat = stream.frames().get(1);
                // Момент пульса — момент отправки; идентичности и
                // содержимого нет.
                assertThat(OffsetDateTime.parse(String.valueOf(beat.content().get("occurredAt"))).toInstant())
                        .isBetween(beforeBeat.toInstant(), afterBeat.toInstant());
                assertThat(beat.carriesId()).isFalse();
                assertThat(beat.content().get("content")).isNull();
                // В темы периметр не публикует ничего: прибавились ровно
                // два факта клетки.
                assertThat(wire.totalRecords() - before).isEqualTo(2L);
            }

            // В окно пульс не попадает: хвост после первого факта — только
            // факт.
            try (Subscription resumed = subscribeAt(replica.port(), ticket, "e-b5-1-1")) {
                resumed.awaitFrames(1);
                assertThat(resumed.ids()).containsExactly("e-b5-1-2");
            }
        }
    }

    @Test
    @DisplayName("B5.7 — Пульс идёт всем тенантам с открытыми подписками")
    void b5_7_thePulseGoesToEveryTenantWithOpenSubscriptions() {
        String firstTenant = "TP7";
        String secondTenant = "TP7B";
        try (Replica replica = Replica.delivering(this, Map.of())) {
            String firstTicket = ticketAt(replica, firstTenant);
            authAnswers(Bodies.memberships(secondTenant, ROLE));
            String secondTicket = issuedTicketAt(replica.port(), identity.tokenFor(secondSubject));

            try (Subscription first = openedStreamAt(replica.port(), firstTenant, firstTicket, "e-b5-7-a");
                 Subscription second = openedStreamAt(replica.port(), secondTenant, secondTicket, "e-b5-7-b")) {
                replica.pulse().beat();
                first.awaitType(PULSE);
                second.awaitType(PULSE);

                // Один тик — ровно один пульс в каждый провод, и содержимого
                // тенанта он не несёт: радиуса не нарушает.
                assertThat(first.types()).containsExactly(FACT, PULSE);
                assertThat(second.types()).containsExactly(FACT, PULSE);
                assertThat(List.of(first.frames().get(1).data(), second.frames().get(1).data()))
                        .allSatisfy(data -> assertThat(data).doesNotContain(firstTenant, secondTenant));
            }
        }
    }

    @Test
    @DisplayName("B5.8 — Пульс не подменяет факта и факта не вытесняет")
    void b5_8_thePulseNeitherReplacesNorEvictsAFact() {
        String tenant = "TP8";
        // Окно наполнено РОВНО до ширины: один лишний член, положенный в
        // окно, вытеснил бы первый факт — и продолжение с него стало бы
        // разрывом. Поэтому узкое окно и есть вход клетки: при штатных
        // двухстах десяток пульсов не вытеснил бы ничего и при дефекте.
        try (Replica replica = Replica.delivering(this,
                Map.of(BffSubstrate.REPLAY_WINDOW_KEY, String.valueOf(NARROW_WINDOW)))) {
            String ticket = ticketAt(replica, tenant);

            try (Subscription stream = openedStreamAt(replica.port(), tenant, ticket, "e-b5-8-1");
                 Subscription resumed = subscribeAtAfterPulses(replica, tenant, ticket, stream)) {
                resumed.awaitFrames(2);
                // Хвост отдан целиком — пульсы его не вытеснили, и ни один
                // пульс в хвосте не появился.
                assertThat(resumed.ids()).containsExactly("e-b5-8-2", "e-b5-8-3");
                assertThat(resumed.types()).containsOnly(FACT);
            }
        }
    }

    /**
     * Наполняет узкое окно тремя фактами, подаёт десяток тиков и открывает
     * вторую подписку с позицией первого факта.
     *
     * <p>Первая подписка остаётся ОТКРЫТОЙ до конца клетки: закрытая, она
     * стала бы брошенной, и следующая запись тенанта попала бы в гонку F-14.
     */
    private Subscription subscribeAtAfterPulses(Replica replica, String tenant, String ticket,
                                                Subscription stream) {
        publishDealOpened(tenant, "e-b5-8-2");
        publishDealOpened(tenant, "e-b5-8-3");
        stream.awaitFrames(NARROW_WINDOW);
        for (int beat = 0; beat < PULSES; beat++) {
            replica.pulse().beat();
        }
        stream.awaitFrames(NARROW_WINDOW + PULSES);
        assertThat(stream.types()).filteredOn(type -> Objects.equals(PULSE, type)).hasSize(PULSES);
        return subscribeAt(replica.port(), ticket, "e-b5-8-1");
    }

    /** Билет субъекта клетки у реплики, чьё единственное членство — названный тенант. */
    private String ticketAt(Replica replica, String tenant) {
        authAnswers(Bodies.memberships(tenant, ROLE));
        return issuedTicketAt(replica.port(), token());
    }

    private static Subscription.Frame frameOf(Subscription stream, String type) {
        return stream.frames().stream()
                .filter(frame -> Objects.equals(type, frame.type()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("В проводе нет записи класса " + type));
    }
}

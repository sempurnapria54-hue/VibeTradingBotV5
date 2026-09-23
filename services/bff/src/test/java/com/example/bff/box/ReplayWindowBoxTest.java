package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B4} документа кейсов: окно переигрывания и явный разрыв
 * (.claude/tests/cases/bff.md §«B4 — Окно переигрывания и явный разрыв»).
 *
 * <p><b>Здесь клетки, которым довольно ШТАТНОГО положения осей.</b> Клетка,
 * чей предмет — ШИРИНА окна, живёт своим классом
 * ({@link NarrowReplayWindowBoxTest}): вытеснение при штатных двухстах
 * записях стоило бы двухсот публикаций ради одного ожидания. Клетка о
 * перезапуске процесса поднимает свои реплики сама
 * ({@link ReplicaStartBoxTest}), клетка о записях периметра в окне — тоже
 * ({@link FreshReplicaPulseBoxTest}): её вход — тик пульса.
 *
 * <p><b>Окно читается ТОЛЬКО проводом, и ни разу — обращением к нему.</b>
 * Состояние эфемерно и наружу не отдаётся ничем
 * (.claude/tests/cases/bff.md §«Новая ось формы»): клетка ставит его
 * чужим ходом — записью темы — и читает своим — открытием подписки с
 * позицией.
 *
 * <p><b>Тенант у каждой клетки СВОЙ, и довода два.</b> Окно ведётся по
 * тенанту и переживает клетку: общий тенант отдавал бы следующей клетке
 * хвост предыдущей, и «хвост равен ровно двум записям» было бы верно или
 * ложно по порядку прогона. Второй довод — наследство подписок: закрытая
 * клиентом подписка выбывает из набора лишь на первой следующей записи
 * тенанта ({@link SubscriptionCeilingBoxTest}).
 *
 * <p><b>Подписка, наполнившая окно, остаётся ОТКРЫТОЙ до конца клетки.</b>
 * Закрытая клиентом подписка остаётся в наборе сервера до первой записи в
 * неё, а эта запись у построенного есть гонка с контейнером (находка F-14):
 * следующая запись тенанта попала бы в неё, и исход клетки зависел бы от
 * того, кто гонку выиграл.
 *
 * <p><b>Всякое переоткрытие с позицией отвечает сразу.</b> Переигрывание
 * и разрыв пишутся в провод при открытии, а заголовки провода уходят с
 * первой записью (находка F-11): ответ открытия с позицией приходит без
 * барьерной записи, а без позиции — только с ней.
 */
class ReplayWindowBoxTest extends SharedBffBox {

    /** Класс записи разрыва: позиция клиента в окне не нашлась. */
    private static final String GAP = "PERIMETER_GAP";

    /** Класс факта, которым клетки наполняют окно. */
    private static final String FACT = "DEAL_OPENED";

    @Test
    @DisplayName("B4.1 — Известная позиция продолжает поток с неё")
    void b4_1_aKnownPositionContinuesTheStreamFromIt() {
        String tenant = "TR1";
        String ticket = ticketOf(tenant);

        try (Subscription filling = filledWindow(tenant, ticket, "e-b4-1-1", "e-b4-1-2", "e-b4-1-3");
             Subscription resumed = subscribe(ticket, "e-b4-1-1")) {
            resumed.awaitFrames(2);
            // После переигрывания новые записи идут в тот же провод.
            publishDealOpened(tenant, "e-b4-1-4");
            resumed.awaitFrames(3);

            assertThat(resumed.ids()).containsExactly("e-b4-1-2", "e-b4-1-3", "e-b4-1-4");
            assertThat(resumed.types()).containsOnly(FACT).doesNotContain(GAP);
        }
    }

    @Test
    @DisplayName("B4.2 — Неизвестная позиция даёт ЯВНЫЙ разрыв, а не молчаливое продолжение")
    void b4_2_anUnknownPositionGivesAnExplicitGap() {
        String tenant = "TR2";
        String ticket = ticketOf(tenant);

        try (Subscription filling = filledWindow(tenant, ticket, "e-b4-2-1", "e-b4-2-2");
             Subscription resumed = subscribe(ticket, "e-b4-2-made-up")) {
            resumed.awaitFrames(1);
            publishDealOpened(tenant, "e-b4-2-new");
            resumed.awaitFrames(2);

            // Разрыв первым, хвоста окна за ним нет, дальше — только новое.
            assertThat(resumed.types()).containsExactly(GAP, FACT);
            assertThat(resumed.ids()).containsExactly(null, "e-b4-2-new");
            assertThat(resumed.ids()).doesNotContain("e-b4-2-1", "e-b4-2-2");
        }
    }

    @Test
    @DisplayName("B4.3 — Первое подключение разрыва не получает")
    void b4_3_theFirstConnectionGetsNoGap() {
        String tenant = "TR3";
        String ticket = ticketOf(tenant);

        // Без позиции ответ открытия приходит только с первой записью
        // (F-11): её и кладёт барьер — запись, положенная ПОСЛЕ открытия.
        try (Subscription filling = filledWindow(tenant, ticket, "e-b4-3-1", "e-b4-3-2");
             Subscription first = openedStreamOf(tenant, ticket, "e-b4-3-after")) {
            assertThat(first.ids()).containsExactly("e-b4-3-after");
            assertThat(first.types()).containsExactly(FACT);
        }
    }

    @Test
    @DisplayName("B4.5 — Окно принадлежит тенанту: чужая позиция не находится")
    void b4_5_theWindowBelongsToTheTenant() {
        String ownTenant = "TR5";
        String foreignTenant = "TR5B";
        String ownTicket = ticketOf(ownTenant);
        // Тенант билета выводится из членств ПРЕДЪЯВИТЕЛЯ: билет второго
        // тенанта берётся вторым субъектом.
        authAnswers(Bodies.memberships(foreignTenant, ROLE));
        String foreignTicket = issuedTicketWith(identity.tokenFor(secondSubject));

        try (Subscription filling = filledWindow(foreignTenant, foreignTicket,
                "e-b4-5-foreign-1", "e-b4-5-foreign-2");
             Subscription resumed = subscribe(ownTicket, "e-b4-5-foreign-1")) {
            resumed.awaitFrames(1);
            publishDealOpened(ownTenant, "e-b4-5-own");
            resumed.awaitFrames(2);

            assertThat(resumed.types()).containsExactly(GAP, FACT);
            assertThat(resumed.ids()).containsExactly(null, "e-b4-5-own");
            // Содержимое чужого окна не наблюдается ни в каком виде.
            assertThat(resumed.frames()).allSatisfy(frame ->
                    assertThat(frame.data()).doesNotContain("e-b4-5-foreign", foreignTenant));
        }
    }

    @Test
    @DisplayName("B4.7 — Смещение темы наружу не уходит ни в одном поле")
    void b4_7_theTopicOffsetDoesNotLeaveInAnyField() {
        String tenant = "TR7";
        authAnswers(Bodies.memberships(tenant, ROLE));
        Answer context = get(CONTEXT);
        Answer issued = post(TICKETS, "");
        String ticket = String.valueOf(issued.asObject().get("ticket"));

        try (Subscription stream = openedStreamOf(tenant, ticket, "e-b4-7");
             Subscription refused = subscribeWithoutTicket();
             Subscription byOffset = subscribe(ticket, "0")) {
            Subscription.Frame frame = stream.frames().getFirst();
            // Позиция чтения выражается только идентичностью события.
            assertThat(frame.id()).isEqualTo("e-b4-7");
            assertThat(frame.content()).containsOnlyKeys("id", "type", "occurredAt", "content");

            assertThat(List.of(frame.data(), context.body(), issued.body(), refused.errorBody()))
                    .allSatisfy(body -> assertThat(body.toLowerCase())
                            .doesNotContain("offset", "partition", "topic",
                                    Wire.CORE_TOPIC, Wire.STRATEGIES_TOPIC));

            // Запросить произвольную позицию клиент не может ничем:
            // значение, похожее на смещение, — не идентичность события, и
            // ответ на него разрыв, а не чтение темы с этого места.
            byOffset.awaitFrames(1);
            assertThat(byOffset.types()).containsExactly(GAP);
        }
    }

    @Test
    @Tag("debt")
    @DisplayName("B4.9 — Окно тенанта без подписок не наполняется")
    void b4_9_theWindowOfATenantWithoutSubscriptionsIsNotFilled() {
        String idleTenant = "TR9";
        String barrierTenant = "TR9B";
        String barrierTicket = ticketOf(barrierTenant);
        authAnswers(Bodies.memberships(idleTenant, ROLE));
        String idleTicket = issuedTicketWith(identity.tokenFor(secondSubject));

        try (Subscription barrier = openedStreamOf(barrierTenant, barrierTicket, "e-b4-9-open")) {
            publishDealOpened(idleTenant, "e-b4-9-1");
            publishDealOpened(idleTenant, "e-b4-9-2");
            publishDealOpened(idleTenant, "e-b4-9-3");
            // Барьер: слушатель читает тему одной партицией по порядку, и
            // доехавшая следом запись другого тенанта предъявляет, что три
            // записи тенанта без подписок уже приняты.
            publishDealOpened(barrierTenant, "e-b4-9-barrier");
            barrier.awaitFrames(2);

            // Ожидание из дома: записей, положенных до существования
            // подписки, окно не держит — держать их не для кого, и ответ на
            // позицию первой из них есть разрыв. Сегодня красно: окно
            // наполняется для всякого тенанта, чья запись доехала, и
            // переигрывается тому, кто на неё не подписывался (находка F-3
            // документа кейсов).
            try (Subscription resumed = subscribe(idleTicket, "e-b4-9-1")) {
                resumed.awaitFrames(1);
                assertThat(resumed.types().getFirst()).isEqualTo(GAP);
                assertThat(resumed.ids()).doesNotContain("e-b4-9-2", "e-b4-9-3");
            }
        }
    }

    /** Билет субъекта клетки, чьё единственное членство — названный тенант. */
    private String ticketOf(String tenant) {
        authAnswers(Bodies.memberships(tenant, ROLE));
        return issuedTicket();
    }

    /**
     * Наполняет окно тенанта названными фактами и отдаёт подписку ОТКРЫТОЙ.
     *
     * <p><b>Через открытую подписку, а не записью «в темноту»:</b> доехавшая
     * запись и есть единственное предъявление того, что слушатель её
     * принял, — окна снаружи не видно. Открытой — см. шапку класса.
     */
    private Subscription filledWindow(String tenant, String ticket, String... eventIds) {
        Subscription filling = openedStreamOf(tenant, ticket, eventIds[0]);
        for (int index = 1; index < eventIds.length; index++) {
            publishDealOpened(tenant, eventIds[index]);
        }
        filling.awaitFrames(eventIds.length);
        assertThat(filling.ids()).containsExactly(eventIds);
        return filling;
    }
}

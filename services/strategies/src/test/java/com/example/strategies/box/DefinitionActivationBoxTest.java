package com.example.strategies.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B5} документа кейсов: активация и её предусловия
 * готовности (.claude/tests/cases/strategies.md).
 *
 * <p><b>Предусловия пересчитываются на ТЕКУЩИХ числах, а не принимаются
 * по создании.</b> Отсюда форма почти каждой клетки: определение
 * заводится при одних ответах соседа, а активируется при других —
 * расхождение между этими двумя моментами и есть предмет группы
 * (docs/rules/strategy-validation.md §«Что проверяется на активации»).
 *
 * <p><b>Порядок несущий: чужие операнды добываются ДО транзакции.</b>
 * Наблюдается он моментами: запись стаба соседа сделана раньше, чем
 * момент происшествия строки outbox. Сеть внутри транзакции удерживала
 * бы соединение пула, и на отказе соседа транзакция висела бы до
 * таймаута.
 *
 * <p><b>Инвариант «одна активная на паре» держат ДВА носителя</b> —
 * проверка приложения и частичный уникальный индекс, — и клетки
 * наблюдают их порознь: первый тропой поверхности, второй прямой записью
 * мимо приложения, то есть ровно тем ходом, ради которого он и заведён.
 */
class DefinitionActivationBoxTest extends SharedStrategiesBox {

    /** Инструмент второй пары того же счёта: им наблюдается радиус инварианта. */
    private static final String SECOND_INSTRUMENT = "btc-usdt-swap";

    /** Потолок одновременного риска, который эталон уже не проходит. */
    private static final String TIGHTENED_SIMULTANEOUS_PERCENT = "0.5";

    @Test
    @DisplayName("B5.1 — Штатная активация переставляет статус и пишет событие одним ходом")
    void b5_1_theRegularActivationMovesTheStatusAndWritesTheEventInOneGo() {
        peerResolvesEverything();
        String internalId = given(TENANT);

        Answer answer = moveTo(internalId, TENANT, "ACTIVE");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject()).containsEntry("status", "ACTIVE");
        Map<String, Object> event = event(ACTIVATED);
        assertThat(events()).as("переход пишет ровно одну строку").hasSize(1);
        assertThat(event).containsEntry("topic", StrategiesSubstrate.FACTS_TOPIC);
        assertThat(event.get("published_at"))
                .as("реле начинается там, где транзакция перехода закончилась")
                .isNull();
        assertThat(peer.paths())
                .as("к соседу ушли ровно два чтения — разрешимость ссылок и числа тенанта")
                .containsExactly(PEER_PAIR_CHECKS, PEER_RISK_APPETITES + "/" + TENANT);
        OffsetDateTime written = (OffsetDateTime) event.get("occurred_at");
        assertThat(peer.requests().stream()
                .map(request -> request.getLoggedDate().toInstant())
                .allMatch(moment -> moment.isBefore(written.toInstant())))
                .as("оба чтения состоялись ДО записи: сети в транзакции нет")
                .isTrue();

        // Второй носитель того же инварианта — частичный уникальный
        // индекс; наблюдается он записью МИМО приложения, ради которой и
        // заведён.
        String second = given(TENANT);
        assertThatThrownBy(() -> rows.write(
                "update strategies set status = 'ACTIVE' where internal_id = ?", second))
                .as("вторую активную на паре не пропускает схема")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("B5.2 — Вторая активная на паре отвергается конфликтом")
    void b5_2_aSecondActiveOnThePairIsRejectedAsAConflict() {
        peerResolvesEverything();
        String first = givenActive(TENANT);
        String second = given(TENANT);
        Long eventsBefore = rows.count(OUTBOX_TABLE);

        Answer answer = moveTo(second, TENANT, "ACTIVE");

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorMessage())
                .contains("STRATEGY_ACTIVE_ALREADY_EXISTS")
                .as("отказ называет ту, что уже активна")
                .contains(first);
        assertThat(statusOf(second, TENANT)).isEqualTo("CREATED");
        assertThat(statusOf(first, TENANT)).as("первое определение не тронуто").isEqualTo("ACTIVE");
        assertThat(rows.count(OUTBOX_TABLE)).isEqualTo(eventsBefore);
    }

    @Test
    @DisplayName("B5.3 — Активная на другой паре того же счёта не мешает")
    void b5_3_anActiveOnAnotherPairOfTheSameAccountDoesNotInterfere() {
        peerResolvesEverything();
        String first = givenActive(TENANT);
        String second = given(TENANT, Bodies.onInstrument(SECOND_INSTRUMENT));

        Answer answer = moveTo(second, TENANT, "ACTIVE");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(statusOf(second, TENANT)).isEqualTo("ACTIVE");
        assertThat(statusOf(first, TENANT))
                .as("радиус инварианта — пара, а не счёт и не инструмент")
                .isEqualTo("ACTIVE");
        assertThat(events(ACTIVATED)).as("у второго — своя строка активации").hasSize(2);
    }

    @Test
    @DisplayName("B5.4 — Ссылка перестала разрешаться после создания")
    void b5_4_aReferenceStoppedResolvingAfterTheCreation() {
        peerResolvesEverything();
        String internalId = given(TENANT);
        peer.answers(PEER_PAIR_CHECKS, Feed.pairCheck(Boolean.TRUE, Boolean.FALSE, Boolean.TRUE));

        Answer answer = moveTo(internalId, TENANT, "ACTIVE");

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorMessage())
                .as("счёт вышел из контекста тенанта уже после создания")
                .contains("STRATEGY_ACCOUNT_NOT_FOUND");
        assertThat(statusOf(internalId, TENANT)).isEqualTo("CREATED");
        assertThat(rows.count(OUTBOX_TABLE)).isZero();
    }

    @Test
    @DisplayName("B5.5 — Числа сняты после создания: активация отвергается")
    void b5_5_theNumbersWereWithdrawnAfterTheCreation() {
        peerResolvesEverything();
        String internalId = given(TENANT);
        peer.answers(PEER_RISK_APPETITES + "/" + TENANT,
                Feed.riskAppetite(TENANT, GLOBAL_SIMULTANEOUS_PERCENT, null));

        Answer answer = moveTo(internalId, TENANT, "ACTIVE");

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorMessage())
                .contains("STRATEGY_RISK_APPETITE_NOT_CONFIGURED")
                .as("операнд не добыт — неравенства не считаются вовсе")
                .doesNotContain("_ABOVE_GLOBAL");
        assertThat(statusOf(internalId, TENANT)).isEqualTo("CREATED");
        assertThat(rows.count(OUTBOX_TABLE)).isZero();
    }

    @Test
    @DisplayName("B5.6 — Ужесточённый потолок отвергает уже заведённое определение")
    void b5_6_aTightenedCeilingRejectsAnAlreadyCreatedDefinition() {
        peerResolvesEverything();
        String internalId = given(TENANT);
        peer.answers(PEER_RISK_APPETITES + "/" + TENANT, Feed.riskAppetite(TENANT,
                TIGHTENED_SIMULTANEOUS_PERCENT, GLOBAL_CATASTROPHIC_MULTIPLIER));

        Answer answer = moveTo(internalId, TENANT, "ACTIVE");

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorMessage())
                .as("неравенства пересчитаны на ТЕКУЩИХ числах, а не приняты по создании")
                .contains("STRATEGY_SIMULTANEOUS_RISK_ABOVE_GLOBAL");
        assertThat(statusOf(internalId, TENANT)).isEqualTo("CREATED");
        assertThat(rows.count(OUTBOX_TABLE)).isZero();
    }

    @Test
    @DisplayName("B5.7 — Смена состояния контура активации не запрещает")
    void b5_7_theContourStateDoesNotForbidTheActivation() {
        peerResolvesEverything();
        String internalId = given(TENANT);

        Answer answer = moveTo(internalId, TENANT, "ACTIVE");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(statusOf(internalId, TENANT)).isEqualTo("ACTIVE");
        assertThat(peer.paths())
                .as("ни ступеней, ни статуса тенанта, ни статуса счёта сервис не спрашивает")
                .containsExactly(PEER_PAIR_CHECKS, PEER_RISK_APPETITES + "/" + TENANT);
    }

    @Test
    @DisplayName("B5.8 — Ядро недоступно: активация отвергается без записи")
    void b5_8_anUnavailablePeerRejectsTheActivationWithoutAWrite() {
        peerResolvesEverything();
        String internalId = given(TENANT);
        peer.answers(PEER_PAIR_CHECKS, 503, "{}");

        Answer answer = moveTo(internalId, TENANT, "ACTIVE");

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode())
                .as("операнд не добыт — класс отказа о недоступности соседа")
                .isEqualTo("PEER_UNAVAILABLE");
        assertThat(statusOf(internalId, TENANT)).isEqualTo("CREATED");
        assertThat(rows.count(OUTBOX_TABLE))
                .as("половины «статус без события» не существует: транзакция не открывалась")
                .isZero();
    }

    /**
     * Ожидание взято из дома, а не из сегодняшнего факта: вторым
     * носителем инварианта стои́т частичный уникальный индекс, и его
     * срабатывание доходит до перехватчика непредусмотренного — ответом
     * идёт {@code INTERNAL_FAILURE} без реджект-кода (находка F-2,
     * .claude/work/backlog.md §«Гонка активаций отвечает `500` вместо
     * объявленного `409`»). Клетка красна этим, а не ослабленным
     * ассертом.
     *
     * <p><b>Барьер стои́т перед ОТПРАВКОЙ, и большего тест обещать не
     * может:</b> разъехаться внутри сервиса потоки вправе, и тогда
     * проигравший упирается в проверку приложения. Инварианты исхода —
     * одна активная и одна строка — верны при обоих раскладах и стоя́т
     * безусловно.
     */
    @Test
    @Tag("debt")
    @DisplayName("B5.9 — Гонка двух одновременных активаций одной пары")
    void b5_9_aRaceOfTwoSimultaneousActivationsOfOnePair() throws Exception {
        peerResolvesEverything();
        String first = given(TENANT);
        String second = given(TENANT);
        CyclicBarrier together = new CyclicBarrier(2);

        List<Answer> answers;
        try (ExecutorService both = Executors.newFixedThreadPool(2)) {
            List<Future<Answer>> sent = List.of(
                    both.submit(() -> raceTo(together, first)),
                    both.submit(() -> raceTo(together, second)));
            answers = List.of(sent.get(0).get(), sent.get(1).get());
        }

        assertThat(answers.stream().filter(answer -> answer.status() == 200).count())
                .as("активной становится ровно одна")
                .isEqualTo(1L);
        assertThat(events(ACTIVATED)).as("строка активации тоже ровно одна").hasSize(1);
        assertThat(rows.countWhere(STRATEGIES_TABLE, "status", "ACTIVE"))
                .as("двух активных на паре в базе не существует")
                .isEqualTo(1L);
        Answer loser = answers.stream().filter(answer -> answer.status() != 200).findFirst()
                .orElseThrow(() -> new AssertionError("B5.9: обе активации прошли — гонки не было"));
        assertThat(loser.errorMessage())
                .as("проигравший отвечает тем же отказом, что и проверка приложения")
                .contains("STRATEGY_ACTIVE_ALREADY_EXISTS");
    }

    /** Активация, отпущенная общим барьером: обе уходят одним мгновением. */
    private Answer raceTo(CyclicBarrier together, String internalId) throws Exception {
        together.await();
        return moveTo(internalId, TENANT, "ACTIVE");
    }
}

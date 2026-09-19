package com.example.bff.unit.stream;

import static com.example.bff.unit.stream.StreamFixture.fact;
import static com.example.bff.unit.stream.StreamFixture.framesOf;
import static com.example.bff.unit.stream.StreamFixture.perimeterRecord;
import static com.example.bff.unit.stream.StreamFixture.properties;
import static com.example.bff.unit.stream.StreamFixture.registry;
import static com.example.bff.unit.stream.StreamFixture.subscribe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bff.domain.stream.StreamRegistry;
import com.example.bff.util.Constants;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.RecordingEmitterChannel;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Подписки реплики: потолок, снятие, радиус рассылки и отказ доставки —
 * группы `U5`, `U8`, `U9` документа
 * `.claude/tests/cases/bff-perimeter-logic.md`
 * (docs/architecture/contracts.md §«Живые данные в браузер»).
 *
 * <p><b>Почему это стои́т проверять.</b> Реестр — единственное, что стои́т
 * между фактом и браузером, и все три его отказа тихие: подписка, потерянная
 * снятием, остаётся открытой и молчит; факт, ушедший чужому тенанту, виден
 * только тому, кому его не полагалось; оборвавшаяся сессия, уронившая
 * рассылку, лишает данных остальные. Ни одно из трёх не отличимо от
 * исправной работы по самому потоку.
 *
 * <p><b>Окно переигрывания и разрыв — соседний класс кода</b>
 * ({@code StreamReplayTest}): предмет тот же, но двух групп из пяти на один
 * файл вдвое больше остальных, и один файл читался бы хуже двух.
 */
class StreamRegistryTest {

    private static final String TENANT = "tenant-7";
    private static final String OTHER_TENANT = "tenant-8";

    /** Первое открытие: подписка есть, а в провод до первого факта не уходит ничего. */
    @Test
    @DisplayName("U5.1 — первое открытие заводит подписку и ничего не пишет")
    void u5_1_theFirstOpenRegistersASubscriptionAndWritesNothing() {
        StreamRegistry registry = registry();

        RecordingEmitterChannel channel = subscribe(registry, TENANT, null);

        assertThat(registry.hasSubscriptions())
                .as("подписка заведена — предикат наличия читает её")
                .isTrue();
        assertThat(framesOf(channel))
                .as("до первого факта в провод не уходит ни одной записи")
                .isEmpty();
    }

    /** Нижняя сторона границы: ровно потолок открытий проходит целиком. */
    @Test
    @DisplayName("U5.2 — открытий ровно по потолку проходят все")
    void u5_2_openingsUpToTheCeilingAllPass() {
        StreamRegistry registry = registry();

        assertThatCode(() -> {
            subscribe(registry, TENANT, null);
            subscribe(registry, TENANT, null);
        }).as("потолок — два: обе подписки под ним").doesNotThrowAnyException();
    }

    /** Верхняя сторона: сверх потолка — отказ, и подписки при этом не заводится. */
    @Test
    @DisplayName("U5.3 — открытие сверх потолка отвергается и подписки не заводит")
    void u5_3_anOpeningBeyondTheCeilingIsRefusedAndRegistersNothing() {
        StreamRegistry registry = registry();
        RecordingEmitterChannel first = subscribe(registry, TENANT, null);
        RecordingEmitterChannel second = subscribe(registry, TENANT, null);

        assertThatThrownBy(() -> registry.open(TENANT, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(failure -> ((ResponseStatusException) failure).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        registry.publish(TENANT, fact("E1"));
        assertThat(first.attempts() + second.attempts())
                .as("набор тенанта не вырос: запись получили ровно две заведённые подписки")
                .isEqualTo(2);
    }

    /** Потолок считается по тенанту, а не по реплике. */
    @Test
    @DisplayName("U5.4 — исчерпанный потолок одного тенанта не закрывает другого")
    void u5_4_anExhaustedCeilingOfOneTenantDoesNotCloseAnother() {
        StreamRegistry registry = registry();
        subscribe(registry, TENANT, null);
        subscribe(registry, TENANT, null);

        assertThatCode(() -> subscribe(registry, OTHER_TENANT, null))
                .as("потолок — величина тенанта, а не реплики")
                .doesNotThrowAnyException();
    }

    /** Завершение освобождает место под потолком. */
    @Test
    @DisplayName("U5.5 — завершённая подписка освобождает место под потолком")
    void u5_5_aCompletedSubscriptionFreesRoom() {
        StreamRegistry registry = registry();
        RecordingEmitterChannel first = subscribe(registry, TENANT, null);
        subscribe(registry, TENANT, null);

        first.fireCompletion();

        assertThatCode(() -> subscribe(registry, TENANT, null))
                .as("место освободилось — третье открытие проходит")
                .doesNotThrowAnyException();
    }

    /** Опустевший набор снимается вместе с отображением тенанта. */
    @Test
    @DisplayName("U5.6 — снятие последней подписки снимает отображение тенанта")
    void u5_6_removingTheLastSubscriptionDropsTheTenantEntry() {
        StreamRegistry registry = registry();
        RecordingEmitterChannel only = subscribe(registry, TENANT, null);

        only.fireCompletion();

        assertThat(registry.hasSubscriptions())
                .as("подписок не осталось ни у одного тенанта")
                .isFalse();
    }

    /** Все три обратных вызова ведут к одному снятию — различать их нечем. */
    @Test
    @DisplayName("U5.7 — истечение срока и ошибка снимают подписку так же, как завершение")
    void u5_7_timeoutAndErrorRemoveASubscriptionLikeCompletion() {
        StreamRegistry byTimeout = registry();
        RecordingEmitterChannel timedOut = subscribe(byTimeout, TENANT, null);
        StreamRegistry byError = registry();
        RecordingEmitterChannel failed = subscribe(byError, TENANT, null);

        timedOut.fireTimeout();
        failed.fireError(new IOException("оборвалось соединение"));

        assertThat(byTimeout.hasSubscriptions())
                .as("истечение срока соединения снимает подписку")
                .isFalse();
        assertThat(byError.hasSubscriptions())
                .as("ошибка снимает подписку тем же ходом")
                .isFalse();
    }

    /** Снятие у тенанта без набора — молчаливый выход, а не отказ. */
    @Test
    @DisplayName("U5.8 — повторное снятие у тенанта без набора ничего не меняет")
    void u5_8_removingFromATenantWithoutASetChangesNothing() {
        StreamRegistry registry = registry();
        RecordingEmitterChannel removed = subscribe(registry, TENANT, null);
        RecordingEmitterChannel survivor = subscribe(registry, OTHER_TENANT, null);
        removed.fireCompletion();

        assertThatCode(removed::fireCompletion)
                .as("набора у тенанта больше нет — снятие выходит молча")
                .doesNotThrowAnyException();

        assertThat(registry.hasSubscriptions())
                .as("подписка другого тенанта жива")
                .isTrue();
        registry.publish(OTHER_TENANT, fact("E1"));
        assertThat(framesOf(survivor))
                .as("и продолжает получать записи")
                .hasSize(1);
    }

    /**
     * Нулевой потолок: ожидание взято из дома («сверх потолка открытие
     * отвечает отказом»), и сегодня оно красно — охрана читает НАБОР, а
     * не счёт, и отсутствие набора принимает за свободное место. Первая
     * подписка тенанта проходит при любом потолке.
     */
    @Test
    @Tag("debt")
    @DisplayName("U5.9 — нулевой потолок отвергает первое же открытие")
    void u5_9_aZeroCeilingRefusesTheVeryFirstOpening() {
        StreamRegistry registry = new StreamRegistry(properties(3, 0));

        assertThatThrownBy(() -> registry.open(TENANT, null))
                .as("сравнение нестрогое: ноль открытых уже не меньше нуля")
                .isInstanceOf(ResponseStatusException.class);
    }

    /** Факт уходит всем открытым подпискам тенанта и каждой по одному разу. */
    @Test
    @DisplayName("U8.1 — факт получают обе подписки тенанта, каждая по разу")
    void u8_1_bothSubscriptionsOfATenantReceiveTheFactOnce() {
        StreamRegistry registry = registry();
        RecordingEmitterChannel first = subscribe(registry, TENANT, null);
        RecordingEmitterChannel second = subscribe(registry, TENANT, null);

        registry.publish(TENANT, fact("E1"));

        assertThat(framesOf(first)).as("первая подписка получила ровно одну запись").hasSize(1);
        assertThat(framesOf(second)).as("вторая — тоже ровно одну").hasSize(1);
    }

    /** Публикация тенанту без подписок молчалива, а окно при этом наполняется. */
    @Test
    @DisplayName("U8.2 — факт тенанта без подписок в провод не уходит")
    void u8_2_aFactOfATenantWithoutSubscriptionsGoesNowhere() {
        StreamRegistry registry = registry();

        assertThatCode(() -> registry.publish(TENANT, fact("E1")))
                .as("отсутствие набора выходит молча")
                .doesNotThrowAnyException();

        RecordingEmitterChannel late = subscribe(registry, TENANT, "E1");
        assertThat(framesOf(late))
                .as("окно наполнилось: позиция нашлась, и хвост после неё пуст")
                .isEmpty();
    }

    /** Радиус рассылки — тенант: чужой факт в поток не идёт. */
    @Test
    @DisplayName("U8.3 — подписка чужого тенанта факта не получает")
    void u8_3_aSubscriptionOfAnotherTenantReceivesNothing() {
        StreamRegistry registry = registry();
        RecordingEmitterChannel foreign = subscribe(registry, OTHER_TENANT, null);

        registry.publish(TENANT, fact("E1"));

        assertThat(framesOf(foreign))
                .as("фильтрация по тенанту сессии")
                .isEmpty();
    }

    /** Запись периметра идёт всем тенантам: она о живости потока, не о фактах. */
    @Test
    @DisplayName("U8.4 — запись периметра получают оба тенанта")
    void u8_4_aPerimeterRecordReachesBothTenants() {
        StreamRegistry registry = registry();
        RecordingEmitterChannel first = subscribe(registry, TENANT, null);
        RecordingEmitterChannel second = subscribe(registry, OTHER_TENANT, null);

        registry.broadcast(perimeterRecord(Constants.StreamRecords.PULSE));

        assertThat(framesOf(first)).singleElement()
                .extracting(SseFrame::eventName).isEqualTo(Constants.StreamRecords.PULSE);
        assertThat(framesOf(second)).singleElement()
                .extracting(SseFrame::eventName).isEqualTo(Constants.StreamRecords.PULSE);
    }

    /** Обход рассылки идёт по подпискам, а не по окнам. */
    @Test
    @DisplayName("U8.5 — тенант с окном, но без подписок, записи периметра не получает")
    void u8_5_aTenantWithAWindowButNoSubscriptionsGetsNothing() {
        StreamRegistry registry = registry();
        RecordingEmitterChannel gone = subscribe(registry, TENANT, null);
        registry.publish(TENANT, fact("E1"));
        gone.fireCompletion();
        RecordingEmitterChannel survivor = subscribe(registry, OTHER_TENANT, null);
        Integer attemptsBefore = gone.attempts();

        registry.broadcast(perimeterRecord(Constants.StreamRecords.PULSE));

        assertThat(gone.attempts())
                .as("окно тенанта осталось, а набора подписок нет — обход его не видит")
                .isEqualTo(attemptsBefore);
        assertThat(framesOf(survivor)).as("тенант с подпиской пульс получил").hasSize(1);
    }

    /** Факт уходит с полем идентичности: по нему браузер просит продолжения. */
    @Test
    @DisplayName("U8.6 — запись с непустой идентичностью уходит с полем id")
    void u8_6_aRecordWithAnIdentityCarriesTheIdField() {
        StreamRegistry registry = registry();
        RecordingEmitterChannel channel = subscribe(registry, TENANT, null);

        registry.publish(TENANT, fact("E1"));

        assertThat(framesOf(channel)).singleElement()
                .satisfies(frame -> {
                    assertThat(frame.carriesId()).as("поле идентичности поставлено").isTrue();
                    assertThat(frame.id()).isEqualTo("E1");
                });
    }

    /** У записи периметра поля идентичности нет вовсе. */
    @Test
    @DisplayName("U8.7 — запись периметра уходит без поля id")
    void u8_7_aPerimeterRecordCarriesNoIdField() {
        StreamRegistry registry = registry();
        RecordingEmitterChannel channel = subscribe(registry, TENANT, null);

        registry.broadcast(perimeterRecord(Constants.StreamRecords.PULSE));
        registry.broadcast(perimeterRecord(Constants.StreamRecords.GAP));

        assertThat(framesOf(channel))
                .as("браузер не запросит продолжения с записи, которой в потоке фактов нет")
                .hasSize(2)
                .allSatisfy(frame -> assertThat(frame.carriesId()).isFalse());
    }

    /** Вход тика пульса: подписок нет — бить некому. */
    @Test
    @DisplayName("U8.8 — без единой подписки предикат наличия отвечает ложью")
    void u8_8_withoutAnySubscriptionThePredicateIsFalse() {
        assertThat(registry().hasSubscriptions())
                .as("пульс бьётся по живости потребителя, а не планировщика")
                .isFalse();
    }

    /** И наоборот: хотя бы одна подписка делает предикат истинным. */
    @Test
    @DisplayName("U8.9 — с открытой подпиской предикат наличия отвечает истиной")
    void u8_9_withAnOpenSubscriptionThePredicateIsTrue() {
        StreamRegistry registry = registry();
        subscribe(registry, TENANT, null);

        assertThat(registry.hasSubscriptions()).isTrue();
    }

    /** Отказ ввода-вывода закрывает подписку и наружу не уходит. */
    @Test
    @DisplayName("U9.1 — отказ ввода-вывода завершает подписку с ошибкой, а рассылку не роняет")
    void u9_1_anIoFailureCompletesTheSubscriptionAndDoesNotBreakTheBroadcast() {
        StreamRegistry registry = registry();
        RecordingEmitterChannel failing = subscribe(registry, TENANT, null);
        failing.failWith(new IOException("сессия оборвалась"));

        assertThatCode(() -> registry.publish(TENANT, fact("E1")))
                .as("вызывающий рассылку отказа не видит")
                .doesNotThrowAnyException();

        assertThat(failing.completedWith())
                .as("подписка завершена с ошибкой")
                .isInstanceOf(IOException.class);
    }

    /** Негодное состояние потока ведёт к тому же исходу, что и отказ ввода-вывода. */
    @Test
    @DisplayName("U9.2 — запись в уже завершённый поток ведёт к тому же исходу")
    void u9_2_writingToACompletedStreamEndsTheSameWay() {
        StreamRegistry registry = registry();
        SseEmitter emitter = registry.open(TENANT, null);
        RecordingEmitterChannel channel = RecordingEmitterChannel.attachedTo(emitter);
        emitter.complete();

        assertThatCode(() -> registry.publish(TENANT, fact("E1")))
                .as("обе формы отказа доставки ведут к одному")
                .doesNotThrowAnyException();

        assertThat(channel.completedWith())
                .as("поток завершён с ошибкой негодного состояния")
                .isInstanceOf(IllegalStateException.class);
    }

    /** Рассылка не прерывается на отказавшей подписке. */
    @Test
    @DisplayName("U9.3 — соседи отказавшей подписки получают запись целиком")
    void u9_3_theNeighboursOfAFailingSubscriptionReceiveTheRecord() {
        StreamRegistry registry = new StreamRegistry(properties(3, 3));
        RecordingEmitterChannel first = subscribe(registry, TENANT, null);
        RecordingEmitterChannel middle = subscribe(registry, TENANT, null);
        RecordingEmitterChannel last = subscribe(registry, TENANT, null);
        middle.failWith(new IOException("сессия оборвалась"));

        registry.publish(TENANT, fact("E1"));

        assertThat(framesOf(first)).as("первая получила запись").hasSize(1);
        assertThat(framesOf(last)).as("третья получила её же, обход не прерван").hasSize(1);
        assertThat(framesOf(middle)).as("отказавшая не получила ничего").isEmpty();
    }

    /** Отказ у единственной подписки снимает отображение тенанта. */
    @Test
    @DisplayName("U9.4 — отказ у единственной подписки снимает отображение тенанта")
    void u9_4_aFailureOfTheOnlySubscriptionDropsTheTenantEntry() {
        StreamRegistry registry = registry();
        RecordingEmitterChannel only = subscribe(registry, TENANT, null);
        only.failWith(new IOException("сессия оборвалась"));

        registry.publish(TENANT, fact("E1"));
        only.fireError(only.completedWith());

        assertThat(registry.hasSubscriptions())
                .as("снятие приходит обратным вызовом ошибки — его и регистрирует открытие")
                .isFalse();
    }

    /** Снятие затрагивает только отказавшую подписку. */
    @Test
    @DisplayName("U9.6 — отказ у одного тенанта не задевает подписки другого")
    void u9_6_aFailureOfOneTenantDoesNotTouchAnother() {
        StreamRegistry registry = registry();
        RecordingEmitterChannel failing = subscribe(registry, TENANT, null);
        RecordingEmitterChannel healthy = subscribe(registry, OTHER_TENANT, null);
        failing.failWith(new IOException("сессия оборвалась"));

        registry.publish(TENANT, fact("E1"));
        failing.fireError(failing.completedWith());
        registry.publish(OTHER_TENANT, fact("E2"));

        assertThat(framesOf(healthy)).as("подписка другого тенанта получает свои факты").hasSize(1);
        assertThat(registry.hasSubscriptions()).as("и остаётся открытой").isTrue();
    }
}

package com.example.bff.unit.domain;

import static com.example.bff.unit.domain.TicketForge.SECRET;
import static com.example.bff.unit.domain.TicketForge.SIGNATURE_SEPARATOR;
import static com.example.bff.unit.domain.TicketForge.fieldsOf;
import static com.example.bff.unit.domain.TicketForge.forge;
import static com.example.bff.unit.domain.TicketForge.service;
import static com.example.bff.unit.domain.TicketForge.serviceWith;
import static com.example.bff.unit.domain.TicketForge.significantPartOf;
import static com.example.bff.unit.domain.TicketForge.signaturePartOf;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bff.domain.SubscriptionTicket;
import com.example.bff.domain.SubscriptionTicketService;
import com.example.bff.exception.TicketRejectedException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Билет подписки: состав, порядок охран и разбор — группы `U1` и `U2`
 * документа `.claude/tests/cases/bff-perimeter-logic.md`
 * (docs/architecture/contracts.md §«Подписку открывает билет, а не сам
 * токен»).
 *
 * <p><b>Почему это стои́т проверять.</b> Билет — единственное, чем
 * подписка отличает своего предъявителя от чужого: таблицы выданных нет,
 * и всё держится подписью. Ошибка здесь не падает, а ОТКРЫВАЕТ: билет,
 * принятый с разошедшимся составом или с истёкшим сроком, ведёт себя как
 * годный, и отличить такую сессию от законной нечем.
 *
 * <p><b>Часть кейсов собирает значение САМА</b> — знанием секрета
 * (`TicketForge`): охраны, лежащие после проверки подписи, предъявителю
 * закрыты, и без кузницы их ветви не спрашиваются вовсе.
 *
 * <p><b>Класс сменил прежнюю пробу того же предмета</b>
 * ({@code com.example.bff.SubscriptionTicketServiceTest}): её шесть
 * утверждений — разбор обратно, подделка, чужой секрет, срок,
 * ненастроенная выдача, непредъявленный билет — стоя́т здесь клетками
 * `U2.1`, `U2.7`, `U2.8`, `U2.10`, `U1.2` с `U2.11` и `U2.2`, и второй
 * записи тех же ожиданий не заводится.
 */
class SubscriptionTicketServiceTest {

    private static final String SUBJECT = "user-42";
    private static final String TENANT = "tenant-7";

    /** Предел попыток у кейсов, чьё предусловие — попадание в одну секунду. */
    private static final int ATTEMPT_LIMIT = 50;

    /**
     * Предел ожидания смены секунды. Часов предмет операндом не принимает,
     * и подставить момент нечем: границу приходится ДОЖИДАТЬСЯ, а предел
     * держит кейс конечным, если ожидание не сбудется.
     */
    private static final int WAIT_LIMIT_SECONDS = 3;

    /** Состав подписываемого значения: три поля и ни одного сверх. */
    @Test
    @DisplayName("U1.1 — билет состои́т из значения и подписи, а значение несёт ровно три поля")
    void u1_1_aTicketCarriesItsValueItsSignatureAndExactlyThreeFields() {
        String issued = service().issue(SUBJECT, TENANT);

        assertThat(issued.split("\\" + SIGNATURE_SEPARATOR))
                .as("значение и его подпись, разделённые служебным символом")
                .hasSize(2);
        String[] fields = fieldsOf(issued);
        assertThat(fields).as("субъект, тенант и момент негодности — и ничего сверх").hasSize(3);
        assertThat(fields[0]).isEqualTo(SUBJECT);
        assertThat(fields[1]).isEqualTo(TENANT);
        assertThat(Long.parseLong(fields[2])).as("третье поле — момент негодности целыми секундами").isPositive();
    }

    /** Незаданная выдача закрывает операцию: незаданное есть отказ, а не разрешение. */
    @Test
    @DisplayName("U1.2 — пустой секрет билета не выдаёт")
    void u1_2_anEmptySecretIssuesNoTicket() {
        assertThatThrownBy(() -> serviceWith("", Duration.ofMinutes(10)).issue(SUBJECT, TENANT))
                .isInstanceOf(TicketRejectedException.class);
    }

    /** Годность секрета мерится непустотой ПОСЛЕ отсечения пробелов. */
    @Test
    @DisplayName("U1.3 — секрет из пробелов равносилен пустому")
    void u1_3_aBlankSecretIsTheSameAsAnEmptyOne() {
        assertThatThrownBy(() -> serviceWith("   ", Duration.ofMinutes(10)).issue(SUBJECT, TENANT))
                .isInstanceOf(TicketRejectedException.class);
    }

    /** Порядок охран наблюдаем: они отвечают РАЗНЫМИ классами. */
    @Test
    @DisplayName("U1.4 — охрана секрета стои́т первой: отказ приходит классом билета")
    void u1_4_theSecretGuardComesFirst() {
        assertThatThrownBy(() -> serviceWith("", Duration.ofMinutes(10)).issue(SUBJECT + "|x", TENANT))
                .as("негодный аргумент тоже есть, но до него не доходит")
                .isInstanceOf(TicketRejectedException.class);
    }

    /** Разделитель в субъекте — негодный аргумент, а не негодный билет. */
    @Test
    @DisplayName("U1.5 — субъект со служебным разделителем билета не получает")
    void u1_5_aSubjectWithTheSeparatorIsRefused() {
        assertThatThrownBy(() -> service().issue(SUBJECT + "|x", TENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(TicketRejectedException.class);
    }

    /** Охрана стои́т на обоих полях, а не на одном. */
    @Test
    @DisplayName("U1.6 — тенант со служебным разделителем билета не получает")
    void u1_6_aTenantWithTheSeparatorIsRefused() {
        assertThatThrownBy(() -> service().issue(SUBJECT, TENANT + "|x"))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(TicketRejectedException.class);
    }

    /** Ни соли, ни счётчика: две выдачи одной секунды совпадают побайтно. */
    @Test
    @DisplayName("U1.7 — две выдачи в пределах одной секунды совпадают побайтно")
    void u1_7_twoIssuesWithinASecondAreIdentical() {
        SubscriptionTicketService service = service();
        String first = service.issue(SUBJECT, TENANT);
        String second = service.issue(SUBJECT, TENANT);
        for (int attempt = 0; attempt < ATTEMPT_LIMIT && expiryDiffers(first, second); attempt++) {
            first = service.issue(SUBJECT, TENANT);
            second = service.issue(SUBJECT, TENANT);
        }

        assertThat(expiryDiffers(first, second))
                .as("предусловие кейса: обе выдачи пришлись на одну секунду")
                .isFalse();
        assertThat(second).as("второе значение первое не отменяет — оно то же самое").isEqualTo(first);
    }

    /** Смена секунды меняет билет, и меняет ровно третьим полем. */
    @Test
    @DisplayName("U1.8 — выдачи по разные стороны смены секунды различаются только моментом")
    void u1_8_issuesAcrossASecondDifferOnlyInTheirExpiry() {
        SubscriptionTicketService service = service();
        String first = service.issue(SUBJECT, TENANT);
        String second = first;
        Instant deadline = Instant.now().plusSeconds(WAIT_LIMIT_SECONDS);
        while (isFalse(expiryDiffers(first, second)) && Instant.now().isBefore(deadline)) {
            second = service.issue(SUBJECT, TENANT);
        }

        assertThat(expiryDiffers(first, second))
                .as("предусловие кейса: между выдачами сменилась секунда")
                .isTrue();
        assertThat(second).as("значения различаются").isNotEqualTo(first);
        assertThat(fieldsOf(second)[0]).isEqualTo(fieldsOf(first)[0]);
        assertThat(fieldsOf(second)[1]).as("различие только в третьем поле").isEqualTo(fieldsOf(first)[1]);
    }

    /** Момент негодности округляется ВНИЗ: срок короче объявленного не более чем на секунду. */
    @Test
    @DisplayName("U1.9 — момент негодности округляется вниз до секунды")
    void u1_9_theExpiryIsRoundedDownToASecond() {
        Duration ttl = Duration.ofMinutes(5);
        Instant before = Instant.now();
        String issued = serviceWith(SECRET, ttl).issue(SUBJECT, TENANT);
        Instant after = Instant.now();

        Instant expiry = Instant.ofEpochSecond(Long.parseLong(fieldsOf(issued)[2]));
        assertThat(expiry).as("дольше объявленного срок не бывает").isBeforeOrEqualTo(after.plus(ttl));
        assertThat(expiry).as("а короче — не более чем на секунду").isAfter(before.plus(ttl).minusSeconds(1));
    }

    /** Пустое поле едет пустым и разбирается обратно пустым. */
    @Test
    @DisplayName("U1.10 — пустой субъект едет пустым и возвращается пустым")
    void u1_10_anEmptySubjectTravelsAndReturnsEmpty() {
        SubscriptionTicketService service = service();

        SubscriptionTicket verified = service.verify(service.issue("", TENANT));

        assertThat(verified.subject()).as("пустое значение едет пустым").isEmpty();
        assertThat(verified.tenantId()).isEqualTo(TENANT);
    }

    /**
     * Пустая ссылка тенанта роняет охрану разыменованием — «наш дефект»,
     * а не отказ вызывающего; дом ветви молчит (находка `F1`). Состояние
     * недостижимо: значение приходит от владельца членств, чья форма
     * тенанта несёт.
     */
    @Test
    @DisplayName("U1.11 — пустая ссылка тенанта роняет выдачу разыменованием")
    void u1_11_anAbsentTenantBreaksTheGuardByDereference() {
        assertThatThrownBy(() -> service().issue(SUBJECT, null))
                .as("охрана разделителя спрашивает значение, не проверив его наличия")
                .isInstanceOf(NullPointerException.class);
    }

    /** Разбор отдаёт ровно то, что подписывалось, и ничего сверх. */
    @Test
    @DisplayName("U2.1 — выданный билет разбирается в свой субъект и свой тенант")
    void u2_1_anIssuedTicketVerifiesBackToItsSubjectAndTenant() {
        SubscriptionTicketService service = service();

        SubscriptionTicket verified = service.verify(service.issue(SUBJECT, TENANT));

        assertThat(verified.subject()).isEqualTo(SUBJECT);
        assertThat(verified.tenantId()).isEqualTo(TENANT);
        assertThat(SubscriptionTicket.class.getRecordComponents())
                .as("иных полей разобранное значение не несёт")
                .hasSize(2);
    }

    /** Непредъявленный билет подписки не открывает. */
    @Test
    @DisplayName("U2.2 — непредъявленный билет отвергается")
    void u2_2_anAbsentTicketIsRejected() {
        assertThatThrownBy(() -> service().verify(null))
                .isInstanceOf(TicketRejectedException.class);
    }

    /** Пустая строка и пробелы — та же непредъявленность. */
    @Test
    @DisplayName("U2.3 — пустой билет отвергается тем же классом")
    void u2_3_aBlankTicketIsRejectedTheSameWay() {
        SubscriptionTicketService service = service();

        assertThatThrownBy(() -> service.verify("")).isInstanceOf(TicketRejectedException.class);
        assertThatThrownBy(() -> service.verify("   ")).isInstanceOf(TicketRejectedException.class);
    }

    /** Без разделителя подписи разбирать нечего. */
    @Test
    @DisplayName("U2.4 — значение без разделителя подписи отвергается")
    void u2_4_aValueWithoutTheSignatureSeparatorIsRejected() {
        assertThatThrownBy(() -> service().verify("значение-без-подписи"))
                .isInstanceOf(TicketRejectedException.class);
    }

    /** Отказ декодера переводится в отказ билета и наружу не выходит. */
    @Test
    @DisplayName("U2.5 — негодная кодировка значимой части отвергается классом билета")
    void u2_5_anUndecodableValueIsRejectedAsATicket() {
        assertThatThrownBy(() -> service().verify("!!!" + SIGNATURE_SEPARATOR + "подпись"))
                .isInstanceOf(TicketRejectedException.class);
    }

    /** То же и у части подписи. */
    @Test
    @DisplayName("U2.6 — негодная кодировка части подписи отвергается тем же классом")
    void u2_6_anUndecodableSignatureIsRejectedTheSameWay() {
        String issued = service().issue(SUBJECT, TENANT);

        assertThatThrownBy(() -> service().verify(significantPartOf(issued) + SIGNATURE_SEPARATOR + "!!!"))
                .isInstanceOf(TicketRejectedException.class);
    }

    /**
     * Подмена символа значимой части ловится подписью. Постоянное время
     * сравнения выходом кейса не наблюдается и ожиданием здесь не стои́т.
     */
    @Test
    @DisplayName("U2.7 — билет с подменённым символом значения отвергается")
    void u2_7_aTamperedValueIsRejected() {
        String issued = service().issue(SUBJECT, TENANT);
        String significant = significantPartOf(issued);
        String tampered = (significant.startsWith("A") ? "B" : "A") + significant.substring(1);

        assertThatThrownBy(() -> service().verify(tampered + SIGNATURE_SEPARATOR + signaturePartOf(issued)))
                .isInstanceOf(TicketRejectedException.class);
    }

    /** Секрет общий у реплик: билет чужого секрета своим не становится. */
    @Test
    @DisplayName("U2.8 — билет чужого секрета отвергается")
    void u2_8_aTicketOfAForeignSecretIsRejected() {
        String foreign = serviceWith("foreign-secret", Duration.ofMinutes(10)).issue(SUBJECT, TENANT);

        assertThatThrownBy(() -> service().verify(foreign))
                .isInstanceOf(TicketRejectedException.class);
    }

    /**
     * Сторона границы, где билет ещё годен: момент негодности —
     * НАЧАЛО следующей секунды, а разбор идёт внутри текущей.
     * Состояние недостижимо предъявителю — значение подписано тестом.
     */
    @Test
    @DisplayName("U2.9 — момент негодности в начале следующей секунды билет принимает")
    void u2_9_anExpiryAtTheNextSecondIsStillAccepted() {
        SubscriptionTicketService service = service();
        SubscriptionTicket verified = null;
        for (int attempt = 0; attempt < ATTEMPT_LIMIT && Objects.isNull(verified); attempt++) {
            long second = Instant.now().getEpochSecond();
            SubscriptionTicket parsed = service.verify(forge(SECRET, SUBJECT, TENANT, String.valueOf(second + 1)));
            verified = Objects.equals(second, Instant.now().getEpochSecond()) ? parsed : null;
        }

        assertThat(verified).as("предусловие кейса: разбор пришёлся на ту же секунду").isNotNull();
        assertThat(verified.tenantId()).isEqualTo(TENANT);
    }

    /** Противоположная сторона: момент негодности в начале текущей секунды уже истёк. */
    @Test
    @DisplayName("U2.10 — момент негодности в начале текущей секунды билет отвергает")
    void u2_10_anExpiryAtTheCurrentSecondIsRejected() {
        String expired = forge(SECRET, SUBJECT, TENANT, String.valueOf(Instant.now().getEpochSecond()));

        assertThatThrownBy(() -> service().verify(expired))
                .as("наблюдаемая цена округления вниз: начало секунды уже позади")
                .isInstanceOf(TicketRejectedException.class);
    }

    /** Незаданная выдача закрывает и разбор: обе операции, а не одну. */
    @Test
    @DisplayName("U2.11 — при пустом секрете разбор отвергает билет так же, как выдача")
    void u2_11_anUnconfiguredSecretRefusesVerificationToo() {
        assertThatThrownBy(() -> serviceWith("", Duration.ofMinutes(10)).verify(null))
                .isInstanceOf(TicketRejectedException.class);
    }

    /** Билет переиспользуем: следа предъявления не остаётся. */
    @Test
    @DisplayName("U2.12 — один билет разбирается дважды с тем же исходом")
    void u2_12_oneTicketVerifiesTwiceWithTheSameOutcome() {
        SubscriptionTicketService service = service();
        String issued = service.issue(SUBJECT, TENANT);

        SubscriptionTicket first = service.verify(issued);
        SubscriptionTicket second = service.verify(issued);

        assertThat(second).as("следа предъявления не остаётся ни в одном поле").isEqualTo(first);
    }

    /** Охрана состава: полей меньше трёх — не тот билет (недостижимо предъявителю). */
    @Test
    @DisplayName("U2.13 — значение верного подписания из двух полей отвергается")
    void u2_13_aForgedValueOfTwoFieldsIsRejected() {
        assertThatThrownBy(() -> service().verify(forge(SECRET, SUBJECT, TENANT)))
                .isInstanceOf(TicketRejectedException.class);
    }

    /** Охрана мерит равенство числу полей, а не «не меньше». */
    @Test
    @DisplayName("U2.14 — значение верного подписания из четырёх полей отвергается так же")
    void u2_14_aForgedValueOfFourFieldsIsRejectedTheSameWay() {
        String forged = forge(SECRET, SUBJECT, TENANT,
                String.valueOf(Instant.now().plusSeconds(600).getEpochSecond()), "лишнее");

        assertThatThrownBy(() -> service().verify(forged))
                .isInstanceOf(TicketRejectedException.class);
    }

    /**
     * Клейм «негодность наружу не различается» держится недостижимостью,
     * а не кодом: разбор момента негодности лежит ПОСЛЕ проверки подписи
     * и отвечает своим классом (находка `F2`).
     */
    @Test
    @DisplayName("U2.15 — нечисловой момент негодности отвечает не классом билета")
    void u2_15_aNonNumericExpiryAnswersWithAnotherClass() {
        String forged = forge(SECRET, SUBJECT, TENANT, "не-число");

        assertThatThrownBy(() -> service().verify(forged))
                .isInstanceOf(NumberFormatException.class)
                .isNotInstanceOf(TicketRejectedException.class);
    }

    /** Третий класс отказа на той же тропе — та же находка `F2`. */
    @Test
    @DisplayName("U2.16 — момент негодности вне диапазона отвечает третьим классом")
    void u2_16_anOutOfRangeExpiryAnswersWithAThirdClass() {
        String forged = forge(SECRET, SUBJECT, TENANT, String.valueOf(Long.MAX_VALUE));

        assertThatThrownBy(() -> service().verify(forged))
                .isInstanceOf(DateTimeException.class)
                .isNotInstanceOf(TicketRejectedException.class);
    }

    /** Охраны непустоты полей у разбора нет: пустой субъект возвращается как есть. */
    @Test
    @DisplayName("U2.17 — пустое поле субъекта разбор принимает")
    void u2_17_anEmptySubjectFieldIsAccepted() {
        String forged = forge(SECRET, "", TENANT, String.valueOf(Instant.now().plusSeconds(600).getEpochSecond()));

        SubscriptionTicket verified = service().verify(forged);

        assertThat(verified.subject()).isEmpty();
        assertThat(verified.tenantId()).isEqualTo(TENANT);
    }

    private Boolean expiryDiffers(String first, String second) {
        return isFalse(Objects.equals(fieldsOf(first)[2], fieldsOf(second)[2]));
    }
}

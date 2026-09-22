package com.example.bff.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;

/**
 * Клетка группы {@code B3}, чей предмет — соотношение ДВУХ ОБЪЯВЛЕННЫХ
 * УМОЛЧАНИЙ: срока билета и срока жизни соединения
 * (.claude/tests/cases/bff.md, {@code B3.10}).
 *
 * <p><b>Читается объявленное умолчание, а не поведение, и это не обход
 * формы ящика.</b> Срок соединения поверхностью не наблюдается ничем:
 * единственная его тень снаружи — плановое закрытие подписки через
 * тридцать минут, и кейс, ждущий его, стоил бы получаса прогона. Предмет
 * здесь — ИНВАРИАНТ между двумя объявленными величинами, и форма его та
 * же, что у счёта {@code @Scheduled}-методов дерева ({@code B5.10}): она
 * мерит объявление, а не поведение.
 *
 * <p><b>Значения берутся из умолчаний плейсхолдеров, а не из контекста
 * прогона.</b> Контекст ящика обе оси переопределяет — на том и стои́т
 * весь субстрат, — поэтому прочитанные у него значения говорили бы о
 * прогоне, а не о том, с чем сервис приедет в окружение, где переменных
 * не задано.
 *
 * <p><b>Клетка красна ожиданием из дома, а не ослабленным ассертом</b>
 * (находка F-1): срок соединения объявлен втрое больше срока билета, и
 * штатное переподключение {@code EventSource} после планового закрытия
 * получает отказ ВСЕГДА. Метка изымает её из умолчания прогона, пока долг
 * не закрыт (.claude/work/backlog.md §«Срок билета подписки короче срока
 * соединения, и штатный разрыв попадает вне его»).
 */
class DeclaredStreamTermsBoxTest extends SharedBffBox {

    /** Носитель объявленных умолчаний сервиса. */
    private static final String DECLARATION = "/application.yaml";

    /** Имя переменной окружения, чьё умолчание есть срок билета. */
    private static final String TICKET_TTL_VARIABLE = "PERIMETER_TICKET_TTL";

    /** Имя переменной окружения, чьё умолчание есть срок соединения. */
    private static final String CONNECTION_TIMEOUT_VARIABLE = "PERIMETER_STREAM_CONNECTION_TIMEOUT";

    @Test
    @Tag("debt")
    @DisplayName("B3.10 — Штатный разрыв по сроку соединения обязан попадать ВНУТРЬ срока билета")
    void b3_10_theGracefulBreakMustFallWithinTheTicketTerm() {
        Duration ticketTtl = declaredDefaultOf(TICKET_TTL_VARIABLE);
        Duration connectionTimeout = declaredDefaultOf(CONNECTION_TIMEOUT_VARIABLE);

        // Подписка, дожившая до планового закрытия соединения, обязана
        // переоткрываться ТЕМ ЖЕ билетом: иначе штатное переподключение
        // браузера отвечает отказом всякий раз.
        assertThat(ticketTtl).isGreaterThanOrEqualTo(connectionTimeout);
    }

    /**
     * Умолчание плейсхолдера названной переменной окружения — то самое
     * значение, с которым сервис приедет в окружение, её не задавшее.
     *
     * <p>Разбирается тем же стилем, что и сам каркас: свой разбор
     * длительности означал бы, что клетка мерит собственную сборку
     * значения, а не объявленное.
     *
     * @param variable имя переменной окружения
     */
    private static Duration declaredDefaultOf(String variable) {
        Matcher placeholder = Pattern.compile(Pattern.quote("${" + variable + ":") + "([^}]+)}")
                .matcher(declaration());
        if (isFalse(placeholder.find())) {
            throw new AssertionError("Умолчания переменной " + variable + " в " + DECLARATION + " нет");
        }
        return DurationStyle.detectAndParse(placeholder.group(1));
    }

    private static String declaration() {
        try (InputStream declared = DeclaredStreamTermsBoxTest.class.getResourceAsStream(DECLARATION)) {
            if (Objects.isNull(declared)) {
                throw new AssertionError("Носителя объявленных умолчаний " + DECLARATION + " нет");
            }
            return new String(declared.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new IllegalStateException("Носитель объявленных умолчаний не прочитан", unreadable);
        }
    }
}

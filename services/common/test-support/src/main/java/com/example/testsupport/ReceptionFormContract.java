package com.example.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Форма приёма целиком: перечень имён конверта и то, чего предмет НЕ
 * делает — клетка `U15.8` и группа `U16` (кроме `U16.4`-`U16.10`, чьи
 * наблюдения поведенческие) документа
 * `.claude/tests/cases/durable-reception.md`.
 *
 * <p><b>Наблюдение здесь идёт ИСХОДНИКОМ, а не прогоном, и это по
 * существу предмета:</b> «в базу не пишется ничего» есть утверждение об
 * отсутствии тропы, а отсутствие тропы прогоном не наблюдается — его
 * читают по коду (`.claude/rules/measurement-commands.md`).
 *
 * <p><b>Мерится исполняемое тело, а не текст файла:</b> комментарии
 * сняты, потому что javadoc законно называет соседей по имени — например,
 * границу, которой класс делегирует, — и проба падала бы на упоминании,
 * а не на тропе.
 */
public abstract class ReceptionFormContract {

    /** Пять имён заголовков конверта — копий их на проводе пять, и все обязаны сойтись. */
    private static final List<String> ENVELOPE_HEADERS =
            List.of("eventId", "eventType", "version", "occurredAt", "traceContext");

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("^\\s*//.*$", Pattern.MULTILINE);
    private static final Pattern CLOCK = Pattern.compile(
            "OffsetDateTime\\.now|Instant\\.now|System\\.currentTimeMillis");

    // --- порты к своему дереву -------------------------------------------

    /** Девять исходников предмета в своём дереве. */
    protected abstract List<Path> subjectSources();

    /** Исходник чтеца конверта своего дерева. */
    protected abstract Path envelopeReaderSource();

    /** Те из девяти, что читают часы процесса: ряды экспорта и слушатель назначения. */
    protected abstract List<Path> clockReadingSources();

    /** Перечень имён заголовков конверта, объявленный своим деревом. */
    protected abstract List<String> eventHeaderNames();

    // --- U15.8: перечень имён заголовков ----------------------------------

    @Test
    @DisplayName("U15.8 — перечни имён заголовков двух деревьев совпадают дословно")
    void u15_8_theEnvelopeHeaderNamesAreTheSameInBothTrees() {
        assertThat(eventHeaderNames())
                .as("носителя имён в общей библиотеке не заводится, и охрана у копий — совпадение перечней")
                .containsExactlyElementsOf(ENVELOPE_HEADERS);
    }

    // --- U15: тождество копий, которое мерит прогон корпуса ----------------

    @Test
    @DisplayName("U15.1 — семейство трекера смещений объявлено в реестре копий")
    void u15_1_theOffsetTrackerFamilyIsDeclared() throws IOException {
        assertThat(copyRegistry())
                .as("тождество ТЕЛ копий мерит `py tools/peer-copy-check.py`; необъявленное семейство "
                        + "ему невидимо, и расхождение осталось бы ровно там, где его никто не ищет")
                .contains("ReceptionOffsetTracker — ожидаемое смещение партиции");
    }

    @Test
    @DisplayName("U15.5 — семейство маркера остановки объявлено в реестре копий")
    void u15_5_theHaltMarkerFamilyIsDeclared() throws IOException {
        assertThat(copyRegistry()).contains("ReceptionHaltMarker — флаг остановки приёма");
    }

    @Test
    @DisplayName("U15.6 — семейство модели моментов объявлено в реестре копий")
    void u15_6_thePairMomentsFamilyIsDeclared() throws IOException {
        assertThat(copyRegistry()).contains("ReceptionPairMoments — моменты пары состояния приёма");
    }

    @Test
    @DisplayName("U15.11 — семейство трёх запросов полноты объявлено в реестре копий")
    void u15_11_theCompletenessQueriesFamilyIsDeclared() throws IOException {
        assertThat(copyRegistry())
                .as("текст запроса — предмет базы, а ТОЖДЕСТВО текстов двух деревьев не спрашивал "
                        + "ни один кейс (пробел `G2`)")
                .contains("ReceptionStateCompletenessQueries — три запроса полноты");
    }

    // --- U16: чего предмет не делает --------------------------------------

    @Test
    @DisplayName("U16.1 — в базу предмет не пишет напрямую: репозиториев и границ хранения у него нет")
    void u16_1_nothingIsWrittenToTheDatabaseDirectly() throws IOException {
        assertThat(sourcesMentioning("Repository", "EntityManager", "JdbcTemplate", "@Transactional"))
                .as("запись идёт только через службу приёма, у которой свой писатель и своя транзакция")
                .isEmpty();
    }

    @Test
    @DisplayName("U16.2 — в брокер предмет не публикует ничего")
    void u16_2_nothingIsPublishedToTheBroker() throws IOException {
        assertThat(sourcesMentioning("KafkaTemplate", "ProducerRecord", "send("))
                .as("durable-потребитель событий не производит")
                .isEmpty();
    }

    @Test
    @DisplayName("U16.3 — чтец конверта смещения не фиксирует и не двигает")
    void u16_3_theEnvelopeReaderNeitherCommitsNorMovesTheOffset() throws IOException {
        String body = executableBody(envelopeReaderSource());

        assertThat(body)
                .as("порядок фиксации смещения — предмет тропы приёма, а не содержимого")
                .doesNotContain("commitSync", "commitAsync", "Acknowledgment", "acknowledge", "seek");
    }

    @Test
    @DisplayName("U16.11 — доменной модели следствия предмет не собирает")
    void u16_11_theConsequenceModelIsBuiltByTheMapper() throws IOException {
        assertThat(sourcesMentioning("AuditRecord.builder", "DealFact.builder", "IncidentFact.builder"))
                .as("перевод в домен делает маппер границы")
                .isEmpty();
    }

    @Test
    @DisplayName("U16.12 — часы процесса читают ровно два класса дерева из девяти")
    void u16_12_exactlyTwoClassesReadTheProcessClock() throws IOException {
        List<Path> reading = new ArrayList<>();
        for (Path source : subjectSources()) {
            if (CLOCK.matcher(executableBody(source)).find()) {
                reading.add(source);
            }
        }

        assertThat(subjectSources())
                .as("популяция предмета в своём дереве — девять классов")
                .hasSize(9);
        assertThat(reading)
                .as("прочие семь получают моменты полями строки и аргументом устаревания")
                .containsExactlyInAnyOrderElementsOf(clockReadingSources());
    }

    @Test
    @DisplayName("U16.13 — торгового решения предмет не принимает и команд не строит")
    void u16_13_noTradingDecisionIsMadeHere() throws IOException {
        List<String> tradingImports = new ArrayList<>();
        for (Path source : subjectSources()) {
            for (String line : Files.readString(existing(source), StandardCharsets.UTF_8).split("\n")) {
                String trimmed = line.strip();
                if (trimmed.startsWith("import ") && tradingType(trimmed)) {
                    tradingImports.add(source.getFileName() + ": " + trimmed);
                }
            }
        }

        assertThat(tradingImports)
                .as("то, что в общую библиотеку НЕ уезжает, не приезжает и к потребителю журнала")
                .isEmpty();
    }

    // --- оснастка ---------------------------------------------------------

    /**
     * Реестр объявленных семейств копий — от каталога модуля, из которого
     * прогон и запускается. Та же форма чтения чужого носителя, что у
     * пробы стыка рядов с манифестом окружения.
     */
    private static String copyRegistry() throws IOException {
        return Files.readString(
                existing(Path.of("..", "..", "tools", "peer-copy-check.py")), StandardCharsets.UTF_8);
    }

    /** Исходники предмета, чьё исполняемое тело содержит хоть одну из строк. */
    private List<String> sourcesMentioning(String... forbidden) throws IOException {
        List<String> found = new ArrayList<>();
        for (Path source : subjectSources()) {
            String body = executableBody(source);
            for (String needle : forbidden) {
                if (body.contains(needle)) {
                    found.add(source.getFileName() + ": " + needle);
                }
            }
        }
        return found;
    }

    /** Исполняемое тело: текст исходника за вычетом комментариев. */
    private static String executableBody(Path source) throws IOException {
        String text = Files.readString(existing(source), StandardCharsets.UTF_8);
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(text).replaceAll(Matcher.quoteReplacement("")))
                .replaceAll("");
    }

    private static Boolean tradingType(String importLine) {
        return importLine.contains(".tradingcore.") || importLine.contains(".strategies.")
                || importLine.contains(".marketdata.") || importLine.contains("Command");
    }

    private static Path existing(Path source) {
        assertThat(Files.isRegularFile(source))
                .as("исходника %s нет — проба мерила бы пустоту", source)
                .isTrue();
        return source;
    }
}

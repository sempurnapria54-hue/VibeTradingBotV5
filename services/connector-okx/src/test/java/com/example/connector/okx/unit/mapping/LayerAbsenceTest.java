package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.mapping.AlgoOrderMapper;
import com.example.connector.okx.mapping.DealCashFlowMapper;
import com.example.connector.okx.mapping.OrderMapper;
import com.example.connector.okx.mapping.PositionMapper;
import com.example.connector.okx.mapping.TradeFeeRateMapper;
import com.example.connector.okx.resolve.OkxCredentialsRejectionResolver;
import com.example.tradingbot.domain.exchange.ExchangeAck;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Отсутствие выходов: чего слой не делает — группа `U32` документа
 * `.claude/tests/cases/okx-mapping.md`
 * (`.claude/rules/codestyle.md` §Маппинг: «маппер делает только перенос
 * данных, доменных решений не принимает»;
 * docs/rules/raw-exchange-dto-boundary.md).
 *
 * <p><b>Базовая сборка:</b> та же, что у соответствующих групп;
 * предмет кейса — <b>несостоявшийся</b> выход. Утверждения об
 * <b>области</b> («ни один класс слоя») читаются по исполняемому телу
 * исходников ({@link LayerSources}), утверждения о составе — рефлексией,
 * утверждения о поведении — прогоном.
 */
class LayerAbsenceTest {

    private static final Path SNAPSHOT_DIRECTORY =
            Path.of("src", "main", "java", "com", "example", "connector", "okx", "snapshot");

    private static final Path REQUEST_DIRECTORY = Path.of("src", "main", "java", "com", "example",
            "connector", "okx", "integration", "external", "api", "model", "okx", "request");

    private static void assertLayerBodiesHaveNo(String label, String regex) {
        Map<String, String> bodies = LayerSources.ofLayer();
        Pattern pattern = Pattern.compile(regex);

        assertThat(bodies).allSatisfy((file, body) -> assertThat(pattern.matcher(body).find())
                .as("%s: %s", label, file)
                .isFalse());
    }

    /** Репозиториев и служб данных у слоя нет — это и есть механический признак уровня. */
    @Test
    @DisplayName("U32.1 — в базу не ходит ни один класс слоя")
    void u32_1_noClassOfTheLayerTouchesADatabase() {
        assertLayerBodiesHaveNo("обращение к базе", "DataService\\b|Repository\\b|EntityManager\\b");
    }

    @Test
    @DisplayName("U32.2 — к площадке не ходит ни один класс слоя")
    void u32_2_noClassOfTheLayerCallsTheExchange() {
        assertLayerBodiesHaveNo("обращение к площадке", "RestClient|WebClient|HttpClient");
    }

    /** Время приезжает эпохой строкой источника. */
    @Test
    @DisplayName("U32.3 — часов не читает ни один класс слоя")
    void u32_3_noClassOfTheLayerReadsAClock() {
        assertLayerBodiesHaveNo("чтение часов",
                "Clock\\b|Instant\\.now|OffsetDateTime\\.now|LocalDate(Time)?\\.now|currentTimeMillis");
    }

    /** Резолвер зовёт шлюз последним шагом чтения. */
    @Test
    @DisplayName("U32.4 — доменный статус не ставит ни заявка, ни условная заявка")
    void u32_4_neitherTransitionSetsTheDomainStatus() {
        OrderMapper orderMapper = Mappers.order();
        AlgoOrderMapper algoOrderMapper = Mappers.algoOrder();
        var source = OkxFixture.order();

        assertThat(orderMapper.snapshotToDomain(orderMapper.integrationToSnapshot(source)).getStatus())
                .isNull();
        assertThat(algoOrderMapper.snapshotToDomain(
                algoOrderMapper.integrationToSnapshot(OkxFixture.algoOrder())).getStatus()).isNull();
    }

    /** Версионирование строки — доменное решение синка, а не маппера. */
    @Test
    @DisplayName("U32.5 — версия строки ставки не ведётся")
    void u32_5_theFeeRateVersionIsNotKept() {
        TradeFeeRateMapper mapper = Mappers.tradeFeeRate();
        var response = OkxFixture.tradeFee();

        var rate = mapper.snapshotToDomain(
                mapper.integrationToSnapshot(response, response.getFeeGroup().getFirst()));

        assertThat(rate.getRefreshCount()).isNull();
    }

    /** Её производит вызывающий: резолв категории — интерпретация факта. */
    @Test
    @DisplayName("U32.6 — категория движения не резолвится")
    void u32_6_theCashFlowCategoryIsNotResolved() {
        DealCashFlowMapper mapper = Mappers.cashFlow();

        assertThat(mapper.snapshotToDomain(mapper.integrationToSnapshot(OkxFixture.cashFlow()))
                .getCategory()).isNull();
    }

    /** Слой резолва доменный, и граница его не требует. */
    @Test
    @DisplayName("U32.7 — торговый исход закрытия на тропе материализации не резолвится")
    void u32_7_theCloseOutcomeIsNotResolved() {
        PositionMapper mapper = Mappers.position();
        var episode = new com.example.tradingbot.domain.model.core.position.Position();
        episode.setStatus(com.example.tradingbot.domain.model.core.position.Position.Status.CLOSED);

        mapper.materializeFromCloseSnapshot(
                mapper.integrationToCloseSnapshot(OkxFixture.positionHistory()), episode);

        assertThat(episode.getExternalCloseType()).isEqualTo("1");
        assertThat(episode.getCloseReason()).isNull();
    }

    /**
     * Бросающих ровно два, и оба живут в конвертере: перевод стороны заявки в домен
     * и перевод направления закрытой позиции. Прочие отказы приходят не от перевода.
     */
    @Test
    @DisplayName("U32.8 — доменного отказа не бросает ни один переход")
    void u32_8_noTransitionThrowsADomainFailure() {
        Map<String, String> bodies = LayerSources.of(List.of("mapping"));
        Pattern throwing = Pattern.compile("throw new (\\w+)");

        assertThat(bodies).allSatisfy((file, body) -> {
            List<String> thrown = throwing.matcher(body).results()
                    .map(match -> match.group(1))
                    .toList();
            if ("OkxResponseConverter.java".equals(file)) {
                assertThat(thrown).containsOnly("ExternalInvariantViolationException",
                        "ExternalStatusException");
            } else {
                assertThat(thrown).as("переход %s не бросает", file).isEmpty();
            }
        });
    }

    /** У коннектора базы нет, и добыть ключ ему нечем: одна перегрузка принимает его аргументом. */
    @Test
    @DisplayName("U32.9 — числового ключа базы не добывает ни один переход")
    void u32_9_noTransitionObtainsANumericKey() {
        assertThat(Mappers.instrumentRules().snapshotToDomain(
                Mappers.instrumentRules().integrationToSnapshot(OkxFixture.instrument()))
                .getInstrumentId()).isNull();
        assertThat(Mappers.marketPrice().snapshotToDomain(
                Mappers.marketPrice().integrationToSnapshot(OkxFixture.ticker()))
                .getInstrumentId()).isNull();
        assertThat(Mappers.instrumentRules().snapshotToDomain(
                Mappers.instrumentRules().integrationToSnapshot(OkxFixture.instrument()), 42L)
                .getInstrumentId()).isEqualTo(42L);
    }

    /** Снапшот не несёт сырую форму источника ни одним ссылочным полем. */
    @Test
    @DisplayName("U32.10 — сырая форма источника за границу не выходит")
    void u32_10_noSnapshotReferencesASourceForm() {
        List<Class<?>> snapshots = snapshotClasses();

        assertThat(snapshots).as("базовый гейт: граничные формы найдены").isNotEmpty();
        assertThat(snapshots).allSatisfy(snapshot ->
                assertThat(Arrays.stream(snapshot.getDeclaredFields())
                        .map(Field::getType)
                        .map(Class::getName)
                        .filter(name -> name.contains(".model.okx."))
                        .toList())
                        .as("граничная форма %s не ссылается на форму источника", snapshot.getSimpleName())
                        .isEmpty());
    }

    /** Домен не амендит: ремоделирование есть REPLACE-оркестрация. */
    @Test
    @DisplayName("U32.11 — амендного запроса не собирает ни один переход")
    void u32_11_noTransitionBuildsAnAmendRequest() {
        List<String> requests = fileNames(REQUEST_DIRECTORY);

        assertThat(requests).as("базовый гейт: формы запроса найдены").isNotEmpty();
        assertThat(requests).allSatisfy(name -> assertThat(name).doesNotContain("Amend"));
        assertLayerBodiesHaveNo("сборка амендного запроса", "Amend");
    }

    /** У подтверждения есть признак приёма и код, и ни одного поля об исходе. */
    @Test
    @DisplayName("U32.12 — исполнением подтверждение не объявляется")
    void u32_12_theAckClaimsNoExecution() {
        assertThat(ExchangeAck.class.getDeclaredFields())
                .extracting(Field::getName)
                .containsExactlyInAnyOrder("success", "externalId", "internalId", "code", "message",
                        "externalCreatedAt");
    }

    /** «Иначе» у обоих резолверов — отказ, а не благоприятное умолчание. */
    @Test
    @DisplayName("U32.13 — благоприятного умолчания нет ни у одного резолвера статуса")
    void u32_13_neitherStatusResolverHasABenignDefault() {
        Map<String, String> bodies = LayerSources.of(List.of("resolve"));

        assertThat(bodies).containsKeys("OkxOrderExternalStatusResolver.java",
                "OkxAlgoOrderExternalStatusResolver.java");
        assertThat(bodies.get("OkxOrderExternalStatusResolver.java"))
                .containsPattern("default -> throw new ExternalStatusException");
        assertThat(bodies.get("OkxAlgoOrderExternalStatusResolver.java"))
                .containsPattern("default -> throw new ExternalStatusException");
    }

    /** «Прочий отказ источника» выражается двумя ложными ответами, а не своим предикатом. */
    @Test
    @DisplayName("U32.14 — третьего вопроса резолвер класса отказа не задаёт")
    void u32_14_theRejectionResolverAsksTwoQuestions() {
        assertThat(Arrays.stream(OkxCredentialsRejectionResolver.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .sorted()
                .toList())
                .containsExactly("isCredentialsRejected", "isOwnRequestDefect");
    }

    /** Непустоту, принадлежность и число элементов мерит читатель источника. */
    @Test
    @DisplayName("U32.15 — структурной валидации не делает ни один переход слоя")
    void u32_15_theLayerDoesNoStructuralValidation() {
        assertLayerBodiesHaveNo("структурная валидация",
                "IllegalArgumentException|IllegalStateException|Objects\\.requireNonNull|@NotNull");
    }

    private static List<Class<?>> snapshotClasses() {
        return fileNames(SNAPSHOT_DIRECTORY).stream()
                .map(name -> name.substring(0, name.length() - ".java".length()))
                .<Class<?>>map(simple -> {
                    try {
                        return Class.forName("com.example.connector.okx.snapshot." + simple);
                    } catch (ClassNotFoundException failure) {
                        throw new IllegalStateException(failure);
                    }
                })
                .toList();
    }

    private static List<String> fileNames(Path directory) {
        assertThat(directory).as("базовый гейт: каталог существует").isDirectory();
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}

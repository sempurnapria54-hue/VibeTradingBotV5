package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.blockedVerdict;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.context;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static com.example.tradingcore.unit.risk.RiskFixture.protection;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static com.example.tradingcore.unit.risk.RiskFixture.workingContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.DealRiskNumbersService;
import com.example.tradingcore.domain.command.risk.RiskBlockAction;
import com.example.tradingcore.domain.command.risk.RiskBlockResolver;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import com.example.tradingcore.domain.command.risk.RiskValidator;
import com.example.tradingcore.domain.command.strategy.ActionRiskGate;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.InstrumentExternalRulesDataService;
import com.example.tradingcore.persistence.service.TenantRiskAppetiteDataService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Отсутствие выходов у предмета — группа {@code U29} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/components/RiskValidator.md §Границы,
 * docs/components/RiskBlockResolver.md §Границы,
 * docs/components/ActionRiskGate.md §Границы).
 *
 * <p><b>Клейм отсутствия читается ДВУМЯ способами.</b> Утверждение о
 * ЧИСЛЕ или МНОЖЕСТВЕ коллабораторов — рефлексией по нестатическим
 * полям: «коллабораторов границы нет» есть утверждение о множестве
 * типов, и текстом оно не читается. Утверждение об отсутствии ИМЕНИ
 * (код, который никто не производит; статус, которого не ставит ни одна
 * фабрика) — исполняемым телом исходника, за вычетом комментариев.
 */
class RiskLayerAbsenceTest {

    /** Дерево исходников ядра: базовый каталог прогона — модуль. */
    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    /** Коды, которые преконтроль заводит фабрикой отказа. */
    private static final String FACTORY_CALL = "RiskCheckCode.";

    private final RiskHarness harness = new RiskHarness();

    private final RiskBlockResolver resolver = new RiskBlockResolver();

    @Test
    @DisplayName("U29.1 — любой вызов преконтроля: статус сделки и транша не меняется")
    void u29_1_theValidatorMovesNoStatus() {
        DealTranche tranche = tranche(List.of(), List.of(protection(STOP.toPlainString())));
        Deal deal = emptyDeal();
        deal.setTranches(List.of(tranche));
        deal.setPositions(List.of(episode("10", ANCHOR)));

        harness.validate(entryAction(), context(deal));

        assertThat(deal.getStatus()).isEqualTo(Deal.Status.ACTIVE);
        assertThat(tranche.getStatus()).isEqualTo(DealTranche.Status.MANAGING);
    }

    @Test
    @DisplayName("U29.2 — команд не создаётся ни одной: создателя команд у предмета нет в полях")
    void u29_2_theValidatorHasNoCommandCreatingCollaborator() {
        assertThat(collaboratorTypes(RiskValidator.class))
                .containsExactlyInAnyOrder(InstrumentExternalRulesDataService.class,
                        AccountInstrumentStateDataService.class, TenantRiskAppetiteDataService.class);
    }

    @Test
    @DisplayName("U29.3 — на биржу не ходит ни один класс предмета: коллабораторов границы у них нет")
    void u29_3_noSubjectClassHoldsAnExchangeCollaborator() {
        Stream.of(RiskValidator.class, RiskBlockResolver.class, ActionRiskGate.class,
                        DealRiskNumbersService.class)
                .forEach(subject -> assertThat(collaboratorTypes(subject))
                        .as("коллабораторы %s", subject.getSimpleName())
                        .noneMatch(type -> type.getName().contains("exchange")
                                || type.getSimpleName().contains("Exchange")
                                || type.getSimpleName().contains("Client")));
    }

    @Test
    @DisplayName("U29.4 — проверки свежести снимка средств у преконтроля нет ни одной")
    void u29_4_theValidatorNeverProducesTheStaleBalanceCode() {
        assertThat(executableBodyOf("domain/command/risk/RiskValidator.java"))
                .doesNotContain("BALANCE_NOT_FRESH");
    }

    @Test
    @DisplayName("U29.5 — строка пары только читается: ступень не поднимается и не снимается")
    void u29_5_thePairStateRowIsOnlyRead() {
        harness.validate(entryAction(), workingContext());

        verify(harness.pairStateBoundary()).getRequiredByPair(RiskFixture.ACCOUNT_ID, RiskFixture.INSTRUMENT_ID);
        verifyNoMoreInteractions(harness.pairStateBoundary());
    }

    @Test
    @DisplayName("U29.6 — четыре числа риска сделки преконтролем не переписываются")
    void u29_6_theValidatorNeverRewritesTheFourNumbers() {
        Deal deal = emptyDeal();
        deal.setPlannedRiskAmount(new java.math.BigDecimal("777"));

        harness.validate(entryAction(), context(deal));

        assertThat(deal.getPlannedRiskAmount()).isEqualByComparingTo("777");
        assertThat(deal.getIncurredRiskAmount()).isNull();
        assertThat(deal.getCurrentRiskAmount()).isNull();
        assertThat(deal.getProtectionRelievedRiskAmount()).isNull();
    }

    @Test
    @DisplayName("U29.7 — вторая точка входа отчётов и происшествий не заводит")
    void u29_7_theSecondEntryPointWritesNothing() {
        DealContext dealContext = contextBuilder(emptyDeal()).build();

        assertThat(harness.ceilingsBreachedWithoutAct(dealContext)).isEmpty();
        assertThat(collaboratorTypes(RiskValidator.class))
                .as("писателя отчётов среди коллабораторов нет")
                .noneMatch(type -> type.getSimpleName().contains("Anomaly")
                        || type.getSimpleName().contains("Report"));
    }

    @Test
    @DisplayName("U29.8 — инвариантов ведомой позиции вторая точка входа не переоценивает")
    void u29_8_thePositionInvariantsAreNotReassessed() {
        assertThat(executableBodyOf("domain/command/risk/RiskValidator.java"))
                .doesNotContain("POSITION_STATE_UNKNOWN")
                .doesNotContain("MULTIPLE_POSITIONS_DETECTED")
                .doesNotContain("PARTIAL_EXIT_NOT_REDUCE_ONLY");
    }

    @Test
    @DisplayName("U29.9 — карта реакций в базу не ходит: коллабораторов у неё нет ни одного")
    void u29_9_theReactionMapHasNoCollaboratorsAtAll() {
        assertThat(collaboratorTypes(RiskBlockResolver.class)).isEmpty();
    }

    @Test
    @DisplayName("U29.10 — реакции карта не исполняет: её выход — значение")
    void u29_10_theReactionMapOnlyReturnsAValue() {
        RiskBlockAction action = resolver.resolve(context(emptyDeal()), DealTranche.Status.PRECHECK,
                blockedVerdict(RiskCheckCode.STOP_LOSS_INVALID_SIDE));

        assertThat(action).isInstanceOf(RiskBlockAction.class);
        assertThat(publicMethodNames(RiskBlockResolver.class))
                .as("публичная поверхность карты — один метод")
                .containsExactly("resolve");
    }

    @Test
    @DisplayName("U29.11 — узел-гейт статусов не пишет, команд не создаёт, условий шага не смотрит")
    void u29_11_theGateHoldsOnlyTheTwoRiskCollaborators() {
        assertThat(collaboratorTypes(ActionRiskGate.class))
                .containsExactlyInAnyOrder(RiskValidator.class, RiskBlockResolver.class);
    }

    @Test
    @DisplayName("U29.12 — два вызова с одним состоянием: состояния между вызовами никто не держит")
    void u29_12_theSubjectKeepsNoStateBetweenCalls() {
        RiskValidationResult first = harness.validate(entryAction(), workingContext());
        RiskValidationResult second = harness.validate(entryAction(), workingContext());

        assertThat(codes(second)).isEqualTo(codes(first));
        assertThat(second.getDecision()).isEqualTo(first.getDecision());
    }

    @Test
    @DisplayName("U29.13 — предупредительного статуса не ставит ни одна фабрика результата")
    void u29_13_noFactoryEverProducesTheWarningStatus() {
        assertThat(executableBodyOf("domain/command/risk/RiskCheckResult.java"))
                .as("единственная фабрика ставит блокирующий статус")
                .contains("RiskCheckStatus.BLOCKED");
        assertThat(riskPackageBodies())
                .as("выражения, ставящего предупредительный статус, в пакете нет")
                .noneMatch(body -> body.contains(".status(RiskCheckStatus.WARNING")
                        || body.contains(".status(RiskCheckStatus.PASSED"));
    }

    @Test
    @Tag("debt")
    @DisplayName("U29.14 — у каждого значения перечня есть производитель (R-1)")
    void u29_14_everyCodeHasAProducer() {
        String producers = producingSources();

        assertThat(Arrays.stream(RiskCheckCode.values())
                .filter(code -> !producers.contains(FACTORY_CALL + code.name()))
                .map(Enum::name)
                .toList())
                .as("ожидание из дома: перечень кодов не шире того, что производит ядро")
                .isEmpty();
    }

    /** Типы нестатических полей класса — его коллабораторы. */
    private static List<Class<?>> collaboratorTypes(Class<?> subject) {
        return Arrays.stream(subject.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .toList();
    }

    /** Имена публичных методов, объявленных самим классом. */
    private static List<String> publicMethodNames(Class<?> subject) {
        return Arrays.stream(subject.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(java.lang.reflect.Method::getName)
                .distinct()
                .toList();
    }

    /** Исполняемое тело исходника: текст за вычетом комментариев. */
    private static String executableBodyOf(String relativePath) {
        Path source = MAIN_SOURCES.resolve("com/example/tradingcore").resolve(relativePath);
        if (!Files.isRegularFile(source)) {
            throw new IllegalStateException("исходник не найден: " + source.toAbsolutePath());
        }
        return stripComments(read(source));
    }

    /** Исполняемые тела всех исходников пакета риска. */
    private static List<String> riskPackageBodies() {
        Path packageDir = MAIN_SOURCES.resolve("com/example/tradingcore/domain/command/risk");
        if (!Files.isDirectory(packageDir)) {
            throw new IllegalStateException("пакет риска не найден: " + packageDir.toAbsolutePath());
        }
        try (Stream<Path> sources = Files.list(packageDir)) {
            return sources.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> stripComments(read(path)))
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Исполняемые тела дерева ядра за вычетом самого перечня (он коды
     * объявляет) и карты реакций (она их читает): производителем считается
     * упоминание кода в теле всякого прочего класса.
     */
    private static String producingSources() {
        try (Stream<Path> sources = Files.walk(MAIN_SOURCES)) {
            List<Path> files = sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith("RiskCheckResult.java"))
                    .filter(path -> !path.endsWith("RiskBlockResolver.java"))
                    .toList();
            if (files.isEmpty()) {
                throw new IllegalStateException("дерево исходников пусто: " + MAIN_SOURCES.toAbsolutePath());
            }
            return files.stream().map(path -> stripComments(read(path))).reduce("", String::concat);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String read(Path source) {
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Текст без блочных и строчных комментариев: клейм читается по исполняемому телу. */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }
}

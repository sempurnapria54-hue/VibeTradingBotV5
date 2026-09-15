package com.example.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyActionApiModel;
import com.example.strategies.api.model.strategy.StrategyAlgoOrderActionApiModel;
import com.example.strategies.api.model.strategy.StrategyDetailApiModel;
import com.example.strategies.api.model.strategy.StrategyOrderActionApiModel;
import com.example.strategies.api.model.strategy.StrategyStepApiModel;
import com.example.strategies.api.model.strategy.StrategyTrancheApiModel;
import com.example.strategies.domain.model.TenantRiskAppetite;
import com.example.strategies.domain.validation.StrategyDefinitionValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.server.ResponseStatusException;

/**
 * Реджекты создания, объявленные домом на дереве ДЕЙСТВИЙ, эмитятся
 * ИМЕНОВАННЫМИ кодами.
 *
 * <p><b>Зачем проверка нужна.</b> docs/rules/strategy-validation.md
 * объявляет действующими четыре реджекта, из которых валидатор не
 * поднимал ни одного: диапазон обеих долей держали аннотации api-модели,
 * а базы триггера защиты и роли BREAKEVEN не проверял никто. Bean
 * Validation отвечает ДО тела обработчика и кода не несёт: объявленный
 * реджект был недостижим, потребитель ветвился бы на тексте сообщения, а
 * спека docs/spec/strategy-reference.json называла это «ограничение жило
 * лишь в коде».
 *
 * <p>Каждый случай мерит ОБЕ стороны: эталон под теми же числами
 * проходит, а мутация роняет создание с названным кодом. Одна сторона
 * показала бы, что проверка исполняется, а не что она мерит этот предмет.
 */
class DeclaredActionRejectsTest {

    private static final String REFERENCE_DEFINITION = "strategy-examples/trend-following-ema.json";

    /** Защиты по типу условия — та же популяция, что у дома правила (docs/spec/strategy-reference.json). */
    private static final Set<String> PROTECTIVE_CONDITION_TYPES =
            Set.of("STOP_LOSS", "PARTIAL_STOP_LOSS", "OCO_FULL", "TRAILING_PERCENTS", "TRAILING_VALUE");

    private final StrategyDefinitionValidator validator = new StrategyDefinitionValidator();

    @Test
    @DisplayName("Нулевая доля аллокации входа отвергается кодом ALLOCATION_NOT_POSITIVE")
    void zeroEntryAllocationIsRejectedByItsNamedCode() throws IOException {
        CreateStrategyApiRequest request = readReference();
        StrategyOrderActionApiModel entry = firstOrderAction(request);
        entry.setAllocationPercents(BigDecimal.ZERO);

        assertThatThrownBy(() -> validator.validateCreate(request, referenceAppetite()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("STRATEGY_ACTION_ALLOCATION_NOT_POSITIVE");
    }

    /**
     * Верхняя граница включающая: {@code 100} законны, {@code 101} — нет.
     * Без этой стороны проверка держала бы только знак.
     */
    @Test
    @DisplayName("Доля аллокации выше ста отвергается тем же кодом, а ровно сто — нет")
    void allocationAboveHundredIsRejectedAndExactlyHundredIsNot() throws IOException {
        CreateStrategyApiRequest above = readReference();
        firstOrderAction(above).setAllocationPercents(new BigDecimal("101"));

        assertThatThrownBy(() -> validator.validateCreate(above, referenceAppetite()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("STRATEGY_ACTION_ALLOCATION_NOT_POSITIVE");

        CreateStrategyApiRequest atBound = readReference();
        firstOrderAction(atBound).setAllocationPercents(new BigDecimal("100"));

        assertThat(rejectionOf(atBound))
                .as("граница включающая: сотня долей аллокации законна")
                .doesNotContain("STRATEGY_ACTION_ALLOCATION_NOT_POSITIVE");
    }

    @Test
    @DisplayName("Нулевая доля закрытия отвергается кодом FRACTION_NOT_POSITIVE")
    void zeroCloseFractionIsRejectedByItsNamedCode() throws IOException {
        CreateStrategyApiRequest request = readReference();
        StrategyAlgoOrderActionApiModel algo = firstAlgoAction(request);
        algo.setCloseFractionPercents(BigDecimal.ZERO);

        assertThatThrownBy(() -> validator.validateCreate(request, referenceAppetite()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("STRATEGY_ACTION_FRACTION_NOT_POSITIVE");
    }

    /**
     * Предмет проверки — диапазон, а не наличие: пустую долю мерит своя
     * проверка объявленности, и диапазонный код на ней не срабатывает.
     */
    @Test
    @DisplayName("Пустая доля закрытия диапазонного кода не поднимает")
    void anAbsentCloseFractionRaisesNoRangeCode() throws IOException {
        CreateStrategyApiRequest request = readReference();
        firstAlgoAction(request).setCloseFractionPercents(null);

        assertThat(rejectionOf(request))
                .as("наличие доли мерит другая проверка")
                .doesNotContain("STRATEGY_ACTION_FRACTION_NOT_POSITIVE");
    }

    @Test
    @DisplayName("База триггера защиты, отличная от MARK, отвергается кодом TRIGGER_PRICE_TYPE_NOT_MARK")
    void aNonMarkProtectiveTriggerIsRejectedByItsNamedCode() throws IOException {
        CreateStrategyApiRequest request = readReference();
        firstProtectiveAlgoAction(request).setTriggerPriceType("LAST");

        assertThatThrownBy(() -> validator.validateCreate(request, referenceAppetite()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("STRATEGY_TRIGGER_PRICE_TYPE_NOT_MARK");
    }

    /**
     * База триггера живёт на ДВУХ носителях защитного действия — своём поле
     * и настройках стопа. Проверка одного носителя пропускала бы ту же
     * подмену второй тропой.
     */
    @Test
    @DisplayName("База триггера в настройках стопа защиты проверяется тем же кодом")
    void theStopSettingsTriggerOfAProtectiveActionIsCheckedToo() throws IOException {
        CreateStrategyApiRequest request = readReference();
        StrategyAlgoOrderActionApiModel protective = firstProtectiveAlgoAction(request);
        if (Objects.isNull(protective.getStopLossSettings())) {
            throw new IllegalStateException("Эталон не несёт настроек стопа у защиты — мутировать нечего");
        }
        protective.getStopLossSettings().setTriggerPriceType("INDEX");

        assertThatThrownBy(() -> validator.validateCreate(request, referenceAppetite()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("STRATEGY_TRIGGER_PRICE_TYPE_NOT_MARK");
    }

    /**
     * Эталон объявляет BREAKEVEN законно — защитным REPLACE с названной
     * целью. Снятая цель делает то же действие первичной постановкой, и
     * ровно её дом запрещает.
     */
    @Test
    @DisplayName("BREAKEVEN без цели переноса отвергается кодом BREAKEVEN_NOT_A_TRANSFER")
    void aBreakevenThatTransfersNothingIsRejectedByItsNamedCode() throws IOException {
        CreateStrategyApiRequest request = readReference();
        breakevenAction(request).setTargetActionKey(null);

        assertThatThrownBy(() -> validator.validateCreate(request, referenceAppetite()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("STRATEGY_BREAKEVEN_NOT_A_TRANSFER");
    }

    @Test
    @DisplayName("Эталон ни одного из объявленных реджектов не поднимает")
    void theReferenceRaisesNoneOfTheDeclaredRejects() throws IOException {
        assertThat(rejectionOf(readReference()))
                .as("иначе мутации мерили бы отказ, который стоял и без них")
                .isEmpty();
    }

    /** Текст отказа создания либо пустая строка, когда отказа нет. */
    private String rejectionOf(CreateStrategyApiRequest request) {
        try {
            validator.validateCreate(request, referenceAppetite());
            return "";
        } catch (ResponseStatusException rejected) {
            return String.valueOf(rejected.getReason());
        }
    }

    /** Первое ЗАЩИТНОЕ действие условной заявки: тип условия — собственное свойство создающего. */
    private StrategyAlgoOrderActionApiModel firstProtectiveAlgoAction(CreateStrategyApiRequest request) {
        return firstMatching(request, StrategyAlgoOrderActionApiModel.class,
                action -> PROTECTIVE_CONDITION_TYPES.contains(action.getConditionType()),
                "защитного действия условной заявки");
    }

    /** Действие, объявляющее BREAKEVEN способом расчёта уровня. */
    private StrategyAlgoOrderActionApiModel breakevenAction(CreateStrategyApiRequest request) {
        return firstMatching(request, StrategyAlgoOrderActionApiModel.class,
                action -> Objects.nonNull(action.getStopLossSettings())
                        && "BREAKEVEN".equals(action.getStopLossSettings().getCalculationType()),
                "действия с BREAKEVEN");
    }

    private StrategyOrderActionApiModel firstOrderAction(CreateStrategyApiRequest request) {
        return firstAction(request, StrategyOrderActionApiModel.class);
    }

    private StrategyAlgoOrderActionApiModel firstAlgoAction(CreateStrategyApiRequest request) {
        return firstAction(request, StrategyAlgoOrderActionApiModel.class);
    }

    /**
     * Шаги лежат на ДВУХ уровнях: у детали и у транша — обход берёт оба.
     * Обход одного уровня нашёл бы действия только у той детали, что
     * траншей не объявляет, и предмет проверки сузился бы молча.
     */
    private <T extends StrategyActionApiModel> T firstAction(CreateStrategyApiRequest request, Class<T> kind) {
        return firstMatching(request, kind, action -> true, "действия вида " + kind.getSimpleName());
    }

    private <T extends StrategyActionApiModel> T firstMatching(CreateStrategyApiRequest request, Class<T> kind,
                                                               Predicate<T> fits, String what) {
        for (StrategyDetailApiModel detail : request.getDetails()) {
            T found = firstIn(detail.getStepsByStatus(), kind, fits);
            if (Objects.nonNull(found)) {
                return found;
            }
            for (StrategyTrancheApiModel tranche : emptyIfNull(detail.getTranches())) {
                found = firstIn(tranche.getStepsByStatus(), kind, fits);
                if (Objects.nonNull(found)) {
                    return found;
                }
            }
        }
        throw new IllegalStateException("Эталон не несёт " + what
                + " — мутировать нечего, и проверка мерила бы пустоту");
    }

    private <T extends StrategyActionApiModel> T firstIn(Map<String, List<StrategyStepApiModel>> stepsByStatus,
                                                         Class<T> kind, Predicate<T> fits) {
        if (Objects.isNull(stepsByStatus)) {
            return null;
        }
        for (List<StrategyStepApiModel> steps : stepsByStatus.values()) {
            for (StrategyStepApiModel step : emptyIfNull(steps)) {
                for (StrategyActionApiModel action : emptyIfNull(step.getActions())) {
                    if (kind.isInstance(action) && fits.test(kind.cast(action))) {
                        return kind.cast(action);
                    }
                }
            }
        }
        return null;
    }

    private <E> List<E> emptyIfNull(List<E> items) {
        return Objects.isNull(items) ? List.of() : items;
    }

    private TenantRiskAppetite referenceAppetite() {
        return new TenantRiskAppetite(BigDecimal.ONE, new BigDecimal("100"));
    }

    private CreateStrategyApiRequest readReference() throws IOException {
        InputStream reference = new ClassPathResource(REFERENCE_DEFINITION).getInputStream();
        CreateStrategyApiRequest request = new ObjectMapper().readValue(reference, CreateStrategyApiRequest.class);
        if (Objects.isNull(request.getDetails())) {
            throw new IllegalStateException("Эталон разобран без деталей — мутировать нечего");
        }
        return request;
    }
}

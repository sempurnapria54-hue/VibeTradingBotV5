package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.dec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.resolve.StatusResolveResult;
import com.example.tradingbot.domain.util.DomainMath;
import com.example.tradingbot.domain.util.EnumNames;
import com.example.tradingbot.domain.util.ExchangeAccountKeyPath;
import com.example.tradingbot.domain.util.IndicatorComponents;
import com.example.tradingbot.domain.util.InternalIdFactory;
import com.example.tradingbot.domain.util.RiskMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Арифметика, формы идентичности, справочники — группа `U19` документа
 * `.claude/tests/cases/domain-model-predicates.md`
 * (docs/rules/decimal-arithmetic.md, docs/spec/risk-at-stop.json,
 * docs/architecture/data-ownership.md §Идентификаторы,
 * docs/integrations/okx/rules/client-id-marker.md,
 * docs/architecture/platform.md §Безопасность,
 * docs/rules/absent-value-semantics.md).
 *
 * <p><b>Базовая сборка:</b> статические хелперы пакета {@code util}:
 * зовутся прямо, состояния не держат. Аргументы — числа, строки и
 * значения перечней.
 *
 * <p><b>Две клетки группы не прогоняются, и это не пропуск.</b> `U19.23`
 * и `U19.24` ожидания не имеют: справочник «тип индикатора → допустимые
 * компоненты» указан домом у файла, который его не несёт, а действующий
 * перечень живёт только в коде справочника (находка `D-3`, звено `Z2`).
 *
 * <p><b>Обе прежние пробы предмета поглощены этой группой целиком</b>
 * (Д1828): {@code InternalIdFormTest} — клетками `U19.11`-`U19.14`,
 * {@code ExchangeAccountKeyPathTest} — клетками `U19.19` и `U19.20`.
 */
class MathAndIdentityTest {

    /** Потолок поля клиентского идентификатора у источника. */
    private static final int EXCHANGE_ID_LIMIT = 32;

    @Test
    @DisplayName("U19.1 — доменный контекст округления")
    void u19_1_bothAxesOfTheDomainContext() {
        assertThat(DomainMath.CONTEXT.getPrecision()).isEqualTo(34);
        assertThat(DomainMath.CONTEXT.getRoundingMode()).isEqualTo(RoundingMode.HALF_UP);
    }

    @Test
    @DisplayName("U19.2 — деление с бесконечным частным этим контекстом")
    void u19_2_anEndlessQuotientDoesNotRefuse() {
        assertThatCode(() -> BigDecimal.ONE.divide(new BigDecimal("3"), DomainMath.CONTEXT))
                .doesNotThrowAnyException();
        assertThat(BigDecimal.ONE.divide(new BigDecimal("3"), DomainMath.CONTEXT).precision())
                .isEqualTo(34);
    }

    @Test
    @DisplayName("U19.3 — длинное направление, уровень ниже якоря")
    void u19_3_aLongStopBelowTheAnchorIsPositive() {
        assertThat(RiskMath.signedStopDistance(StrategyTradeDirection.LONG, dec("100"), dec("90")))
                .isEqualByComparingTo("10");
    }

    /** Модуль не берётся: перенос за безубыток отличим от постановки под входом. */
    @Test
    @DisplayName("U19.4 — длинное направление, уровень выше якоря")
    void u19_4_aLongStopAboveTheAnchorIsNegative() {
        assertThat(RiskMath.signedStopDistance(StrategyTradeDirection.LONG, dec("100"), dec("110")))
                .isEqualByComparingTo("-10");
    }

    @Test
    @DisplayName("U19.5 — короткое направление, уровень выше якоря")
    void u19_5_aShortStopAboveTheAnchorIsPositive() {
        assertThat(RiskMath.signedStopDistance(StrategyTradeDirection.SHORT, dec("100"), dec("110")))
                .isEqualByComparingTo("10");
    }

    /** Продуктового ожидания нет — дом пустого направления не допускает. */
    @Test
    @DisplayName("U19.6 — направление пусто")
    void u19_6_anAbsentDirectionFallsToTheShortBranch() {
        assertThat(RiskMath.signedStopDistance(null, dec("100"), dec("110")))
                .isEqualByComparingTo("10");
    }

    /** Численного буфера сверх комиссии нет. */
    @Test
    @DisplayName("U19.7 — ставка комиссии, якорь и уровень заданы")
    void u19_7_theFloorIsTheRoundTripFee() {
        assertThat(RiskMath.stopDistanceFloor(dec("100"), dec("90"), dec("0.001")))
                .isEqualByComparingTo("0.19");
    }

    /** Точный безубыток: клейм «ноль» проверяется этим состоянием. */
    @Test
    @DisplayName("U19.8 — знаковая дистанция и комиссия взаимно гасятся")
    void u19_8_theExactBreakEvenIsZero() {
        assertThat(RiskMath.lossAtStopPerUnit(StrategyTradeDirection.LONG, dec("9"), dec("11"), dec("0.1")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Экспозиции и клэмпа форма не знает — потребитель домножает и клэмпует сам. */
    @Test
    @DisplayName("U19.9 — убыток на единицу равен сумме двух своих слагаемых")
    void u19_9_theLossIsTheSumOfItsAddends() {
        BigDecimal distance = RiskMath.signedStopDistance(StrategyTradeDirection.LONG, dec("100"), dec("90"));
        BigDecimal floor = RiskMath.stopDistanceFloor(dec("100"), dec("90"), dec("0.001"));

        assertThat(RiskMath.lossAtStopPerUnit(StrategyTradeDirection.LONG, dec("100"), dec("90"), dec("0.001")))
                .isEqualByComparingTo(distance.add(floor));
    }

    @Test
    @DisplayName("U19.10 — ставка комиссии равна нулю")
    void u19_10_aZeroFeeLeavesOnlyTheDistance() {
        assertThat(RiskMath.lossAtStopPerUnit(StrategyTradeDirection.LONG, dec("100"), dec("90"),
                BigDecimal.ZERO)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("U19.11 — идентичность внутренней сущности")
    void u19_11_theInternalFormIsAUuidWithoutTheMarker() {
        String id = InternalIdFactory.forInternalEntity();

        assertThat(id).hasSize(36);
        assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(InternalIdFactory.isOurs(id)).isFalse();
    }

    @Test
    @DisplayName("U19.12 — идентичность сущности, уезжающей на площадку")
    void u19_12_theExchangeBoundFormFitsTheLimitAndCarriesTheMarker() {
        String id = InternalIdFactory.forExchangeBoundEntity();

        assertThat(id).hasSize(EXCHANGE_ID_LIMIT);
        assertThat(id).startsWith("vtb");
        assertThat(id).matches("[a-z0-9]+");
    }

    @Test
    @DisplayName("U19.13 — два вызова уезжающей формы подряд")
    void u19_13_identifiersDoNotRepeat() {
        assertThat(InternalIdFactory.forExchangeBoundEntity())
                .isNotEqualTo(InternalIdFactory.forExchangeBoundEntity());
    }

    /** Ровно поэтому формы и разведены. */
    @Test
    @DisplayName("U19.14 — длина внутренней формы против потолка поля источника")
    void u19_14_theInternalFormWouldNotFitTheLimit() {
        assertThat(InternalIdFactory.forInternalEntity().length()).isGreaterThan(EXCHANGE_ID_LIMIT);
    }

    @Test
    @DisplayName("U19.15 — клиентский идентификатор, начинающийся маркером контура")
    void u19_15_theMarkerMakesItOurs() {
        assertThat(InternalIdFactory.isOurs("vtbabc123")).isTrue();
    }

    /** Наличие строки в базе в признаке не участвует. */
    @Test
    @DisplayName("U19.16 — клиентский идентификатор без маркера")
    void u19_16_withoutTheMarkerItIsNotOurs() {
        assertThat(InternalIdFactory.isOurs("abc123")).isFalse();
    }

    @Test
    @DisplayName("U19.17 — клиентский идентификатор пуст")
    void u19_17_anAbsentIdentifierIsNotOurs() {
        assertThatCode(() -> InternalIdFactory.isOurs(null)).doesNotThrowAnyException();
        assertThat(InternalIdFactory.isOurs(null)).isFalse();
    }

    /** Проверяется префикс, а не вхождение. */
    @Test
    @DisplayName("U19.18 — маркер стои́т не в начале")
    void u19_18_theMarkerIsAPrefixNotAnOccurrence() {
        assertThat(InternalIdFactory.isOurs("xxvtbabc")).isFalse();
    }

    @Test
    @DisplayName("U19.19 — окружение и идентичность счёта")
    void u19_19_theEnvironmentIsTheFirstSegment() {
        assertThat(ExchangeAccountKeyPath.of("prod", "acc-1")).isEqualTo("prod/exchange-accounts/acc-1");
    }

    /** На этом стои́т политика хранилища. */
    @Test
    @DisplayName("U19.20 — два разных окружения при одной идентичности счёта")
    void u19_20_differentEnvironmentsNeverSharePrefix() {
        String dev = ExchangeAccountKeyPath.of("dev", "acc-1");
        String prod = ExchangeAccountKeyPath.of("prod", "acc-1");

        assertThat(dev).isNotEqualTo(prod);
        assertThat(dev.startsWith(prod)).isFalse();
        assertThat(prod.startsWith(dev)).isFalse();
    }

    @Test
    @DisplayName("U19.21 — значение перечня")
    void u19_21_theEnumNameIsReturned() {
        assertThat(EnumNames.name(Order.Status.ACTIVE)).isEqualTo("ACTIVE");
    }

    /** Литералом не подменяется, иначе неизвестное выдавалось бы за исход. */
    @Test
    @DisplayName("U19.22 — пустое значение перечня")
    void u19_22_anAbsentEnumStaysEmpty() {
        assertThat(EnumNames.name(null)).isNull();
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b>
     * docs/rules/absent-value-semantics.md требует, чтобы пустое значение
     * читалось отсутствием, а не отказом; справочник построен на
     * {@code Map.of}, и неизменяемая карта роняет
     * {@code NullPointerException} и на {@code getOrDefault(null, …)}, и на
     * {@code containsKey(null)} — то есть пустой тип индикатора обрушивает
     * ПРОХОД вместо того, чтобы дать пустое множество (находка `D-9`,
     * `.claude/work/backlog.md` §«Справочник компонентов индикатора роняет
     * проход на пустом типе»). Красный прогон и есть предъявление долга.
     */
    @Test
    @Tag("debt")
    @DisplayName("U19.25 — тип индикатора пуст")
    void u19_25_anAbsentIndicatorTypeGivesAnEmptySet() {
        assertThatCode(() -> IndicatorComponents.allowedFor(null)).doesNotThrowAnyException();
        assertThat(IndicatorComponents.allowedFor(null)).isEmpty();
        assertThat(IndicatorComponents.isMultiComponent(null)).isFalse();
    }

    /** Пустая причина остаётся пустой и применяется читателем однократно. */
    @Test
    @DisplayName("U19.26 — результат резолва статуса с непустой причиной и с пустой")
    void u19_26_theResolveResultCarriesBothFieldsAsGiven() {
        StatusResolveResult<Order.Status, Order.CloseReason> withReason =
                StatusResolveResult.of(Order.Status.CANCELED, Order.CloseReason.KILL_SWITCH);
        StatusResolveResult<Order.Status, Order.CloseReason> withoutReason =
                StatusResolveResult.of(Order.Status.ACTIVE, null);

        assertThat(withReason.getStatus()).isEqualTo(Order.Status.CANCELED);
        assertThat(withReason.getCloseReason()).isEqualTo(Order.CloseReason.KILL_SWITCH);
        assertThat(withoutReason.getStatus()).isEqualTo(Order.Status.ACTIVE);
        assertThat(withoutReason.getCloseReason()).isNull();
    }

    /**
     * Граница самого потолка (пробел `G6` документа, добран под-шагом 3).
     * <b>Перевода внутренней формы в потолок площадки как отдельного
     * метода в артефакте нет</b> — форма порождается сразу, — поэтому
     * граница наблюдается у ПОРОЖДЕНИЯ: сотня вызовов ложится в потолок
     * ровно, ни на знак меньше и ни на знак больше, а внутренняя форма
     * потолок превышает.
     */
    @Test
    @DisplayName("U19.27 — граница потолка поля источника")
    void u19_27_theGeneratedFormLandsExactlyOnTheLimit() {
        assertThat(IntStream.range(0, 100)
                .mapToObj(attempt -> InternalIdFactory.forExchangeBoundEntity())
                .map(String::length)
                .distinct()
                .toList()).containsExactly(EXCHANGE_ID_LIMIT);
        assertThat(InternalIdFactory.forInternalEntity().length()).isNotEqualTo(EXCHANGE_ID_LIMIT);
    }
}

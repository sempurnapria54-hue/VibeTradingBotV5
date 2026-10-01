package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.dec;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.core.balance.AccountMode;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.balance.PositionMode;
import com.example.tradingbot.domain.model.core.instrument.PositionTier;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import java.math.BigDecimal;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Операнды преконтроля риска, живущие на моделях: режим снимка вне контура и
 * покрытие размера позиционным тиром — группа `U23` документа
 * `.claude/tests/cases/domain-model-predicates.md` (дом —
 * docs/spec/risk-limits.json, величины {@code accountModeOutOfContour} и
 * {@code postActMaintenanceMarginRate}; модели —
 * docs/models/domain/core/BalanceContainer.md,
 * docs/models/domain/other/InstrumentExternalRules.md).
 *
 * <p><b>Базовая сборка:</b> снимок средств с настоящими режимами счёта и
 * позиций; тир с настоящими границами. Посылку свежести снимка величины
 * {@code accountModeOutOfContour} проверяет вызывающий — здесь её нет.
 *
 * <p><b>Изъятие предиката из сериализации спрашивается у интроспектора
 * сериализатора</b>, а не пересказывается отражением: какой метод он считает
 * свойством, решает библиотека.
 */
class AccountModeAndPositionTierTest {

    private static final BigDecimal MIN = dec("1");
    private static final BigDecimal MAX = dec("1000");

    @Test
    @DisplayName("U23.1 — фьючерсный режим счёта и нетто-позиции: режим в контуре")
    void u23_1_futuresWithNetPositionsIsInsideTheContour() {
        assertThat(snapshot(AccountMode.FUTURES, PositionMode.NET).isAccountModeOutOfContour()).isFalse();
    }

    @ParameterizedTest(name = "U23.2 — {0}")
    @EnumSource(value = AccountMode.class, names = "FUTURES", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("U23.2 — любой режим счёта, кроме фьючерсного, при нетто-позициях: вне контура")
    void u23_2_everyNonFuturesAccountModeIsOutOfTheContour(AccountMode accountMode) {
        assertThat(snapshot(accountMode, PositionMode.NET).isAccountModeOutOfContour()).isTrue();
    }

    @Test
    @DisplayName("U23.3 — фьючерсный режим счёта и раздельные позиции: вне контура")
    void u23_3_longShortPositionsAreOutOfTheContour() {
        assertThat(snapshot(AccountMode.FUTURES, PositionMode.LONG_SHORT).isAccountModeOutOfContour()).isTrue();
    }

    /** Одна пустая посылка из двух выполненной не читается. */
    @Test
    @DisplayName("U23.4 — фьючерсный режим счёта, режим позиций пуст: вне контура")
    void u23_4_anEmptyPositionModeIsOutOfTheContour() {
        assertThat(snapshot(AccountMode.FUTURES, null).isAccountModeOutOfContour()).isTrue();
    }

    @Test
    @DisplayName("U23.5 — снимок режимов не несёт: вне контура")
    void u23_5_aSnapshotWithoutModesIsOutOfTheContour() {
        assertThat(snapshot(null, null).isAccountModeOutOfContour()).isTrue();
    }

    /**
     * Снимок уезжает по проводу из коннектора в ядро: нульарный предикат
     * ключом без поля не уезжает.
     */
    @Test
    @DisplayName("U23.6 — предикат режима вне контура сериализатор свойством снимка не видит")
    void u23_6_theContourPredicateIsNotASerializerProperty() {
        assertThat(serializerProperties(BalanceContainer.class))
                .contains("accountMode", "positionMode")
                .doesNotContain("accountModeOutOfContour");
    }

    @Test
    @DisplayName("U23.7 — размер внутри границ тира: покрыт")
    void u23_7_aSizeInsideTheBoundsIsCovered() {
        assertThat(tier(MIN, MAX).covers(dec("500"))).isTrue();
    }

    @Test
    @DisplayName("U23.8 — размер равен нижней границе: покрыт, граница включена")
    void u23_8_theLowerBoundIsIncluded() {
        assertThat(tier(MIN, MAX).covers(MIN)).isTrue();
    }

    /** На стыке двух тиров размер покрывают оба — выбор делает вызывающий. */
    @Test
    @DisplayName("U23.9 — размер равен верхней границе: покрыт, граница включена")
    void u23_9_theUpperBoundIsIncluded() {
        assertThat(tier(MIN, MAX).covers(MAX)).isTrue();
    }

    @Test
    @DisplayName("U23.10 — размер ниже нижней границы: не покрыт")
    void u23_10_aSizeBelowTheLowerBoundIsNotCovered() {
        assertThat(tier(MIN, MAX).covers(dec("0.5"))).isFalse();
    }

    @Test
    @DisplayName("U23.11 — размер выше верхней границы: не покрыт")
    void u23_11_aSizeAboveTheUpperBoundIsNotCovered() {
        assertThat(tier(MIN, MAX).covers(dec("1000.1"))).isFalse();
    }

    @Test
    @DisplayName("U23.12 — нижняя граница тира пуста: не покрыт")
    void u23_12_anEmptyLowerBoundCoversNothing() {
        assertThat(tier(null, MAX).covers(dec("500"))).isFalse();
    }

    @Test
    @DisplayName("U23.13 — верхняя граница тира пуста: не покрыт")
    void u23_13_anEmptyUpperBoundCoversNothing() {
        assertThat(tier(MIN, null).covers(dec("500"))).isFalse();
    }

    @Test
    @DisplayName("U23.14 — размер пуст: не покрыт, отказа нет")
    void u23_14_anEmptySizeIsNotCovered() {
        assertThat(tier(MIN, MAX).covers(null)).isFalse();
    }

    @Test
    @DisplayName("U23.15 — свойства сериализатора тира — ровно три компонента записи")
    void u23_15_theTierSerializerPropertiesAreItsThreeComponents() {
        assertThat(serializerProperties(PositionTier.class))
                .containsExactlyInAnyOrder("minSize", "maxSize", "maintenanceMarginRate");
    }

    // --- материал кейсов --------------------------------------------------

    private static BalanceContainer snapshot(AccountMode accountMode, PositionMode positionMode) {
        BalanceContainer container = new BalanceContainer();
        container.setExchangeAccountId(2L);
        container.setAccountMode(accountMode);
        container.setPositionMode(positionMode);
        return container;
    }

    private static PositionTier tier(BigDecimal minSize, BigDecimal maxSize) {
        return new PositionTier(minSize, maxSize, dec("0.004"));
    }

    /** Имена свойств, которые сериализатор запишет у формы: у каждого есть аксессор чтения. */
    private static Set<String> serializerProperties(Class<?> form) {
        ObjectMapper mapper = new ObjectMapper();
        BeanDescription description = mapper.getSerializationConfig()
                .introspect(mapper.constructType(form));
        return description.findProperties().stream()
                .filter(BeanPropertyDefinition::couldSerialize)
                .map(BeanPropertyDefinition::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}

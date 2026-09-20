package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.integration.external.api.model.okx.response.TradeFeeOkxResponse;
import com.example.connector.okx.mapping.TradeFeeRateMapper;
import com.example.connector.okx.snapshot.TradeFeeRateExternalSnapshot;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.other.TradeFeeRate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ставка комиссии → снапшот группы: знак, время, перечень — группа
 * `U16` документа `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/TradeFeeRate.md §«`TradeFeeOkxResponse` →
 * `TradeFeeRateExternalSnapshot`» и §«Знак ставки — снимается здесь»).
 *
 * <p><b>Базовая сборка:</b> {@code TradeFeeOkxResponse} с одной группой
 * ставок; переход принимает <b>ответ плюс одну его группу</b> — по
 * снапшоту на группу.
 *
 * <p><b>Знак снимается ровно здесь, и ниже про него никто не знает.</b>
 * Умножение на минус единицу (а не модуль) сохраняет различимость:
 * ребейт уезжает отрицательной издержкой и в формуле корректно
 * уменьшает убыток на стопе.
 *
 * <p>Кейсы {@code U16.6}, {@code U16.7} и {@code U16.8} в код не пошли:
 * дом объявляет пустую и непарсящуюся ставку, а также неразбираемое
 * время контролируемой ошибкой границы, код же глотает отказ и отдаёт
 * пустоту — `.claude/work/backlog.md` §«Отказ разбора ставки комиссии
 * проглочен маппером вопреки контракту границы».
 */
class TradeFeeRateToSnapshotTest {

    private final TradeFeeRateMapper mapper = Mappers.tradeFeeRate();

    private TradeFeeRateExternalSnapshot snapshotOf(TradeFeeOkxResponse response) {
        return mapper.integrationToSnapshot(response, response.getFeeGroup().getFirst());
    }

    @Test
    @DisplayName("U16.1 — базовая сборка: оси группы, обе ставки издержкой, уровень и момент")
    void u16_1_theBaseAssemblyLandsFieldByField() {
        TradeFeeRateExternalSnapshot snapshot = snapshotOf(OkxFixture.tradeFee());

        assertThat(snapshot.getExternalInstrumentType()).isEqualTo("SWAP");
        assertThat(snapshot.getExternalFeeGroupId()).isEqualTo("1");
        assertThat(snapshot.getExternalTakerFeeRate()).isEqualTo("0.0005");
        assertThat(snapshot.getExternalMakerFeeRate()).isEqualTo("0.0002");
        assertThat(snapshot.getExternalFeeLevel()).isEqualTo("Lv1");
        assertThat(snapshot.getExternalModifiedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
    }

    /** Ребейт не теряется: он становится отрицательной издержкой, а не нулём и не модулем. */
    @Test
    @DisplayName("U16.2 — ребейт становится отрицательной издержкой")
    void u16_2_aRebateBecomesANegativeCost() {
        TradeFeeOkxResponse response = OkxFixture.tradeFee();
        response.setFeeGroup(List.of(OkxFixture.feeGroup("1", "0.0001", "-0.0002")));

        assertThat(snapshotOf(response).getExternalTakerFeeRate()).isEqualTo("-0.0001");
    }

    @Test
    @DisplayName("U16.3 — снятие знака нуля даёт нуль плоской записью")
    void u16_3_negatingZeroGivesZero() {
        TradeFeeOkxResponse response = OkxFixture.tradeFee();
        response.setFeeGroup(List.of(OkxFixture.feeGroup("1", "0", "-0.0002")));

        assertThat(snapshotOf(response).getExternalTakerFeeRate()).isEqualTo("0");
    }

    /** Строка едет в поле носителя как есть, поэтому масштаб экспоненты разворачивается. */
    @Test
    @DisplayName("U16.4 — экспоненциальная запись ставки разворачивается в плоскую")
    void u16_4_anExponentialRateIsWrittenPlain() {
        TradeFeeOkxResponse response = OkxFixture.tradeFee();
        response.setFeeGroup(List.of(OkxFixture.feeGroup("1", "-5E-4", "-0.0002")));

        assertThat(snapshotOf(response).getExternalTakerFeeRate()).isEqualTo("0.0005");
    }

    @Test
    @DisplayName("U16.5 — обрамляющие пробелы ставки снимаются до разбора")
    void u16_5_aRateIsTrimmedBeforeParsing() {
        TradeFeeOkxResponse response = OkxFixture.tradeFee();
        response.setFeeGroup(List.of(OkxFixture.feeGroup("1", " -0.0005 ", "-0.0002")));

        assertThat(snapshotOf(response).getExternalTakerFeeRate()).isEqualTo("0.0005");
    }

    /** Пустая строка есть объявленная пустота, и от проглоченного отказа она по выходу неотличима. */
    @Test
    @DisplayName("U16.9 — пустое время данных источника даёт пустоту законно")
    void u16_9_anEmptySourceTimeIsLawfulEmptiness() {
        TradeFeeOkxResponse response = OkxFixture.tradeFee();
        response.setTs("");

        assertThat(snapshotOf(response).getExternalModifiedAt()).isNull();
    }

    /** Непустоту ключа мерит структурная валидация читателя. */
    @Test
    @DisplayName("U16.10 — пустой ключ группы: пустая строка, отказа нет")
    void u16_10_anEmptyGroupKeyIsAnEmptyString() {
        TradeFeeOkxResponse response = OkxFixture.tradeFee();
        response.setFeeGroup(List.of(OkxFixture.feeGroup("", "-0.0005", "-0.0002")));

        assertThat(snapshotOf(response).getExternalFeeGroupId()).isNotNull().isEmpty();
    }

    /** Один ответ источника → N снапшотов. */
    @Test
    @DisplayName("U16.11 — три группы дают три снапшота с общими полями ответа")
    void u16_11_threeGroupsGiveThreeSnapshots() {
        TradeFeeOkxResponse response = OkxFixture.tradeFee();
        response.setFeeGroup(List.of(
                OkxFixture.feeGroup("1", "-0.0005", "-0.0002"),
                OkxFixture.feeGroup("2", "-0.0004", "-0.0001"),
                OkxFixture.feeGroup("3", "-0.0003", "0")));

        List<TradeFeeRateExternalSnapshot> snapshots = response.getFeeGroup().stream()
                .map(group -> mapper.integrationToSnapshot(response, group))
                .toList();

        assertThat(snapshots).extracting(TradeFeeRateExternalSnapshot::getExternalFeeGroupId)
                .containsExactly("1", "2", "3");
        assertThat(snapshots).extracting(TradeFeeRateExternalSnapshot::getExternalTakerFeeRate)
                .containsExactly("0.0005", "0.0004", "0.0003");
        assertThat(snapshots).allSatisfy(snapshot -> {
            assertThat(snapshot.getExternalInstrumentType()).isEqualTo("SWAP");
            assertThat(snapshot.getExternalFeeLevel()).isEqualTo("Lv1");
            assertThat(snapshot.getExternalModifiedAt())
                    .isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
        });
    }

    /** Счёт-владельца проставляет ядро; версионирование ведёт синк, не маппер. */
    @Test
    @DisplayName("U16.12 — материализация доменной ставки: проекция есть, ключей владельца нет")
    void u16_12_materializationCarriesNoOwnerKeys() {
        TradeFeeRate rate = mapper.snapshotToDomain(snapshotOf(OkxFixture.tradeFee()));

        assertThat(rate.getInstrumentType()).isEqualTo(InstrumentExternalRules.InstrumentType.SWAP);
        assertThat(rate.getExternalInstrumentType()).isEqualTo("SWAP");
        assertThat(rate.getExternalFeeGroupId()).isEqualTo("1");
        assertThat(rate.getExternalTakerFeeRate()).isEqualTo("0.0005");
        assertThat(rate.getExternalMakerFeeRate()).isEqualTo("0.0002");
        assertThat(rate.getExternalFeeLevel()).isEqualTo("Lv1");
        assertThat(rate.getExchangeAccountId()).isNull();
        assertThat(rate.getRefreshCount()).isNull();
    }

    /** Проекция осью резолва не является: иначе две группы столкнулись бы в одном ключе. */
    @Test
    @DisplayName("U16.13 — неизвестный тип: проекция неизвестна, ключ группы остаётся сырой парой")
    void u16_13_anUnknownTypeKeepsTheRawGroupKey() {
        TradeFeeRateExternalSnapshot snapshot = TradeFeeRateExternalSnapshot.builder()
                .externalInstrumentType("PERP")
                .externalFeeGroupId("1")
                .externalTakerFeeRate("0.0005")
                .externalMakerFeeRate("0.0002")
                .build();

        TradeFeeRate rate = mapper.snapshotToDomain(snapshot);

        assertThat(rate.getInstrumentType()).isEqualTo(InstrumentExternalRules.InstrumentType.UNKNOWN);
        assertThat(rate.getExternalInstrumentType()).isEqualTo("PERP");
        assertThat(rate.getExternalFeeGroupId()).isEqualTo("1");
        assertThat(rate.sameGroupAs("PERP", "1")).isTrue();
    }

    /** Охрана конъюнктивна (звено `Z1`); выражений у перехода нет, поэтому отказа нет ни на одном. */
    @Test
    @DisplayName("U16.14 — три формы пустого входа: пустота, половина ответа, половина группы")
    void u16_14_aConjunctiveGuardSplitsTheTwoSources() {
        assertThat(mapper.integrationToSnapshot(null, null)).isNull();

        TradeFeeRateExternalSnapshot groupOnly =
                mapper.integrationToSnapshot(null, OkxFixture.feeGroup("1", "-0.0005", "-0.0002"));

        assertThat(groupOnly).isNotNull();
        assertThat(groupOnly.getExternalFeeGroupId()).isEqualTo("1");
        assertThat(groupOnly.getExternalTakerFeeRate()).isEqualTo("0.0005");
        assertThat(groupOnly.getExternalInstrumentType()).isNull();
        assertThat(groupOnly.getExternalFeeLevel()).isNull();
        assertThat(groupOnly.getExternalModifiedAt()).isNull();

        TradeFeeRateExternalSnapshot responseOnly =
                mapper.integrationToSnapshot(OkxFixture.tradeFee(), null);

        assertThat(responseOnly).isNotNull();
        assertThat(responseOnly.getExternalInstrumentType()).isEqualTo("SWAP");
        assertThat(responseOnly.getExternalFeeLevel()).isEqualTo("Lv1");
        assertThat(responseOnly.getExternalModifiedAt()).isNotNull();
        assertThat(responseOnly.getExternalFeeGroupId()).isNull();
        assertThat(responseOnly.getExternalTakerFeeRate()).isNull();
    }
}

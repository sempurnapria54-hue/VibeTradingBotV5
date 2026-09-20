package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.mapping.InstrumentMapper;
import com.example.connector.okx.mapping.PositionMapper;
import com.example.connector.okx.snapshot.InstrumentExternalSnapshot;
import com.example.connector.okx.snapshot.PositionCloseResultExternalSnapshot;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.position.Position;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Снапшот → доменная модель обновлением цели — группа `U22` документа
 * `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/Instrument.md §«Что персистится в шаге 1» и
 * docs/models/mapping/PositionCloseResult.md §«Снапшот → `Position`:
 * две тропы»).
 *
 * <p><b>Базовая сборка:</b> существующая доменная модель с
 * заполненными полями плюс снапшот; вызов переноса в цель.
 *
 * <p><b>Переходов этой формы два, и стратегии пустоты у них разные:</b>
 * у инструмента пустое поле снапшота <b>затирает</b> цель (стратегии
 * игнорирования нет); у материализации эпизода — <b>не затирает</b>,
 * потому что строка заводится первой ногой и наполняется второй.
 *
 * <p>Кейс {@code U22.9} в код не пошёл: дом и код называют разное —
 * `.claude/work/backlog.md` §«Четыре таблицы маппинга отрицают поле,
 * которое их модели несут».
 */
class SnapshotToDomainUpdateTest {

    private final InstrumentMapper instrumentMapper = Mappers.instrument();
    private final PositionMapper positionMapper = Mappers.position();

    private static Instrument existingInstrument() {
        Instrument instrument = new Instrument();
        instrument.setId(7L);
        instrument.setInternalId("in-7");
        instrument.setExchangeId(3L);
        instrument.setLeverage(5);
        instrument.setMarginMode(Instrument.MarginMode.ISOLATED);
        instrument.setExternalSettlementCurrency("USDT");
        return instrument;
    }

    /** Эпизод, заведённый первой ногой закрытым и без положения закрытия. */
    private static Position closedEpisodeWithoutRecord() {
        Position position = new Position();
        position.setId(11L);
        position.setStatus(Position.Status.CLOSED);
        return position;
    }

    private PositionCloseResultExternalSnapshot closeSnapshot() {
        return positionMapper.integrationToCloseSnapshot(OkxFixture.positionHistory());
    }

    @Test
    @DisplayName("U22.1 — инструмент: биржевые поля обновлены, собственные не тронуты")
    void u22_1_theInstrumentKeepsItsOwnFields() {
        Instrument instrument = existingInstrument();
        InstrumentExternalSnapshot snapshot =
                instrumentMapper.integrationToSnapshot(OkxFixture.instrument());

        instrumentMapper.snapshotToDomain(instrument, snapshot);

        assertThat(instrument.getExternalId()).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(instrument.getExternalType()).isEqualTo("SWAP");
        assertThat(instrument.getExternalStatus()).isEqualTo("live");
        assertThat(instrument.getExternalLeverage()).isEqualTo("10");
        assertThat(instrument.getExternalBaseCurrency()).isEqualTo("ETH");
        assertThat(instrument.getExternalQuoteCurrency()).isEqualTo("USDT");
        assertThat(instrument.getExternalSettlementCurrency()).isEqualTo("USDT");
        assertThat(instrument.getId()).isEqualTo(7L);
        assertThat(instrument.getInternalId()).isEqualTo("in-7");
        assertThat(instrument.getExchangeId()).isEqualTo(3L);
        assertThat(instrument.getLeverage()).isEqualTo(5);
        assertThat(instrument.getMarginMode()).isEqualTo(Instrument.MarginMode.ISOLATED);
    }

    /** Их дом — навес правил. */
    @Test
    @DisplayName("U22.2 — размерные поля спецификации в доменный инструмент не идут")
    void u22_2_theSizingSpecsDoNotLandOnTheInstrument() {
        Instrument instrument = existingInstrument();
        InstrumentExternalSnapshot snapshot =
                instrumentMapper.integrationToSnapshot(OkxFixture.instrument());

        assertThat(snapshot.getExternalLotSize()).isEqualTo("1");
        assertThat(snapshot.getExternalTickSize()).isEqualTo("0.01");

        instrumentMapper.snapshotToDomain(instrument, snapshot);

        assertThat(Instrument.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("externalLotSize", "externalMinSize", "externalTickSize",
                        "externalContractValue", "externalContractMultiplier");
    }

    /** Стратегии игнорирования у перехода нет; расчётная валюта — авторитет валюты результата. */
    @Test
    @DisplayName("U22.3 — пустая расчётная валюта затирает непустую цель")
    void u22_3_anEmptySettlementCurrencyOverwritesTheTarget() {
        Instrument instrument = existingInstrument();
        var source = OkxFixture.instrument();
        source.setSettleCcy(null);
        InstrumentExternalSnapshot snapshot = instrumentMapper.integrationToSnapshot(source);

        instrumentMapper.snapshotToDomain(instrument, snapshot);

        assertThat(instrument.getExternalSettlementCurrency()).isNull();
    }

    @Test
    @DisplayName("U22.4 — эпизод наполняется положением закрытия целиком")
    void u22_4_theEpisodeIsFilledFromTheCloseRecord() {
        Position position = closedEpisodeWithoutRecord();

        positionMapper.materializeFromCloseSnapshot(closeSnapshot(), position);

        assertThat(position.getExternalRealizedProfit()).isEqualByComparingTo("10");
        assertThat(position.getExternalRealizedProfitGross()).isEqualByComparingTo("12.5");
        assertThat(position.getExternalResultCurrency()).isEqualTo("USDT");
        assertThat(position.getExternalCloseAveragePrice()).isEqualByComparingTo("105");
        assertThat(position.getExternalCloseType()).isEqualTo("1");
        assertThat(position.getExternalFee()).isEqualByComparingTo("-0.3");
        assertThat(position.getExternalFundingCost()).isEqualByComparingTo("2");
        assertThat(position.getExternalLiquidationPenalty()).isEqualByComparingTo("-5");
        assertThat(position.getExternalModifiedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:14:20Z"));
        assertThat(position.getExternalId()).isEqualTo("p1");
        assertThat(position.getDirection()).isEqualTo(Position.Direction.LONG);
        assertThat(position.getExternalCreatedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
    }

    /** Стратегия игнорирования пустого: уже приземлённое значение переживает второй проход. */
    @Test
    @DisplayName("U22.5 — пустой тип закрытия цель не затирает")
    void u22_5_anEmptyCloseTypeDoesNotOverwriteTheTarget() {
        Position position = closedEpisodeWithoutRecord();
        position.setExternalCloseType("1");
        var source = OkxFixture.positionHistory();
        source.setType(null);

        positionMapper.materializeFromCloseSnapshot(
                positionMapper.integrationToCloseSnapshot(source), position);

        assertThat(position.getExternalCloseType()).isEqualTo("1");
    }

    /** Игнорируется только пустое: сверка идентичности делается до наполнения, у вызывающего. */
    @Test
    @DisplayName("U22.6 — непустой идентификатор перезаписывает цель")
    void u22_6_aNonEmptyIdOverwritesTheTarget() {
        Position position = closedEpisodeWithoutRecord();
        position.setExternalId("p0");
        var source = OkxFixture.positionHistory();
        source.setPosId("p9");

        positionMapper.materializeFromCloseSnapshot(
                positionMapper.integrationToCloseSnapshot(source), position);

        assertThat(position.getExternalId()).isEqualTo("p9");
    }

    /** Резолв уже сделан на границе. */
    @Test
    @DisplayName("U22.7 — направление переносится доменным значением, без резолва здесь")
    void u22_7_theDirectionIsCarriedAsADomainValue() {
        Position position = closedEpisodeWithoutRecord();
        PositionCloseResultExternalSnapshot snapshot = closeSnapshot();

        assertThat(snapshot.getDirection()).isEqualTo(Position.Direction.LONG);

        positionMapper.materializeFromCloseSnapshot(snapshot, position);

        assertThat(position.getDirection()).isEqualTo(Position.Direction.LONG);
    }

    /** Ноль — значение, стратегия игнорирования его не гасит. */
    @Test
    @DisplayName("U22.8 — четыре нулевых слагаемых записываются нулями")
    void u22_8_fourZeroAddendsAreWritten() {
        Position position = closedEpisodeWithoutRecord();
        position.setExternalRealizedProfitGross(new BigDecimal("99"));
        position.setExternalFee(new BigDecimal("99"));
        position.setExternalFundingCost(new BigDecimal("99"));
        position.setExternalLiquidationPenalty(new BigDecimal("99"));
        var source = OkxFixture.positionHistory();
        source.setPnl("0");
        source.setFee("0");
        source.setFundingFee("0");
        source.setLiqPenalty("0");

        positionMapper.materializeFromCloseSnapshot(
                positionMapper.integrationToCloseSnapshot(source), position);

        assertThat(position.getExternalRealizedProfitGross()).isEqualByComparingTo("0");
        assertThat(position.getExternalFee()).isEqualByComparingTo("0");
        assertThat(position.getExternalFundingCost()).isEqualByComparingTo("0");
        assertThat(position.getExternalLiquidationPenalty()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U22.10 — пустота вместо снапшота цель не меняет")
    void u22_10_emptinessLeavesTheTargetUntouched() {
        Position position = closedEpisodeWithoutRecord();
        position.setExternalId("p0");
        position.setExternalCloseType("1");

        positionMapper.materializeFromCloseSnapshot(null, position);

        assertThat(position.getExternalId()).isEqualTo("p0");
        assertThat(position.getExternalCloseType()).isEqualTo("1");
        assertThat(position.getExternalRealizedProfit()).isNull();
    }
}

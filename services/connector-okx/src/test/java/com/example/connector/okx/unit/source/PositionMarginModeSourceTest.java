package com.example.connector.okx.unit.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.connector.okx.integration.external.api.client.OkxRestClient;
import com.example.connector.okx.integration.external.api.model.okx.request.ClosePositionOkxRequest;
import com.example.connector.okx.integration.external.api.model.okx.response.OkxApiResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.OrderAckOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.PositionOkxResponse;
import com.example.connector.okx.mapping.AlgoOrderMapper;
import com.example.connector.okx.mapping.BalanceContainerMapper;
import com.example.connector.okx.mapping.CandleMapper;
import com.example.connector.okx.mapping.DealCashFlowMapper;
import com.example.connector.okx.mapping.InstrumentExternalRulesMapper;
import com.example.connector.okx.mapping.InstrumentMapper;
import com.example.connector.okx.mapping.MarketPriceDataMapper;
import com.example.connector.okx.mapping.MarketSnapshotMapper;
import com.example.connector.okx.mapping.OkxResponseConverter;
import com.example.connector.okx.mapping.OrderMapper;
import com.example.connector.okx.mapping.PositionMapperImpl;
import com.example.connector.okx.mapping.TradeFeeRateMapper;
import com.example.connector.okx.resolve.OkxCredentialsRejectionResolver;
import com.example.connector.okx.snapshot.PositionExternalSnapshot;
import com.example.connector.okx.source.OkxSourceReader;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Режим маржи позиции на читателе источника: отбор записи контура при
 * живом чтении по инструменту и режим в теле закрытия
 * (docs/models/mapping/Position.md §«Invariant checks (общая идея)», §«OKX
 * close-position request body»).
 *
 * <p><b>Предмет — читатель, а не маппер:</b> отбор стои́т до маппинга, и
 * тело закрытия собирает он же. Клиент площадки подменён; маппер позиции и
 * конвертер — настоящие, иначе отбор мерился бы против заглушки ровно там,
 * где живёт перевод режима.
 */
class PositionMarginModeSourceTest {

    private static final String INSTRUMENT = "BTC-USDT-SWAP";

    private final OkxRestClient client = mock(OkxRestClient.class);

    private final OkxSourceReader reader = new OkxSourceReader(
            client, mock(OkxCredentialsRejectionResolver.class),
            mock(InstrumentMapper.class), mock(InstrumentExternalRulesMapper.class),
            mock(MarketPriceDataMapper.class), mock(MarketSnapshotMapper.class), mock(CandleMapper.class),
            mock(OrderMapper.class), positionMapper(), mock(BalanceContainerMapper.class),
            mock(AlgoOrderMapper.class), mock(TradeFeeRateMapper.class), mock(DealCashFlowMapper.class),
            new OkxResponseConverter());

    /** Порядок записей в ответе не задан: кросс-запись впереди нашей не должна её подменить. */
    @Test
    @DisplayName("живое чтение берёт запись режима контура, а не первую в ответе")
    void theLiveReadTakesTheContourModeRecordNotTheFirstOne() {
        when(client.getPositions(any(), eq(INSTRUMENT)))
                .thenReturn(ok(List.of(record("pos-cross", "cross", "7"), record("pos-iso", "isolated", "3"))));

        PositionExternalSnapshot snapshot = reader.getPosition(null, INSTRUMENT);

        assertThat(snapshot.getExternalId()).isEqualTo("pos-iso");
        assertThat(snapshot.getExternalSize()).isEqualByComparingTo("3");
        assertThat(snapshot.getMarginMode()).isEqualTo(Instrument.MarginMode.ISOLATED);
    }

    /** Чужая запись — не нарушение контракта: отбор, а не сверка, и отказа нет. */
    @Test
    @DisplayName("одна запись иного режима — позиции контура нет, отказа нет")
    void aLoneForeignModeRecordMeansNoContourPosition() {
        when(client.getPositions(any(), eq(INSTRUMENT)))
                .thenReturn(ok(List.of(record("pos-cross", "cross", "7"))));

        assertThat(reader.getPosition(null, INSTRUMENT)).isNull();
    }

    /** Срез отбора не делает: каждая запись едет со своим режимом. */
    @Test
    @DisplayName("счёт-широкий срез отдаёт записи обоих режимов, каждую со своим режимом")
    void theAccountWideSliceCarriesEveryRecordWithItsMode() {
        when(client.getAllPositions(any()))
                .thenReturn(ok(List.of(record("pos-cross", "cross", "7"), record("pos-iso", "isolated", "3"))));

        assertThat(reader.getPositions(null))
                .extracting(PositionExternalSnapshot::getMarginMode)
                .containsExactly(Instrument.MarginMode.CROSS, Instrument.MarginMode.ISOLATED);
    }

    @Test
    @DisplayName("закрытие без режима уходит режимом контура")
    void aClosureWithoutAModeTravelsWithTheContourMode() {
        assertThat(sentClosure(null).getMgnMode()).isEqualTo("isolated");
    }

    /** Снятие риска вне графа сделок закрывает чужую запись её собственным режимом. */
    @Test
    @DisplayName("закрытие с режимом уходит этим режимом")
    void aClosureWithAModeTravelsWithThatMode() {
        ClosePositionOkxRequest sent = sentClosure(Instrument.MarginMode.CROSS);

        assertThat(sent.getMgnMode()).isEqualTo("cross");
        assertThat(sent.getInstId()).isEqualTo(INSTRUMENT);
        assertThat(sent.getPosSide()).isEqualTo("net");
        assertThat(sent.getAutoCxl()).isTrue();
        assertThat(sentClosure(Instrument.MarginMode.ISOLATED).getMgnMode()).isEqualTo("isolated");
    }

    /** Тело последнего закрытия: захватчик отдаёт последний захват. */
    private ClosePositionOkxRequest sentClosure(Instrument.MarginMode marginMode) {
        when(client.closePosition(any(), any())).thenReturn(ok(List.of(new OrderAckOkxResponse())));

        reader.closePosition(null, INSTRUMENT, "USDT", marginMode);

        ArgumentCaptor<ClosePositionOkxRequest> request = ArgumentCaptor.forClass(ClosePositionOkxRequest.class);
        verify(client, atLeastOnce()).closePosition(any(), request.capture());
        return request.getValue();
    }

    private static PositionMapperImpl positionMapper() {
        PositionMapperImpl mapper = new PositionMapperImpl();
        ReflectionTestUtils.setField(mapper, "okxResponseConverter", new OkxResponseConverter());
        return mapper;
    }

    private static PositionOkxResponse record(String posId, String mgnMode, String pos) {
        PositionOkxResponse response = new PositionOkxResponse();
        response.setPosId(posId);
        response.setInstId(INSTRUMENT);
        response.setPos(pos);
        response.setMgnMode(mgnMode);
        response.setPosSide("net");
        response.setcTime("1700000000000");
        response.setuTime("1700000060000");
        return response;
    }

    private static <T> OkxApiResponse<T> ok(List<T> data) {
        OkxApiResponse<T> response = new OkxApiResponse<>();
        response.setCode("0");
        response.setMsg("");
        response.setData(data);
        return response;
    }
}

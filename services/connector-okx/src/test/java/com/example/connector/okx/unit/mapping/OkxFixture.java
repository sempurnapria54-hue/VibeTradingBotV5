package com.example.connector.okx.unit.mapping;

import com.example.connector.okx.integration.external.api.model.okx.response.AccountBillOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.AlgoOrderOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.AttachAlgoOrdOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.BalanceDetailOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.BalanceOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.CandleOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.InstrumentOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.OrderOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.PositionOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.PositionsHistoryOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.TickerOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.TradeFeeOkxResponse;
import java.util.List;

/**
 * Базовые сборки форм источника для групп документа
 * `.claude/tests/cases/okx-mapping.md`.
 *
 * <p><b>Вход собирается прямо, а не мутацией эталона из файла:</b> у
 * этого предмета эталона нет — формы источника собираются полем за
 * полем, а там, где вход есть <b>текст</b>, его разбирает
 * {@link WireJson}, и сборка сюда не относится вовсе.
 *
 * <p>Каждая сборка отдаёт форму со <b>всеми</b> полями непустыми:
 * отклонение кейса ставится вызывающим одним сеттером, и тогда видно,
 * что именно кейс меняет.
 */
final class OkxFixture {

    static final String INSTRUMENT = "ETH-USDT-SWAP";
    static final String CREATED_MILLIS = "1700000000000";
    static final String MODIFIED_MILLIS = "1700000060000";

    private OkxFixture() {
    }

    /** Заявка площадки со всеми непустыми полями; встроенной защиты нет. */
    static OrderOkxResponse order() {
        OrderOkxResponse response = new OrderOkxResponse();
        response.setInstId(INSTRUMENT);
        response.setClOrdId("tb-1");
        response.setOrdId("1");
        response.setOrdType("limit");
        response.setSide("buy");
        response.setState("live");
        response.setPx("100");
        response.setSz("2");
        response.setAccFillSz("1");
        response.setAvgPx("99.5");
        response.setFee("-0.25");
        response.setcTime(CREATED_MILLIS);
        response.setuTime(MODIFIED_MILLIS);
        response.setAttachAlgoClOrdId("tb-p1");
        response.setTpTriggerPx("120");
        response.setSlTriggerPx("90");
        return response;
    }

    /** Встроенная защита в теле родителя: все девять полей непусты. */
    static AttachAlgoOrdOkxResponse attachedInParentBody() {
        AttachAlgoOrdOkxResponse response = new AttachAlgoOrdOkxResponse();
        response.setAttachAlgoId("a1");
        response.setAttachAlgoClOrdId("tb-p1");
        response.setAlgoId("9");
        response.setTpOrdKind("condition");
        response.setSz("100");
        response.setSlTriggerPx("90");
        response.setSlTriggerPxType("mark");
        response.setFailCode("0");
        response.setFailReason("");
        return response;
    }

    /** Самостоятельная условная заявка со всеми непустыми полями. */
    static AlgoOrderOkxResponse algoOrder() {
        AlgoOrderOkxResponse response = new AlgoOrderOkxResponse();
        response.setInstId(INSTRUMENT);
        response.setAlgoClOrdId("tb-9");
        response.setAlgoId("9");
        response.setState("live");
        response.setFailCode("0");
        response.setFailReason("");
        response.setSz("100");
        response.setActualSz("3");
        response.setActualPx("99");
        response.setTriggerTime(CREATED_MILLIS);
        response.setOrdIdList(List.of("1", "2"));
        response.setSlTriggerPx("90");
        response.setSlTriggerPxType("mark");
        response.setTpTriggerPx("120");
        response.setTpTriggerPxType("last");
        response.setActivePx("110");
        response.setMoveTriggerPx("95");
        response.setcTime(CREATED_MILLIS);
        response.setuTime(MODIFIED_MILLIS);
        return response;
    }

    /** Живая позиция со всеми непустыми полями. */
    static PositionOkxResponse position() {
        PositionOkxResponse response = new PositionOkxResponse();
        response.setPosId("p1");
        response.setInstId(INSTRUMENT);
        response.setPos("5");
        response.setAvgPx("100");
        response.setMarkPx("101");
        response.setLiqPx("50");
        response.setMargin("20");
        response.setUpl("5");
        response.setcTime(CREATED_MILLIS);
        response.setuTime(MODIFIED_MILLIS);
        response.setInstType("SWAP");
        response.setPosSide("net");
        response.setMgnMode("isolated");
        response.setLever("10");
        return response;
    }

    /** Запись истории позиций со всеми непустыми полями. */
    static PositionsHistoryOkxResponse positionHistory() {
        PositionsHistoryOkxResponse response = new PositionsHistoryOkxResponse();
        response.setPosId("p1");
        response.setInstId(INSTRUMENT);
        response.setDirection("long");
        response.setRealizedPnl("10");
        response.setCcy("USDT");
        response.setCloseAvgPx("105");
        response.setPnl("12.5");
        response.setFee("-0.3");
        response.setFundingFee("-2");
        response.setLiqPenalty("-5");
        response.setType("1");
        response.setcTime(CREATED_MILLIS);
        response.setuTime(MODIFIED_MILLIS);
        return response;
    }

    /** Свеча: девять непустых строк, признак закрытия — закрыта. */
    static CandleOkxResponse candle() {
        return CandleOkxResponse.of(
                List.of(CREATED_MILLIS, "100", "110", "90", "105", "12", "13", "14", "1"));
    }

    /** Свеча с отклонением ровно одной позиции. */
    static CandleOkxResponse candleWith(int index, String value) {
        List<String> raw = new java.util.ArrayList<>(
                List.of(CREATED_MILLIS, "100", "110", "90", "105", "12", "13", "14", "1"));
        raw.set(index, value);
        return CandleOkxResponse.of(raw);
    }

    /** Индекс позиции времени открытия свечи. */
    static final int CANDLE_TS = 0;

    /** Индекс позиции цены открытия свечи. */
    static final int CANDLE_OPEN = 1;

    /** Индекс позиции объёма свечи. */
    static final int CANDLE_VOLUME = 5;

    /** Индекс позиции признака закрытия свечи. */
    static final int CANDLE_CONFIRM = 8;

    /** Инструмент листинга: все девятнадцать полей худой формы непусты. */
    static InstrumentOkxResponse instrument() {
        InstrumentOkxResponse response = new InstrumentOkxResponse();
        response.setInstId(INSTRUMENT);
        response.setInstType("SWAP");
        response.setBaseCcy("ETH");
        response.setQuoteCcy("USDT");
        response.setSettleCcy("USDT");
        response.setLotSz("1");
        response.setMinSz("1");
        response.setCtVal("0.1");
        response.setCtValCcy("ETH");
        response.setCtMult("1");
        response.setGroupId("1");
        response.setCtType("linear");
        response.setTickSz("0.01");
        response.setMaxLmtSz("1000000");
        response.setMaxMktSz("100000");
        response.setMaxTriggerSz("100000");
        response.setMaxStopSz("100000");
        response.setState("live");
        response.setLever("10");
        return response;
    }

    /** Ответ о ставках комиссии с одной группой. */
    static TradeFeeOkxResponse tradeFee() {
        TradeFeeOkxResponse response = new TradeFeeOkxResponse();
        response.setInstType("SWAP");
        response.setLevel("Lv1");
        response.setTs(CREATED_MILLIS);
        response.setFeeGroup(List.of(feeGroup("1", "-0.0005", "-0.0002")));
        return response;
    }

    /** Одна группа ставок. */
    static TradeFeeOkxResponse.FeeGroupOkxResponse feeGroup(String groupId, String taker, String maker) {
        TradeFeeOkxResponse.FeeGroupOkxResponse group = new TradeFeeOkxResponse.FeeGroupOkxResponse();
        group.setGroupId(groupId);
        group.setTaker(taker);
        group.setMaker(maker);
        return group;
    }

    /** Баланс счёта с одной валютной записью. */
    static BalanceOkxResponse balance() {
        BalanceOkxResponse response = new BalanceOkxResponse();
        response.setuTime(CREATED_MILLIS);
        response.setTotalEq("1000");
        response.setAdjEq("1000");
        response.setAvailEq("900");
        response.setDetails(new java.util.ArrayList<>(List.of(balanceDetail("USDT"))));
        return response;
    }

    /** Одна валютная запись баланса. */
    static BalanceDetailOkxResponse balanceDetail(String currency) {
        BalanceDetailOkxResponse detail = new BalanceDetailOkxResponse();
        detail.setCcy(currency);
        detail.setuTime(MODIFIED_MILLIS);
        detail.setEq("900");
        detail.setCashBal("800");
        detail.setAvailBal("700");
        detail.setFrozenBal("100");
        return detail;
    }

    /** Движение средств со всеми непустыми полями. */
    static AccountBillOkxResponse cashFlow() {
        AccountBillOkxResponse response = new AccountBillOkxResponse();
        response.setBillId("b1");
        response.setType("2");
        response.setSubType("173");
        response.setTs(CREATED_MILLIS);
        response.setBalChg("12.5");
        response.setPosBalChg("-3");
        response.setFee("-0.25");
        response.setCcy("USDT");
        response.setOrdId("1");
        response.setInstId(INSTRUMENT);
        return response;
    }

    /** Тикер со всеми непустыми полями. */
    static TickerOkxResponse ticker() {
        TickerOkxResponse response = new TickerOkxResponse();
        response.setInstType("SWAP");
        response.setInstId(INSTRUMENT);
        response.setLast("100.5");
        response.setAskPx("100.6");
        response.setBidPx("100.4");
        response.setAskSz("5");
        response.setBidSz("7");
        response.setTs(CREATED_MILLIS);
        response.setVol24h("3000");
        return response;
    }
}

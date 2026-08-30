package com.example.tradingbot.integration.model.okx.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Регрессия на бинд полей read-DTO OKX из сырого JSON под Jackson 3
 * (дефолт RestClient в SB4/Spring 7) — находка F4. Корень тот же, что
 * F3a: Lombok beanspec-аксессоры со строчной-первой/заглавной-второй
 * буквами (`cTime`/`uTime` → `getcTime()`/`getuTime()`) Jackson 3 не
 * матчит с JSON-ключом → поле биндится в null.
 *
 * <p>Проверка — полным представительным payload (ключ = имя поля OKX,
 * т.е. контракт бинда по имени): каждое объявленное поле обязано
 * связаться (non-null). Значения читаются рефлексией прямо из поля, мимо
 * аксессоров, чтобы тест проверял именно бинд, а не геттер.
 */
class OkxReadDtoDeserializationTest {

    private static final ObjectMapper JACKSON3 = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    void balanceResponseBindsAllFields() {
        String json = """
                {"uTime":"1700000000000","totalEq":"77842.27","adjEq":"77000",\
                "availEq":"5000","details":[{"ccy":"USDT","uTime":"1700000000000",\
                "eq":"5000","cashBal":"5000","availBal":"5000","frozenBal":"0"}]}""";
        assertAllFieldsBind(BalanceOkxResponse.class, json);
    }

    @Test
    void balanceDetailResponseBindsAllFields() {
        String json = """
                {"ccy":"USDT","uTime":"1700000000000","eq":"5000","cashBal":"5000",\
                "availBal":"5000","frozenBal":"0"}""";
        assertAllFieldsBind(BalanceDetailOkxResponse.class, json);
    }

    @Test
    void positionResponseBindsAllFields() {
        String json = """
                {"posId":"123","pos":"1","avgPx":"1674","markPx":"1674.22","liqPx":"100",\
                "margin":"10","upl":"0","cTime":"1700000000000","uTime":"1700000000001",\
                "instId":"ETH-USDT-SWAP","instType":"SWAP","posSide":"net","mgnMode":"isolated",\
                "lever":"3"}""";
        assertAllFieldsBind(PositionOkxResponse.class, json);
    }

    @Test
    void orderResponseBindsAllFields() {
        String json = """
                {"clOrdId":"cli-1","ordId":"ord-1","ordType":"limit","side":"buy","state":"live",\
                "px":"837","sz":"0.01","accFillSz":"0","avgPx":"0","fee":"0",\
                "cTime":"1700000000000","uTime":"1700000000001","attachAlgoClOrdId":"a-1",\
                "tpTriggerPx":"1800","slTriggerPx":"1500","attachAlgoOrds":[{}]}""";
        assertAllFieldsBind(OrderOkxResponse.class, json);
    }

    @Test
    void algoOrderResponseBindsAllFields() {
        String json = """
                {"algoClOrdId":"algo-1","algoId":"aid-1","state":"live","failCode":"0",\
                "actualSz":"0.01","actualPx":"0","triggerTime":"1700000000000","ordIdList":["o1"],\
                "slTriggerPx":"1500","slTriggerPxType":"last","tpTriggerPx":"1800",\
                "tpTriggerPxType":"last","activePx":"1700","moveTriggerPx":"1690",\
                "cTime":"1700000000000","uTime":"1700000000001"}""";
        assertAllFieldsBind(AlgoOrderOkxResponse.class, json);
    }

    /**
     * Десериализует {@code json} под Jackson 3 и проверяет, что каждое
     * объявленное поле {@code type} связалось (non-null). Поле читается
     * рефлексией напрямую, минуя Lombok-аксессоры.
     */
    private static void assertAllFieldsBind(Class<?> type, String json) {
        Object dto = JACKSON3.readValue(json, type);
        List<String> unbound = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            field.setAccessible(true);
            try {
                if (field.get(dto) == null) {
                    unbound.add(field.getName());
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        assertThat(unbound)
                .as("Jackson 3 must bind every field of %s, but these stayed null", type.getSimpleName())
                .isEmpty();
    }
}

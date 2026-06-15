package com.example.tradingbot.integration.model.okx.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Регрессия на бинд per-element полей write-ack OKX из сырого JSON
 * (находка F3a). Сырой ключ {@code sCode}/{@code sMsg} обязан доезжать
 * до DTO под обоими Jackson на classpath: Jackson 2 (com.fasterxml,
 * совместимостный бин) и Jackson 3 (tools.jackson, дефолт SB4/Spring 7,
 * который и десериализует ответ RestClient'а). Тело — из живого probe
 * place-реджекта (run-log 2026-06-12).
 */
class OkxAckDeserializationTest {

    private static final String ORDER_ACK_ELEMENT = """
            {"clOrdId":"f3probe1781282363","ordId":"","sCode":"51010",\
            "sMsg":"You can't complete this request under your current account mode. ",\
            "subCode":"","tag":"tb","ts":"1781282363291"}""";

    private static final String ALGO_ACK_ELEMENT = """
            {"algoClOrdId":"algo-probe-1","algoId":"","sCode":"51277",\
            "sMsg":"TP trigger price can not be higher than the last price.","tag":"tb"}""";

    @Test
    void orderAckBindsScodeUnderJackson2() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        OrderAckOkxResponse ack = mapper.readValue(ORDER_ACK_ELEMENT, OrderAckOkxResponse.class);

        assertThat(ack.getsCode()).isEqualTo("51010");
        assertThat(ack.getsMsg()).isNotBlank();
        assertThat(ack.getClOrdId()).isEqualTo("f3probe1781282363");
    }

    @Test
    void orderAckBindsScodeUnderJackson3() {
        tools.jackson.databind.ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        OrderAckOkxResponse ack = mapper.readValue(ORDER_ACK_ELEMENT, OrderAckOkxResponse.class);

        assertThat(ack.getsCode()).isEqualTo("51010");
        assertThat(ack.getsMsg()).isNotBlank();
        assertThat(ack.getClOrdId()).isEqualTo("f3probe1781282363");
    }

    @Test
    void algoAckBindsScodeUnderJackson2() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        AlgoOrderAckOkxResponse ack = mapper.readValue(ALGO_ACK_ELEMENT, AlgoOrderAckOkxResponse.class);

        assertThat(ack.getsCode()).isEqualTo("51277");
        assertThat(ack.getsMsg()).isNotBlank();
    }

    @Test
    void algoAckBindsScodeUnderJackson3() {
        tools.jackson.databind.ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        AlgoOrderAckOkxResponse ack = mapper.readValue(ALGO_ACK_ELEMENT, AlgoOrderAckOkxResponse.class);

        assertThat(ack.getsCode()).isEqualTo("51277");
        assertThat(ack.getsMsg()).isNotBlank();
    }
}

package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * AG5. Bills deep-архив — {@code POST+GET /api/v5/account/bills-history-archive}
 * (Account), {@code signed:true}. Read-only флоу (заявка не меняет торговое
 * состояние), без teardown. Содержательный {@code finished}-файл недостижим на
 * свежем demo (async + нет истории квартала). **POST-заявка расходует квоту
 * 12/сутки — запускать осознанно, не в цикле.** Ассертим форму ACK/состояния,
 * содержимое не выдумываем.
 */
@Order(35)
class Ag5BillsHistoryArchiveLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/account/bills-history-archive";

    @Test
    @Order(10)
    @DisplayName("AG5.1 Прямой POST-заявка — ACK заявки (расходует квоту 12/сутки)")
    void ag5_1_postRequestAck() {
        RawResponse r = post(PATH, map("year", "2025", "quarter", "Q1"), SIGNED);

        assertOk(r);
        observe("AG5.1", r);
        if (!r.dataEmpty()) {
            assertThat(r.d0().path("result").isMissingNode()).isFalse();
            assertThat(r.d0().path("ts").isMissingNode()).isFalse();
        }
    }

    @Test
    @Order(20)
    @DisplayName("AG5.2 Прямой GET — получение файла (ожидается ongoing/нет данных)")
    void ag5_2_getFile() {
        RawResponse r = get(PATH, map("year", "2025", "quarter", "Q1"), SIGNED);

        assertOk(r);
        observe("AG5.2", r);
        if (!r.dataEmpty()) {
            assertThat(r.d0().path("state").isMissingNode()).isFalse();
        }
    }

    @Test
    @Order(30)
    @DisplayName("AG5.3 Негатив — битое значение quarter (вне домена Q1..Q4)")
    void ag5_3_quarterOutOfDomain() {
        RawResponse r = post(PATH, map("year", "2025", "quarter", "Q9"), SIGNED);

        assertBusinessReject(r);
        observe("AG5.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("AG5.4 Негатив — пропуск обязательного quarter (POST)")
    void ag5_4_missingQuarter() {
        RawResponse r = post(PATH, map("year", "2025"), SIGNED);

        assertBusinessReject(r);
        observe("AG5.4", r);
    }
}

package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M21. cancelAdvanceAlgos — {@code POST /api/v5/trade/cancel-advance-algos}
 * (Cancel advance algo), {@code signed:true}. Тело — массив
 * {@code [{instId, algoId}]}. Прямой (advance семья, гипотеза И-2) покрыт
 * цепочкой M19 trailing (.cancel, не дублируется). Здесь — no-state негатив:
 * реджект несущ. algoId ИЛИ «endpoint не существует» (И-2: эндпоинт выведен
 * из офдока) — точный исход наблюдается, делистинг не выдумывается.
 */
@Order(21)
class M21CancelAdvanceAlgosLiveTest extends OkxSourceApiLiveTestBase {

    @Test
    @Order(10)
    @DisplayName("M21.1 негатив (no-state) — cancel несущ. algoId (advance)")
    void m21_1_cancelNonexistent() {
        RawResponse r = post(CANCEL_ADVANCE_ALGOS_PATH,
                List.of(map("instId", INST_ID, "algoId", "9999999999999999")), SIGNED);

        observe("M21.1", r);
        // Реджект на любом уровне ИЛИ пустой/иной ответ (И-2: возможный делистинг).
        // Точный исход — наблюдение, делистинг → находка интегратору (C3).
        if (!(r.businessReject() || r.firstElementReject() || r.dataEmpty())) {
            log.warn("[M21.1] И-2 finding (C3): cancel-advance-algos did not reject a fake algoId "
                    + "(code={}, msg={}) — verify endpoint contract", r.code(), r.msg());
        }
        assertThat(r.status()).as("HTTP status (envelope reached app)").isEqualTo(200);
    }
}

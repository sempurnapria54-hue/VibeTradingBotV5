package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assumptions;
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

        // Эндпоинт достижим (HTTP 200 + структурный конверт OKX), content на
        // свежем demo недостижим. Все три исхода — валидные, не дефект:
        //  - ACK заявки (b.code=0) — ссылка готова/генерится;
        //  - RUN-факт (2026-06-19) demo system-error b.code=50026 "System error.
        //    Try again later." — архив на demo не инициируется (нет истории
        //    квартала / async-бэкенд недоступен);
        //  - RUN-факт (2026-06-20) rate-limit/квота (b.code=50011 "Too Many
        //    Requests") — исчерпан суточный лимит 12 заявок/сутки. Лимит прямо
        //    предусмотрен планом (AG5 §достижимость); base.raw() уже исчерпал
        //    backoff-ретраи → возвращённый rate-limit терминален.
        // C3: реальный исход — наблюдение в апидок.
        assertHttp200(r);
        observe("AG5.1", r);
        assertThat(r.codeZero() || "50026".equals(r.code()) || isRateLimited(r))
                .as("AG5.1: ACK code=0, demo system-error 50026 или rate-limit/квота 12/сутки (b.code=%s)", r.code())
                .isTrue();
        if (r.codeZero() && !r.dataEmpty()) {
            assertThat(r.d0().path("result").isMissingNode()).isFalse();
            assertThat(r.d0().path("ts").isMissingNode()).isFalse();
        }
    }

    @Test
    @Order(20)
    @DisplayName("AG5.2 Прямой GET — получение файла (ожидается ongoing/нет данных)")
    void ag5_2_getFile() {
        RawResponse r = get(PATH, map("year", "2025", "quarter", "Q1"), SIGNED);

        // RUN-факт (2026-06-19): GET до успешной инициализации заявки →
        // b.code=51604 "Initiate a download request before obtaining the
        // hyperlink". На demo заявка не инициализируется (см. AG5.1), файл
        // недостижим. Принимаем готовый файл (code=0) ИЛИ 51604. C3.
        assertHttp200(r);
        observe("AG5.2", r);
        assertThat(r.codeZero() || "51604".equals(r.code()))
                .as("AG5.2: файл code=0 или 51604 (нет инициализации на demo) (b.code=%s)", r.code())
                .isTrue();
        if (r.codeZero() && !r.dataEmpty()) {
            assertThat(r.d0().path("state").isMissingNode()).isFalse();
        }
    }

    @Test
    @Order(30)
    @DisplayName("AG5.3 Негатив — битое значение quarter (вне домена Q1..Q4)")
    void ag5_3_quarterOutOfDomain() {
        RawResponse r = post(PATH, map("year", "2025", "quarter", "Q9"), SIGNED);

        observe("AG5.3", r);
        // Квота 12/сутки: при исчерпании OKX реджектит на rate-limit (50011) ДО
        // валидации quarter, маскируя реджект-по-домену. Не засчитываем ложный
        // pass — rate-limit → кейс пропускается (валидацию quarter перепроверить
        // вне исчерпания квоты). Иначе — реджект OKX по домену quarter.
        Assumptions.assumeFalse(isRateLimited(r),
                "AG5.3: валидация quarter не достигнута — rate-limit/квота 12/сутки маскирует реджект");
        assertBusinessReject(r);
    }

    @Test
    @Order(40)
    @DisplayName("AG5.4 Негатив — пропуск обязательного quarter (POST)")
    void ag5_4_missingQuarter() {
        RawResponse r = post(PATH, map("year", "2025"), SIGNED);

        observe("AG5.4", r);
        // Как AG5.3: rate-limit (квота 12/сутки) реджектит ДО проверки
        // обязательного quarter — не засчитываем ложный pass, кейс пропускается.
        Assumptions.assumeFalse(isRateLimited(r),
                "AG5.4: валидация обязательного quarter не достигнута — rate-limit/квота 12/сутки маскирует реджект");
        assertBusinessReject(r);
    }
}

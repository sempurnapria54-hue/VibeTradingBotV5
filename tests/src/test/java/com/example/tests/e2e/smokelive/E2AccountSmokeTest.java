package com.example.tests.e2e.smokelive;

import com.example.tests.e2e.smokelive.Perimeter.Reply;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E2} дыма: счёт демо-контура и его ключи
 * (.claude/tests/cases/smoke-live.md §«E2 — Счёт демо-контура и его ключи»).
 *
 * <p><b>Счёт дым находит, а не заводит</b>
 * (.claude/tests/cases/smoke-live.md §«Предусловия дыма — что он
 * обязан застать»): число счетов тенанта снято {@link SmokeRun} до первого
 * хода прогона и сверяется здесь и в группе-конце.
 *
 * <p><b>Не ассертится — наружу не наблюдается</b>
 * (.claude/tests/cases/smoke-live.md §«Пробелы покрытия»,
 * {@code G4}, {@code G6}): строка ставки комиссии после тика ({@code E2.2}) и
 * «в хранилище секретов по адресу счёта ничего не записано» ({@code E2.3}).
 */
@Tag("smoke")
@DisplayName("E2 — Счёт демо-контура и его ключи")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class E2AccountSmokeTest {

    private static final List<String> KEY_FIELDS = List.of("apiKey", "secret", "passphrase");

    private static SmokeRun run;

    @BeforeAll
    static void requirePreconditions() {
        run = SmokeRun.get();
        run.requireCommonPreconditions();
    }

    @Test
    @Order(1)
    @DisplayName("E2.1 — Счёт найден чтением, контур DEMO, и дым его не заводит")
    void e2_1_theAccountIsFoundByReadingItsContourIsDemo() {
        Reply accounts = run.perimeter().get(run.accountsPath());

        assertThat(accounts.status()).as("E2.1: реестр счетов тенанта не прочитан: %s", accounts).isEqualTo(200);
        assertThat(accounts.json()).as("E2.1: у тенанта не ровно один счёт: %s", accounts).hasSize(1);
        JsonNode account = accounts.json().get(0);
        assertThat(account.path("exchangeCode").asString("")).as("E2.1: счёт не площадки OKX").isEqualTo("OKX");
        assertThat(account.path("contour").asString("")).as("E2.1: контур счёта не DEMO").isEqualTo("DEMO");
        assertThat(account.path("status").asString("")).as("E2.1: счёт не действующий").isEqualTo("ACTIVE");
        accounts.json().forEach(row -> KEY_FIELDS.forEach(field -> assertThat(row.has(field))
                .as("E2.1: реестр отдал секрет счёта — поле %s", field).isFalse()));
        assertThat(accounts.json().size()).as("E2.1: число счетов тенанта изменилось с начала прогона")
                .isEqualTo(run.accountCountAtStart());
    }

    @Test
    @Order(2)
    @DisplayName("E2.2 — Подписанное обращение к площадке проходит: ключи счёта достаёт коннектор")
    void e2_2_theFeeRateTickIsAccepted() {
        Reply tick = run.perimeter().post("/api/v1/trading-core/jobs/trade-fee-rates", null);

        assertThat(tick.status()).as("E2.2: тик синка ставки не принят асинхронным фасадом: %s", tick)
                .isEqualTo(202);
    }

    @Test
    @Order(3)
    @DisplayName("E2.3 — Счёт боевого контура окружение не допускает")
    void e2_3_aLiveContourAccountIsNotAdmitted() {
        String body = """
                {"tenantInternalId": "%s", "exchangeCode": "OKX", "label": "smoke-live-contour-refused",
                 "contour": "LIVE", "apiKey": "smoke-invalid-key", "secret": "smoke-invalid-secret",
                 "passphrase": "smoke-invalid-passphrase"}
                """.formatted(run.tenant());

        Reply refused = run.perimeter().post("/api/v1/auth/exchange-accounts", body);

        assertThat(refused.status()).as("E2.3: регистрация счёта контура LIVE не отвергнута: %s", refused)
                .isEqualTo(422);
        assertThat(refused.errorCode()).as("E2.3: отказ не по недопустимому контуру: %s", refused)
                .isEqualTo("CONTOUR_NOT_ADMITTED");
        Reply accounts = run.perimeter().get(run.accountsPath());
        assertThat(accounts.json().size()).as("E2.3: отказанная регистрация оставила строку счёта")
                .isEqualTo(run.accountCountAtStart());
        accounts.json().forEach(row -> assertThat(row.path("contour").asString(""))
                .as("E2.3: в реестре тенанта есть счёт боевого контура").isNotEqualTo("LIVE"));
    }
}

package com.example.tests.e2e.perimeterread;

import com.example.tests.e2e.IdentityStub;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Кейс {@code E7.4} группы {@code E7} тропы периметра: отказ доступа на
 * периметре строки не оставляет нигде (.claude/tests/cases/e2e-perimeter-read.md
 * §«E7.4 — Отказ доступа на периметре строки не оставляет нигде»). Прочие
 * клетки группы стоят на состоянии {@code E3.1} и гейтятся находкой
 * {@code F6} первой тропы.
 *
 * <p><b>Красна по построению:</b> следом отказа у периметра дом называет лог
 * и метрику, а сегодня нет ни того, ни другого (.claude/work/backlog.md
 * §«Отказ доступа у периметра не оставляет ни лога, ни метрики»). Ассерт
 * журнала стоит последним: прочие ожидания клетки прогон мерит раньше.
 */
@Tag("e2e")
@DisplayName("E7 — Отказ стороны на тропе")
class PerimeterRefusalPathTest {

    private static final String JOURNAL = "/api/v1/audit/journal/records"
            + "?from=2026-01-01T00:00:00Z&to=2026-01-02T00:00:00Z";

    private static final List<Party> OWNERS =
            List.of(Party.TRADING_CORE, Party.STRATEGIES, Party.AUDIT, Party.STATISTICS, Party.AUTH);

    private static Trail trail;

    @BeforeAll
    static void openTrail() {
        trail = Trail.openPerimeter("p7");
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @Tag("debt")
    @DisplayName("E7.4 — Отказ доступа на периметре строки не оставляет нигде")
    void e7_4_aPerimeterRefusalLeavesNoRowAnywhere() {
        IdentityStub foreign = new IdentityStub();
        String owner = trail.identity().browserToken("subject-s1", "Trader One");
        assertThat(trail.callWith(owner, Party.BFF, "GET", "/api/v1/bff/context", null, null).status())
                .as("предусловие — состояние E1.1").isEqualTo(200);
        Long auditDenials = trail.database(Party.AUDIT).count("access_denials");
        Long statisticsDenials = trail.database(Party.STATISTICS).count("access_denials");
        Long logMark = trail.side(Party.BFF).logMark();
        trail.forgetTraces();

        Answer anonymous = trail.callWith("", Party.BFF, "GET", JOURNAL, null, null);
        Answer forged = trail.callWith(foreign.browserToken("subject-s1", "Trader One"), Party.BFF, "GET", JOURNAL,
                null, null);
        Answer forgedTicket = trail.callWith(owner, Party.BFF, "GET", "/api/v1/bff/stream?ticket=forged-ticket",
                null, null);
        foreign.stop();

        for (Map.Entry<String, Answer> refused : Map.of("без токена", anonymous, "чужой подписью", forged,
                "подделанным билетом", forgedTicket).entrySet()) {
            assertThat(refused.getValue().status()).as("E7.4: отвергнуто " + refused.getKey() + " — "
                    + refused.getValue().body()).isEqualTo(401);
            assertThat(Json.object(refused.getValue().body())).as("E7.4: единый формат отказа — " + refused.getKey())
                    .containsKeys("code", "occurredAt");
        }
        for (Party side : OWNERS) {
            assertThat(trail.accesses(side)).as("E7.4: к " + side.module() + " не ушло ни одного запроса").isEmpty();
        }
        assertThat(trail.marketData().requests()).as("E7.4: к стабу владельца рыночных данных тоже").isEmpty();
        assertThat(trail.database(Party.AUTH).hasTable("access_denials"))
                .as("E7.4: у владельца членств строки отказа доступа нет — у отказа периметра базы нет").isFalse();
        assertThat(trail.database(Party.AUDIT).count("access_denials")).as("E7.4: у журнала строк не изменилось")
                .isEqualTo(auditDenials);
        assertThat(trail.database(Party.STATISTICS).count("access_denials"))
                .as("E7.4: у статистики строк не изменилось").isEqualTo(statisticsDenials);
        assertThat(trail.side(Party.BFF).logSince(logMark))
                .as("E7.4: след отказа у периметра — запись журнала с поверхностью отказа")
                .contains("/api/v1/audit/journal/records");
    }
}

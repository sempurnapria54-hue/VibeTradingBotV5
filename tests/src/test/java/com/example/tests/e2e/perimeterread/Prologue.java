package com.example.tests.e2e.perimeterread;

import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

/**
 * Пролог тропы периметра: такты 1 и 2 — контекст субъекта заводит тенанта,
 * его биржевой счёт регистрируется у настоящего владельца членств, и ходы
 * первой тропы идут под ними до сложенной строки агрегата
 * (.claude/tests/cases/e2e-perimeter-read.md §«Предусловия тропы — что лежит до
 * первого хода»).
 *
 * <p><b>Тенант заводится раньше пролога, и порядок несущий:</b> регистрация
 * счёта требует существующего тенанта, а заводит его первый ход самой тропы —
 * вывод контекста по браузерному токену.
 *
 * <p><b>Расписание пересчёта после пролога снова выключено:</b> строка суток
 * сложена, и кейсу, утверждающему «чтение такта не запускает», бьющее
 * расписание подменило бы предмет.
 */
final class Prologue {

    private static final String CONTEXT = "/api/v1/bff/context";

    private static final String REGISTER = "/api/v1/auth/exchange-accounts";

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static final String NEVER = "0 0 0 1 1 *";

    private Prologue() {
    }

    /**
     * Проходит такты 1 и 2 под браузерным токеном субъекта.
     *
     * @param trail тропа периметра
     * @param token браузерный токен субъекта
     * @return тенант, счёт, определение и сделка пролога
     */
    static Tenancy walk(Trail trail, String token) {
        Tenancy tenancy = walkToOpenDeal(trail, token);
        trail.entrySubmitted();
        trail.relayCore();
        trail.exchangeFillsEntry();
        trail.passUntil("пролог: налив наблюдён", () -> isFalse(trail.database(Party.TRADING_CORE)
                .query("select id from orders where external_status = 'filled'").isEmpty()));
        trail.relayCore();
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        Trail.await("пролог: строка суток сложена с решением о заявке", () -> isFalse(trail
                .database(Party.STATISTICS)
                .query("select id from incident_aggregates where tenant_id = ? and bucket_date = ?"
                        + " and order_decisions = 1", tenancy.tenant(), LocalDate.now(ZoneOffset.UTC)).isEmpty()));
        trail.statisticsRecomputes(NEVER);
        return tenancy;
    }

    /**
     * Проходит пролог до заведённой сделки, чья входная заявка ещё не решена:
     * факт открытия опубликован, а следующий проход по сделке решает заявку.
     *
     * <p><b>Это единственная точка пролога, где проход ядра решает заявку</b> —
     * у налитой сделки таких шагов нет: сопровождение эталона ставит только
     * условные заявки, а факт решения пишет лишь создание заявки. Поэтому
     * группа, чей вход — факт ядра, начинает здесь.
     *
     * @param trail тропа периметра
     * @param token браузерный токен субъекта
     * @return тенант, счёт, определение и сделка без решённой заявки
     */
    static Tenancy walkToOpenDeal(Trail trail, String token) {
        Answer context = trail.callWith(token, Party.BFF, "GET", CONTEXT, null, null);
        require(context, 200, "контекст субъекта");
        String tenant = String.valueOf(Json.object(context.body()).get("tenantId"));
        Answer registered = trail.call(Party.AUTH, "POST", REGISTER, null, """
                {
                  "tenantInternalId": "%s",
                  "exchangeCode": "OKX",
                  "label": "e2e perimeter account",
                  "contour": "%s",
                  "apiKey": "api-key-prologue",
                  "secret": "secret-prologue",
                  "passphrase": "passphrase-prologue"
                }
                """.formatted(tenant, Trail.CONTOUR));
        require(registered, 201, "регистрация биржевого счёта");
        String account = String.valueOf(Json.object(registered.body()).get("internalId"));
        trail.under(tenant, account);
        trail.factSeriesStartedYesterday();
        trail.commonPreconditions();
        String definition = trail.activeDefinition();
        String deal = trail.openDeal();
        trail.relayCore();
        return new Tenancy(tenant, account, definition, deal);
    }

    private static void require(Answer answer, Integer status, String step) {
        if (isFalse(Objects.equals(status, answer.status()))) {
            throw new IllegalStateException("Пролог не поставлен: " + step + " — " + answer.status() + " "
                    + answer.body());
        }
    }

    /**
     * Что пролог сложил.
     *
     * @param tenant     тенант, заведённый контекстом
     * @param account    биржевой счёт, выданный регистрацией
     * @param definition активное определение
     * @param deal       сделка с наблюдённым наливом
     */
    record Tenancy(String tenant, String account, String definition, String deal) {
    }
}

package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.tradingbot.domain.util.ExchangeAccountSecretFields;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Регистрация биржевого счёта — группа {@code B2} документа
 * `.claude/tests/cases/auth.md`.
 *
 * <p><b>Ключи — собственный выход предмета:</b> `auth` единственное место
 * платформы, где они пишутся (`docs/architecture/tenant-and-exchange.md`
 * §Ключи), и наблюдается запись СНАРУЖИ — запросом к контейнеру
 * хранилища, а не {@code VaultTemplate} контекста.
 *
 * <p><b>Тенант у каждой клетки свой:</b> уникальное ограничение метки
 * счёта живёт на тройке «тенант × площадка × метка», и общий тенант
 * сделал бы исход клетки зависящим от порядка соседей.
 *
 * <p><b>Клетки, требующие другого положения осей окружения, живут своими
 * классами:</b> {@code B2.3} — незаданный перечень контуров
 * ({@link UnconfiguredContoursBoxTest}), {@code B2.10} — незаданное имя
 * окружения ({@link UnnamedEnvironmentBoxTest}), {@code B2.9} — токен без
 * права записи ({@link SecretStoreWriteDeniedBoxTest}), {@code B2.17} —
 * остановленный контейнер хранилища ({@link SecretStoreUnavailableBoxTest};
 * свой контейнер по первому основанию — кейс лишает соседей адреса).
 *
 * <p><b>Кейса {@code B2.15} здесь нет:</b> справочника площадок у `auth`
 * нет ни таблицей, ни поверхностью, и ожидание не выведено ни одним
 * домом — клетка описана и не прогоняется до исхода парковки.
 */
class ExchangeAccountRegistrationBoxTest extends SharedAuthBox {

    @Test
    @DisplayName("B2.1 — штатная регистрация допущенного контура")
    void b2_1_aRegularRegistrationOfAnAdmittedContour() {
        String tenant = provisionTenant("user-b2-1");
        Long accountsBefore = rows.count("exchange_accounts");

        Answer answer = post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b2-1"),
                Bodies.registration(tenant).body());

        assertThat(answer.status()).isEqualTo(201);
        Map<String, Object> body = answer.asObject();
        assertThat(body).containsOnlyKeys("internalId", "tenantInternalId", "exchangeCode",
                "label", "contour", "status");
        assertThat(body.get("tenantInternalId")).isEqualTo(tenant);
        assertThat(body.get("exchangeCode")).isEqualTo("OKX");
        assertThat(body.get("label")).isEqualTo("main");
        assertThat(body.get("contour")).isEqualTo("DEMO");
        assertThat(body.get("status")).isEqualTo("ACTIVE");
        assertThat(answer.body()).doesNotContain(Bodies.API_KEY_MARKER, Bodies.SECRET_MARKER,
                Bodies.PASSPHRASE_MARKER);
        assertThat(rows.count("exchange_accounts")).isEqualTo(accountsBefore + 1);
        String internalId = String.valueOf(body.get("internalId"));
        Map<String, Object> row = rows.row("exchange_accounts", "internal_id", internalId);
        assertThat(row.get("tenant_id")).isEqualTo(tenant);
        assertThat(row.get("exchange_code")).isEqualTo("OKX");
        assertThat(row.get("label")).isEqualTo("main");
        assertThat(row.get("contour")).isEqualTo("DEMO");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(secrets.account(AuthSubstrate.ENVIRONMENT, internalId))
                .containsOnlyKeys(ExchangeAccountSecretFields.API_KEY,
                        ExchangeAccountSecretFields.SECRET,
                        ExchangeAccountSecretFields.PASSPHRASE,
                        ExchangeAccountSecretFields.CONTOUR)
                .containsEntry(ExchangeAccountSecretFields.API_KEY, Bodies.API_KEY_MARKER)
                .containsEntry(ExchangeAccountSecretFields.CONTOUR, "DEMO");
    }

    @Test
    @DisplayName("B2.2 — недопущенный контур отвергается на регистрации")
    void b2_2_anInadmissibleContourIsRefused() {
        String tenant = provisionTenant("user-b2-2");
        Long accountsBefore = rows.count("exchange_accounts");
        Integer secretsBefore = secrets.accountNames(AuthSubstrate.ENVIRONMENT).size();

        Answer answer = post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b2-2"),
                Bodies.registration(tenant).with("contour", "LIVE").body());

        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.errorCode()).isEqualTo("CONTOUR_NOT_ADMITTED");
        assertThat(answer.body()).doesNotContain(Bodies.API_KEY_MARKER, Bodies.SECRET_MARKER,
                Bodies.PASSPHRASE_MARKER);
        assertThat(rows.count("exchange_accounts")).isEqualTo(accountsBefore);
        assertThat(secrets.accountNames(AuthSubstrate.ENVIRONMENT)).hasSize(secretsBefore);
    }

    @Test
    @DisplayName("B2.4 — счёт несуществующего тенанта")
    void b2_4_anAccountOfANonExistentTenant() {
        provisionTenant("user-b2-4");
        Long accountsBefore = rows.count("exchange_accounts");
        Integer secretsBefore = secrets.accountNames(AuthSubstrate.ENVIRONMENT).size();

        Answer answer = post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b2-4"),
                Bodies.registration("нет-такого").body());

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.errorCode()).isEqualTo("INVALID_REQUEST");
        assertThat(rows.count("exchange_accounts")).isEqualTo(accountsBefore);
        assertThat(secrets.accountNames(AuthSubstrate.ENVIRONMENT)).hasSize(secretsBefore);
    }

    @Test
    @DisplayName("B2.5 — непредъявленный обязательный ключ счёта отвечает 400 единым error-DTO")
    void b2_5_anAbsentApiKeyIsRefused() {
        refusedByValidation("apiKey", "user-b2-5");
    }

    /**
     * Значение контура вне перечня: {@code Contour.valueOf} бросает в теле
     * метода контроллера, до всякого обращения к сервису.
     *
     * <p><b>Половина клетки о ТЕКСТЕ платформенного исключения не
     * проверяется, и это названо:</b> запрета на имя доменного класса в
     * теле отказа не несёт ни один дом вне тропы отказа доступа и `500`
     * (находка {@code F-6} документа). Ослаблять ассерт под текущее
     * поведение здесь нечего — ожидания не существует.
     */
    @Test
    @DisplayName("B2.6 — значение контура вне перечня отвергается")
    void b2_6_aContourValueOutsideTheEnumIsRefused() {
        String tenant = provisionTenant("user-b2-6");
        Long accountsBefore = rows.count("exchange_accounts");
        Integer secretsBefore = secrets.accountNames(AuthSubstrate.ENVIRONMENT).size();

        Answer answer = post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b2-6"),
                Bodies.registration(tenant).with("contour", "PAPER").body());

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(rows.count("exchange_accounts")).isEqualTo(accountsBefore);
        assertThat(secrets.accountNames(AuthSubstrate.ENVIRONMENT)).hasSize(secretsBefore);
    }

    /**
     * Дубль метки: уникальное ограничение схемы стои́т на тройке «тенант ×
     * площадка × метка». Нарушение ограничения поимённо не ловит ни один
     * обработчик сервиса — тело собирает последний.
     */
    @Test
    @DisplayName("B2.7 — дубль метки счёта отвергается единым error-DTO")
    void b2_7_aDuplicateAccountLabelIsRefused() {
        String tenant = provisionTenant("user-b2-7");
        String token = identity.browserToken("user-b2-7");
        post(EXCHANGE_ACCOUNTS, token, Bodies.registration(tenant).body());
        Long accountsBefore = rows.count("exchange_accounts");
        Integer secretsBefore = secrets.accountNames(AuthSubstrate.ENVIRONMENT).size();

        Answer answer = post(EXCHANGE_ACCOUNTS, token, Bodies.registration(tenant).body());

        assertThat(answer.status()).isNotEqualTo(201);
        assertThat(rows.count("exchange_accounts")).isEqualTo(accountsBefore);
        assertThat(rows.rowsWhere("exchange_accounts", "tenant_id", tenant)).hasSize(1);
        assertThat(secrets.accountNames(AuthSubstrate.ENVIRONMENT)).hasSize(secretsBefore);
        assertThat(answer.carriesErrorDto()).isTrue();
    }

    @Test
    @DisplayName("B2.8 — второй счёт той же площадки с другой меткой")
    void b2_8_aSecondAccountOfTheSameExchangeWithAnotherLabel() {
        String tenant = provisionTenant("user-b2-8");
        String token = identity.browserToken("user-b2-8");
        String first = String.valueOf(post(EXCHANGE_ACCOUNTS, token,
                Bodies.registration(tenant).body()).asObject().get("internalId"));
        Long accountsBefore = rows.count("exchange_accounts");

        Answer answer = post(EXCHANGE_ACCOUNTS, token,
                Bodies.registration(tenant).with("label", "reserve").body());

        assertThat(answer.status()).isEqualTo(201);
        String second = String.valueOf(answer.asObject().get("internalId"));
        assertThat(second).isNotEqualTo(first);
        assertThat(rows.count("exchange_accounts")).isEqualTo(accountsBefore + 1);
        assertThat(rows.rowsWhere("exchange_accounts", "tenant_id", tenant))
                .extracting(row -> row.get("label"))
                .containsExactlyInAnyOrder("main", "reserve");
        assertThat(secrets.account(AuthSubstrate.ENVIRONMENT, first))
                .containsEntry(ExchangeAccountSecretFields.CONTOUR, "DEMO");
        assertThat(secrets.account(AuthSubstrate.ENVIRONMENT, second))
                .containsEntry(ExchangeAccountSecretFields.CONTOUR, "DEMO");
    }

    /**
     * Форма отрицания здесь АБСОЛЮТНАЯ, и это не исключение из разностной
     * формы: предмет клетки — не состояние субстрата, а то, что узнаваемые
     * значения не встречаются ни в одном из четырёх каналов выхода.
     */
    @Test
    @DisplayName("B2.11 — ключи не выходят ни одним каналом")
    void b2_11_theKeysLeaveByNoChannel() {
        String tenant = provisionTenant("user-b2-11");
        String token = identity.browserToken("user-b2-11");

        Answer registered = post(EXCHANGE_ACCOUNTS, token, Bodies.registration(tenant).body());
        Answer registry = get(EXCHANGE_ACCOUNTS, token);
        Answer ofTenant = get(EXCHANGE_ACCOUNTS + "/tenant/" + tenant, token);

        List<String> markers = List.of(Bodies.API_KEY_MARKER, Bodies.SECRET_MARKER,
                Bodies.PASSPHRASE_MARKER);
        markers.forEach(marker -> {
            assertThat(registered.body()).doesNotContain(marker);
            assertThat(registry.body()).doesNotContain(marker);
            assertThat(ofTenant.body()).doesNotContain(marker);
            assertThat(rows.all("exchange_accounts").toString()).doesNotContain(marker);
            assertThat(AppLog.text()).doesNotContain(marker);
        });
    }

    @Test
    @DisplayName("B2.12 — реестр не принимает и не отдаёт ступеней лестницы")
    void b2_12_theRegistryNeitherTakesNorGivesSafetyRungs() {
        String tenant = provisionTenant("user-b2-12");
        String token = identity.browserToken("user-b2-12");

        Answer registered = post(EXCHANGE_ACCOUNTS, token, Bodies.registration(tenant).body());
        String internalId = String.valueOf(registered.asObject().get("internalId"));
        Answer read = get(EXCHANGE_ACCOUNTS + "/tenant/" + tenant, token);

        assertThat(registered.asObject().get("status")).isEqualTo("ACTIVE");
        assertThat(read.body()).doesNotContain("HOLD", "TRADE_BLOCKED", "riskBase", "safetyRung");
        assertThat(registered.body()).doesNotContain("HOLD", "TRADE_BLOCKED", "riskBase", "safetyRung");
        assertThat(rows.row("exchange_accounts", "internal_id", internalId).get("status"))
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("B2.13 — колонки аудита заполняются по правилу состава")
    void b2_13_theAuditColumnsFollowTheCompositionRule() {
        String tenant = provisionTenant("user-b2-13");

        Answer answer = post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b2-13"),
                Bodies.registration(tenant).body());

        Map<String, Object> row = rows.row("exchange_accounts", "internal_id",
                String.valueOf(answer.asObject().get("internalId")));
        assertThat(row.get("created_at")).isNotNull();
        assertThat(((OffsetDateTime) row.get("created_at")).toInstant())
                .isCloseTo(Instant.now(), within(2L, ChronoUnit.MINUTES));
        assertThat(row.get("created_by")).isEqualTo("user-b2-13");
        assertThat(row.get("external_created_at")).isNull();
        assertThat(row.get("external_modified_at")).isNull();
    }

    @Test
    @DisplayName("B2.14 — регистрация без предъявленного принципала отвечает 401 единым error-DTO")
    void b2_14_aRegistrationWithoutAPrincipalIsRefused() {
        String tenant = provisionTenant("user-b2-14");
        Long accountsBefore = rows.count("exchange_accounts");
        Integer secretsBefore = secrets.accountNames(AuthSubstrate.ENVIRONMENT).size();

        Answer answer = post(EXCHANGE_ACCOUNTS, Bodies.registration(tenant).body());

        assertThat(answer.status()).isEqualTo(401);
        assertThat(rows.count("exchange_accounts")).isEqualTo(accountsBefore);
        assertThat(secrets.accountNames(AuthSubstrate.ENVIRONMENT)).hasSize(secretsBefore);
        assertThat(answer.carriesErrorDto()).isTrue();
    }

    @Test
    @DisplayName("B2.16 — непредъявленная метка счёта отвечает 400 единым error-DTO")
    void b2_16_anAbsentLabelIsRefused() {
        refusedByValidation("label", "user-b2-16");
    }

    @Test
    @DisplayName("B2.18 — непредъявленный код площадки отвечает 400 единым error-DTO")
    void b2_18_anAbsentExchangeCodeIsRefused() {
        refusedByValidation("exchangeCode", "user-b2-18");
    }

    @Test
    @DisplayName("B2.19 — непредъявленный секрет ключа отвечает 400 единым error-DTO")
    void b2_19_anAbsentSecretIsRefused() {
        refusedByValidation("secret", "user-b2-19");
    }

    @Test
    @DisplayName("B2.20 — непредъявленный passphrase ключа отвечает 400 единым error-DTO")
    void b2_20_anAbsentPassphraseIsRefused() {
        refusedByValidation("passphrase", "user-b2-20");
    }

    @Test
    @DisplayName("B2.21 — непредъявленная идентичность тенанта отвечает 400 единым error-DTO")
    void b2_21_anAbsentTenantIdentityIsRefused() {
        refusedByValidation("tenantInternalId", "user-b2-21");
    }

    @Test
    @DisplayName("B2.22 — непредъявленный контур площадки отвечает 400 единым error-DTO")
    void b2_22_anAbsentContourIsRefused() {
        refusedByValidation("contour", "user-b2-22");
    }

    /**
     * Общая тропа шести клеток негатива по единице: одно обязательное поле
     * пусто, прочие корректны.
     *
     * <p><b>Своих предусловий у этих клеток нет:</b> {@code @Valid}
     * отвергает тело ДО метода контроллера, поэтому ни заведённый тенант,
     * ни перечень допущенных контуров на исход не влияют — тенант
     * заводится лишь затем, чтобы вход отличался от входа `B2.4` ровно
     * одной осью.
     *
     * @param field   обязательное поле, которое кейс не предъявляет
     * @param subject субъект кейса — свой, чтобы тенант не делился
     */
    private void refusedByValidation(String field, String subject) {
        String tenant = provisionTenant(subject);
        Long accountsBefore = rows.count("exchange_accounts");
        Integer secretsBefore = secrets.accountNames(AuthSubstrate.ENVIRONMENT).size();

        Answer answer = post(EXCHANGE_ACCOUNTS, identity.browserToken(subject),
                Bodies.registration(tenant).with(field, "").body());

        assertThat(answer.status()).isEqualTo(400);
        assertThat(rows.count("exchange_accounts")).isEqualTo(accountsBefore);
        assertThat(secrets.accountNames(AuthSubstrate.ENVIRONMENT)).hasSize(secretsBefore);
        assertThat(answer.carriesErrorDto()).isTrue();
    }
}

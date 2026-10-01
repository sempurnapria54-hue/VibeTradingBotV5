package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.util.ExchangeAccountSecretFields;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Смена ключей биржевого счёта — группа {@code B8} документа
 * `.claude/tests/cases/auth.md`.
 *
 * <p><b>Ключи — собственный выход предмета</b>, и смена наблюдается там же,
 * где регистрация: запросом к контейнеру хранилища снаружи. Новые ключи у
 * кейсов узнаваемо отличны от ключей регистрации
 * ({@link Bodies#ROTATED_API_KEY_MARKER}), иначе «новые легли» было бы
 * неотличимо от «старые остались».
 *
 * <p><b>Счёт у каждой клетки свой</b>, и тенант тоже: секрет лежит по пути
 * счёта, а метка счёта уникальна на тройке «тенант × площадка × метка».
 *
 * <p><b>Отказ, при котором ключи НЕ должны смениться, мерится по секрету
 * счёта кейса, а не по числу секретов:</b> смена пишет по уже существующему
 * пути, и разностная форма «новых записей не появилось» отказа от успеха не
 * отличает — отличает содержимое секрета.
 */
class ExchangeAccountKeyRotationBoxTest extends SharedAuthBox {

    private static final String ACCOUNTS = "exchange_accounts";

    @Test
    @DisplayName("B8.1 — ключи ложатся по пути счёта с прежним контуром")
    void b8_1_theKeysLandOnTheAccountPathWithTheSameContour() {
        String account = registeredAccount("user-b8-1");
        Map<String, Object> before = rows.row(ACCOUNTS, "internal_id", account);
        Long accountsBefore = rows.count(ACCOUNTS);
        Integer secretsBefore = secrets.accountNames(AuthSubstrate.ENVIRONMENT).size();

        Answer answer = put(keysOf(account), identity.browserToken("user-b8-1-rotator"),
                Bodies.keyRotation().body());

        assertThat(answer.status()).isEqualTo(200);
        Map<String, Object> body = answer.asObject();
        assertThat(body).containsOnlyKeys("internalId", "tenantInternalId", "exchangeCode",
                "label", "contour", "status");
        assertThat(body.get("internalId")).isEqualTo(account);
        assertThat(body.get("contour")).isEqualTo("DEMO");
        assertThat(body.get("status")).isEqualTo("ACTIVE");
        assertThat(secrets.account(AuthSubstrate.ENVIRONMENT, account))
                .containsOnlyKeys(ExchangeAccountSecretFields.API_KEY,
                        ExchangeAccountSecretFields.SECRET,
                        ExchangeAccountSecretFields.PASSPHRASE,
                        ExchangeAccountSecretFields.CONTOUR)
                .containsEntry(ExchangeAccountSecretFields.API_KEY, Bodies.ROTATED_API_KEY_MARKER)
                .containsEntry(ExchangeAccountSecretFields.SECRET, Bodies.ROTATED_SECRET_MARKER)
                .containsEntry(ExchangeAccountSecretFields.PASSPHRASE, Bodies.ROTATED_PASSPHRASE_MARKER)
                .containsEntry(ExchangeAccountSecretFields.CONTOUR, "DEMO");
        assertThat(secrets.accountNames(AuthSubstrate.ENVIRONMENT)).hasSize(secretsBefore);
        assertThat(rows.count(ACCOUNTS)).isEqualTo(accountsBefore);
        Map<String, Object> after = rows.row(ACCOUNTS, "internal_id", account);
        assertThat(after.get("tenant_id")).isEqualTo(before.get("tenant_id"));
        assertThat(after.get("exchange_code")).isEqualTo(before.get("exchange_code"));
        assertThat(after.get("label")).isEqualTo(before.get("label"));
        assertThat(after.get("contour")).isEqualTo("DEMO");
        assertThat(after.get("status")).isEqualTo("ACTIVE");
        assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
        assertThat(after.get("created_by")).isEqualTo("user-b8-1");
        assertThat(after.get("modified_by")).isEqualTo("user-b8-1-rotator");
        assertThat((OffsetDateTime) after.get("modified_at"))
                .isAfter((OffsetDateTime) before.get("modified_at"));
        assertThat(answer.body()).doesNotContain(Bodies.ROTATED_API_KEY_MARKER,
                Bodies.ROTATED_SECRET_MARKER, Bodies.ROTATED_PASSPHRASE_MARKER);
        assertThat(after.toString()).doesNotContain(Bodies.ROTATED_API_KEY_MARKER,
                Bodies.ROTATED_SECRET_MARKER, Bodies.ROTATED_PASSPHRASE_MARKER);
        assertThat(AppLog.text()).doesNotContain(Bodies.ROTATED_API_KEY_MARKER,
                Bodies.ROTATED_SECRET_MARKER, Bodies.ROTATED_PASSPHRASE_MARKER);
    }

    @Test
    @DisplayName("B8.2 — контур из тела не принят")
    void b8_2_aContourInTheBodyIsNotAccepted() {
        String account = registeredAccount("user-b8-2");

        Answer answer = put(keysOf(account), identity.browserToken("user-b8-2"),
                Bodies.keyRotation().with("contour", "LIVE").body());

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject().get("contour")).isEqualTo("DEMO");
        assertThat(rows.row(ACCOUNTS, "internal_id", account).get("contour")).isEqualTo("DEMO");
        assertThat(secrets.account(AuthSubstrate.ENVIRONMENT, account))
                .containsEntry(ExchangeAccountSecretFields.CONTOUR, "DEMO")
                .containsEntry(ExchangeAccountSecretFields.API_KEY, Bodies.ROTATED_API_KEY_MARKER);
    }

    @Test
    @DisplayName("B8.3 — несуществующий счёт")
    void b8_3_aNonExistentAccountIsRefused() {
        provisionTenant("user-b8-3");
        Integer secretsBefore = secrets.accountNames(AuthSubstrate.ENVIRONMENT).size();

        Answer answer = put(keysOf("no-such-account"), identity.browserToken("user-b8-3"),
                Bodies.keyRotation().body());

        assertThat(answer.status()).isEqualTo(404);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo("INVALID_REQUEST");
        assertThat(answer.body()).doesNotContain(Bodies.ROTATED_API_KEY_MARKER,
                Bodies.ROTATED_SECRET_MARKER, Bodies.ROTATED_PASSPHRASE_MARKER);
        assertThat(secrets.accountNames(AuthSubstrate.ENVIRONMENT)).hasSize(secretsBefore);
        assertThat(secrets.account(AuthSubstrate.ENVIRONMENT, "no-such-account")).isEmpty();
    }

    /**
     * Отключённый счёт: предусловие ставится ПРЯМОЙ записью статуса в базу —
     * операции, производящей {@code CLOSED}, у поверхности сегодня нет
     * ({@link Rows#forceAccountStatus}).
     */
    @Test
    @DisplayName("B8.4 — отключённый счёт ключей не принимает")
    void b8_4_aClosedAccountRefusesKeys() {
        String account = registeredAccount("user-b8-4");
        rows.forceAccountStatus(account, "CLOSED");
        Map<String, Object> before = rows.row(ACCOUNTS, "internal_id", account);

        Answer answer = put(keysOf(account), identity.browserToken("user-b8-4-rotator"),
                Bodies.keyRotation().body());

        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo("INVALID_REQUEST");
        assertKeysUntouched(account, before);
        assertThat(rows.row(ACCOUNTS, "internal_id", account).get("status")).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("B8.5 — смена ключей без предъявленного принципала")
    void b8_5_aRotationWithoutAPrincipalIsRefused() {
        String account = registeredAccount("user-b8-5");
        Map<String, Object> before = rows.row(ACCOUNTS, "internal_id", account);

        Answer answer = put(keysOf(account), Bodies.keyRotation().body());

        assertThat(answer.status()).isEqualTo(401);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertKeysUntouched(account, before);
    }

    @Test
    @DisplayName("B8.6 — непредъявленный API-ключ смены")
    void b8_6_anAbsentApiKeyIsRefused() {
        refusedByValidation("apiKey", "user-b8-6");
    }

    @Test
    @DisplayName("B8.7 — непредъявленный секрет ключа смены")
    void b8_7_anAbsentSecretIsRefused() {
        refusedByValidation("secret", "user-b8-7");
    }

    @Test
    @DisplayName("B8.8 — непредъявленный passphrase ключа смены")
    void b8_8_anAbsentPassphraseIsRefused() {
        refusedByValidation("passphrase", "user-b8-8");
    }

    /**
     * Обязательное поле смены не предъявлено: {@code @Valid} отвергает тело
     * ДО метода контроллера, и ни счёт, ни хранилище ход не трогает.
     *
     * @param field   обязательное поле, которое кейс не предъявляет
     * @param subject субъект кейса — свой, чтобы счёт не делился
     */
    private void refusedByValidation(String field, String subject) {
        String account = registeredAccount(subject);
        Map<String, Object> before = rows.row(ACCOUNTS, "internal_id", account);

        Answer answer = put(keysOf(account), identity.browserToken(subject),
                Bodies.keyRotation().with(field, "").body());

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertKeysUntouched(account, before);
    }

    /**
     * Ключи счёта — прежние, ключей регистрации; момент и автор изменения
     * строки не сдвинулись.
     */
    private void assertKeysUntouched(String account, Map<String, Object> before) {
        assertThat(secrets.account(AuthSubstrate.ENVIRONMENT, account))
                .containsEntry(ExchangeAccountSecretFields.API_KEY, Bodies.API_KEY_MARKER)
                .containsEntry(ExchangeAccountSecretFields.SECRET, Bodies.SECRET_MARKER)
                .containsEntry(ExchangeAccountSecretFields.PASSPHRASE, Bodies.PASSPHRASE_MARKER);
        Map<String, Object> after = rows.row(ACCOUNTS, "internal_id", account);
        assertThat(after.get("modified_at")).isEqualTo(before.get("modified_at"));
        assertThat(after.get("modified_by")).isEqualTo(before.get("modified_by"));
    }

    /**
     * Заводит тенанта и счёт контура {@code DEMO} тропой поверхности.
     *
     * @param subject субъект кейса
     * @return {@code internalId} счёта
     */
    private String registeredAccount(String subject) {
        String tenant = provisionTenant(subject);
        Answer registered = post(EXCHANGE_ACCOUNTS, identity.browserToken(subject),
                Bodies.registration(tenant).body());
        if (registered.status() != 201) {
            throw new IllegalStateException("Предусловие не поставлено: регистрация ответила "
                    + registered.status() + " " + registered.body());
        }
        return String.valueOf(registered.asObject().get("internalId"));
    }
}

package com.example.auth.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос регистрации биржевого счёта тенанта.
 *
 * <p><b>Ключи приходят вместе со счётом и дальше в базу не идут:</b> они
 * уезжают в хранилище секретов и живут только там
 * (docs/architecture/tenant-and-exchange.md §Ключи). Ответ их не
 * возвращает, лог их не пишет.
 */
@Getter
@Setter
public class RegisterExchangeAccountApiRequest {

    @NotBlank
    @Schema(description = "Идентичность тенанта-владельца счёта")
    private String tenantInternalId;

    @NotBlank
    @Schema(description = "Код площадки: OKX, BYBIT")
    private String exchangeCode;

    @NotBlank
    @Schema(description = "Метка счёта, видимая человеку: у тенанта на одной площадке счетов может быть несколько")
    private String label;

    @NotBlank
    @Schema(description = "Контур площадки: LIVE — боевая, DEMO — демо-контур. Допустимость проверяет окружение")
    private String contour;

    @NotBlank
    @Schema(description = "API-ключ счёта на площадке. Уезжает в хранилище секретов, в базу не пишется")
    private String apiKey;

    @NotBlank
    @Schema(description = "Секрет API-ключа: им подписывается запрос площадке. В хранилище секретов, не в базу")
    private String secret;

    @NotBlank
    @Schema(description = "Passphrase API-ключа. В хранилище секретов, не в базу")
    private String passphrase;
}

package com.example.auth.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос смены ключей биржевого счёта.
 *
 * <p><b>Контура здесь нет, и это несущее:</b> контур берётся со строки
 * счёта, иначе смена ключей стала бы тропой смены контура в обход проверки
 * допуска (docs/architecture/tenant-and-exchange.md §Ключи). Лишнее поле
 * тела контура не меняет — его просто некому принять.
 *
 * <p>Ключи уезжают в хранилище секретов и живут только там: ответ их не
 * возвращает, лог их не пишет.
 */
@Getter
@Setter
public class RotateExchangeAccountKeysApiRequest {

    @NotBlank
    @Schema(description = "Новый API-ключ счёта на площадке. Уезжает в хранилище секретов, в базу не пишется")
    private String apiKey;

    @NotBlank
    @Schema(description = "Секрет нового API-ключа: им подписывается запрос площадке. В хранилище секретов, не в базу")
    private String secret;

    @NotBlank
    @Schema(description = "Passphrase нового API-ключа. В хранилище секретов, не в базу")
    private String passphrase;
}

package com.example.tradingcore.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Разрешаются ли ссылки определения стратегии — счёт и инструмент — в
 * контексте названного тенанта.
 *
 * <p><b>Единственный вызывающий — владелец определений</b> при валидации
 * создания и при активации (docs/architecture/contracts.md §«Синхронные
 * вызовы»). Ядро отвечает по СВОИМ проекциям чужих реестров, и ошибиться
 * они могут только в одну сторону: членство в обоих реестрах монотонно,
 * поэтому устаревшая проекция способна лишь не знать о новом — то есть
 * отвергнуть создание, которое прошло бы на свежей
 * (docs/rules/strategy-validation.md §«Что проверяется на активации»).
 *
 * <p><b>Три признака, а не один булев ответ:</b> отказ адресует тот
 * конъюнкт, который ложен (docs/concept.md П3) — иначе автор получает
 * «не годится» без указания, что именно.
 */
@Getter
@Setter
public class PairCheckApiResponse {

    @Schema(description = "Счёт с такой идентичностью существует в реестре")
    private Boolean accountFound;

    @Schema(description = "Счёт принадлежит названному тенанту")
    private Boolean accountBelongsToTenant;

    @Schema(description = "Инструмент с такой идентичностью существует в каталоге")
    private Boolean instrumentFound;
}

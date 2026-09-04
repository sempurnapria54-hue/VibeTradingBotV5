package com.example.tradingbot.api.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

/**
 * Конверт generic raw-passthrough контура тестов API источника
 * (`.claude/decisions/source-api-target-rebase.md`, раздел D): описывает
 * произвольный in-perimeter вызов OKX, который app отправляет через
 * {@code OkxRestClient#dispatch} (подпись и креды — на стороне app). Тело
 * и query строятся руками по контракту OKX; ответ возвращается сырым.
 */
@Getter
@Setter
public class OkxRawApiRequest {

    @NotBlank
    @Schema(description = "HTTP-метод вызова OKX: GET / POST", requiredMode = Schema.RequiredMode.REQUIRED)
    private String method;

    @NotBlank
    @Schema(description = "Путь эндпоинта OKX (например /api/v5/account/config)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String path;

    @Schema(description = "Query-параметры запроса (имя → значение); опускаются, если пусто")
    private Map<String, Object> query;

    // Jackson 3 (tools.jackson) JsonNode, не Jackson 2: входящее тело /raw
    // десериализует Jackson-3 веб-конвертер SB4 (JacksonJsonHttpMessageConverter),
    // а из dispatch это же тело уходит в OKX тем же Jackson-3 RestClient-конвертером.
    // Jackson-2 com.fasterxml JsonNode здесь даёт InvalidDefinitionException
    // (абстрактный тип без Jackson-3 creators). См. tech-radar (Jackson 2/3).
    @Schema(description = "Сырое тело запроса для write-вызовов (JSON по контракту OKX)")
    private JsonNode body;

    @NotNull
    @Schema(description = "true → подписанный приватный вызов, false → публичный",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean signed;
}

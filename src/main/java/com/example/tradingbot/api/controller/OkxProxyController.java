package com.example.tradingbot.api.controller;

import com.example.tradingbot.api.model.request.OkxRawApiRequest;
import com.example.tradingbot.integration.model.okx.response.OkxApiResponse;
import com.example.tradingbot.integration.service.okx.OkxRestClient;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generic raw-passthrough — единственная поверхность контура тестов API
 * источника (`.claude/decisions/source-api-target-rebase.md`, раздел D).
 * Принимает конверт {@code {method, path, query, body, signed}} и достаёт
 * любой in-perimeter эндпоинт OKX через универсальный
 * {@link OkxRestClient#dispatch}: подпись и креды — на стороне app (signing
 * interceptor), demo/prod — профилем OKX; ответ — **сырой**
 * {@link OkxApiResponse} с элементами data как {@link JsonNode} (контракт
 * биржи, без нашей типизации). Тест-обращённый инструмент: шлёт write'ы в
 * произвольный path → закрыт {@code @Profile("!prod")}, в prod недоступен.
 * Не часть продуктового API.
 */
@RestController
@RequestMapping("/api/proxy/okx")
@Profile("!prod")
@RequiredArgsConstructor
@Tag(name = "OKX raw passthrough", description = "Сырой generic-прогон OkxRestClient (тестирование интеграции)")
public class OkxProxyController {

    private static final ParameterizedTypeReference<OkxApiResponse<JsonNode>> RAW_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final OkxRestClient okxRestClient;

    @PostMapping("/raw")
    @Operation(summary = "Сырой generic-вызов любого in-perimeter эндпоинта OKX по конверту запроса")
    public OkxApiResponse<JsonNode> raw(@Valid @RequestBody OkxRawApiRequest request) {
        return okxRestClient.dispatch(
                HttpMethod.valueOf(request.getMethod()),
                request.getPath(),
                request.getQuery(),
                request.getBody(),
                request.getSigned(),
                RAW_RESPONSE_TYPE);
    }
}

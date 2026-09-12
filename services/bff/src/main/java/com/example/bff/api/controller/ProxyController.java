package com.example.bff.api.controller;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.bff.domain.TenantContext;
import com.example.bff.domain.TenantContextResolver;
import com.example.bff.integration.internal.api.OwnerProxyClient;
import com.example.bff.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Проксирование поверхностей владельцев
 * (docs/architecture/contracts.md §«Периметр: что {@code bff} отдаёт и
 * чего не делает»).
 *
 * <p><b>Владельца называет ПУТЬ, а не таблица маршрутов.</b> Адресат —
 * первый сегмент после версии; перечня владельцев периметр не держит,
 * иначе таблица стала бы вторым носителем состава поверхности и старела
 * бы при каждом новом пути. Допустимый набор охраняет не этот класс, а
 * сетевая политика кластера: пара, которой нет в §«Синхронные вызовы»,
 * запрещена.
 *
 * <p><b>Экранных контрактов периметр не заводит</b> и формы владельца не
 * переписывает: пересылаемое идёт формой владельца, версионируемой
 * вместе с ним. Агрегации здесь нет — она обусловлена экраном, а экран
 * приезжает своим шагом.
 *
 * <p><b>Собственные точки периметра сюда не попадают.</b> Их отличает
 * тот же первый сегмент, и второго признака различения не заводится.
 */
@RestController
@RequiredArgsConstructor
public class ProxyController {

    private final TenantContextResolver tenantContextResolver;
    private final OwnerProxyClient proxyClient;

    /**
     * Переслать запрос владельцу в контексте тенанта предъявителя.
     *
     * @param owner   имя единицы-владельца — первый сегмент после версии
     * @param body    тело запроса, как его прислал браузер
     * @param request исходный запрос: из него берутся путь, глагол и
     *                строка запроса
     * @param token   принятый токен предъявителя
     * @return ответ владельца как есть
     */
    @Operation(summary = "Переслать запрос владельцу поверхности")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ответ владельца"),
            @ApiResponse(responseCode = "404", description = "Собственная точка периметра по этому адресу не заведена"),
            @ApiResponse(responseCode = "503", description = "Владелец недоступен")
    })
    @RequestMapping(Constants.Paths.API_V1 + "/{owner}/**")
    public ResponseEntity<byte[]> forward(@PathVariable String owner,
                                          @RequestBody(required = false) byte[] body,
                                          HttpServletRequest request,
                                          @AuthenticationPrincipal Jwt token) {
        requireOwnerBehindPerimeter(owner);
        String bearer = bearerOf(token);
        TenantContext context = tenantContextResolver.resolve(token.getSubject(), bearer);
        return proxyClient.forward(owner, HttpMethod.valueOf(request.getMethod()), pathOf(request),
                forwardedHeaders(request, context, bearer), body);
    }

    /**
     * Периметр себя не проксирует: собственная точка по этому адресу
     * либо заведена контроллером, либо не существует вовсе.
     *
     * <p><b>Форма имени проверяется здесь же, а не только сетевой
     * политикой.</b> Имя владельца становится адресом исходящего
     * вызова, и клейм «периметр ходит только к единицам инвентаря»
     * держался бы иначе конфигурацией среды, в которой он запущен.
     * Политика кластера остаётся вторым рубежом, а не единственным.
     */
    private void requireOwnerBehindPerimeter(String owner) {
        if (Objects.equals(Constants.Paths.PERIMETER_OWNER, owner)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (isFalse(owner.matches(Constants.Paths.OWNER_NAME_FORM))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /** Путь и строка запроса, как их прислал браузер. */
    private String pathOf(HttpServletRequest request) {
        String query = request.getQueryString();
        return isNull(query) ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    /**
     * Заголовки, уходящие владельцу: токен предъявителя, контекст
     * тенанта и переговорные заголовки содержимого.
     *
     * <p><b>Перечень закрытый, а не «всё кроме».</b> Транспортные
     * заголовки исходного соединения (длина, кодировка, хост)
     * относятся к нему, а не к пересылаемому запросу, и пересланные
     * ломали бы второе соединение.
     */
    private HttpHeaders forwardedHeaders(HttpServletRequest request, TenantContext context, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearer);
        headers.set(Constants.ContextHeaders.TENANT_ID, context.tenantId());
        headers.set(Constants.ContextHeaders.TENANT_ROLE, context.role());
        String contentType = request.getContentType();
        if (isBlank(contentType)) {
            return headers;
        }
        headers.setContentType(MediaType.parseMediaType(contentType));
        return headers;
    }

    /**
     * Заголовок предъявления, пересобранный из ПРИНЯТОГО токена: дальше
     * уезжает ровно то, что контур принял, и второго источника истины о
     * предъявителе не появляется.
     */
    private String bearerOf(Jwt token) {
        return Constants.Authorization.BEARER_PREFIX + token.getTokenValue();
    }
}

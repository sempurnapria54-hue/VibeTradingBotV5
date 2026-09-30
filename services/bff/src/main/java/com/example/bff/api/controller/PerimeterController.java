package com.example.bff.api.controller;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.bff.api.model.SubscriptionTicketApiResponse;
import com.example.bff.api.model.TenantContextApiResponse;
import com.example.bff.config.PerimeterProperties;
import com.example.bff.domain.SubscriptionTicket;
import com.example.bff.domain.SubscriptionTicketService;
import com.example.bff.domain.TenantContext;
import com.example.bff.domain.TenantContextResolver;
import com.example.bff.domain.stream.StreamRegistry;
import com.example.bff.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Собственная поверхность периметра: контекст, билет подписки и сам
 * поток.
 *
 * <p><b>Адрес — та же конвенция, что у владельцев</b>
 * (docs/architecture/contracts.md §«Владельца называет путь…»):
 * {@code bff} есть единица инвентаря, и первым сегментом после версии
 * маршрутизатор отличает своё от проксируемого.
 *
 * <p><b>Форма — своя, потому что порождена периметром</b> (§«Форму
 * задаёт происхождение: порождённое — своё, пересланное — чужое»).
 *
 * <p><b>Две формы предъявления, и вторая — не исключение.</b> Контекст и
 * билет требуют bearer-токена; подписку открывает БИЛЕТ, потому что
 * браузерный {@code EventSource} заголовка {@code Authorization} не
 * ставит, а токен в query-параметре уехал бы в логи ингресса, историю
 * браузера и {@code Referer}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(Constants.Paths.PERIMETER_ROOT)
public class PerimeterController {

    private final TenantContextResolver tenantContextResolver;
    private final SubscriptionTicketService ticketService;
    private final StreamRegistry streamRegistry;
    private final PerimeterProperties properties;

    /**
     * Контекст предъявителя: тенант и роль.
     *
     * @param token принятый токен предъявителя
     * @return тенант и роль предъявителя
     */
    @Operation(summary = "Контекст предъявителя: тенант и роль")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Контекст предъявителя"),
            @ApiResponse(responseCode = "409", description = "Членств больше одного, а выбора ещё нет"),
            @ApiResponse(responseCode = "503", description = "Владелец членств недоступен")
    })
    @GetMapping("/context")
    public TenantContextApiResponse context(@AuthenticationPrincipal Jwt token) {
        TenantContext context = tenantContextResolver.resolve(token.getSubject(), bearerOf(token));
        return new TenantContextApiResponse(context.tenantId(), context.role());
    }

    /**
     * Выдать билет подписки.
     *
     * @param token принятый токен предъявителя
     * @return билет и момент, после которого он негоден для открытия
     */
    @Operation(summary = "Выдать билет подписки на поток живых данных")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Билет подписки"),
            @ApiResponse(responseCode = "401", description = "Выдача билетов не настроена")
    })
    @PostMapping("/stream-tickets")
    public SubscriptionTicketApiResponse issueTicket(@AuthenticationPrincipal Jwt token) {
        TenantContext context = tenantContextResolver.resolve(token.getSubject(), bearerOf(token));
        return new SubscriptionTicketApiResponse(ticketService.issue(token.getSubject(), context.tenantId()),
                OffsetDateTime.ofInstant(Instant.now().plus(properties.getTicket().getTtl()), ZoneOffset.UTC));
    }

    /**
     * Открыть подписку на поток живых данных тенанта.
     *
     * <p><b>Билет проверяется при ОТКРЫТИИ</b>; установленный поток по
     * истечении срока не рвётся — иначе живой поток обрывался бы по
     * таймеру без всякой причины.
     *
     * <p><b>Идентичность последнего события принимается в двух формах, и
     * старшинство у них названо</b> (docs/architecture/contracts.md
     * §«Живые данные в браузер», клауза «Обязанность клиента, которую это
     * заводит, названа»): заголовок ставит браузер на своих
     * переподключениях, параметр адреса передаёт клиент при пересоздании
     * подписки — браузерный {@code EventSource} заголовков не принимает.
     *
     * @param ticket          билет, выданный точкой выше
     * @param addressPosition идентичность последнего полученного события,
     *                        переданная клиентом при пересоздании подписки
     * @param headerPosition  идентичность последнего полученного события,
     *                        которую браузер шлёт сам на переподключении
     * @return поток записей тенанта
     */
    @Operation(summary = "Открыть подписку на поток живых данных")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Поток открыт"),
            @ApiResponse(responseCode = "401", description = "Билет не предъявлен, испорчен либо просрочен")
    })
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam(required = false)
            @Parameter(description = "Билет подписки, выданный точкой выдачи билетов под bearer-токеном")
            String ticket,
            @RequestParam(value = Constants.StreamParameters.LAST_EVENT_ID, required = false)
            @Parameter(description = "Идентичность последнего полученного события при ПЕРЕСОЗДАНИИ подписки "
                    + "(новым билетом): браузерный EventSource заголовков не ставит, и клиент передаёт её "
                    + "адресом. Непустой заголовок Last-Event-ID старше этого параметра")
            String addressPosition,
            @RequestHeader(value = Constants.StreamHeaders.LAST_EVENT_ID, required = false)
            @Parameter(description = "Идентичность последнего полученного события на штатном "
                    + "переподключении: ставит браузер сам. Непустой — старше параметра lastEventId")
            String headerPosition) {
        SubscriptionTicket verified = ticketService.verify(ticket);
        return streamRegistry.open(verified.tenantId(), positionOf(headerPosition, addressPosition));
    }

    /**
     * Позиция чтения из двух форм приёма: непустой заголовок старше
     * параметра адреса.
     *
     * <p><b>Старше то, что свежее, и свежее по построению заголовок.</b>
     * Параметр заморожен в адресе с момента пересоздания, а
     * автоматическое переподключение {@code EventSource} идёт ТЕМ ЖЕ
     * адресом, прикладывая заголовок с последним, что получила уже эта
     * подписка. Старший параметр переигрывал бы каждое переподключение с
     * момента пересоздания — повтором, а как только позиция пересоздания
     * покинет окно, ложным разрывом. Заголовка нет, пока пересозданная
     * подписка не получила ни одного события, — и тогда параметр верен.
     */
    private String positionOf(String headerPosition, String addressPosition) {
        return isNotBlank(headerPosition) ? headerPosition : addressPosition;
    }

    /**
     * Заголовок предъявления для вызова к владельцу членств —
     * пересобранный из ПРИНЯТОГО токена.
     */
    private String bearerOf(Jwt token) {
        return Constants.Authorization.BEARER_PREFIX + token.getTokenValue();
    }
}

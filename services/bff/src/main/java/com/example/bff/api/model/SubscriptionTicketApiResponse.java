package com.example.bff.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Билет подписки, выданный предъявителю токена.
 *
 * <p>Предъявляется при ОТКРЫТИИ подписки; установленный поток по
 * истечении срока не рвётся (docs/architecture/contracts.md §«Подписку
 * открывает билет, а не сам токен»).
 *
 * @param ticket    значение билета
 * @param expiresAt момент, после которого билет негоден для открытия
 */
public record SubscriptionTicketApiResponse(
        @Schema(description = "Значение билета, предъявляемое при открытии подписки") String ticket,
        @Schema(description = "Момент, после которого билет негоден для открытия, UTC") OffsetDateTime expiresAt) {
}

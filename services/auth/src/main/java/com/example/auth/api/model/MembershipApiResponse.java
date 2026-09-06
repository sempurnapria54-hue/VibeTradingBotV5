package com.example.auth.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Членство предъявителя, отданное наружу.
 *
 * <p><b>Запись, а не {@code @Value}:</b> у формы есть ЧИТАТЕЛЬ — периметр
 * разбирает её из JSON, — и значение, пересекающее сериализацию, обязано
 * собираться без скрытых механизмов
 * (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»). Аксессоров этой формы существующий код не зовёт, и
 * потому переименования она не стоит.
 *
 * @param internalId идентичность членства
 * @param tenantId   {@code internalId} тенанта
 * @param role       роль предъявителя в этом тенанте
 */
public record MembershipApiResponse(
        @Schema(description = "Идентичность членства") String internalId,
        @Schema(description = "Идентичность тенанта, к которому относится членство") String tenantId,
        @Schema(description = "Роль предъявителя в тенанте: OWNER, TRADER либо VIEWER") String role) {
}

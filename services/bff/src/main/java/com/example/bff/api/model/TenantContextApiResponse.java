package com.example.bff.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Контекст предъявителя, отданный браузеру.
 *
 * <p>Форма порождена периметром, значит и модель его. Тенант браузер не
 * называет — он его УЗНАЁТ: вывод идёт из членств предъявителя
 * (docs/architecture/contracts.md §«Контекст тенанта в вызове»).
 *
 * @param tenantId идентичность тенанта предъявителя
 * @param role     роль предъявителя в этом тенанте
 */
public record TenantContextApiResponse(
        @Schema(description = "Идентичность тенанта предъявителя") String tenantId,
        @Schema(description = "Роль предъявителя в тенанте") String role) {
}

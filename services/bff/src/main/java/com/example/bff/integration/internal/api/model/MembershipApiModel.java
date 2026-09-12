package com.example.bff.integration.internal.api.model;

/**
 * Членство, прочитанное у владельца «кто есть кто».
 *
 * <p><b>Запись, а не {@code @Value}:</b> у формы есть читатель — этот
 * сервис разбирает её из JSON, — и значение, пересекающее сериализацию,
 * обязано собираться без скрытых механизмов
 * (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 *
 * @param internalId идентичность членства
 * @param tenantId   идентичность тенанта
 * @param role       роль предъявителя в этом тенанте
 */
public record MembershipApiModel(String internalId, String tenantId, String role) {
}

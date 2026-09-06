package com.example.bff.domain;

/**
 * Разобранный билет подписки.
 *
 * @param subject  идентичность предъявителя у провайдера
 * @param tenantId тенант, чьи события уйдут в этот поток
 */
public record SubscriptionTicket(String subject, String tenantId) {
}

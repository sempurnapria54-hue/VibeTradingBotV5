package com.example.tradingcore.domain.command;

/**
 * Маркер-база параметров команды — без поведения.
 *
 * <p>Подтип выбирается по {@link ServiceCommand#getType()}; отдельного
 * поля-дискриминатора в параметрах нет. Подтипы документируются разделом
 * в доке своего исполнителя: без своей команды параметры смысла не имеют
 * (docs/components/models/ServiceCommandPayload.md).
 *
 * <p>База даёт единый тип поля и границу маршрутизации диспетчера.
 */
public interface ServiceCommandPayload {
}

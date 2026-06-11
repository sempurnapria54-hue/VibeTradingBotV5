package com.example.tradingbot.domain.command;

/**
 * Маркер-базовый тип параметров команды (ServiceCommand.payload) — без
 * поведения. Конкретный подтип выбирается по ServiceCommand.type
 * (дискриминатор — ServiceCommandType; отдельного поля-дискриминатора в
 * payload'е нет). Подтипы (CreateOrderCommandPayload, …) документируются
 * разделом дока своего executor'а; амендных payload'ов нет
 * (docs/decisions/replace-not-amend.md). База даёт единый тип поля и
 * границу generic-диспетча ServiceCommandExecutor. См.
 * docs/components/models/ServiceCommandPayload.md,
 * docs/decisions/service-command-payload-base-type.md.
 */
public interface ServiceCommandPayload {
}

package com.example.strategies.api.controller;

import com.example.strategies.domain.jobs.facade.OutboxRelayJobFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ручной запуск тиков владельца определений вне расписания.
 *
 * <p><b>{@code 202}, а не {@code 200}: фасад отвечает только за
 * ЗАПУСК.</b> Исход самой работы наружу не транслируется и уходит во
 * внутреннюю градацию (docs/rules/error-handling-policy.md,
 * .claude/rules/codestyle.md §«Обработка ошибок»). Перекрывающий запуск
 * гасит защита от конкурентного выполнения — молча, потому что пропуск
 * перекрытия и есть штатное поведение.
 *
 * <p>Точка закрыта тем же умолчанием контура, что и вся поверхность
 * (docs/rules/api-access-policy.md): открытых точек у сервиса нет, кроме
 * пробы живости.
 *
 * <p><b>Префикс тот же, что у прочих сервисов — имя сервиса, — и с
 * перечнем определений он не спорит.</b> Тропа {@code POST} на
 * {@code /jobs/**} с ресурсными путями не пересекается; {@code GET} на
 * {@code /jobs} прочитался бы идентичностью определения и ответил бы
 * ненайденностью, а идентичности — UUID, и такой не бывает.
 */
@RestController
@RequestMapping("/api/v1/strategies/jobs")
@RequiredArgsConstructor
public class JobController {

    private final OutboxRelayJobFacade outboxRelayJobFacade;

    @Operation(summary = "Запустить реле outbox")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "Тик запущен"))
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/outbox-relay")
    public void triggerOutboxRelay() {
        outboxRelayJobFacade.trigger();
    }
}

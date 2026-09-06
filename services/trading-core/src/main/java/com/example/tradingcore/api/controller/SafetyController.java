package com.example.tradingcore.api.controller;

import com.example.tradingcore.api.model.ManualHaltApiRequest;
import com.example.tradingcore.domain.jobs.facade.ManualHaltFacade;
import com.example.tradingcore.domain.safety.ManualHaltClass;
import com.example.tradingcore.domain.safety.ManualHaltService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ручное управление safety-остановкой: держатель управляет теми же
 * ступенями, что и автоматика, — тем же механизмом и через ту же точку
 * входа (docs/rules/manual-halt.md).
 *
 * <p><b>Операций две, форм запуска тоже две, и разница счётная.</b>
 * Полная постановка асинхронна: она гоняет снятие риска с повторами, и
 * отказать при запуске не может — её пара всегда допустима, а по статусу
 * она не отказывает. Мягкая постановка и снятие синхронны: они на биржу
 * не ходят, зато <b>могут отказать</b>, и асинхронная форма спрятала бы
 * отказ — держатель получил бы {@code 202}, ступень не поднята, отчёта
 * нет, а он считает объект остановленным.
 *
 * <p><b>Второго механизма остановки в системе нет:</b> поверхность
 * остаётся тонким входом, а не параллельной реализацией.
 */
@RestController
@RequestMapping("/api/v1/trading-core/safety")
@RequiredArgsConstructor
public class SafetyController {

    private final ManualHaltService manualHaltService;
    private final ManualHaltFacade manualHaltFacade;

    /**
     * Постановка ступени. Полный класс уходит асинхронным фасадом и
     * отвечает {@code 202}; мягкие классы применяются синхронно и
     * отвечают {@code 204}.
     */
    @Operation(summary = "Поднять safety-ступень радиуса вручную")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Полная постановка запущена"),
            @ApiResponse(responseCode = "204", description = "Мягкая ступень применена"),
            @ApiResponse(responseCode = "400", description = "Отказ при запуске: недопустимая пара"
                    + " либо объект вне множества входа")})
    @PostMapping("/halts")
    public ResponseEntity<Void> raise(@Valid @RequestBody ManualHaltApiRequest request) {
        ManualHaltClass haltClass = haltClassOf(request);
        if (ManualHaltClass.FULL.equals(haltClass)) {
            manualHaltFacade.raiseFull(request.getExchangeAccountInternalId(),
                    request.getInstrumentInternalId());
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }
        manualHaltService.raise(haltClass, request.getExchangeAccountInternalId(),
                request.getInstrumentInternalId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Снятие ступени — <b>единственный</b> выход из холда: автоматического
     * снятия нет ни у одного основания.
     *
     * <p>Ступень называется явно и здесь: иначе повторный вызов шагал бы
     * по лестнице дальше — первый снял бы сворачивание, второй мягкую
     * ступень, — и двойное нажатие вернуло бы торговлю.
     */
    @Operation(summary = "Снять названную safety-ступень радиуса")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Снятие применено либо холостое"),
            @ApiResponse(responseCode = "400", description = "Отказ при запуске: недопустимая пара,"
                    + " прыжок через ступень либо непогашенный живой риск на радиусе")})
    @PostMapping("/halt-clearances")
    public ResponseEntity<Void> clear(@Valid @RequestBody ManualHaltApiRequest request) {
        manualHaltService.clear(haltClassOf(request), request.getExchangeAccountInternalId(),
                request.getInstrumentInternalId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Класс вмешательства из строки тела в доменный перечень: перевод
     * api → domain делает контроллер (.claude/rules/codestyle.md §Слои).
     * Неизвестное значение — негодный ВХОД вызова, и отказ у него общий
     * с прочими негодными входами поверхности.
     */
    private ManualHaltClass haltClassOf(ManualHaltApiRequest request) {
        try {
            return ManualHaltClass.valueOf(request.getHaltClass());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown manual halt class: "
                    + request.getHaltClass(), e);
        }
    }
}

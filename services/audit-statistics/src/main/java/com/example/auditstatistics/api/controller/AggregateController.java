package com.example.auditstatistics.api.controller;

import com.example.auditstatistics.api.model.AggregatePageApiResponse;
import com.example.auditstatistics.api.model.AggregateReadApiQuery;
import com.example.auditstatistics.domain.service.AggregateReadService;
import com.example.auditstatistics.mapping.AggregateMapper;
import com.example.auditstatistics.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Агрегатная выборка чтения: строки выбранного зерна и объявленная полнота
 * журнала рядом с ними
 * (docs/rules/statistics-aggregates.md §«Что это за числа и кто их
 * читает»).
 *
 * <p><b>Первым сегментом после версии стои́т имя владельца</b> — так
 * периметр выбирает адресата и своего перечня маршрутов не держит
 * (docs/architecture/contracts.md §«Периметр: что `bff` отдаёт и чего не
 * делает»). Хвост пути, имена параметров и форму ответа выбирает владелец
 * предмета; состав входа и выхода выбором не является — он назван домом.
 *
 * <p><b>Точка ОДНА на два зерна, и это не оболочка на глазок:</b> зерно
 * объявлено домом <b>операндом вызова</b> с закрытым перечнем из двух
 * значений, а не отдельной точкой на каждое. Второй выборки зерно не
 * заводит: предмет у неё один — строки агрегатов.
 *
 * <p><b>Тенант принимается заголовком контекста и НЕ СВЕРЯЕТСЯ</b>
 * (docs/architecture/contracts.md §«Контекст тенанта в вызове»). Это
 * названное ограничение, а не пропуск: кэша членств у сервиса нет и строки
 * к `auth` за ними не заводится, радиус держат три звена тропы, а цена и её
 * оживитель названы домом. Тропа при этом ЧИТАЮЩАЯ: полученный тенант
 * отбирает строки, а не присваивается новым.
 *
 * <p><b>Команд у сервиса нет по инвентарю</b>
 * (docs/architecture/services.md), поэтому здесь только чтение — и контур
 * доступа его не смягчает: агрегаты отвечают на вопрос «как торговал
 * тенант», и открытое чтение отдало бы эту картину кому угодно.
 */
@RestController
@RequestMapping("/api/v1/audit-statistics/aggregates")
@RequiredArgsConstructor
public class AggregateController {

    private final AggregateReadService aggregateReadService;
    private final AggregateMapper mapper;

    /**
     * Страница окна выбранного зерна — от новых суток к старым.
     *
     * <p><b>Отказ у вопроса один класс, а поводов шесть</b> (зерно не
     * названо либо названо вне перечня, окна нет, окно перевёрнуто, окно
     * шире предела, позиция названа наполовину, позиция несёт компоненты
     * чужого зерна), и все они говорят вызывающему одно: вопрос не принят.
     * Урезать вопрос до допустимого поверхность не станет
     * (docs/concept.md, П1).
     *
     * <p><b>Обязательность тенанта выражена аннотацией, а обязательность
     * зерна и окна — нет, и это не разнобой.</b> У тенанта ветвь отвержения
     * одна — его нет, — и она выразима описанием контракта; у зерна их две,
     * у окна три, и третья опирается на величину конфигурации сервиса, то
     * есть выразима только кодом. Разложенный по двум носителям, один отказ
     * отвечал бы двумя разными кодами.
     */
    @Operation(summary = "Строки агрегатов тенанта: зерно операндом, окно по суткам, страница курсором")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Страница отдана вместе с объявленной полнотой"),
            @ApiResponse(responseCode = "400", description = "Вопрос не принят: зерно не названо либо вне "
                    + "перечня, окна нет, окно перевёрнуто, окно шире предела, позиция названа наполовину "
                    + "либо несёт компоненты чужого зерна"),
            @ApiResponse(responseCode = "401", description = "Принципал не предъявлен либо не принят")})
    @GetMapping("/rows")
    public AggregatePageApiResponse findRows(
            @RequestHeader(Constants.Header.TENANT) @NotBlank String tenantInternalId,
            @ParameterObject AggregateReadApiQuery query) {
        return mapper.domainToApi(aggregateReadService.read(mapper.apiToDomain(query, tenantInternalId)));
    }
}

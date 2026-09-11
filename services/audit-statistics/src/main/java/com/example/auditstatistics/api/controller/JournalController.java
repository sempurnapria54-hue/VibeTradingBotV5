package com.example.auditstatistics.api.controller;

import com.example.auditstatistics.api.model.JournalPageApiResponse;
import com.example.auditstatistics.api.model.JournalReadApiQuery;
import com.example.auditstatistics.domain.service.JournalReadService;
import com.example.auditstatistics.mapping.AuditRecordMapper;
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
 * Журнальная выборка чтения: строки окна тенанта и объявленная полнота
 * рядом с ними
 * (docs/models/domain/other/AuditRecord.md §«Как журнал читается»).
 *
 * <p><b>Первым сегментом после версии стои́т имя владельца</b> — так
 * периметр выбирает адресата и своего перечня маршрутов не держит
 * (docs/architecture/contracts.md §«Периметр: что `bff` отдаёт и чего не
 * делает»). Хвост пути, имена параметров и форму ответа выбирает владелец
 * предмета; состав входа и выхода выбором не является — он назван домом.
 *
 * <p><b>Тенант принимается заголовком контекста и НЕ СВЕРЯЕТСЯ</b>
 * (docs/architecture/contracts.md §«Контекст тенанта в вызове»). Это
 * названное ограничение, а не пропуск: кэша членств у сервиса нет и
 * строки к `auth` за ними не заводится, радиус держат три звена тропы, а
 * цена и её оживитель названы домом. Тропа при этом ЧИТАЮЩАЯ: полученный
 * тенант отбирает строки, а не присваивается новым.
 *
 * <p><b>Команд у сервиса нет по инвентарю</b>
 * (docs/architecture/services.md), поэтому здесь только чтение — и контур
 * доступа его не смягчает: журнал отвечает на вопрос «что происходило у
 * тенанта», и открытое чтение отдало бы историю любого тенанта кому
 * угодно.
 */
@RestController
@RequestMapping("/api/v1/audit-statistics/journal")
@RequiredArgsConstructor
public class JournalController {

    private final JournalReadService journalReadService;
    private final AuditRecordMapper mapper;

    /**
     * Страница окна — от новых к старым.
     *
     * <p><b>Отказ у вопроса один класс, а поводов четыре</b> (окна нет,
     * окно перевёрнуто, окно шире предела, курсор назван наполовину), и
     * все они говорят вызывающему одно: вопрос не принят. Урезать вопрос
     * до допустимого поверхность не станет (docs/concept.md, П1).
     *
     * <p><b>Обязательность тенанта выражена аннотацией, а обязательность
     * окна — нет, и это не разнобой.</b> У тенанта ветвь отвержения одна —
     * его нет, — и она выразима описанием контракта; у окна их три, и
     * третья опирается на величину конфигурации сервиса, то есть
     * выразима только кодом. Разложенный по двум носителям, один отказ
     * отвечал бы двумя разными кодами.
     */
    @Operation(summary = "Строки журнала тенанта: окно по моменту происшествия, страница курсором")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Страница отдана вместе с объявленной полнотой"),
            @ApiResponse(responseCode = "400", description = "Вопрос не принят: окна нет, окно перевёрнуто, "
                    + "окно шире предела либо курсор назван наполовину"),
            @ApiResponse(responseCode = "401", description = "Принципал не предъявлен либо не принят")})
    @GetMapping("/records")
    public JournalPageApiResponse findRecords(
            @RequestHeader(Constants.Header.TENANT) @NotBlank String tenantInternalId,
            @ParameterObject JournalReadApiQuery query) {
        return mapper.domainToApi(journalReadService.read(mapper.apiToDomain(query, tenantInternalId)));
    }
}

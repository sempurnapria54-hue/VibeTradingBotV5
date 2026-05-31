# Шаблон: REST-контроллер (Java)

## На какой вопрос отвечает этот файл

Каков абстрактный паттерн контроллера нашего API на Java.

## Назначение

Код-шаблон для написания контроллера на под-шаге `CODE`. Абстрактный
паттерн с плейсхолдерами — не конкретный пример. Вход для
`code-writer` при письме. Конкретные примеры из реального кода
подбираются отдельно, после написания, скиллом `find-code-examples`
(другой инструмент, другая фаза — см.
`.claude/decisions/code-templates-vs-examples.md`).

## Плейсхолдеры

- `<Model>` — доменная модель (PascalCase). **Не `Entity`** — этот
  суффикс зарезервирован за persistence-слоем (см.
  `.claude/rules/codestyle.md`, нейминг по слоям).
- `<model>` — та же модель в camelCase (имя бина/переменной).
- `<models>` / `<Models>` — множественное число для пути и тега.
- `<authority>`, `<summary>`, `<описание>` — по месту.

## Шаблон

```java
package com.example.tradingbot.api.controller;

import com.example.tradingbot.domain.model.<...>.<Model>;
import com.example.tradingbot.domain.service.<...>.<Model>Service;
import com.example.tradingbot.mapping.<Model>Mapper;
import com.example.tradingbot.api.model.response.<Model>ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/<models>")
@Tag(name = "<Models>", description = "<описание>")
public class <Model>Controller {

    private final <Model>Service <model>Service;
    private final <Model>Mapper mapper;

    @GetMapping("/{<model>Id}")
    @PreAuthorize("hasAuthority('<authority>')") // TODO: политика — шаг «Безопасность»
    @Operation(summary = "<summary>")
    // @ApiResponses(...) — коды по error-конвенции (TBD)
    public <Model>ApiResponse getById(
            @Parameter(description = "<описание>", required = true)
            @PathVariable(name = "<model>Id") @NotBlank String <model>Id) {
        <Model> <model> = <model>Service.getRequiredById(<model>Id);
        return mapper.domainToApi(<model>);
    }
}
```

## Зафиксированные решения по паттерну

- Плейсхолдер `<Model>` (не `Entity` — зарезервирован за
  persistence-слоем).
- **Без хардкода кодов** (HTTP-статусы / коды ошибок в шаблон не
  вшиты).
- `@PreAuthorize` — **placeholder с TODO**: конкретная политика
  определяется на шаге «Безопасность» Фазы 1
  (`.claude/work/roadmap/phase-1.md`).
- `@ApiResponses` — **закомментирован**: error-конвенция (коды,
  `@ControllerAdvice` vs per-endpoint, документирование ошибок) пока
  TBD в `.claude/rules/codestyle.md`. Раскомментируется, когда
  конвенция зафиксируется.

## Связи

- Стиль / нейминг слоёв — `.claude/rules/codestyle.md`.
- Автор кода — `.claude/agents/code-writer.md`.
- Решённая модель шаблоны vs примеры —
  `.claude/decisions/code-templates-vs-examples.md`.

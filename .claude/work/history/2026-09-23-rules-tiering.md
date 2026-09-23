# Ярусные правила: ядро и правила со scope

## На какой вопрос отвечает этот файл

Что сделано задачей «ярусные правила» и какими замерами это подтверждено.

## Итог

Семнадцать правил `.claude/rules/` получили frontmatter `paths:`, пять остались
в ядре. Раскладка и её доводы — `.claude/decisions/rules-tiering.md`; критерий
яруса — `.claude/rules/structure.md`, строка `.claude/rules/`. Разнесения на
два файла не потребовалось.

Текст правил не менялся. Сверка с `HEAD` после снятия frontmatter дала
тождество у 21 файла; у `structure.md` отличается одна строка — строка
`.claude/rules/`, куда записан критерий по условию задачи. Окончания строк
frontmatter совпадают с окончаниями своего файла: девять правил в рабочей копии
хранятся с CRLF.

## Безусловная нагрузка до и после

Замер 2026-09-23, CLI 2.1.280, модель `claude-opus-5-5[1m]`.

| Замер | До | После | Разница |
|---|---|---|---|
| `/context`, всего | 105,7K | 50,4K | −55,3K |
| `/context`, файлы памяти | 87,8K | 32,5K | −55,3K |
| из них правила `rules/` | 86,8K | 31,5K | −55,3K |
| сессия без задачи, входных токенов по `usage` | 113 895 | 57 865 | −56 030 |
| субагент без задачи, `subagent_tokens` | 107 413 | 51 383 | −56 030 |

Ядро — 36% прежнего объёма правил. Субагент мерился дважды на каждой стороне,
оба прогона дали одно число.

```bash
MSYS_NO_PATHCONV=1 claude -p "/context" < /dev/null
MSYS_NO_PATHCONV=1 claude -p "Ответь одним словом: ok" --output-format json < /dev/null
MSYS_NO_PATHCONV=1 claude -p "Сам ничего не читай и не запускай, кроме одного: запусти одного субагента general-purpose с заданием «Ответь одним словом: ok. Ничего не делай.» Затем верни дословно строку с subagent_tokens из результата инструмента." --permission-mode bypassPermissions --output-format stream-json --verbose < /dev/null | grep -o 'subagent_tokens[^0-9]*[0-9]*'
```

`MSYS_NO_PATHCONV=1` обязателен: Git Bash иначе переписывает `/context` в путь
установки Git.

## Проверка загрузки каждого правила со scope

`/memory` в headless-режиме недоступен («/memory isn't available in this
environment»). Загрузка проверялась двенадцатью сессиями: каждая читала
инструментом `Read` один файл либо не читала ничего и отвечала строкой JSON —
какие файлы `rules/` были в контексте с начала и какие пришли после вызова.
Ожидаемый набор выводился из масок frontmatter.

| Касание | Пришло после `Read` |
|---|---|
| `.claude/work/backlog.md` | 11 правил: формы бэклога, перечня работ, рабочих файлов, парковки, единицы сессии и шесть корпусных |
| `.claude/snapshots/snapshot-v411.md` | 8: `snapshot-format`, `session-work-unit` и шесть корпусных |
| `docs/integrations/okx/contracts/order.md` | 9: `external-source-sync`, `carrier-levels`, `knowledge-ownership-by-service` и шесть корпусных |
| миграция `V1__audit_baseline.sql` сервиса `audit` | 1: `pre-launch-schema-changes` |
| Java-класс сервиса `audit` | 1: `carrier-levels` |
| `.claude/skills/place-knowledge.md` | 7: `classification-report` и шесть корпусных |
| `tools/backlog-check.py` | 3: `backlog-section-form`, `measurement-commands`, `structure` |
| `services/audit/pom.xml` | ничего |
| без касания | ничего |
| `tools/session-prompt.md` | 3: `session-work-unit`, `measurement-commands`, `structure` |
| `docs/models/domain/core/Order.md` | 9: `pre-launch-schema-changes`, `carrier-levels`, `knowledge-ownership-by-service` и шесть корпусных |
| `.claude/work/history/snapshots/snapshot-v1.md` | 10: `snapshot-format`, рабочие файлы, парковка, единица сессии и шесть корпусных |

Все двенадцать ответов совпали с выведенным набором. Во всех двенадцати
начальный контекст держал ровно пять правил ядра. Каждое правило со scope
пришло хотя бы в одной сессии и отсутствовало хотя бы в четырёх. Корпусные —
`curation`, `policy-home`, `edit-kind-obligations`, `self-description-form`,
`measurement-commands`, `structure`.

Субагент проверен отдельно временным правилом-зондом: до действий он видел
`codestyle` и `backlog-section-form` (тогда ещё ядро) и не видел зонда; после
собственного `Read` файла по маске — видел только его.

## Проверка механизма зондами

Зонды — временные правила с уникальной строкой, удалены до замера «до».

| Действие сессии | Правило пришло |
|---|---|
| `Read` файла по маске | да |
| `Read` несуществующего файла по маске | да |
| `Write` нового файла по маске, дважды | нет |
| `Edit` или `Write` существующего файла без чтения | нет |
| Bash `head` файла по маске | нет |
| Grep и Glob по каталогу маски | нет |
| `Read` файла вне маски | нет |
| сессия, которой инструмент не назван, в режимах `bypassPermissions`, `acceptEdits`, `default` | да — все читали через `Read` |

Отсюда `codestyle` и `tech-radar` остались в ядре по условию задачи: загрузка
при создании файла не срабатывает.

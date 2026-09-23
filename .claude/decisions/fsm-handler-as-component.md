# FSM-handler — компонент

## На какой вопрос отвечает этот файл

Где живёт handler-per-status FSM-сущности — компонент или раздел
lifecycle.

## Контекст

NQ-H третьей обкатки (отчёт
`.claude/work/history/2026-05-27-третья-обкатка-классификации-процессы.md`, Ф40). В
`Deal management/FSM этапы сделки.md` всплыли per-status handler'ы
Deal (PrecheckHandler, EntrySubmittedHandler, EntryFinalizedHandler,
ProtectionSwitchedHandler, ManagingHandler, ExitPendingHandler,
ErrorHandler). Handler формально подходит под «компонент»
(Java-класс-исполнитель, аналог executor'а), но неотделим от
состояния — без контекста статуса смысла не имеет.

DealStateMachine уже отнесён к компонентам первой обкаткой
(`.claude/decisions/component-vs-process.md`); механика переходов и
recovery-переходы — в lifecycle Deal (D5 первой обкатки). Вопрос:
handler'ы как внутренности — отдельные компоненты или разделы
lifecycle.

## Принятое решение

- Handler-per-status — отдельный компонент
  `docs/components/<Handler>.md` (PascalCase, имя совпадает с
  Java-классом).
- Раздел lifecycle на статус, общая конструкция handler'а у оркестратора и
  односторонняя связь живут в `.claude/skills/classify-type.md`
  §«FSM-handler vs раздел lifecycle».

## Альтернативы

- **B. Handler — всегда раздел lifecycle Deal.** Один раздел на
  статус, содержащий и механику, и описание handler'а как
  исполнителя. Отказались: создаёт исключение из правила
  «Java-класс-исполнитель → компонент»; handler как класс
  перестаёт быть видимым в `docs/components/`.
- **C. Гибрид по самостоятельности.** Handler по умолчанию — раздел
  lifecycle; отдельный компонент — если у handler'а есть
  самостоятельные обязанности. Отказались: критерий «самостоятельные
  обязанности» расплывчат до миграции; однородность A проще держать
  и не требует судейских решений per-handler.

## Следствия

- Живёт в `docs/components/`: компонент-доки обработчиков
  (`Tranche*Handler.md`, `DealActiveHandler.md`, …), конструкция —
  `docs/components/DealTrancheStateMachine.md`.
- Уточняет NQ-D (гранулярность компонентов) на handler-per-status —
  даёт первый прецедент: компонент 1:1 с элементом структуры
  объекта-владельца допустим.
- Закрывает NQ-H третьей обкатки.

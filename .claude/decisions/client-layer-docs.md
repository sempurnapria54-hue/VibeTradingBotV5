# Документация биржевого клиента: где живут exchange-specific факты

## На какой вопрос отвечает этот файл

Где живут exchange-specific факты — биржевые модели API, маппинги,
правила конкретной биржи.

## Контекст

В `.claude/rules/structure.md` не было типа под exchange-specific
знание. В архивной системе для этого был `docs/integrations/` с
шаблоном integration-mapping. Вторая обкатка
(`.claude/notes/2026-05-26-обкатка-классификации-торговые-модели.md`)
дала кейсы: «`tdMode=isolated` / `posSide=net` — константы в
`OkxClientService`», «raw exchange DTO не выходит за пределы
`ClientService`», «`positionReducingOnly` vs OKX `reduceOnly`», OKX
mapping в `OrderExternalStatusResolver`, `TimeFrameMapper`. Реальный
пробел типа.

## Принятое решение

- Новый каталог `docs/client/<ExchangeName>/` под exchange-specific
  знание. Внутри:
  - `docs/client/<ExchangeName>/models/` — биржевые модели API с
    описанием полей (PascalCase, имя совпадает с DTO / моделью API
    биржи);
  - `docs/client/<ExchangeName>/rules/` — правила биржи: константы,
    маппинги, договорённости (kebab-case по теме).
- Компонент-маппер живёт в `docs/components/` и ссылается на
  `docs/client/<ExchangeName>/` за деталями маппинга.

## Альтернативы

- **A. Не вводить отдельный тип; маппинги и константы — внутри
  компонента-маппера.** Отказались: специфики OKX уже сейчас много,
  компоненты распухнут.
- **B. Гибрид — приложения к компоненту в том же файле.** Отказались:
  знание о бирже размазывается между несколькими компонентами, нет
  единой точки входа.

## Следствия

- В `.claude/rules/structure.md` добавлены строки для
  `docs/client/<ExchangeName>/models/` и
  `docs/client/<ExchangeName>/rules/`.
- В `.claude/skills/classify-type.md` добавлен продуктовый тип
  «биржевой клиент» (модель API биржи + правило биржи) с признаками
  различения «биржевая модель vs доменная модель» и «правило биржи vs
  сквозное правило».
- Закрывает NQ-B второй обкатки.

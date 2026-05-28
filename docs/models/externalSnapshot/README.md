# externalSnapshot — нормализованные граничные модели

## На какой вопрос отвечает этот файл

Что это за слой `docs/models/externalSnapshot/` и когда здесь
появляются файлы.

## Назначение

Тир `externalSnapshot` хранит нормализованные граничные объекты
(`*ExternalSnapshot`). Это единственное, что выходит за
`ClientService`/adapter (`docs/rules/raw-exchange-dto-boundary.md`).
Смыслово snapshot принадлежит домену, но материально — отдельный
тир, граница между интеграцией и доменом.

## Когда заводить файл

Отдельный файл `*ExternalSnapshot.md` создаётся только при наличии
самостоятельного содержания:

- нетривиальная валидация в конструкторе / factory;
- нестандартный набор полей (не совпадает один-в-один с inventory
  source-модели);
- собственные invariants snapshot-уровня.

В простом случае поля snapshot уже зафиксированы в
`docs/models/mapping/<Сущность>.md` (раздел про snapshot-структуру);
дублировать их отдельным файлом не нужно.

## Что не место здесь

- Маппинг native → snapshot (это в `docs/models/mapping/`).
- Поля native-модели (это в `docs/models/integrations/<name>/`).
- Бизнес-логика (это в `docs/models/domain/...`).

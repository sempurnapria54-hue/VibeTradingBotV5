# IndicatorService

## На какой вопрос отвечает этот файл

Кто отдаёт готовые значения индикаторов.

## Назначение

`IndicatorService` отдаёт готовые `IndicatorValue`
(`docs/models/domain/other/IndicatorValue.md`). Сам индикаторы не
считает — их заранее считает `docs/components/IndicatorJob.md`.

## Контракт

- `Optional<IndicatorValue> getLatestValue(Long instrumentId,
  Long indicatorConfigId, Duration tolerance)` — последнее значение
  идентичности, свежее под срок **запрашивающего**;
- `Optional<IndicatorValue> getPreviousValue(Long instrumentId,
  Long indicatorConfigId)` — предыдущее значение (slope / crossover);
  свежесть не гейтит: это направление, а не точка решения.

## Срок приезжает операндом, а не читается со строки

Настройка заказчика живёт в чужой базе, а одна и та же строка
результата шарится между заказчиками с разной толерантностью: строка о
заказчике ничего не знает и срока не несёт
(`docs/models/domain/other/IndicatorValue.md`). Отсюда `tolerance` —
параметр вызова.

Метода «пачкой по коллекции настроек» контракт не несёт: адресация идёт
по идентичности, и пачку собирает читатель из своих привязок.

## Поведение при отсутствии / устаревании

Значения нет либо оно старше названного срока — отдаётся пустота. Обе
пустоты означают «данным доверять нельзя» и ведут к одной реакции;
различать их читателю не нужно (`docs/spec/market-data-freshness.json`).
Что делает с этим потребитель — его правило, не наше
(`docs/rules/market-data-freshness.md`).

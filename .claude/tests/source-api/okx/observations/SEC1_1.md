# Наблюдения кейса SEC1.1

## На какой вопрос отвечает этот файл

Что наблюдал в источнике кейс SEC1.1?

Файл пишет прогон кейса (`persistObservation`), руками не правится:
расхождение с источником чинится перепрогоном, а не редактурой.
Записи **дописываются**: исход каждого прогона — свой факт своего
момента, и затирание прежнего стирало бы добытое.

## Прогон 2026-09-04 — коды auth-отказа источника на /api/v5/account/balance

- **Строк перечня:** 6

```text
форма=контроль — верные креды · HTTP=200 · code=0 · msg=
форма=неизвестный ключ · HTTP=401 · code=50119 · msg=API key doesn't exist
форма=испорченная подпись · HTTP=401 · code=50113 · msg=Invalid Sign
форма=неверная passphrase · HTTP=401 · code=50105 · msg=Request header OK-ACCESS-PASSPHRASE incorrect.
форма=ключ не того окружения · HTTP=401 · code=50101 · msg=APIKey does not match current environment.
форма=заголовок ключа отсутствует · HTTP=401 · code=50103 · msg=Request header OK-ACCESS-KEY can not be empty.
```

## Прогон 2026-09-04 — коды auth-отказа источника на /api/v5/account/balance

- **Строк перечня:** 8

```text
форма=контроль — верные креды · HTTP=200 · code=0 · msg=
форма=неизвестный ключ · HTTP=401 · code=50119 · msg=API key doesn't exist
форма=испорченная подпись · HTTP=401 · code=50113 · msg=Invalid Sign
форма=неверная passphrase · HTTP=401 · code=50105 · msg=Request header OK-ACCESS-PASSPHRASE incorrect.
форма=ключ неверной формы · HTTP=401 · code=50111 · msg=Invalid OK-ACCESS-KEY
форма=ключ не того окружения · HTTP=401 · code=50101 · msg=APIKey does not match current environment.
форма=заголовок ключа отсутствует · HTTP=401 · code=50103 · msg=Request header OK-ACCESS-KEY can not be empty.
форма=верные креды, устаревший timestamp · HTTP=401 · code=50102 · msg=Timestamp request expired
```

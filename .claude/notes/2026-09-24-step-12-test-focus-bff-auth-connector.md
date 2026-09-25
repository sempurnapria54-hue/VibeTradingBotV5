# Фокус `test` шага 12: проход по `bff`, `auth` и `connector-okx`

## На какой вопрос отвечает этот файл

Какие находки дал проход фокуса `test` по тестам `bff`, `auth` и `connector-okx` 2026-09-24?

## Статус

Вход отчёту фокуса `test`, а не отчёт. Проход — субагент захода 146; вернулся
после конверта сессии. Находки проверены им чтением кода; заходом, пишущим
отчёт, перепроверяются.

## Флейк B2.2 ящика `bff` — механизм уточнён

Вход — секция бэклога «Ящик `bff`: кейс B2.2 краснеет от событий соседних
классов в окне переигрывания» (снята заходом 171 шага 12 —
`.claude/work/history/2026-09-25-step-12-sections-closed-by-code.md`; механизм,
измеренный логом, — перечитка с зафиксированного смещения, ловушка TC-196
`.claude/traps/test-code-traps.md`). **Её формулировка неверна:
окно переигрывания не участвует.** B2.2 открывает подписку без
`Last-Event-ID`, и `StreamRegistry#replay` выходит сразу
(`StreamRegistry.java:133-135`); чужие записи приходят **живой доставкой**.

- **Тенант общий** — `BffBox.java:83` `TENANT = "T1"`; классы B2 и B3 делят
  контекст через `SharedBffBox`.
- **Источник первый** — B3.3 публикует `e-b3-3` на `T1` и не ждёт её
  (`StreamSubscriptionBoxTest.java:118`): запись в пути достаётся следующей
  клетке.
- **Источник второй — повторная доставка (F-14 документа `bff.md:497`, `:507`):**
  закрытые клиентом подписки копятся в наборе тенанта, запись в них бросает,
  обработчик доставляет заново.
- **Своя гонка B2.2** — публикация сразу после `subscribe`, без барьера
  регистрации (`BffBox.java:423-428`).
- **Порядок классов не закреплён** — ни `ClassOrderer`, ни настроек платформы.

**Члены того же класса** (точный ассерт по проводу `T1` при соседях на `T1`):
B2.2 `SubscriptionTicketBoxTest.java:72`, B2.4 `:115`, B2.8 `:156`; B3.1
`StreamSubscriptionBoxTest.java:62,81`, B3.2 `:100-101`, B3.4 `:149`; B2.5
`ExpiringTicketBoxTest.java:72`, B3.11 `:90,103`. В `auth` и `connector-okx`
членов нет. **Крен правки** — тенант на клетку в `BffBox#resetSubstrate`
(форма `SubscriptionCeilingBoxTest`, довод `bff.md:497`); B2.2, B3.1, B3.2 —
через `openedStreamOf` с барьером; очистка окна отвергнута (не канал и
касание бина), свой контекст — тоже (не лечит утечку внутри класса).

## Находки по содержанию

1. **B10.1 `bff`, B6.1 `auth`, B9.1 `connector-okx` — невалидность.**
   `UnconfiguredAccessContourTest` каждого: `isInstanceOf(Exception.class)`;
   пустой `issuer-uri` старт сам не роняет (CS-013). Тот же класс — у
   `strategies` и `market-data` (соседние заметки 2026-09-24).
2. **`auth` B2.11 — невалидность.** `ExchangeAccountRegistrationBoxTest.java:202-221`:
   приёмник `AppLog` вешается при загрузке класса на строке 219 — после входа,
   журнал пуст; статусы не проверены.
3. **`connector-okx` B9.4 — невалидность, тот же дефект.** `ConfigurationInputBoxTest.java:64`;
   рядом B1.3 `CredentialsBoxTest.java:226` — лог и `API_KEY` не проверены.
4. **`auth` B1.15 — невалидность.** `MembershipResolutionBoxTest.java:317-320`:
   тело error-DTO не проверено.
5. **`auth` B7.2, B7.3 — невалидность.** `AbsentOutputsBoxTest.java:61,75`:
   ассерт по `application.yaml`, где слова `datasource` нет, — истинен по
   построению; «два адреса» не проверено.
6. **`auth` B1.1 — невалидность.** `MembershipResolutionBoxTest.java:55-56`:
   `String.valueOf(null)` проходит `isNotBlank`.
7. **`auth` B2.9 — детерминизм.** `SecretStoreWriteDeniedBoxTest.java:50`: права
   общего контейнера не возвращаются (`.claude/skills/test-code.md` §«Уровень 1»).
8. **`connector-okx` B1.12 — невалидность.** `UnnamedEnvironmentBoxTest.java:41`:
   `SecretStore.reads()` считает только префикс `dev/exchange-accounts/`
   (`SecretStore.java:135`) — чтение без окружения невидимо по построению.
9. **`connector-okx` B10.1, B10.2 — невалидность.** `AbsentOutputsBoxTest.java:40,74`:
   `doesNotContain` по `/actuator/health` без деталей.
10. **`connector-okx` B3.1 (`debt`) — невалидность.** `OrderFactsBoxTest.java:63-73`:
    нет ассерта `externalStatus`.
11. **`connector-okx` B2.13 — невалидность.** `ExchangeCommandsBoxTest.java:296`:
    проверен только `ordType`.
12. **`bff` U2.9 — детерминизм.** `SubscriptionTicketServiceTest.java:291-298`:
    смена секунды бросает отказ из цикла повторов.
13. **`bff` U5.3 — невалидность.** `StreamRegistryTest.java:87-90`.
14. **`bff` U6.5 — невалидность.** `StreamReplayTest.java:116-126`: `broadcast`
    без подписок ничего не обходит.
15. **Юниты, мелкое — невалидность.** U7.11 `OrderToSnapshotTest.java:148-163`
    (8 полей из 16); U3.10 `MembershipCacheTest.java:207`; U27.7
    `AckTest.java:133-141`; U8.4 `StreamRegistryTest.java:248-251`.
16. **Мишень `-D` коннектора — форма.** Метки живого прогона и гейта профиля
    prod нет (`services/connector-okx/pom.xml:57` исключает только `debt`).
17. **Классы вне документов — форма.** `CachingExchangeCredentialsResolverTest.java:66` —
    `Thread.sleep(40)` при сроке 20 мс; корневые классы `bff` и
    `connector-okx` без меток.

Сверх того: `auth` B2.10, B7.4 — мелкие недоборы; коннектор B6.4, B6.5
(`isNotEqualTo` вместо перечня), B9.2, B10.3 (`count()==0` по построению) —
форма. Хрупко без мигания: просроченный токен `exp = now − 1 мин` без запаса
сверх допуска часов (`IdentityStub` коннектора и `bff`).

## Охват прохода

Ящик `bff`: одиннадцать классов прочитаны по клеткам, одиннадцать — только
грепом по ассертам общего субстрата. Ящики `auth` и `connector-okx` и юниты
трёх сервисов — выборочно, перечень в выдаче субагента; девятнадцать меток
`debt` коннектора совпадают с документом. Порты динамические, бины не
подменены.

# Фокус `test` шага 12: проход по группам B9-B11 ящика `audit`

## На какой вопрос отвечает этот файл

Какие находки дал проход фокуса `test` по группам B9-B11 чёрного ящика `audit` 2026-09-24?

## Статус

Вход отчёту фокуса `test`, а не отчёт. Проход — субагент захода 147; вернулся
после конверта сессии. Находки проверены им чтением теста и продуктового кода;
заходом, пишущим отчёт, перепроверяются. Пути — от
`services/audit/src/test/java/com/example/audit/box/`; `док:N` — строки
`.claude/tests/cases/audit.md`. Покрытие полное; B9.8, B10.10 объявлены
непрогоняемыми, долговых клеток нет.

## Находки

1. **B10.1, B10.2 — невалидность.** `UnconfiguredAccessContourTest.java:38-44`,
   `UnconfiguredReceptionPathTest.java:37-45`: `isInstanceOf(Exception.class)` —
   причина неподъёма (названная в «Факте») не пинится.
2. **B9.6 — невалидность.** `AccessContourBoxTest.java:264-266`:
   `isNotEqualTo(200)` без токена, а дом фиксирует `401`.
3. **B9.7 — невалидность.** `AuditBox.java:869-871`: `String.valueOf(…get("message"))`
   даёт `"null"` — `isNotBlank` вхолостую. Тот же класс — `auth` B1.1.
4. **B11.2 — невалидность.** `AbsentOutputsBoxTest.java:257-260`: `isLessThan(5)`
   при «ни одного обращения».
5. **B11.2, B11.5 — изоляция.** `AbsentOutputsBoxTest.java:266-268, 352-355,
   476-497`: греп `application.yaml` сервиса — чтение внутренности (док:1624).
6. **B10.8 — невалидность.** `SchemaInputBoxTest.java:124-138`: любая ошибка SQL
   зеленит четыре инварианта. Правка: SQLState либо имя ограничения.
7. **B10.7 — невалидность, тавтология.** `SchemaInputBoxTest.java:108-110`:
   множественное число — на константах теста. Тот же класс — `strategies` B9.4.
8. **B9.2 — невалидность.** `AccessContourBoxTest.java:188-191`: выдача стаба за
   весь прогон; `contains("jwks")` подстрокой.
9. **B9.2, B9.3 — спорно.** `IdentityStub.java:96, 149`: чужой ключ под чужим
   `kid` — отказ до проверки подписи; `IdentityStub.java:101-102`: просрочка равна
   допуску часов 60 с. Тот же класс — `bff`, коннектор.
10. **B9.8 — пробел.** `JournalSurfaceReadTest.java:236-246`: проба покрывает
    одно ожидание из трёх.
11. **B10.6 и все счёты `reception_states` — детерминизм.**
    `SelfFiringTickBoxTest.java:37` без `@DirtiesContext`: тик 500 мс живого
    кэшированного контекста пишет строки в общую базу; задеты
    `givenReceptionStateRows`, `pairs()`, B9.7, B10.7, B11.6, `TickDisabledBoxTest`,
    `UntickedPairsBoxTest`.
12. **B10.6 — детерминизм.** `ConfiguredValuesBoxTest.java:112-124`: запас в одну
    секунду от настенных часов.
13. **B10.5, B10.6 — принятая цена.** Окна «не случилось за 8 с».
14. **B9.10 — детерминизм, спорно.** `AppLog.java:67` без синхронизации (тот же
    класс — `…-audit-box-b7-b8.md`, п. 3).
15. **Пробел дока.** Раздел «Где живёт код кейсов» (док:1482-1486) без B11; «Факт»
    B10.10 пуст (док:1398); половина B10.3 не мерится, «Факт» молчит.
16. **Форма.** `AuditBox.java:564, 852` `Objects.isNull` с именем класса.

## Охват прохода

Чисто: `EarliestPositionBoxTest`, `NarrowedSubscriptionBoxTest`,
`SubstrateImagePinTest`, `Wire`, `SharedAuditBox`, `AuditSubstrate`; B9.1, B9.4,
B9.5, B9.9, B9.11, B11.1, B11.3, B11.4, B11.7. Группы B3-B6 ящика `audit` не
покрыты.

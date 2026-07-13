---
name: build-command
description: How to compile the Maven project from CLI (no wrapper; PATH java is wrong version)
metadata:
  type: reference
---

Сборка проекта из CLI нетривиальна: в репозитории **нет** Maven-wrapper
(`mvnw`), `mvn` не на PATH, а `java` на PATH — JDK 11 (проекту нужен 25).
Рабочая команда компиляции (PowerShell), через бандл-Maven IntelliJ IDEA
и установленный Corretto 25:

```powershell
$env:JAVA_HOME = "C:\Users\RomanKrd\.jdks\corretto-25.0.3"
$mvn = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3\bin\mvn.cmd"
& $mvn -q -DskipTests compile
```

`EXIT 0` = чисто. Маven печатает предупреждения Guice/Unsafe — это не наш
код. Полный прогон/рантайм всё равно делает пользователь в IDEA (см.
снапшоты: «рантайм-проверка в IDEA — за пользователем»). Путь к IDEA
может смениться с версией (сейчас `IntelliJ IDEA 2026.1`).

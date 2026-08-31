---
name: build-command
description: Where the acting build command and environment facts live (single home; do not duplicate)
metadata:
  type: reference
---

Команда сборки, пути JDK и Maven, лаунчер Python — **один дом**:
`.claude/tests/source-api/okx/code-preconditions.md` §«Среда контура —
проверено прогоном, не выведено» (таблица + §«Воспроизводимые команды»).
Факты там **проверены прогоном**, а не выведены, и обновляются вместе со
средой.

Здесь редакция не держится намеренно: расходящихся копий было три на
четыре носителя, и две предписывали несуществующие команды (`./mvnw`,
`py -3`). Копия команды в этом файле стареет первой — её читает тот, кто
до дома не дошёл.

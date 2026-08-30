# -*- coding: utf-8 -*-
"""Разрывы области видимости исполнимых спецификаций (docs/spec).

ПРЕДМЕТ ПРОВЕРКИ. Механизм `includes` нетранзитивен: раннер
(src/test/java/com/example/tradingbot/spec/Spec.java) разворачивает у
подключённого файла только его `values`, а его собственные `includes` — нет.
Поэтому файл, подключивший набор величин, обязан подключить и дома, на
которые этот набор опирается. Иначе заимствованная величина отказывает
вычислением, а прогон tools/spec-run.sh МОЛЧИТ до тех пор, пока какой-нибудь
пример эту величину не позовёт. Скрипт ищет такие разрывы статически, без
примеров.

КОМАНДА ЗАПУСКА (из корня репозитория):

    py -3 tools/spec-scope-check.py docs/spec

ИМЕННО `py`. В этой среде `python` и `python3` в PATH — заглушки Windows
Store: они печатают строку «Python» и завершаются с кодом 49, не выполнив
скрипт. Перечня разрывов при этом нет и итоговой строки тоже нет, поэтому по
ВЫВОДУ такой запуск читается как чистый прогон — ложный зелёный, хотя проверка
не исполнялась вовсе. Настоящий интерпретатор поднимает только лаунчер `py`;
код 49 без итоговой строки означает «скрипт не запускался», а не «разрывов
нет».

Код возврата: 0 — разрывов нет; 1 — есть (перечень в stdout).

КАК СЧИТАЕТСЯ. Для каждого файла строится его область видимости ровно как в
раннере: свои `values` плюс `values` файлов из `includes`, нетранзитивно.
Затем идентификаторы ВСЕХ величин области (в том числе заимствованных)
резолвятся против имён величин этой области, операндов проверяемого файла,
операндов файла-источника величины и функций языка. Нерезолвимое имя даёт
находку одного из двух классов.

  КЛАСС A — РАЗРЫВ: имя не резолвится ничем и при этом объявлено величиной
    где-то в корпусе. Вызов такой величины откажет вычислением.
  КЛАСС B — ПОДМЕНА ДОМА ГОСТЕВЫМ СОСТОЯНИЕМ: имя резолвится ТОЛЬКО
    операндом файла-источника, а у самого имени есть дом-величина в корпусе.
    То есть величина, у которой в корпусе есть вычислимый дом, приезжает к
    потребителю сырым состоянием — и приедет ли, он не объявлял. Класс живой:
    именно так величина, уехавшая из `values` в `operands`, тихо ломает
    область видимости у тех, кто подключал её дом.

ЧЕГО ИНСТРУМЕНТ НЕ МЕРИТ (названо, чтобы им не удостоверяли лишнего): общий
операндный контракт — имена полей строк (`type`, `size`, `status`, `carrier`,
…), которые приходят из состояния примера и дома-величины в корпусе не имеют.
Это нормальный режим корпуса, а не дефект: файл законно берёт набор целиком и
пользуется частью. Сколько таких имён — печатает сам прогон полем «ПРОПУЩЕНО
ПО ОПЕРАНДНОМУ КОНТРАКТУ» итоговой строки (определение — РАЗЛИЧНЫЕ имена,
которые не резолвятся ни величиной области, ни операндом, и дома-величины в
корпусе не имеют). Числом в шапке оно не фиксируется: корпус живой, число
дрейфует с каждой новой величиной, и замороженный клейм устаревает молча.

ОБЪЯВЛЕННЫЕ ОСИ И ИХ ПАДАЮЩИЕ ПРОБЫ. Инструмент, чьи оси не доказаны
падением, ничего не удостоверяет. Каждая проба — мутация во временной копии
(cp docs/spec/*.json target/probe-scope/), вносящая дефект ровно этой оси;
засчитывается только при коде возврата 1. Батарея воспроизводима целиком:

    bash tools/spec-scope-probe.sh

  ось 1 — РАЗРЫВ У ЗАИМСТВОВАННОЙ ВЕЛИЧИНЫ (класс, ради которого скрипт
    заведён): снять "order-lifecycle" из includes файла deal-risk-numbers.
    Ожидание: 1 разрыв — trancheHasLiveEntryOrder (из protection-coverage)
    зовёт orderIsLive.
  ось 2 — РАЗРЫВ У СОБСТВЕННОЙ ВЕЛИЧИНЫ: снять "order-lifecycle" из includes
    файла protection-coverage. Ожидание: 1 разрыв — в самом
    protection-coverage, на его собственной величине trancheHasLiveEntryOrder.
    Потребители при этом чисты: каждый подключает order-lifecycle сам — именно
    поэтому ось 2 не выводится из оси 1 и доказывается отдельно.
  ось 3 — ОПЕРАНД-УКАЗАТЕЛЬ ПРИЗНАЁТСЯ ТРОПОЙ РЕЗОЛВА: удалить объявление
    операнда hasLiveEpisode из stop-distance. Ожидание: разрыв на entryAnchor
    — то есть молчание скрипта на действительном состоянии обеспечено именно
    объявленным операндом, а не пропуском имени.
  ось 4 — КЛАСС B, ПОДМЕНА ДОМА ГОСТЕВЫМ СОСТОЯНИЕМ: снять
    "protection-coverage" из includes файла strategy-reference. Ожидание:
    1 находка класса B — заимствованная entryAnchor стои́т на hasLiveEpisode,
    у которого есть дом-величина, но в область видимости он не введён.
  ось C — КОПИЯ ФОРМЫ ПОД СОБСТВЕННЫМ ИМЕНЕМ: переименовать величину-дом в
    копию у соседа (или объявить второе имя с тем же выражением). Ожидание:
    находка класса C — два имени несут одно выражение, и расхождение копий
    ничем не ловится. Правило — .claude/rules/structure.md, строка docs/spec:
    «копия формулы под другим именем или на другом префиксе операнда —
    дефект». ИСКЛЮЧЕНИЕ, названное тем же правилом: совпадение выражений у
    РАЗНЫХ ПРЕДМЕТОВ (два жизненных цикла, две сущности) дублем не считается —
    перечни независимы и могут разойтись законно. Исключение объявляется в
    самой спеке ключом "independentFrom": "<спека>/<величина>" на величине;
    молчаливого пропуска нет — без объявления пара остаётся находкой.

Зелёная проба означает, что мутация перестала попадать в носитель (файл
переименован, форма изменилась), и проба переякоривается, а не удаляется.
"""
import io
import json
import os
import re
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

FUNCS = {"true", "false", "null", "min", "max", "abs", "floorTo", "not",
         "isNull", "notNull", "coalesce", "if", "in"}
IDENT = re.compile(r"\??[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*")

d = sys.argv[1] if len(sys.argv) > 1 else "docs/spec"


def load(name):
    with io.open(os.path.join(d, name + ".json"), encoding="utf-8") as f:
        return json.load(f)


def operand_tokens(spec):
    """Имена, приходящие из состояния примера: путь операнда и его сегменты."""
    t = set()
    for key in spec.get("operands", {}):
        clean = key.replace("[]", "").replace("{}", "")
        t.add(clean)
        for seg in clean.split("."):
            if seg:
                t.add(seg)
    return t


def strip_literals(e):
    return re.sub(r"'[^']*'", "''", e)


specs = sorted(f[:-5] for f in os.listdir(d) if f.endswith(".json"))
declared_in = {}
for s in specs:
    for v in load(s).get("values", []):
        declared_in.setdefault(v["name"], []).append(s)

bad = 0
guest = 0
copies = 0
skipped = set()
for name in specs:
    spec = load(name)
    incs = spec.get("includes", [])
    scope, names = [], set()
    for inc in incs:
        for v in load(inc).get("values", []):
            scope.append((inc, v))
            names.add(v["name"])
    for v in spec.get("values", []):
        scope.append((name, v))
        names.add(v["name"])
    ops = operand_tokens(spec)
    ops_of = {name: ops}
    for inc in incs:
        ops_of[inc] = operand_tokens(load(inc))
    seen = set()
    for origin, v in scope:
        for slot in ("expr", "where", "of"):
            if slot not in v or v[slot] is None:
                continue
            for m in IDENT.finditer(strip_literals(str(v[slot]))):
                ident = m.group(0).lstrip("?")
                if "." in ident or ident in FUNCS or ident in names or ident in ops:
                    continue
                if ident not in declared_in:
                    skipped.add(ident)
                    continue
                key = (name, v["name"], ident)
                if key in seen:
                    continue
                seen.add(key)
                if ident in ops_of.get(origin, set()):
                    if origin != name:
                        print("КЛАСС B %s: заимствованная %s (из %s) стои́т на %s — "
                              "дом-величина %s, но в область видимости не введена и "
                              "приходит только состоянием"
                              % (name, v["name"], origin, ident, ", ".join(declared_in[ident])))
                        guest += 1
                    continue
                print("КЛАСС A %s: %s (из %s) зовёт %s — дом %s, в область видимости не входит"
                      % (name, v["name"], origin, ident, ", ".join(declared_in[ident])))
                bad += 1

# --- ось C: копия формы под собственным именем -------------------------------
def form_of(v):
    """Нормализованная форма величины: скаляр — выражение, агрегат — его части."""
    if v.get("expr") is not None:
        return re.sub(r"\s+", " ", str(v["expr"])).strip()
    parts = {k: v.get(k) for k in ("op", "over", "where", "of") if v.get(k) is not None}
    if not parts:
        return None
    return "АГРЕГАТ " + json.dumps(parts, ensure_ascii=False, sort_keys=True)


forms = {}
exempt = {}
for name in specs:
    for v in load(name).get("values", []):
        form = form_of(v)
        if form is None:
            continue
        forms.setdefault(form, []).append((name, v["name"]))
        for target in ([v["independentFrom"]] if isinstance(v.get("independentFrom"), str)
                       else v.get("independentFrom", [])):
            exempt.setdefault((name, v["name"]), set()).add(target)

for form, owners in sorted(forms.items()):
    distinct = sorted({owner for owner in owners})
    if len(distinct) < 2 or len({value for _, value in distinct}) < 2:
        continue
    labels = {owner: "%s/%s" % owner for owner in distinct}
    unresolved = []
    for owner in distinct:
        others = [labels[other] for other in distinct if other != owner]
        declared = exempt.get(owner, set())
        if not all(other in declared for other in others):
            unresolved.append(owner)
    if not unresolved:
        continue
    print("КЛАСС C: одно выражение под разными именами — %s; форма: %s"
          % (", ".join(labels[owner] for owner in distinct), form[:120]))
    copies += 1

print("РАЗРЫВОВ (A): %d; ПОДМЕН ДОМА ГОСТЕВЫМ СОСТОЯНИЕМ (B): %d; "
      "КОПИЙ ФОРМЫ ПОД СОБСТВЕННЫМ ИМЕНЕМ (C): %d; "
      "ПРОПУЩЕНО ПО ОПЕРАНДНОМУ КОНТРАКТУ (не дефект): %d имён"
      % (bad, guest, copies, len(skipped)))
sys.exit(1 if bad or guest or copies else 0)

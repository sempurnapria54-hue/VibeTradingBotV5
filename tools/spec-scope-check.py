# -*- coding: utf-8 -*-
"""Разрывы области видимости и копии формы в исполнимых спецификациях.

ПРЕДМЕТ ПРОВЕРКИ. Механизм `includes` нетранзитивен: раннер
(donor/src/test/java/com/example/tradingbot/spec/Spec.java) разворачивает у
подключённого файла только его `values`, а его собственные `includes` — нет.
Поэтому файл, подключивший набор величин, обязан подключить и дома, на
которые этот набор опирается. Иначе заимствованная величина отказывает
вычислением, а прогон tools/spec-run.sh МОЛЧИТ до тех пор, пока какой-нибудь
пример эту величину не позовёт. Скрипт ищет такие разрывы статически, без
примеров. Второй предмет — копия формы: у величины один дом.

КОМАНДА ЗАПУСКА (из корня репозитория):

    python3 tools/spec-scope-check.py docs/spec

Лаунчер проверяется прогоном, а не шапкой: команда, не выполнившая файл,
печатает пустой перечень, который читается как чистый прогон. Признак
исполнения — строка батареи осей первым же выводом; её нет — скрипт не
запускался.

ФОРМЫ ПРЕДМЕТА, КОТОРЫЕ ДЕТЕКТОР ВИДИТ (объявлено, доказано осями батареи):

  КЛАСС A — РАЗРЫВ: имя не резолвится ничем и при этом объявлено величиной
    где-то в корпусе. Вызов такой величины откажет вычислением.
  КЛАСС B — ПОДМЕНА ДОМА ГОСТЕВЫМ СОСТОЯНИЕМ: имя резолвится ТОЛЬКО
    операндом файла-источника, а у самого имени есть дом-величина в корпусе.
    Величина, у которой в корпусе есть вычислимый дом, приезжает к
    потребителю сырым состоянием — и приедет ли, он не объявлял.
  КЛАСС C — КОПИЯ ФОРМЫ, в ДВУХ письменных формах, обе обязательны:
    C.1 дословная — одно выражение под разными именами;
    C.2 префиксная — одна форма на разных префиксах операнда
        (`deal.entryPrice - deal.stopPrice` против
         `tranche.entryPrice - tranche.stopPrice`).
    Правило — .claude/rules/structure.md, строка docs/spec: «копия формулы
    под другим именем ИЛИ НА ДРУГОМ ПРЕФИКСЕ ОПЕРАНДА — дефект». Прежняя
    редакция мерила только C.1, то есть половину собственного правила.
    ИСКЛЮЧЕНИЕ, названное тем же правилом: совпадение выражений у РАЗНЫХ
    ПРЕДМЕТОВ (два жизненных цикла, две сущности) дублем не считается.
    Объявляется ключом "independentFrom": "<спека>/<величина>" на величине;
    молчаливого пропуска нет — без объявления пара остаётся находкой.
  КЛАСС D — ДВА ДОМА У ОДНОГО ИМЕНИ: имя величины объявлено более чем в
    одной спеке. Правило — .claude/rules/structure.md, строка docs/spec:
    «величина объявляется ровно в одной спеке»; docs/concept.md §Ссылки
    ставит на машинной проверке этого правила законность §`имя`-ссылок.
    Класс D — та самая машинная проверка: до его ввода правило не мерил
    никто, а индекс домов tools/spec-pointer-check.py при двух домах
    вырождается в МНОЖЕСТВО и признаёт верным указатель на не тот дом.
    Исключение "independentFrom" сюда НЕ распространяется: оно снимает
    совпадение ВЫРАЖЕНИЙ у разных предметов, а два предмета под одним
    ИМЕНЕМ остаются неразличимыми для читателя и для указателя.

ЧЕГО ИНСТРУМЕНТ НЕ МЕРИТ (названо, чтобы им не удостоверяли лишнего): общий
операндный контракт — имена полей строк (`type`, `size`, `status`, `carrier`,
…), которые приходят из состояния примера и дома-величины в корпусе не имеют.
Это нормальный режим корпуса, а не дефект: файл законно берёт набор целиком и
пользуется частью. Сколько таких имён — печатает сам прогон полем «ПРОПУЩЕНО
ПО ОПЕРАНДНОМУ КОНТРАКТУ» итоговой строки. Числом в шапке оно не фиксируется:
корпус живой, число дрейфует с каждой новой величиной.

БАТАРЕЯ ОСЕЙ ИСПОЛНЯЕТСЯ ЭТОЙ ЖЕ КОМАНДОЙ, до проверки. Каждая ось —
самодостаточная фикстура с дефектом ровно этой оси плюс контроль на ложное
срабатывание; фикстура самодостаточна намеренно: проба, заякоренная на
величину живого корпуса, зеленеет от любой правки этого корпуса, и
переякоривание проб становится постоянной работой.

Код возврата: 0 — находок нет; 1 — есть (перечень в stdout); 2 — ПРОВЕРКА НЕ
ПРОВОДИЛАСЬ (ось не доказана, каталога нет, спецификаций нет).
"""
import io
import json
import os
import re
import sys
import tempfile

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

FUNCS = {"true", "false", "null", "min", "max", "abs", "floorTo", "not",
         "isNull", "notNull", "coalesce", "if", "in"}
IDENT = re.compile(r"\??[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*")


def load(directory, name):
    with io.open(os.path.join(directory, name + ".json"), encoding="utf-8") as handle:
        return json.load(handle)


def operand_tokens(spec):
    """Имена, приходящие из состояния примера: путь операнда и его сегменты."""
    tokens = set()
    for key in spec.get("operands", {}):
        clean = key.replace("[]", "").replace("{}", "")
        tokens.add(clean)
        for segment in clean.split("."):
            if segment:
                tokens.add(segment)
    return tokens


def strip_literals(expression):
    return re.sub(r"'[^']*'", "''", expression)


def form_of(value):
    """Нормализованная форма величины: скаляр — выражение, агрегат — его части."""
    if value.get("expr") is not None:
        return re.sub(r"\s+", " ", str(value["expr"])).strip()
    parts = {key: value.get(key) for key in ("op", "over", "where", "of")
             if value.get(key) is not None}
    if not parts:
        return None
    return "АГРЕГАТ " + json.dumps(parts, ensure_ascii=False, sort_keys=True)


def unprefixed(form):
    """Форма без ПЕРВОГО сегмента точечных имён: deal.x - deal.y -> x - y."""
    if form is None:
        return None
    return re.sub(r"\b[A-Za-z_][A-Za-z0-9_]*\.(?=[A-Za-z_])", "", form)


def scope_findings(directory, specs, declared_in, out):
    """Классы A и B: область видимости величин."""
    broken, guest, skipped = 0, 0, set()
    for name in specs:
        spec = load(directory, name)
        includes = spec.get("includes", [])
        scope, names = [], set()
        for included in includes:
            for value in load(directory, included).get("values", []):
                scope.append((included, value))
                names.add(value["name"])
        for value in spec.get("values", []):
            scope.append((name, value))
            names.add(value["name"])
        operands = {name: operand_tokens(spec)}
        for included in includes:
            operands[included] = operand_tokens(load(directory, included))
        seen = set()
        for origin, value in scope:
            for slot in ("expr", "where", "of"):
                if slot not in value or value[slot] is None:
                    continue
                for match in IDENT.finditer(strip_literals(str(value[slot]))):
                    ident = match.group(0).lstrip("?")
                    if "." in ident or ident in FUNCS or ident in names or ident in operands[name]:
                        continue
                    if ident not in declared_in:
                        skipped.add(ident)
                        continue
                    key = (name, value["name"], ident)
                    if key in seen:
                        continue
                    seen.add(key)
                    if ident in operands.get(origin, set()):
                        if origin != name:
                            out.append("КЛАСС B %s: заимствованная %s (из %s) стои́т на %s — "
                                       "дом-величина %s, но в область видимости не введена и "
                                       "приходит только состоянием"
                                       % (name, value["name"], origin, ident,
                                          ", ".join(declared_in[ident])))
                            guest += 1
                        continue
                    out.append("КЛАСС A %s: %s (из %s) зовёт %s — дом %s, в область "
                               "видимости не входит"
                               % (name, value["name"], origin, ident, ", ".join(declared_in[ident])))
                    broken += 1
    return broken, guest, skipped


def copy_findings(directory, specs, out):
    """Класс C: копия формы — дословная и на другом префиксе операнда."""
    literal, prefixless, exempt = {}, {}, {}
    for name in specs:
        for value in load(directory, name).get("values", []):
            form = form_of(value)
            if form is None:
                continue
            literal.setdefault(form, []).append((name, value["name"]))
            prefixless.setdefault(unprefixed(form), []).append((name, value["name"]))
            declared = value.get("independentFrom", [])
            for target in [declared] if isinstance(declared, str) else declared:
                exempt.setdefault((name, value["name"]), set()).add(target)

    def report(groups, label, seen):
        found = 0
        for form, owners in sorted(groups.items()):
            distinct = sorted(set(owners))
            if len(distinct) < 2 or len({value for _, value in distinct}) < 2:
                continue
            key = tuple(distinct)
            if key in seen:
                continue
            seen.add(key)
            labels = {owner: "%s/%s" % owner for owner in distinct}
            resolved = all(
                all(labels[other] in exempt.get(owner, set())
                    for other in distinct if other != owner)
                for owner in distinct)
            if resolved:
                continue
            out.append("%s: %s; форма: %s"
                       % (label, ", ".join(labels[owner] for owner in distinct), form[:120]))
            found += 1
        return found

    seen = set()
    copies = report(literal, "КЛАСС C.1: одно выражение под разными именами", seen)
    copies += report(prefixless, "КЛАСС C.2: одна форма на разных префиксах операнда", seen)
    return copies


def scan(directory):
    """Полный разбор каталога. Возвращает (строки, счёт) либо отказ."""
    if not os.path.isdir(directory):
        return None, "каталог спецификаций %s не найден" % directory
    specs = sorted(name[:-5] for name in os.listdir(directory) if name.endswith(".json"))
    if not specs:
        return None, "в каталоге %s нет ни одной спецификации — проверять нечего" % directory
    declared_in = {}
    for name in specs:
        for value in load(directory, name).get("values", []):
            declared_in.setdefault(value["name"], []).append(name)
    if not declared_in:
        return None, "ни одной величины не объявлено — проверять нечего"
    out = []
    broken, guest, skipped = scope_findings(directory, specs, declared_in, out)
    copies = copy_findings(directory, specs, out)
    homes = home_findings(declared_in, out)
    return (out, (broken, guest, copies, homes, len(skipped))), None


def home_findings(declared_in, out):
    """КЛАСС D: имя величины объявлено более чем в одной спеке."""
    found = 0
    for name in sorted(declared_in):
        homes = sorted(set(declared_in[name]))
        if len(homes) > 1:
            found += 1
            out.append("КЛАСС D: имя «%s» объявлено в %d спеках (%s) — "
                       "у величины один дом, иначе указатель на неё неразличим"
                       % (name, len(homes), ", ".join(homes)))
    return found


# --- батарея осей ------------------------------------------------------------

def write(directory, name, body):
    with io.open(os.path.join(directory, name + ".json"), "w", encoding="utf-8") as handle:
        json.dump(body, handle, ensure_ascii=False)


def value(name, expr, **extra):
    body = {"name": name, "note": "фикстура батареи", "expr": expr}
    body.update(extra)
    return body


def battery():
    axes = []
    with tempfile.TemporaryDirectory() as work:
        def area(name):
            path = os.path.join(work, name)
            os.makedirs(path, exist_ok=True)
            return path

        def axis(title, directory, marker, expected):
            result, refusal = scan(directory)
            if refusal:
                axes.append((title, False, "отказ: " + refusal))
                return
            lines, _counts = result
            hits = [line for line in lines if line.startswith(marker)]
            axes.append((title, bool(hits) == expected,
                         "находок «%s»: %d (всего %d)" % (marker, len(hits), len(lines))))

        # A: дом заимствованной величины не подключён потребителем
        one = area("a")
        write(one, "base", {"subject": "base", "values": [value("orderIsLive", "status == 'LIVE'")]})
        write(one, "mid", {"subject": "mid", "includes": ["base"],
                           "values": [value("trancheHasLive", "orderIsLive")]})
        write(one, "top", {"subject": "top", "includes": ["mid"],
                           "values": [value("dealHasLive", "trancheHasLive")]})
        axis("1. класс A: дом заимствованной величины не подключён", one, "КЛАСС A", True)

        # контроль A: тот же расклад, но дом подключён
        two = area("b")
        write(two, "base", {"subject": "base", "values": [value("orderIsLive", "status == 'LIVE'")]})
        write(two, "mid", {"subject": "mid", "includes": ["base"],
                           "values": [value("trancheHasLive", "orderIsLive")]})
        write(two, "top", {"subject": "top", "includes": ["mid", "base"],
                           "values": [value("dealHasLive", "trancheHasLive")]})
        axis("2. контроль: дом подключён — разрыва нет", two, "КЛАСС A", False)

        # B: имя приходит операндом источника, а дом-величина есть в корпусе
        three = area("c")
        write(three, "base", {"subject": "base", "values": [value("orderIsLive", "status == 'LIVE'")]})
        write(three, "mid", {"subject": "mid", "operands": {"orderIsLive": "приходит состоянием"},
                             "values": [value("trancheHasLive", "orderIsLive")]})
        write(three, "top", {"subject": "top", "includes": ["mid"],
                             "values": [value("dealHasLive", "trancheHasLive")]})
        axis("3. класс B: дом подменён гостевым состоянием", three, "КЛАСС B", True)

        # C.1: одно выражение под разными именами
        four = area("d")
        write(four, "left", {"subject": "left", "operands": {"deal.entryPrice": "", "deal.stopPrice": ""},
                             "values": [value("riskLeft", "deal.entryPrice - deal.stopPrice")]})
        write(four, "right", {"subject": "right", "operands": {"deal.entryPrice": "", "deal.stopPrice": ""},
                              "values": [value("riskRight", "deal.entryPrice - deal.stopPrice")]})
        axis("4. класс C.1: одно выражение под разными именами", four, "КЛАСС C.1", True)

        # C.2: та же форма на другом префиксе операнда
        five = area("e")
        write(five, "left", {"subject": "left", "operands": {"deal.entryPrice": "", "deal.stopPrice": ""},
                             "values": [value("riskLeft", "deal.entryPrice - deal.stopPrice")]})
        write(five, "right", {"subject": "right", "operands": {"tranche.entryPrice": "", "tranche.stopPrice": ""},
                              "values": [value("riskRight", "tranche.entryPrice - tranche.stopPrice")]})
        axis("5. класс C.2: одна форма на разных префиксах операнда", five, "КЛАСС C.2", True)

        # контроль C: объявленная независимость снимает находку
        six = area("f")
        write(six, "left", {"subject": "left", "operands": {"deal.entryPrice": "", "deal.stopPrice": ""},
                            "values": [value("riskLeft", "deal.entryPrice - deal.stopPrice",
                                             independentFrom="right/riskRight")]})
        write(six, "right", {"subject": "right", "operands": {"tranche.entryPrice": "", "tranche.stopPrice": ""},
                             "values": [value("riskRight", "tranche.entryPrice - tranche.stopPrice",
                                              independentFrom="left/riskLeft")]})
        axis("6. контроль: объявленная независимость снимает находку класса C", six, "КЛАСС C", False)

        # D: одно имя объявлено в двух спеках. Выражения РАЗНЫЕ намеренно —
        # иначе сработал бы класс C и ось меряла бы не свой предмет.
        nine = area("h")
        write(nine, "deal", {"subject": "deal", "operands": {"deal.from": "", "deal.to": ""},
                             "values": [value("transitionAllowed", "deal.from != deal.to")]})
        write(nine, "tranche", {"subject": "tranche", "operands": {"tranche.stage": ""},
                                "values": [value("transitionAllowed", "tranche.stage > 0")]})
        axis("9. класс D: одно имя объявлено в двух спеках", nine, "КЛАСС D", True)

        # контроль D: те же два предмета под РАЗНЫМИ именами находки не дают
        ten = area("i")
        write(ten, "deal", {"subject": "deal", "operands": {"deal.from": "", "deal.to": ""},
                            "values": [value("transitionAllowed", "deal.from != deal.to")]})
        write(ten, "tranche", {"subject": "tranche", "operands": {"tranche.stage": ""},
                               "values": [value("trancheTransitionAllowed", "tranche.stage > 0")]})
        axis("10. контроль: разные имена у разных предметов находки не дают", ten, "КЛАСС D", False)

        # контроль D: объявленная независимость класс D НЕ снимает — она про
        # совпадение выражений, а не про одноимённость домов.
        eleven = area("j")
        write(eleven, "deal", {"subject": "deal", "operands": {"deal.from": "", "deal.to": ""},
                               "values": [value("transitionAllowed", "deal.from != deal.to",
                                                independentFrom="tranche/transitionAllowed")]})
        write(eleven, "tranche", {"subject": "tranche", "operands": {"tranche.stage": ""},
                                  "values": [value("transitionAllowed", "tranche.stage > 0",
                                                   independentFrom="deal/transitionAllowed")]})
        axis("11. объявленная независимость класс D не снимает", eleven, "КЛАСС D", True)

        # базовый гейт
        _, refusal = scan(os.path.join(work, "нет-такого"))
        axes.append(("7. каталога нет — проверка отказывает", bool(refusal),
                     refusal or "проверка отчиталась"))
        _, refusal = scan(area("g"))
        axes.append(("8. спецификаций нет — проверка отказывает", bool(refusal),
                     refusal or "проверка отчиталась"))
    return axes


def main():
    directory = sys.argv[1] if len(sys.argv) > 1 else "docs/spec"
    axes = battery()
    print("--- батарея осей детектора (исполняется той же командой)")
    for title, passed, observed in axes:
        print("  %s: %s — %s" % ("доказана" if passed else "НЕ ДОКАЗАНА", title, observed))
    broken = [title for title, passed, _ in axes if not passed]
    if broken:
        print("ПРОВЕРКА НЕ ПРОВОДИТСЯ: недоказанных осей %d — перечень находок "
              "ничего не удостоверял бы" % len(broken))
        return 2

    result, refusal = scan(directory)
    if refusal:
        print("ПРОВЕРКА НЕ ПРОВОДИТСЯ: " + refusal)
        return 2
    lines, (gaps, guest, copies, homes, skipped) = result
    for line in lines:
        print(line)
    print("РАЗРЫВОВ (A): %d; ПОДМЕН ДОМА ГОСТЕВЫМ СОСТОЯНИЕМ (B): %d; "
          "КОПИЙ ФОРМЫ (C): %d; ДВУХ ДОМОВ У ИМЕНИ (D): %d; "
          "ПРОПУЩЕНО ПО ОПЕРАНДНОМУ КОНТРАКТУ (не дефект): %d имён"
          % (gaps, guest, copies, homes, skipped))
    return 1 if gaps or guest or copies or homes else 0


if __name__ == "__main__":
    sys.exit(main())

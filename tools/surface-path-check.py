#!/usr/bin/env python3
"""Предмет проверки: конвенция путей внешней поверхности владельцев.

Внешняя поверхность любого владельца начинается его именем: `/api/v1/<владелец>/…`,
где `<владелец>` — имя единицы из инвентаря `docs/architecture/services.md`. Дом
конвенции и её довод — `docs/architecture/contracts.md` §«Периметр: что `bff`
отдаёт и чего не делает»; здесь они не пересказываются.

ПОЧЕМУ ИНСТРУМЕНТ ЗАВЕДЁН. Конвенция держалась дисциплиной того, кто заводит
контроллер, и её дом это прямо объявлял. Нарушение обнаружилось бы не проверкой,
а отказом маршрута периметра — то есть у ВЫЗЫВАЮЩЕГО и в рантайме: периметр
выбирает адресата первым сегментом после версии и своего перечня маршрутов не
держит, поэтому путь, начинающийся не именем владельца, уходит в чужой сервис
либо в никуда. Условие возврата было названо в `.claude/work/backlog.md`
§«Энфорсер конвенции путей внешней поверхности».

ОБЛАСТЬ ВЫВОДИТСЯ, А НЕ ПЕРЕЧИСЛЯЕТСЯ. Конвенция адресует поверхность,
адресуемую через периметр, — единицы, у которых в таблице §«Синхронные вызовы»
есть строка `bff → …`, — и сам периметр. Перечень таких единиц команда выводит
из той таблицы; собственной копии у неё нет, иначе она старела бы при первом
новом владельце за периметром (`.claude/rules/policy-home.md`).

ЯРУС ИНТЕГРАЦИИ ВНЕ ОБЛАСТИ, И ЭТО ПЕЧАТАЕТСЯ ПОИМЁННО. Строки `bff →
connector-<биржа>` нет и по ярусам быть не может: его вызывающие ходят по
прямому адресу, не через маршрут. Молчаливого пропуска нет — исключённые единицы
прогон называет.

РОУТЕР ПЕРИМЕТРА — ОБЪЯВЛЕННОЕ ИСКЛЮЧЕНИЕ. Путь, у которого сегмент владельца
есть переменная пути (`/api/v1/{owner}/**`), не поверхность владельца, а сам
маршрутизатор; он законен только у `bff`. У всякой другой единицы переменная на
месте имени владельца — дефект: она означает, что владельца называет не путь.

ОСИ ДОКАЗАНЫ БАТАРЕЕЙ, исполняемой ЭТОЙ ЖЕ командой: команда, которая ничего не
измерила, обязана быть отличима от команды, которая измерила и не нашла. Код
возврата 2 — «не измерялось».
"""
import os
import re
import sys

# Вывод — UTF-8 независимо от кодовой страницы консоли: имена пассажей и
# стрелка указателя в cp1251 не выражаются, и прогон падал бы на печати
# находки, а не на её отсутствии.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

SERVICES_DIR = 'services'
INVENTORY = os.path.join('docs', 'architecture', 'services.md')
CONTRACTS = os.path.join('docs', 'architecture', 'contracts.md')
PERIMETER = 'bff'
VERSION_PREFIX = '/api/v1'

CONTROLLER = re.compile(r'@RestController\b')
CLASS_MAPPING = re.compile(r'@RequestMapping\s*\(\s*(.+?)\s*\)\s*(?:@\w+[^\n]*\s*)*\npublic\s+class', re.S)
ANY_MAPPING = re.compile(r'@(?:RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\s*\(')
CONSTANT = re.compile(r'static\s+final\s+String\s+(\w+)\s*=\s*([^;]+);')
LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')
NAME_REF = re.compile(r'^[A-Za-z_][\w.]*$')
INVENTORY_UNIT = re.compile(r'^\|\s*`([a-z][a-z0-9-]*)(?:-<[^>]+>)?`')
CALL_ROW = re.compile(r'^\|\s*`([a-z][a-z0-9-]*)`\s*\|\s*(.+?)\s*\|')
BACKTICKED = re.compile(r'`([a-z][a-z0-9-]*)`')


def read(path):
    return open(path, encoding='utf-8', errors='replace').read().replace('\r\n', '\n')


def inventory_units(text):
    """Имена единиц развёртывания из инвентаря: первая колонка в кавычках."""
    units = []
    for line in text.split('\n'):
        match = INVENTORY_UNIT.match(line)
        if match:
            units.append(match.group(1))
    return units


def perimeter_callees(text, units):
    """Единицы, к которым периметр ходит: колонка «К» строк `bff → …`."""
    callees = []
    for line in text.split('\n'):
        match = CALL_ROW.match(line)
        if not match or match.group(1) != PERIMETER:
            continue
        for name in BACKTICKED.findall(match.group(2)):
            if name in units and name not in callees:
                callees.append(name)
    return callees


def constants_of(module_dir):
    """Таблица строковых констант модуля: короткое имя → выражение."""
    table = {}
    for directory, _, names in os.walk(module_dir):
        if 'target' in directory.replace(os.sep, '/').split('/'):
            continue
        for name in names:
            if not name.endswith('.java'):
                continue
            for constant, expression in CONSTANT.findall(read(os.path.join(directory, name))):
                table[constant] = expression
    return table


def resolve(expression, table, depth=0):
    """Значение выражения пути либо None, если оно не разрешается.

    Разрешаются конкатенации литералов и ссылок на строковые константы модуля
    (в любой квалификации — последний сегмент имени и есть ключ таблицы).
    Нерезолвимое выражение НЕ пропускается молча: None поднимается вызывающим
    в отказ прогона.
    """
    if depth > 8:
        return None
    value = ''
    for token in expression.split('+'):
        token = token.strip()
        literal = LITERAL.fullmatch(token)
        if literal:
            value += literal.group(1)
            continue
        if NAME_REF.match(token):
            key = token.split('.')[-1]
            if key not in table:
                return None
            nested = resolve(table[key], table, depth + 1)
            if nested is None:
                return None
            value += nested
            continue
        return None
    return value


def mapping_expressions(text):
    """Выражения путей контроллера: корень класса либо пути его методов."""
    class_level = CLASS_MAPPING.search(text)
    if class_level:
        return [first_argument(class_level.group(1))]
    expressions = []
    for match in ANY_MAPPING.finditer(text):
        tail = text[match.end():]
        depth = 1
        for index, char in enumerate(tail):
            if char == '(':
                depth += 1
            elif char == ')':
                depth -= 1
                if depth == 0:
                    expressions.append(first_argument(tail[:index]))
                    break
    return expressions


def first_argument(arguments):
    """Первый аргумент аннотации: `value = …` либо позиционный, до запятой."""
    arguments = arguments.strip()
    named = re.match(r'value\s*=\s*(.+)', arguments, re.S)
    if named:
        arguments = named.group(1)
    depth = 0
    for index, char in enumerate(arguments):
        if char in '({':
            depth += 1
        elif char in ')}':
            depth -= 1
        elif char == ',' and depth == 0:
            return arguments[:index].strip()
    return arguments.strip()


def verdict(path, unit):
    """Исход одного пути: None — конвенция исполнена, иначе причина дефекта."""
    if not path.startswith(VERSION_PREFIX + '/'):
        return 'корень не начинается `%s/`' % VERSION_PREFIX
    segment = path[len(VERSION_PREFIX) + 1:].split('/')[0]
    if segment.startswith('{'):
        if unit == PERIMETER:
            return None
        return 'на месте имени владельца — переменная пути `%s`' % segment
    if segment != unit:
        return 'первый сегмент после версии — `%s`, а владелец — `%s`' % (segment, unit)
    return None


def controllers_of(module_dir):
    """Файлы-контроллеры модуля: (относительный путь, текст)."""
    found = []
    for directory, _, names in os.walk(module_dir):
        if 'target' in directory.replace(os.sep, '/').split('/'):
            continue
        for name in names:
            if not name.endswith('.java'):
                continue
            path = os.path.join(directory, name)
            text = read(path)
            if CONTROLLER.search(text):
                found.append((path, text))
    return found


def scan(base_dir, area):
    """Обход области: (число путей, дефекты, нерезолвимые, число контроллеров)."""
    total = 0
    defects = []
    unresolved = []
    controllers = 0
    for unit in area:
        module_dir = os.path.join(base_dir, SERVICES_DIR, unit)
        if not os.path.isdir(module_dir):
            continue
        table = constants_of(module_dir)
        for path, text in controllers_of(module_dir):
            controllers += 1
            relative = os.path.relpath(path, base_dir).replace(os.sep, '/')
            for expression in mapping_expressions(text):
                value = resolve(expression, table)
                if value is None:
                    unresolved.append((relative, expression))
                    continue
                total += 1
                reason = verdict(value, unit)
                if reason:
                    defects.append((relative, value, reason))
    return total, defects, unresolved, controllers


def battery():
    """Оси детектора, доказанные падающей пробой на каждой."""
    axes = []
    table = {'API_V1': '"/api/v1"', 'PERIMETER_OWNER': '"bff"',
             'PERIMETER_ROOT': 'API_V1 + "/" + PERIMETER_OWNER'}

    # Ось 1: чужое имя владельца первым сегментом — дефект.
    axes.append(('чужое имя владельца первым сегментом — дефект',
                 verdict('/api/v1/market/instruments', 'market-data') is not None,
                 'исход: %r' % (verdict('/api/v1/market/instruments', 'market-data'),)))

    # Ось 2: корень без версии — дефект.
    axes.append(('корень без версии — дефект',
                 verdict('/market-data/instruments', 'market-data') is not None,
                 'исход: %r' % (verdict('/market-data/instruments', 'market-data'),)))

    # Ось 3: имя владельца как ПРЕФИКС чужого сегмента — дефект (граница сегмента).
    axes.append(('имя владельца как префикс чужого сегмента — дефект',
                 verdict('/api/v1/strategies-draft/x', 'strategies') is not None,
                 'исход: %r' % (verdict('/api/v1/strategies-draft/x', 'strategies'),)))

    # Ось 4: переменная пути на месте владельца — дефект у всех, кроме периметра.
    axes.append(('переменная пути на месте владельца вне периметра — дефект',
                 verdict('/api/v1/{owner}/**', 'trading-core') is not None,
                 'исход: %r' % (verdict('/api/v1/{owner}/**', 'trading-core'),)))

    # Ось 5: нерезолвимое выражение не пропускается молча.
    axes.append(('нерезолвимое выражение пути — не пропуск, а отказ',
                 resolve('someMethod()', table) is None,
                 'исход: %r' % (resolve('someMethod()', table),)))

    # Ось 6: константа, собранная из констант, разрешается.
    axes.append(('константа из констант разрешается',
                 resolve('Constants.Paths.PERIMETER_ROOT', table) == '/api/v1/bff',
                 'значение: %r' % (resolve('Constants.Paths.PERIMETER_ROOT', table),)))

    # Ось 7: область выводится из таблицы вызовов, а не из перечня внутри команды.
    derived = perimeter_callees('| `bff` | `auth`, `strategies` | чтение |\n'
                                '| `trading-core` | `auth` | реестр |',
                                ['auth', 'strategies', 'trading-core'])
    axes.append(('область берёт только строки `bff → …`',
                 derived == ['auth', 'strategies'], 'выведено: %r' % (derived,)))

    # Ось 8: единица инвентаря-шаблона (`connector-<биржа>`) читается по основе.
    units = inventory_units('| `connector-<биржа>` (по одной) | … |\n| `auth` | … |')
    axes.append(('шаблонное имя единицы читается по основе',
                 units == ['connector', 'auth'], 'выведено: %r' % (units,)))

    # Ось 9: корень без хвоста дефектом не является.
    axes.append(('контроль: корень без хвоста законен',
                 verdict('/api/v1/strategies', 'strategies') is None,
                 'исход: %r' % (verdict('/api/v1/strategies', 'strategies'),)))

    # Ось 10: контроль — конвенционный путь дефектом не считается.
    axes.append(('контроль: конвенционный путь законен',
                 verdict('/api/v1/audit-statistics/journal', 'audit-statistics') is None,
                 'исход: %r' % (verdict('/api/v1/audit-statistics/journal', 'audit-statistics'),)))

    # Ось 11: контроль — роутер периметра законен у периметра.
    axes.append(('контроль: роутер периметра законен у периметра',
                 verdict('/api/v1/{owner}/**', PERIMETER) is None,
                 'исход: %r' % (verdict('/api/v1/{owner}/**', PERIMETER),)))

    # Ось 12: первый аргумент аннотации отделяется от прочих.
    argument = first_argument('value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE')
    axes.append(('первый аргумент аннотации отделяется от прочих',
                 argument == '"/stream"', 'разобрано: %r' % (argument,)))

    return axes


def main():
    base_dir = sys.argv[1] if len(sys.argv) > 1 else '.'
    axes = battery()
    print('--- батарея осей детектора (исполняется той же командой)')
    for title, passed, observed in axes:
        print('  %s: %s — %s' % ('доказана' if passed else 'НЕ ДОКАЗАНА', title, observed))
    undone = [title for title, passed, _ in axes if not passed]
    if undone:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: недоказанных осей %d — исход ничего не '
              'удостоверял бы' % len(undone))
        return 2

    inventory_path = os.path.join(base_dir, INVENTORY)
    contracts_path = os.path.join(base_dir, CONTRACTS)
    for path in (inventory_path, contracts_path):
        if not os.path.isfile(path):
            print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: нет носителя области — %s' % path)
            return 2

    units = inventory_units(read(inventory_path))
    if not units:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: инвентарь не разобран — ни одной единицы')
        return 2

    callees = perimeter_callees(read(contracts_path), units)
    if not callees:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: таблица §«Синхронные вызовы» не дала ни одной '
              'строки `%s → …` — область не выводится' % PERIMETER)
        return 2

    area = sorted(set(callees) | {PERIMETER})
    built = [unit for unit in area if os.path.isdir(os.path.join(base_dir, SERVICES_DIR, unit))]
    outside = sorted(name for name in os.listdir(os.path.join(base_dir, SERVICES_DIR))
                     if os.path.isdir(os.path.join(base_dir, SERVICES_DIR, name))
                     and name not in area)

    total, defects, unresolved, controllers = scan(base_dir, built)
    if unresolved:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: выражение пути не разрешается — %d шт.'
              % len(unresolved))
        for relative, expression in sorted(unresolved):
            print('  НЕ РАЗРЕШАЕТСЯ: %s → %s' % (relative, expression))
        return 2
    if total == 0:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: путей в области нет вовсе '
              '(контроллеров обойдено: %d) — мерить нечего' % controllers)
        return 2

    print('единиц области: %d (%s); контроллеров: %d; путей проверено: %d; ДЕФЕКТОВ: %d'
          % (len(built), ', '.join(built), controllers, total, len(defects)))
    print('  вне области (ярус интеграции и единицы без строки `%s → …`): %s'
          % (PERIMETER, ', '.join(outside) if outside else 'нет'))
    for relative, value, reason in sorted(defects):
        print('  ДЕФЕКТ: %s → `%s` — %s' % (relative, value, reason))
    return 1 if defects else 0


if __name__ == '__main__':
    sys.exit(main())

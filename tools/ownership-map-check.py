#!/usr/bin/env python3
"""Предмет проверки: карта владельцев-сервисов против фактики корпуса.

Вторая ось размещения знания — какой сервис владеет носителем — живёт разметкой,
а не раскладкой: её дом `.claude/rules/knowledge-ownership-by-service.md`
§«Карта владельцев». Разметка каталогами не выражается и потому стареет молча —
цена, принятая при выборе «`docs/` остаётся на месте»
(`.claude/decisions/monorepo-restructuring-in-place.md`). Этот прогон — её
погашение.

ЧТО МЕРИТСЯ.
  A. Указатель карты разрешается: путь, названный строкой, в репозитории есть.
  B. Владелец назван словарём: имя в колонке владельца есть в инвентаре
     `docs/architecture/services.md` — среди единиц развёртывания либо общих
     артефактов, — или является объявленным признаком («сквозное», «вне оси»).
  C. Живой носитель `docs/**` покрыт: его накрывает хотя бы одна строка карты.
  D. Двойного ИМЕНОВАННОГО владения нет: две строки, называющие носитель
     поимённо, — это два дома у одной истины (`.claude/rules/policy-home.md`).

ЧТО НЕ МЕРИТСЯ, И ЭТО НАЗВАНО, А НЕ УМОЛЧАНО.
  * ВЕРНОСТЬ владельца. Что `Deal.md` принадлежит именно `trading-core`, а не
    соседу, из файловой системы не выводится ничем; прогон мерит покрытие и
    словарь, верность читает критик — та же граница, что у всех разметочных
    осей корпуса.
  * ПРОЗАИЧЕСКИЙ КЛАСТЕР. Строка, чья левая клетка называет носители словами
    («джобы и сервисы свечей, индикаторов…»), машине не разрешается: имена
    файлов в ней не написаны. Такие строки прогон ПЕЧАТАЕТ ПОИМЁННО, а их
    носители уходят в строку-катч-олл своего каталога. Молчаливого пропуска
    нет.

ФОРМА СТРОКИ. Каталог в кавычках покрывает поддерево; каталог, за которым
стои́т двоеточие, — лишь префикс для перечня основ после него. Относительное
имя (`other/X.md`) разрешается от РОДИТЕЛЯ текущего каталога — так карта и
написана. Клетка, содержащая тире-разделитель « — », есть строка-РЕШЕНИЕ об
отсутствии дока и не покрывает ничего.

ОСИ ДОКАЗАНЫ БАТАРЕЕЙ, исполняемой ЭТОЙ ЖЕ командой. Код возврата 2 — «не
измерялось».
"""
import os
import re
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

MAP = os.path.join('.claude', 'rules', 'knowledge-ownership-by-service.md')
INVENTORY = os.path.join('docs', 'architecture', 'services.md')
AREA = 'docs'
MAP_HEADING = '## Карта владельцев'
CARRIER_SUFFIXES = ('.md', '.json')
STEM_SUFFIXES = ('.md', '.json')
DECISION_MARK = ' — '
CATCH_ALL = 'всё остальное'
FEATURES = ('сквозное', 'вне оси')
ABSOLUTE_ROOTS = ('docs/', '.claude/', 'tools/', 'deploy/', 'libs/', 'services/', 'web/')
BACKTICKED = re.compile(r'`([^`]+)`')
INVENTORY_NAME = re.compile(r'^\|\s*`([a-z][a-z0-9-]*)(?:-<[^>]+>)?`')
OWNER_NAME = re.compile(r'^[a-z][a-z0-9-]*$')


def read(path):
    return open(path, encoding='utf-8', errors='replace').read().replace('\r\n', '\n')


def vocabulary(text):
    """Словарь владельцев: единицы развёртывания и общие артефакты инвентаря.

    Шаблонная единица инвентаря (`connector-<биржа>`) даёт не имя, а ОСНОВУ:
    карта законно называет конкретный экземпляр (`connector-okx`), которого в
    инвентаре нет и не будет — инвентарь держит род, а не перечень площадок.
    """
    names = set()
    stems = set()
    for line in text.split('\n'):
        match = INVENTORY_NAME.match(line)
        if not match:
            continue
        names.add(match.group(1))
        if '-<' in line.split('`')[1]:
            stems.add(match.group(1) + '-')
    return names, stems


def known_owner(name, names, stems):
    """Есть ли имя в словаре: поимённо, основой шаблонной единицы либо признаком."""
    return (name in names or name in FEATURES
            or any(name.startswith(stem) for stem in stems))


def map_rows(text):
    """Строки карты владельцев: (номер строки файла, левая клетка, правая)."""
    if MAP_HEADING not in text:
        return []
    start = text.index(MAP_HEADING)
    rows = []
    for offset, line in enumerate(text[start:].split('\n')):
        if line.startswith('## ') and offset > 0:
            break
        if not line.startswith('|') or line.startswith('|---') or line.startswith('| Носитель'):
            continue
        cells = [cell.strip() for cell in line.strip().strip('|').split(' | ')]
        if len(cells) < 2:
            continue
        number = text[:start].count('\n') + offset + 1
        rows.append((number, cells[0], ' | '.join(cells[1:])))
    return rows


def parse_left(cell):
    """Разбор левой клетки: (покрытые каталоги, покрытые пути, есть ли проза).

    Каталог, за которым в тексте клетки идёт двоеточие, поддерева не покрывает —
    он лишь префикс перечня основ. Исключение — клетка-катч-олл («всё
    остальное»): там двоеточие стои́т, а каталог покрывает всё, что не названо
    поимённо другими строками. Прозой считается всякий текст клетки вне кавычек
    и вне разделителей: по нему носитель не выводится.
    """
    directories = []
    paths = []
    base = None
    catch = CATCH_ALL in cell
    for match in BACKTICKED.finditer(cell):
        token = match.group(1)
        tail = cell[match.end():].lstrip()
        if token.startswith(ABSOLUTE_ROOTS) or '/' not in token and token.endswith('.md'):
            resolved = token
        elif '/' in token:
            parent = os.path.dirname(base.rstrip('/')) + '/' if base else ''
            resolved = parent + token
        else:
            if base is None:
                continue
            resolved = base + token
        if resolved.endswith('/'):
            base = resolved
            if catch or not tail.startswith(':'):
                directories.append(resolved)
            continue
        if not resolved.endswith(STEM_SUFFIXES):
            resolved_candidates = [resolved + suffix for suffix in STEM_SUFFIXES]
        else:
            resolved_candidates = [resolved]
        paths.append(resolved_candidates)
        base = os.path.dirname(resolved) + '/'
    prose = BACKTICKED.sub('', cell)
    prose = re.sub(r'[\s,;:()«»—–\-]+', '', prose)
    return directories, paths, bool(prose)


def carriers(base_dir):
    """Живые носители области: относительные пути `docs/**` с доковым суффиксом."""
    found = []
    root_path = os.path.join(base_dir, AREA)
    for directory, _, names in os.walk(root_path):
        for name in names:
            if not name.endswith(CARRIER_SUFFIXES):
                continue
            path = os.path.join(directory, name)
            found.append(os.path.relpath(path, base_dir).replace(os.sep, '/'))
    return sorted(found)


def build(rows, base_dir):
    """Свод карты: именованные покрытия, катч-олл-каталоги, прозаические строки."""
    named = {}
    blanket = []
    prose_rows = []
    dangling = []
    for number, left, _ in rows:
        if DECISION_MARK in left:
            continue
        directories, paths, has_prose = parse_left(left)
        catch = CATCH_ALL in left
        for candidates in paths:
            existing = [c for c in candidates if os.path.exists(os.path.join(base_dir, c))]
            if not existing:
                dangling.append((number, candidates[0]))
                continue
            named.setdefault(existing[0], []).append(number)
        for directory in directories:
            if not os.path.isdir(os.path.join(base_dir, directory.rstrip('/'))):
                dangling.append((number, directory))
                continue
            blanket.append((directory, number, catch))
        if has_prose and not catch:
            prose_rows.append((number, left))
    return named, blanket, prose_rows, dangling


def owners_of(cell):
    """Имена-кандидаты владельцев правой клетки: слова в кавычках без пути."""
    return [token for token in BACKTICKED.findall(cell) if OWNER_NAME.match(token)]


def battery():
    """Оси детектора, доказанные падающей пробой на каждой."""
    axes = []

    # Ось 1: каталог без двоеточия покрывает поддерево.
    directories, paths, _ = parse_left('`docs/architecture/`, `docs/dictionary/`')
    axes.append(('каталог без двоеточия покрывает поддерево',
                 directories == ['docs/architecture/', 'docs/dictionary/'] and not paths,
                 'каталоги: %r; пути: %r' % (directories, paths)))

    # Ось 2: каталог с двоеточием поддерева НЕ покрывает — он префикс перечня.
    directories, paths, _ = parse_left('`docs/rules/`: `time-utc`, `manual-halt`')
    axes.append(('каталог с двоеточием — префикс, а не покрытие',
                 directories == [] and [candidates[0] for candidates in paths]
                 == ['docs/rules/time-utc.md', 'docs/rules/manual-halt.md'],
                 'каталоги: %r; пути: %r' % (directories, paths)))

    # Ось 3: относительное имя разрешается от РОДИТЕЛЯ текущего каталога.
    _, paths, _ = parse_left('`docs/models/domain/core/Instrument.md`, '
                             '`other/InstrumentExternalRules.md`')
    axes.append(('относительное имя разрешается от родителя',
                 paths[-1] == ['docs/models/domain/other/InstrumentExternalRules.md'],
                 'пути: %r' % (paths,)))

    # Ось 4: строка-решение об отсутствии дока не покрывает ничего.
    rows = [(1, '`docs/components/` — файлов владельца `bff` нет', '`bff`')]
    named, blanket, _, _ = build(rows, '.')
    axes.append(('строка-решение не покрывает ничего',
                 named == {} and blanket == [], 'покрытия: %r / %r' % (named, blanket)))

    # Ось 5: проза в левой клетке опознаётся (носитель по ней не выводится).
    _, _, prose = parse_left('`docs/components/`: джобы и сервисы свечей, `MarketPriceDataService`')
    axes.append(('прозаический кластер опознаётся',
                 prose is True, 'проза: %r' % (prose,)))

    # Ось 6: контроль — клетка без прозы прозаической не считается.
    _, _, prose = parse_left('`docs/rules/`: `time-utc`, `manual-halt`')
    axes.append(('контроль: клетка без прозы прозаической не считается',
                 prose is False, 'проза: %r' % (prose,)))

    # Ось 7: имя владельца отделяется от пути в той же клетке.
    names = owners_of('формы — `domain-model`; писатели — `trading-core` '
                      '(`docs/architecture/data-ownership.md`)')
    axes.append(('имя владельца отделяется от пути',
                 names == ['domain-model', 'trading-core'], 'имена: %r' % (names,)))

    # Ось 8: словарь берётся из инвентаря, а не из перечня внутри команды.
    names, stems = vocabulary('| `trading-core` | … |\n| `connector-<биржа>` (по одной) | … |\n'
                              '| провайдер идентичности | … |')
    axes.append(('словарь владельцев выводится из инвентаря',
                 names == {'trading-core', 'connector'} and stems == {'connector-'},
                 'словарь: %r; основы: %r' % (sorted(names), sorted(stems))))

    # Ось 8a: конкретный экземпляр шаблонной единицы словарю принадлежит, а
    # похожее имя без границы сегмента — нет.
    axes.append(('экземпляр шаблонной единицы принадлежит словарю',
                 known_owner('connector-okx', names, stems)
                 and not known_owner('connectorx', names, stems),
                 'okx: %r; чужое: %r' % (known_owner('connector-okx', names, stems),
                                         known_owner('connectorx', names, stems))))

    # Ось 9: несуществующий путь строки — дефект указателя.
    rows = [(1, '`docs/rules/такого-правила-нет.md`', '`trading-core`')]
    _, _, _, dangling = build(rows, '.')
    axes.append(('несуществующий путь строки — дефект',
                 len(dangling) == 1, 'висячие: %r' % (dangling,)))

    # Ось 10: контроль — существующий путь висячим не считается.
    rows = [(1, '`docs/concept.md`', 'сквозное')]
    _, _, _, dangling = build(rows, '.')
    axes.append(('контроль: существующий путь висячим не считается',
                 dangling == [], 'висячие: %r' % (dangling,)))

    # Ось 11: катч-олл-каталог отличается от сплошного покрытия.
    rows = [(1, '`docs/components/`: всё остальное (оркестратор)', '`trading-core`')]
    _, blanket, _, _ = build(rows, '.')
    axes.append(('катч-олл-каталог помечается как катч-олл',
                 blanket == [('docs/components/', 1, True)], 'каталоги: %r' % (blanket,)))

    # Ось 12: основа без расширения разрешается доковым суффиксом.
    _, paths, _ = parse_left('`docs/spec/`: `statistics-aggregates`')
    axes.append(('основа перечня разрешается доковым суффиксом',
                 paths == [['docs/spec/statistics-aggregates.md',
                            'docs/spec/statistics-aggregates.json']],
                 'пути: %r' % (paths,)))

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

    map_path = os.path.join(base_dir, MAP)
    inventory_path = os.path.join(base_dir, INVENTORY)
    for path in (map_path, inventory_path):
        if not os.path.isfile(path):
            print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: нет носителя — %s' % path)
            return 2

    rows = map_rows(read(map_path))
    if not rows:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: карта не разобрана — ни одной строки')
        return 2
    known, stems = vocabulary(read(inventory_path))
    if not known:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: инвентарь не разобран — словаря владельцев нет')
        return 2

    live = carriers(base_dir)
    if not live:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: носителей `%s/**` нет вовсе — мерить нечего' % AREA)
        return 2

    named, blanket, prose_rows, dangling = build(rows, base_dir)

    unknown_owners = []
    for number, _, right in rows:
        for name in owners_of(right):
            if not known_owner(name, known, stems):
                unknown_owners.append((number, name))

    uncovered = []
    doubled = []
    on_blanket = 0
    for carrier in live:
        owners = list(named.get(carrier, []))
        if len(owners) > 1:
            doubled.append((carrier, owners))
        if owners:
            continue
        covering = [number for directory, number, _ in blanket if carrier.startswith(directory)]
        if covering:
            on_blanket += 1
            continue
        uncovered.append(carrier)

    defects = len(dangling) + len(unknown_owners) + len(uncovered) + len(doubled)
    print('строк карты: %d; носителей `%s/**`: %d; поимённо покрыто: %d; '
          'каталогом либо катч-оллом: %d; ДЕФЕКТОВ: %d'
          % (len(rows), AREA, len(live), len(live) - on_blanket - len(uncovered),
             on_blanket, defects))
    print('  строк с прозаическим кластером (носитель по ним не выводится): %d'
          % len(prose_rows))
    for number, left in prose_rows:
        print('    ПРОЗА (не дефект, названное ограничение): строка %d — %s'
              % (number, left[:90]))
    for number, path in sorted(dangling):
        print('  ВИСЯЧИЙ УКАЗАТЕЛЬ: строка %d → %s' % (number, path))
    for number, name in sorted(unknown_owners):
        print('  ВЛАДЕЛЕЦ ВНЕ СЛОВАРЯ: строка %d → `%s`' % (number, name))
    for carrier in uncovered:
        print('  НЕ ПОКРЫТ КАРТОЙ: %s' % carrier)
    for carrier, owners in doubled:
        print('  ДВА ИМЕНОВАННЫХ ВЛАДЕНИЯ: %s → строки %s'
              % (carrier, ', '.join(str(n) for n in owners)))
    return 1 if defects else 0


if __name__ == '__main__':
    sys.exit(main())

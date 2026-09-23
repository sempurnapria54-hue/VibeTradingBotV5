#!/usr/bin/env python3
"""Предмет проверки: каждый файл `.claude/rules/` явно объявляет свой ярус.

Каталог `rules/` делится на ядро, которое грузится в каждую сессию и каждого
субагента, и правила со scope, которые входят в контекст по чтению файла по
маске (решение держателя 2026-09-23, `.claude/decisions/rules-tiering.md`;
критерий яруса — `.claude/rules/structure.md`, строка `.claude/rules/`).

ПОЧЕМУ ИНСТРУМЕНТ ЗАВЕДЁН. Харнесс грузит безусловно всякий файл каталога, у
которого нет маски, — и заведённый без раздумий, и заведённый с ошибкой в
шапке. Пока ядро опознавалось отсутствием шапки, новое правило попадало в него
умолчанием, и ни один прогон этого не видел: ядро отрастало бы обратно ровно
тем путём, который разделение закрыло. Теперь ядро объявляется признаком, и
файл без объявления — дефект, а не молчаливый член ядра.

ОБЛАСТЬ. Все файлы `*.md` под `.claude/rules/`, включая подкаталоги: харнесс
читает каталог правил рекурсивно.

ПИСЬМЕННЫЕ ФОРМЫ, КОТОРЫЕ ДЕТЕКТОР ВИДИТ (каждая доказана осью батареи):
  - шапка — блок между строкой `---` в самом начале файла и следующей строкой
    `---`; окончания строк LF и CRLF;
  - признак ядра — строка `tier: core` (значение без кавычек либо в двойных
    либо одинарных кавычках);
  - ярус scope — строка `paths:` и за ней блочный YAML-список, элемент на
    строку: `  - "маска"`, `  - 'маска'` или `  - маска`.
Иных форм детектор не принимает, и это решение, а не пропуск: строчный список
`paths: [...]`, строка через запятую и прочие ключи шапки — дефект формы. Шапка
держит ровно одно объявление яруса в одной форме, чтобы его читал и харнесс, и
этот прогон.

ЧТО ДЕФЕКТ (код 1): шапки нет; шапка не закрыта; в шапке нет ни `tier`, ни
`paths`; есть оба сразу; значение `tier` не `core`; `paths:` без единой маски
либо с пустым элементом; строка шапки вне объявленных форм; повтор ключа.

ЧЕГО ПРОГОН НЕ МЕРИТ — печатается каждым прогоном. Верность яруса (правило в
ядре, которому место в scope, и наоборот) и верность маски (покрывает ли она
предмет правила) из файла не выводятся: их читает критик по критерию яруса.
Прогон печатает ядро поимённо, чтобы его рост был виден без поиска.

ОСИ ДОКАЗАНЫ БАТАРЕЕЙ, исполняемой ЭТОЙ ЖЕ командой. Код возврата 2 — «не
измерялось»: батарея не доказана, каталога нет, файлов в нём нет, файл не
читается как UTF-8.
"""
import os
import re
import sys

RULES_DIR = os.path.join('.claude', 'rules')
TIER_CORE = 'core'
TIER_LINE = re.compile(r'''^tier:\s*(?:"([^"]*)"|'([^']*)'|(\S.*?))\s*$''')
PATHS_LINE = re.compile(r'^paths:\s*(.*?)\s*$')
ITEM_LINE = re.compile(r'''^\s+-\s*(?:"([^"]*)"|'([^']*)'|(.*?))\s*$''')


def classify(text):
    """Разбор одного файла: (ярус или None, список дефектов, маски)."""
    lines = text.replace('\r\n', '\n').split('\n')
    if not lines or lines[0] != '---':
        return None, ['шапки нет: ярус не объявлен (нужен `tier: core` либо `paths:`)'], []
    try:
        end = lines.index('---', 1)
    except ValueError:
        return None, ['шапка не закрыта строкой `---`'], []

    defects = []
    tier = None
    paths = None
    seen = set()
    in_paths = False
    for number, line in enumerate(lines[1:end], 2):
        if in_paths and line.startswith((' ', '\t')):
            item = ITEM_LINE.match(line)
            if not item:
                defects.append('строка %d: элемент `paths` не в форме `  - маска`' % number)
                continue
            mask = next((g for g in item.groups() if g is not None), '')
            if not mask:
                defects.append('строка %d: пустая маска в `paths`' % number)
            else:
                paths.append(mask)
            continue
        in_paths = False
        if not line.strip():
            continue
        key = line.split(':', 1)[0].strip() if ':' in line else None
        if key in seen:
            defects.append('строка %d: ключ `%s` повторён' % (number, key))
            continue
        tier_match = TIER_LINE.match(line)
        paths_match = PATHS_LINE.match(line)
        if tier_match:
            seen.add('tier')
            tier = next(g for g in tier_match.groups() if g is not None)
            if tier != TIER_CORE:
                defects.append('строка %d: `tier: %s` — известен только `tier: %s`'
                               % (number, tier, TIER_CORE))
        elif paths_match:
            seen.add('paths')
            paths = []
            if paths_match.group(1):
                defects.append('строка %d: `paths:` в строчной форме — принят только '
                               'блочный список' % number)
            else:
                in_paths = True
        else:
            defects.append('строка %d: вне объявленных форм шапки — %r' % (number, line))

    if tier is None and paths is None:
        defects.append('в шапке нет ни `tier: core`, ни `paths:` — ярус не объявлен')
    if tier is not None and paths is not None:
        defects.append('в шапке и `tier`, и `paths` — ярус объявлен дважды')
    if paths is not None and not paths and not any('строчной форме' in d for d in defects):
        defects.append('`paths:` без единой маски')
    if defects:
        return None, defects, paths or []
    return ('ядро' if tier is not None else 'scope'), [], paths


def battery():
    """Оси детектора, доказанные падающей пробой на каждой, и контроли."""
    axes = []

    def defect(text, title):
        _, found, _ = classify(text)
        axes.append((title, bool(found), 'дефектов: %d' % len(found)))

    def clean(text, expected, title):
        got, found, _ = classify(text)
        axes.append((title, got == expected and not found,
                     'ярус: %s, дефектов: %d' % (got, len(found))))

    body = '# Правило\n\nТекст.\n'
    defect(body, 'ось 1: файл без шапки — дефект')
    defect('---\ntier: core\n' + body, 'ось 2: незакрытая шапка — дефект')
    defect('---\n---\n' + body, 'ось 3: пустая шапка — дефект')
    defect('---\ntier: core\npaths:\n  - "docs/**"\n---\n' + body,
           'ось 4: оба объявления сразу — дефект')
    defect('---\ntier: scoped\n---\n' + body, 'ось 5: неизвестное значение `tier` — дефект')
    defect('---\npaths:\n---\n' + body, 'ось 6: `paths:` без масок — дефект')
    defect('---\npaths:\n  - ""\n---\n' + body, 'ось 7: пустая маска — дефект')
    defect('---\npaths: ["docs/**"]\n---\n' + body, 'ось 8: строчный список `paths` — дефект')
    defect('---\ndescription: x\npaths:\n  - "docs/**"\n---\n' + body,
           'ось 9: чужой ключ шапки — дефект')
    defect('---\ntier: core\ntier: core\n---\n' + body, 'ось 10: повтор ключа — дефект')

    clean('---\ntier: core\n---\n' + body, 'ядро', 'контроль: `tier: core` — ядро')
    clean('---\ntier: "core"\n---\n' + body, 'ядро', 'контроль: `tier` в кавычках — ядро')
    clean('---\r\ntier: core\r\n---\r\n' + body.replace('\n', '\r\n'), 'ядро',
          'контроль: CRLF — ядро')
    clean('---\npaths:\n  - "docs/**"\n  - \'.claude/**\'\n  - tools/x.py\n---\n' + body,
          'scope', 'контроль: три формы элемента — scope')
    got, _, masks = classify('---\npaths:\n  - "a/**"\n  - b.md\n---\n' + body)
    axes.append(('контроль: маски разобраны дословно', masks == ['a/**', 'b.md'],
                 'маски: %r' % (masks,)))
    return axes


def main():
    base_dir = sys.argv[1] if len(sys.argv) > 1 else '.'
    axes = battery()
    print('--- батарея осей детектора (исполняется той же командой)')
    for title, passed, observed in axes:
        print('  %s: %s — %s' % ('доказана' if passed else 'НЕ ДОКАЗАНА', title, observed))
    if not all(passed for _, passed, _ in axes):
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: батарея не доказана — число ярусов ничего '
              'не удостоверяло бы')
        return 2

    rules_dir = os.path.join(base_dir, RULES_DIR)
    if not os.path.isdir(rules_dir):
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: каталога %s нет' % RULES_DIR)
        return 2
    files = []
    for directory, _, names in os.walk(rules_dir):
        files.extend(os.path.join(directory, n) for n in names if n.endswith('.md'))
    if not files:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: в %s нет ни одного файла `*.md`' % RULES_DIR)
        return 2

    core = []
    scoped = []
    broken = []
    for path in sorted(files):
        rel = os.path.relpath(path, base_dir).replace(os.sep, '/')
        try:
            text = open(path, encoding='utf-8').read()
        except (OSError, UnicodeDecodeError) as error:
            print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: %s не читается как UTF-8 — %s' % (rel, error))
            return 2
        tier, defects, masks = classify(text)
        if defects:
            broken.append((rel, defects))
        elif tier == 'ядро':
            core.append(rel)
        else:
            scoped.append((rel, len(masks)))

    print('файлов правил: %d; ядро: %d; scope: %d; БЕЗ ОБЪЯВЛЕННОГО ЯРУСА: %d'
          % (len(files), len(core), len(scoped), len(broken)))
    print('  ядро поимённо (грузится в каждую сессию и каждого субагента):')
    for rel in core:
        print('    %s' % rel)
    print('  не мерится (названное ограничение): верность яруса и верность маски '
          '— их читает критик по критерию `.claude/rules/structure.md`')
    for rel, defects in broken:
        for text in defects:
            print('  ЯРУС НЕ ОБЪЯВЛЕН: %s — %s' % (rel, text))
    return 1 if broken else 0


if __name__ == '__main__':
    sys.exit(main())

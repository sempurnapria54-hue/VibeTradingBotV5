#!/usr/bin/env python3
"""Предмет проверки: существование доковых файлов, на которые ссылается тело кода.

Javadoc и комментарии живых деревьев кода (`services/**`, `libs/**`, `web/**`)
адресуют доки корпуса путём — `docs/...`, `.claude/...`, `tools/...`,
`deploy/...`. Путь, которому в репозитории не соответствует файл, — битый
указатель: читатель кода уходит за обоснованием и не находит его.

ПОЧЕМУ ИНСТРУМЕНТ ЗАВЕДЁН. Класс измерялся руками дважды (курационный свип
2026-09-02 по `donor/src/**`; фокус `conventions` шага 6 фазы 2 по
`services/market-data`), и оба раза возвращался в области, которой замер не
касался: каждый следующий сервис заводит свою порцию заново. Бэклог
предсказал возврат и назвал приоритетом заведение измерения, а не третий
разбор руками (`.claude/work/backlog.md` §«Указатели javadoc в
несуществующие доки»).

ОБЛАСТЬ. Живые деревья кода. `donor/**` вне области намеренно: донор
заморожен и уходит с концом фазы 2, а его 49 указателей объявлены долгом в
том же пассаже бэклога — мерить замороженное значит держать красный прогон
на том, что не правится.

ЧТО СЧИТАЕТСЯ УКАЗАТЕЛЕМ. Путь с известным корневым сегментом и доковым
расширением (`.md`, `.json`, `.yaml`, `.yml`, `.sh`, `.py`, `.txt`),
встреченный в любой строке `.java`. §-адрес внутри файла этот детектор не
разбирает — его предмет `tools/anchor-check.py`.

ОСИ ДОКАЗАНЫ БАТАРЕЕЙ, исполняемой ЭТОЙ ЖЕ командой: команда, которая
ничего не измерила, обязана быть отличима от команды, которая измерила и не
нашла. Код возврата 2 — «не измерялось».
"""
import os
import re
import sys

ROOTS = ('services', 'libs', 'web')
DONOR = 'donor'
POINTER = re.compile(
    r'(?<![\w/.-])((?:\.claude|docs|tools|deploy|libs|services|web)/[A-Za-z0-9_./-]+'
    r'\.(?:md|json|yaml|yml|sh|py|txt))(?![\w.-])')


def pointers_in(text):
    """Все указатели строки-за-строкой: (номер строки, путь)."""
    found = []
    for number, line in enumerate(text.split('\n'), 1):
        for match in POINTER.findall(line):
            found.append((number, match))
    return found


def scan(base_dir):
    """Обход области: (число указателей, список битых, число файлов)."""
    total = 0
    broken = []
    files = 0
    for root in ROOTS:
        root_path = os.path.join(base_dir, root)
        if not os.path.isdir(root_path):
            continue
        for directory, _, names in os.walk(root_path):
            if 'target' in directory.replace(os.sep, '/').split('/'):
                continue
            for name in names:
                if not name.endswith('.java'):
                    continue
                path = os.path.join(directory, name)
                files += 1
                text = open(path, encoding='utf-8', errors='replace').read()
                for number, pointer in pointers_in(text):
                    total += 1
                    if not os.path.exists(os.path.join(base_dir, pointer)):
                        broken.append((os.path.relpath(path, base_dir).replace(os.sep, '/'),
                                       number, pointer))
    return total, broken, files


def battery():
    """Оси детектора, доказанные падающей пробой на каждой."""
    axes = []

    # Ось 1: указатель на существующий файл дефектом не считается.
    hit = pointers_in(' * (.claude/rules/codestyle.md §Слои)')
    axes.append(('видит указатель в javadoc-строке',
                 hit == [(1, '.claude/rules/codestyle.md')],
                 'разобрано: %r' % (hit,)))

    # Ось 2: несколько указателей в одной строке считаются все.
    hit = pointers_in('docs/rules/a.md и docs/rules/b.md')
    axes.append(('считает несколько указателей одной строки',
                 len(hit) == 2, 'найдено: %d' % len(hit)))

    # Ось 3: расширение вне перечня указателем не является.
    hit = pointers_in('см. docs/rules/codestyle.txtx и services/x/Foo.java')
    axes.append(('не принимает чужое расширение за указатель',
                 hit == [], 'найдено: %r' % (hit,)))

    # Ось 4: корневой сегмент вне перечня указателем не является.
    hit = pointers_in('см. vendor/rules/x.md')
    axes.append(('не принимает чужой корень за указатель',
                 hit == [], 'найдено: %r' % (hit,)))

    # Ось 5: путь внутри более длинного пути не откусывается с середины.
    hit = pointers_in('см. a/b/docs/rules/x.md')
    axes.append(('не откусывает указатель из середины чужого пути',
                 hit == [], 'найдено: %r' % (hit,)))

    # Ось 6: номер строки указателя верен.
    hit = pointers_in('первая\nвторая docs/concept.md')
    axes.append(('называет номер строки указателя',
                 hit == [(2, 'docs/concept.md')], 'разобрано: %r' % (hit,)))

    return axes


def main():
    base_dir = sys.argv[1] if len(sys.argv) > 1 else '.'
    axes = battery()
    print('--- батарея осей детектора (исполняется той же командой)')
    for title, passed, observed in axes:
        print('  %s: %s — %s' % ('доказана' if passed else 'НЕ ДОКАЗАНА', title, observed))
    undone = [title for title, passed, _ in axes if not passed]
    if undone:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: недоказанных осей %d — число указателей '
              'ничего не удостоверяло бы' % len(undone))
        return 2

    if not any(os.path.isdir(os.path.join(base_dir, root)) for root in ROOTS):
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: ни одного дерева области не существует — %s'
              % ', '.join(ROOTS))
        return 2

    total, broken, files = scan(base_dir)
    if total == 0:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: указателей в области нет вовсе '
              '(файлов обойдено: %d) — мерить нечего' % files)
        return 2

    print('файлов обойдено: %d; указателей проверено: %d; битых: %d '
          '(область: %s; `%s/**` вне области — заморожен)'
          % (files, total, len(broken), ', '.join(ROOTS), DONOR))
    for path, number, pointer in sorted(broken):
        print('  БИТЫЙ УКАЗАТЕЛЬ: %s:%d → %s' % (path, number, pointer))
    return 1 if broken else 0


if __name__ == '__main__':
    sys.exit(main())

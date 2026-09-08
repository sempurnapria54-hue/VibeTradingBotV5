# -*- coding: utf-8 -*-
"""Предмет проверки: у корпусного захода единицы работы классификация введённого
по РОДАМ полна по закрытому словарю.

ЗАЧЕМ. Обязанности полноты закрытия — популяция, свипы обоих предметов,
чек-лист глубины — пришиты к ЕДИНИЦЕ и спрашиваются У ОТЧЁТА (гейт выхода из
`GAPS_CLOSE_N`, то есть после всего). Корпус при этом меняет ЗАХОД: у узла с
доработками их до четырёх, а инструмент один. Пока единица меняет корпус один
раз, заход и отчёт совпадают и разницы не видно; на первой же доработке
инструмент описывает состояние, которое доработка уже сдвинула. Измерено на
двух узлах одного закрытия, сопоставимых по предмету: у узла, доведённого одной
партией правок, гейт выхода дал ноль красных предметов из шести, у узла с
доработкой — три, и все блокирующие корпусные находки его второй верификации
пришли от ОДНОЙ правки доработки — заведшей дом и не исполнившей ни одной
обязанности заведения.

ПРАВИЛО, КОТОРОЕ КОМАНДА ЭНФОРСИТ. Дом — `.claude/rules/edit-kind-obligations.md`:
обязанности полноты выводятся из РОДА того, что правка ввела, и несёт их заход,
меняющий корпус. Каждый такой заход проставляет классификацию по ВСЕМУ словарю
родов: род, который заход не играет, несёт исход «не играет — почему».

ФОРМА, КОТОРУЮ КОМАНДА ЧИТАЕТ:

    | Заход | Состояние |
    |---|---|
    | доработка 1 (корпусный) | `записан` — §«Доработка 1» |

и в разделе §«Доработка 1» — таблица классификации:

    | Род | Что ввёл заход | Исход |
    |---|---|---|
    | `снятие` | ... | ... |

ОСИ ПРОВЕРКИ (каждая доказана падающей пробой батареи; батарея исполняется ЭТИМ
ЖЕ прогоном):

  1. словарь родов ВЫВОДИТСЯ из таблицы дома правила, своей копии у команды
     нет; дом недостижим либо таблица в нём не разобрана — замер ОТКАЗЫВАЕТ
     (код 2). Тот же приём, что у оси 4a `self-count-check.py`;
  2. строка таблицы заходов с маркером `(корпусный)` и состоянием `записан`
     называет раздел, и раздел в том же файле СУЩЕСТВУЕТ;
  3. в названном разделе есть таблица классификации — шапка с первой колонкой
     `Род`. Строка таблицы опознаётся только С НАЧАЛА СТРОКИ: носитель,
     ВВОДЯЩИЙ форму примером, пишет её отступом кодового блока, и клеймом она
     тогда не считается — та же конвенция, что у `self-count-check.py`;
  4. множество родов таблицы классификации РАВНО словарю: род вне словаря —
     дефект (перечень закрыт), недостающий род словаря — дефект (классификация
     неполна);
  5. клетки строки классификации непусты: пустая клетка исходом не считается —
     то же требование, что у популяции.

ЧЕГО КОМАНДА НЕ МЕРИТ (названо, а не умолчано):

  * заход, изменивший корпус и маркера `(корпусный)` НЕ поставивший. Маркер
    самозаявляемый — тот же класс остатка, что у имени генератора в
    `self-count-check.py`. Мерится ОБЪЯВЛЕННОЕ; компенсирующий якорь — гейт
    выхода из `GAPS_CLOSE_N` (`.claude/skills/update-roadmap-progress.md`),
    спрашивающий классификацию у каждого корпусного захода поимённо;
  * ВЕРНОСТЬ исхода в клетке. Прогон мерит наличие исхода и принадлежность
    рода словарю; верность читает критик — та же граница, что у популяции;
  * «указатель ведёт не к тому дому» — обязанность рода `дом`. Оба адреса
    разрешаются, оба файла существуют, различие смысловое; механически не
    обнаруживается вовсе (`.claude/rules/policy-home.md` §«Заведение и перенос
    дома — не дополнение»).

ОБЛАСТЬ СВИПА. Живые отчёты единиц работы: `.claude/work/progress/**` (md).
Отчёты `history/` описывают прошлое и не размечаются
(`.claude/rules/curation.md` §«Чек-лист свипа»). Число просмотренных носителей
и найденных корпусных заходов печатает сам прогон: корпусных заходов ноль —
это напечатано, а не спрятано под зелёным кодом.

КОДЫ ВОЗВРАТА: 0 — сошлось; 1 — дефекты; 2 — измерить нечем (дом правила не
разобран, область не найдена или пуста, отказ батареи).
"""

import io
import os
import re
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

HOME_REL = os.path.join('.claude', 'rules', 'edit-kind-obligations.md')
HOME_SECTION = 'Словарь родов'
SCAN_REL = os.path.join('.claude', 'work', 'progress')

MARKER = '(корпусный)'
RECORDED = 'записан'
ADDRESS_RE = re.compile('§[«"]([^»"]+)[»"]')
HEADING_RE = re.compile(r'^(#{1,6})\s+(.+?)\s*$')


def read(path):
    with io.open(path, encoding='utf-8') as handle:
        return handle.read()


def cells(line):
    """Ячейки строки markdown-таблицы без крайних пустышек."""
    parts = line.strip().split('|')
    if parts and not parts[0].strip():
        parts = parts[1:]
    if parts and not parts[-1].strip():
        parts = parts[:-1]
    return [part.strip() for part in parts]


def is_separator(line):
    body = line.strip().strip('|').replace(' ', '')
    return bool(body) and set(body) <= set('-:|')


def tables(lines, start=0, end=None):
    """Таблицы диапазона: (индекс шапки, шапка, строки-данные)."""
    end = len(lines) if end is None else end
    found = []
    index = start
    while index < end - 1:
        line = lines[index]
        if line.startswith('|') and is_separator(lines[index + 1]):
            header = cells(line)
            rows = []
            cursor = index + 2
            while cursor < end and lines[cursor].startswith('|'):
                rows.append((cursor, cells(lines[cursor])))
                cursor += 1
            found.append((index, header, rows))
            index = cursor
        else:
            index += 1
    return found


def plain(text):
    """Ячейка без разметки: обратные кавычки, звёздочки, неразрывные пробелы."""
    return text.replace('`', '').replace('*', '').replace(' ', ' ').strip()


def sections(lines):
    """Карта «имя заголовка -> (уровень, начало тела, конец тела)»."""
    heads = []
    for index, line in enumerate(lines):
        match = HEADING_RE.match(line)
        if match:
            heads.append((index, len(match.group(1)), plain(match.group(2))))
    result = {}
    for order, (index, level, name) in enumerate(heads):
        end = len(lines)
        for next_index, next_level, _ in heads[order + 1:]:
            if next_level <= level:
                end = next_index
                break
        result.setdefault(name, (level, index + 1, end))
    return result


def parse_kinds(home_path):
    """Ось 1: словарь родов выводится из таблицы дома правила."""
    if not os.path.isfile(home_path):
        return None, 'дом правила не найден: %s' % home_path
    lines = read(home_path).splitlines()
    found = sections(lines)
    body = None
    for name, span in found.items():
        if name.startswith(HOME_SECTION):
            body = span
            break
    if body is None:
        return None, 'в доме правила нет раздела «%s»' % HOME_SECTION
    _, start, end = body
    for _, header, rows in tables(lines, start, end):
        if header and plain(header[0]) == 'Род':
            kinds = [plain(row[1][0]) for row in rows if row[1] and plain(row[1][0])]
            if kinds:
                return kinds, None
    return None, 'в разделе «%s» дома правила не разобрана таблица родов' % HOME_SECTION


def scan_file(path, rel, kinds, defects):
    lines = read(path).splitlines()
    found = sections(lines)
    passes = 0
    for _, header, rows in tables(lines):
        if not header or plain(header[0]) != 'Заход':
            continue
        for line_no, row in rows:
            if not row or MARKER not in row[0]:
                continue
            state = plain(row[-1])
            if not state.lower().startswith(RECORDED):
                continue
            passes += 1
            label = plain(row[0])
            address = ADDRESS_RE.search(row[-1])
            if not address:
                defects.append('%s:%d — корпусный заход «%s» объявлен записанным, '
                               'но раздела не называет' % (rel, line_no + 1, label))
                continue
            name = plain(address.group(1))
            if name not in found:
                defects.append('%s:%d — корпусный заход «%s» называет раздел «%s», '
                               'которого в файле нет' % (rel, line_no + 1, label, name))
                continue
            _, start, end = found[name]
            table = None
            for entry in tables(lines, start, end):
                if entry[1] and plain(entry[1][0]) == 'Род':
                    table = entry
                    break
            if table is None:
                defects.append('%s:%d — у корпусного захода «%s» в разделе «%s» нет '
                               'таблицы классификации по родам' % (rel, line_no + 1, label, name))
                continue
            declared = []
            for row_no, row_cells in table[2]:
                kind = plain(row_cells[0]) if row_cells else ''
                if not kind:
                    defects.append('%s:%d — строка классификации захода «%s» без рода'
                                   % (rel, row_no + 1, label))
                    continue
                declared.append(kind)
                if kind not in kinds:
                    defects.append('%s:%d — род «%s» у захода «%s» вне закрытого словаря'
                                   % (rel, row_no + 1, kind, label))
                if len(row_cells) < len(table[1]) or any(not plain(cell) for cell in row_cells):
                    defects.append('%s:%d — у рода «%s» захода «%s» пустая клетка: '
                                   'исходом это не считается'
                                   % (rel, row_no + 1, kind, label))
            missing = [kind for kind in kinds if kind not in declared]
            if missing:
                defects.append('%s:%d — классификация захода «%s» неполна: словарь не '
                               'покрыт родами %s' % (rel, line_no + 1, label,
                                                     ', '.join('«%s»' % kind for kind in missing)))
    return passes


def measure(root, home_path, scan_root):
    kinds, refusal = parse_kinds(home_path)
    if kinds is None:
        return 2, refusal, 0, 0, []
    if not os.path.isdir(scan_root):
        return 2, 'область не найдена: %s' % scan_root, 0, 0, []
    files = []
    for base, _, names in os.walk(scan_root):
        for name in sorted(names):
            if name.endswith('.md'):
                files.append(os.path.join(base, name))
    if not files:
        return 2, 'в области %s нет ни одного носителя — проверять нечего' % scan_root, 0, 0, []
    defects = []
    passes = 0
    for path in sorted(files):
        rel = os.path.relpath(path, root).replace(os.sep, '/')
        passes += scan_file(path, rel, kinds, defects)
    return (1 if defects else 0), None, len(files), passes, defects


# ---------------------------------------------------------------- батарея осей

HOME_TEMPLATE = """# Дом

## Словарь родов — закрытый

| Род | Признак | Дом обязанностей |
|---|---|---|
%s
"""

KINDS_PROBE = ('снятие', 'дом', 'величина')


def write(path, text):
    directory = os.path.dirname(path)
    if directory and not os.path.isdir(directory):
        os.makedirs(directory)
    with io.open(path, 'w', encoding='utf-8', newline='\n') as handle:
        handle.write(text)


def probe_home(box, kinds=KINDS_PROBE):
    rows = '\n'.join('| `%s` | признак | дом |' % kind for kind in kinds)
    path = os.path.join(box, 'home.md')
    write(path, HOME_TEMPLATE % rows)
    return path


def probe_report(box, name, body):
    path = os.path.join(box, 'scan', name)
    write(path, body)
    return path


def classification(rows, header='| Род | Что ввёл заход | Исход |\n|---|---|---|\n'):
    return header + ''.join(rows)


FULL = classification([
    '| `снятие` | редакция X | свип прогнан |\n',
    '| `дом` | не играет | дома не заводит |\n',
    '| `величина` | не играет | величин не вводит |\n',
])


def report(state_cell, section_body, section='Доработка 1'):
    return ('# Отчёт\n\n'
            '| Заход | Состояние |\n'
            '|---|---|\n'
            '| доработка 1 (корпусный) | %s |\n'
            '\n# %s\n\n%s\n' % (state_cell, section, section_body))


def battery():
    """Каждая ось — падающей пробой; контроли — на ложное срабатывание."""
    outcomes = []

    def axis(name, condition, detail):
        outcomes.append((name, condition, detail))

    box = tempfile.mkdtemp()

    # 1. словарь выводится из дома: подменённый дом меняет исход
    home = probe_home(box, ('снятие', 'дом'))
    probe_report(box, 'a.md', report('`записан` — §«Доработка 1»', classification([
        '| `снятие` | редакция X | свип прогнан |\n',
        '| `дом` | не играет | дома не заводит |\n',
    ])))
    code, _, _, _, defects = measure(box, home, os.path.join(box, 'scan'))
    axis('1. словарь выводится из дома правила, своей копии у команды нет',
         code == 0 and not defects, 'дефектов %d при словаре из двух родов' % len(defects))

    # 1a. дом без таблицы — отказ
    box2 = tempfile.mkdtemp()
    write(os.path.join(box2, 'home.md'), '# Дом\n\n## Словарь родов — закрытый\n\nтекста нет\n')
    probe_report(box2, 'a.md', report('`записан` — §«Доработка 1»', FULL))
    code, refusal, _, _, _ = measure(box2, os.path.join(box2, 'home.md'), os.path.join(box2, 'scan'))
    axis('1a. дом не разобран — проверка отказывает', code == 2, refusal or '')

    # 2. названный раздел не существует
    box3 = tempfile.mkdtemp()
    home3 = probe_home(box3)
    probe_report(box3, 'a.md', report('`записан` — §«Такого раздела нет»', FULL))
    code, _, _, passes, defects = measure(box3, home3, os.path.join(box3, 'scan'))
    axis('2. корпусный заход называет несуществующий раздел — дефект',
         code == 1 and passes == 1 and any('которого в файле нет' in d for d in defects),
         '; '.join(defects) or 'дефектов нет')

    # 3. раздел есть, таблицы классификации нет
    box4 = tempfile.mkdtemp()
    home4 = probe_home(box4)
    probe_report(box4, 'a.md', report('`записан` — §«Доработка 1»', 'правки сделаны\n'))
    code, _, _, _, defects = measure(box4, home4, os.path.join(box4, 'scan'))
    axis('3. раздела классификации нет — дефект',
         code == 1 and any('нет таблицы классификации' in d for d in defects),
         '; '.join(defects) or 'дефектов нет')

    # 4. род вне словаря
    box5 = tempfile.mkdtemp()
    home5 = probe_home(box5)
    probe_report(box5, 'a.md', report('`записан` — §«Доработка 1»', classification([
        '| `снятие` | редакция X | свип прогнан |\n',
        '| `дом` | не играет | дома не заводит |\n',
        '| `величина` | не играет | величин не вводит |\n',
        '| `выдуманный` | нечто | исход |\n',
    ])))
    code, _, _, _, defects = measure(box5, home5, os.path.join(box5, 'scan'))
    axis('4. род вне закрытого словаря — дефект',
         code == 1 and any('вне закрытого словаря' in d for d in defects),
         '; '.join(defects) or 'дефектов нет')

    # 4b. недостающий род словаря
    box6 = tempfile.mkdtemp()
    home6 = probe_home(box6)
    probe_report(box6, 'a.md', report('`записан` — §«Доработка 1»', classification([
        '| `снятие` | редакция X | свип прогнан |\n',
        '| `дом` | не играет | дома не заводит |\n',
    ])))
    code, _, _, _, defects = measure(box6, home6, os.path.join(box6, 'scan'))
    axis('4b. род словаря пропущен — классификация неполна',
         code == 1 and any('словарь не покрыт родами' in d for d in defects),
         '; '.join(defects) or 'дефектов нет')

    # 5. пустая клетка
    box7 = tempfile.mkdtemp()
    home7 = probe_home(box7)
    probe_report(box7, 'a.md', report('`записан` — §«Доработка 1»', classification([
        '| `снятие` | редакция X | свип прогнан |\n',
        '| `дом` | не играет |  |\n',
        '| `величина` | не играет | величин не вводит |\n',
    ])))
    code, _, _, _, defects = measure(box7, home7, os.path.join(box7, 'scan'))
    axis('5. пустая клетка исходом не считается',
         code == 1 and any('пустая клетка' in d for d in defects),
         '; '.join(defects) or 'дефектов нет')

    # 6. контроль: полная классификация проходит
    box8 = tempfile.mkdtemp()
    home8 = probe_home(box8)
    probe_report(box8, 'a.md', report('`записан` — §«Доработка 1»', FULL))
    code, _, _, passes, defects = measure(box8, home8, os.path.join(box8, 'scan'))
    axis('6. контроль: полная классификация дефектом не объявляется',
         code == 0 and passes == 1 and not defects,
         'корпусных заходов %d, дефектов %d' % (passes, len(defects)))

    # 7. контроль: строка без маркера не проверяется
    box9 = tempfile.mkdtemp()
    home9 = probe_home(box9)
    probe_report(box9, 'a.md', '# Отчёт\n\n| Заход | Состояние |\n|---|---|\n'
                               '| верификация 1 | `записан` — §«Доработка 1» |\n'
                               '\n# Доработка 1\n\nтекст\n')
    code, _, _, passes, defects = measure(box9, home9, os.path.join(box9, 'scan'))
    axis('7. контроль: читающий заход (без маркера) классификации не требует',
         code == 0 and passes == 0 and not defects,
         'корпусных заходов %d, дефектов %d' % (passes, len(defects)))

    # 8. контроль: состояние не «записан» — классификация не требуется
    box10 = tempfile.mkdtemp()
    home10 = probe_home(box10)
    probe_report(box10, 'a.md', report('`не начат`', 'текст\n'))
    code, _, _, passes, defects = measure(box10, home10, os.path.join(box10, 'scan'))
    axis('8. контроль: незаписанный заход классификации не требует',
         code == 0 and passes == 0 and not defects,
         'корпусных заходов %d, дефектов %d' % (passes, len(defects)))

    # 8a. контроль: таблица заходов отступом кодового блока — иллюстрация формы
    box10a = tempfile.mkdtemp()
    home10a = probe_home(box10a)
    probe_report(box10a, 'a.md',
                 '# Отчёт\n\nформа:\n\n'
                 '    | Заход | Состояние |\n    |---|---|\n'
                 '    | доработка 1 (корпусный) | `записан` — §«Нет такого» |\n')
    code, _, _, passes, defects = measure(box10a, home10a, os.path.join(box10a, 'scan'))
    axis('8a. контроль: иллюстрация формы отступом кодового блока не мерится',
         code == 0 and passes == 0 and not defects,
         'корпусных заходов %d, дефектов %d' % (passes, len(defects)))

    # 9. отказ: области нет
    box11 = tempfile.mkdtemp()
    home11 = probe_home(box11)
    code, refusal, _, _, _ = measure(box11, home11, os.path.join(box11, 'нет-такой'))
    axis('9. область не найдена — проверка отказывает', code == 2, refusal or '')

    # 10. отказ: область пуста
    box12 = tempfile.mkdtemp()
    home12 = probe_home(box12)
    empty = os.path.join(box12, 'пусто')
    os.makedirs(empty)
    code, refusal, _, _, _ = measure(box12, home12, empty)
    axis('10. область пуста — проверка отказывает', code == 2, refusal or '')

    return outcomes


def main():
    outcomes = battery()
    broken = [entry for entry in outcomes if not entry[1]]
    for name, ok, detail in outcomes:
        print('  %s: %s — %s' % ('доказана' if ok else 'ПРОВАЛЕНА', name, detail))
    if broken:
        print('БАТАРЕЯ ОТКАЗАЛА: осей провалено %d — измерение не проводилось' % len(broken))
        return 2

    home = os.path.join(ROOT, HOME_REL)
    scan = os.path.join(ROOT, SCAN_REL)
    code, refusal, files, passes, defects = measure(ROOT, home, scan)
    if code == 2:
        print('ОТКАЗ: %s' % refusal)
        return 2
    for defect in defects:
        print('  ДЕФЕКТ: %s' % defect)
    kinds, _ = parse_kinds(home)
    print('носителей просмотрено: %d; корпусных заходов найдено: %d; '
          'родов в словаре: %d; дефектов: %d' % (files, passes, len(kinds), len(defects)))
    return code


if __name__ == '__main__':
    sys.exit(main())

# -*- coding: utf-8 -*-
"""Предмет проверки: число и исход, написанные носителем о СОБСТВЕННОМ предмете,
сходятся с тем, что они описывают.

ЗАЧЕМ. Инструменты корпуса мерят ПРЕДМЕТ — спеки, области видимости, указатели
величин, адреса пассажей, снятые редакции, популяции, раскладку, доковые
указатели тела кода. Числа и описания исхода, которые закрытие пишет О СЕБЕ, не
мерил ни один. Измерено на одном узле: из тринадцати блокирующих корпусную
правку находок ПЯТЬ пришли оттуда, включая единственную, оставшуюся после второй
верификации, — и механизм у всех один: правка меняет предмет, а текст о правке
пишется рядом, глядя на ПРЕДЫДУЩЕЕ состояние предмета вместо пересчёта. Пока
предмет менялся один раз, пересказ совпадал с фактом; на первой же доработке
самоописание отстаёт на одну редакцию — незаметно, потому что механическая
часть верна и прогон зелен, а расходится проза.

ПРАВИЛО, КОТОРОЕ КОМАНДА ЭНФОРСИТ. Дом — `.claude/rules/self-description-form.md`:
у производного клейма две законные формы — ПОРОЖДЁННЫЙ БЛОК, сверяемый этим
прогоном, либо ОТСУТСТВИЕ клейма. Прозы рядом с перечнем среди законных форм нет.

ФОРМА ПОРОЖДЁННОГО БЛОКА:

    <!-- счёт: <генератор> -->
    <тело, напечатанное генератором>
    <!-- /счёт -->

Тело сверяется ДОСЛОВНО: расхождение — дефект. Рукой тело не пишут — его
печатает `py tools/self-count-check.py --emit '<генератор>'`.

Открывающая строка опознаётся только С НАЧАЛА СТРОКИ. Носитель, ВВОДЯЩИЙ форму
примером (дом правила, отчёт прохода, дайджест), пишет её отступом кодового
блока либо внутри строки — и блоком она тогда не считается. Отдельного маркера
исключения у иллюстраций поэтому нет: различает их расположение, а не
объявление.

ГЕНЕРАТОРЫ (перечень ЗАКРЫТ; неизвестный генератор ОТКАЗЫВАЕТ проверку, код 2 —
иначе опечатка в имени давала бы зелёный прогон на неизмеренной оси):

  rows:<файл>#<раздел>    счёт членов markdown-таблицы раздела: заведено (строк
                          с индексом), снято (строк с индексом «—»), живых.
  sum:<файл>#<раздел>     итог таблицы по колонкам: числовая колонка — сумма,
                          перечислительная — счёт элементов.
  passes:<префикс>        заходы мини-петли узла по именам отчётов рядом:
                          `<префикс>-critique*.md` — раунды критики,
                          `<префикс>-verification*.md` — верификации.

ОСИ ПРОВЕРКИ (каждая доказана падающей пробой батареи, батарея исполняется ЭТИМ
ЖЕ прогоном):

  1. тело порождённого блока совпадает с выводом своего генератора;
  2. генератор блока — из закрытого перечня (иначе отказ, код 2);
  3. итоговая строка таблицы (первая ячейка `Итого`) сходится с выведенным по
     колонке; колонка, у которой вывода нет, ПРОПУСКАЕТСЯ и попадает в
     печатаемый счёт пропущенных — молчаливого недомера нет;
  4a. словарь состояний ВЫВОДИТСЯ из строки «исход собственного захода»
     таблицы дома правила, своей копии у команды нет; дом не разобран —
     замер ОТКАЗЫВАЕТ (код 2). Регистр при сверке не различается;
  4. ячейка состояния в таблице заходов/узлов начинается словом из ЗАКРЫТОГО
     словаря, и слово `записан` обязано назвать разрешимый адрес раздела: исход,
     объявленный до захода, перестаёт быть выразимым. ПРИЗНАК ТАБЛИЦЫ
     МЕХАНИЧЕСКИЙ И УЗКИЙ: первая колонка названа `Заход` либо `Узел`, последняя
     — `Состояние` либо `Статус`. Шире брать нельзя: словарь состояний у радара
     технологий и у ростера свой, и общий словарь объявил бы их дефектными.
     Строка таблицы опознаётся только С НАЧАЛА СТРОКИ — та же конвенция, что у
     порождённого блока: носитель, ВВОДЯЩИЙ форму примером (дом правила, отчёт
     прохода), пишет её отступом кодового блока, и клеймом она тогда не
     считается. Различает расположение, а не объявление.

ЧЕГО КОМАНДА НЕ МЕРИТ (названо, а не умолчано). Неразмеченный счётный клейм в
прозе. Обнаружить «число + существительное перечня» в естественном языке
механически нельзя без ложных срабатываний на каждом втором абзаце. Поэтому
правило запрещает ФОРМУ, а прогон мерит ОБЪЯВЛЕННОЕ: блоки, итоговые строки,
состояния заходов. Остаток держится дисциплиной пишущего — тот же класс
остатка, что у `retired-check.py` (носитель, выражающий снятое другими словами и
в популяции не названный).

ОБЛАСТЬ СВИПА. Живые носители: `CLAUDE.md`, `README.md`, `docs/**` (md),
`.claude/**` (md) и `tools/**` (md) за вычетом архива, истории, библиотеки и
снапшотов. Отчёт закрытого узла и файл `history/` описывают ПРОШЛОЕ — их
переписывание сделало бы их ложными (`.claude/rules/curation.md` §Чек-лист).
Число просмотренных носителей печатает сам прогон.

КОДЫ ВОЗВРАТА: 0 — сошлось; 1 — дефекты; 2 — измерить нечем (неизвестный
генератор, недостижимый файл генератора, отказ батареи).
"""

import io
import os
import re
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SCAN_ROOTS = ('.claude', 'docs', 'tools')
SCAN_FILES = ('CLAUDE.md', 'README.md')
SKIP_PARTS = (
    os.sep + '.claude-archive' + os.sep,
    os.sep + 'history' + os.sep,
    os.sep + 'library' + os.sep,
    os.sep + 'snapshots' + os.sep,
)

BLOCK_OPEN = re.compile(r'^<!--\s*счёт:\s*(.+?)\s*-->\s*$')
BLOCK_CLOSE = re.compile(r'^<!--\s*/счёт\s*-->\s*$')

# Закрытый словарь состояний ЗДЕСЬ НЕ ЖИВЁТ: его дом — строка «исход
# собственного захода» таблицы правила, и команда выводит словарь оттуда
# разбором. Прежняя редакция держала копию кортежем — и копия успела стать
# ШИРЕ дома (несла `ОСТАНОВЛЕН` и `ЗАКРЫТ`, которых дом не объявлял), то есть
# пропускала состояние, которое правило запрещает. Расхождение было тихим:
# копия мерила себя. Регистр при сверке не различается — отчёты пишут
# состояние заглавными, и это форма выделения, а не другое слово.
RULE_DOC = os.path.join('.claude', 'rules', 'self-description-form.md')
STATE_ROW = 'исход собственного захода'
STATE_HEADERS = ('Состояние', 'Статус')
STATE_SUBJECTS = ('Заход', 'Узел')

ADDRESS = re.compile(r'§«([^»]+)»')
ITEM_LIST = re.compile(r'^[A-ZА-Я][\wЀ-ӿ]*\d*(\s*,\s*[A-ZА-Я][\wЀ-ӿ]*\d*)*$')


class Refusal(Exception):
    """Измерить нечем — код 2, а не «дефектов нет»."""


# ---------------------------------------------------------------- разбор


def read(path):
    with io.open(path, encoding='utf-8') as handle:
        return handle.read()


def strip_markup(cell):
    return cell.replace('**', '').replace('`', '').strip()


def section_lines(text, title):
    """Строки раздела: от заголовка с этим именем до следующего заголовка."""
    lines = text.split('\n')
    start = None
    for index, line in enumerate(lines):
        if line.startswith('#') and strip_markup(line.lstrip('#')) == strip_markup(title):
            start = index + 1
            break
    if start is None:
        raise Refusal('раздела «%s» в файле нет' % title)
    out = []
    for line in lines[start:]:
        if line.startswith('#'):
            break
        out.append(line)
    return out


def first_table(lines):
    """Первая markdown-таблица среди строк: (заголовки, строки данных)."""
    rows = []
    started = False
    for line in lines:
        stripped = line.strip()
        if stripped.startswith('|'):
            started = True
            rows.append([cell.strip() for cell in stripped.strip('|').split('|')])
        elif started and not stripped:
            if len(rows) > 2:
                break
            rows = []
            started = False
    if len(rows) < 3:
        raise Refusal('таблицы в разделе нет')
    header = [strip_markup(cell) for cell in rows[0]]
    data = [row for row in rows[2:] if not re.match(r'^-+$', row[0].strip('| '))]
    return header, data


def is_total(row):
    return strip_markup(row[0]).lower().startswith(('итого', 'всего'))


# ------------------------------------------------------------ генераторы


def gen_rows(argument):
    path, _, title = argument.partition('#')
    full = os.path.join(ROOT, path.strip())
    if not os.path.isfile(full):
        raise Refusal('файла «%s» нет' % path.strip())
    header, data = first_table(section_lines(read(full), title.strip()))
    members = [row for row in data if not is_total(row)]
    retired = [row for row in members if strip_markup(row[0]) in ('—', '-')]
    established = len(members) - len(retired)
    return 'Членов перечня: заведено — %d, снято — %d, живых — %d.' % (
        established, len(retired), established - len(retired))


def column_total(cells):
    """Вывод по колонке: сумма чисел либо счёт элементов перечня. Иначе None."""
    plain = [strip_markup(cell) for cell in cells]
    if plain and all(re.match(r'^\d+$', cell) for cell in plain):
        return sum(int(cell) for cell in plain)
    if plain and all(ITEM_LIST.match(cell) for cell in plain):
        return sum(len([item for item in cell.split(',') if item.strip()]) for cell in plain)
    return None


def gen_sum(argument):
    path, _, title = argument.partition('#')
    full = os.path.join(ROOT, path.strip())
    if not os.path.isfile(full):
        raise Refusal('файла «%s» нет' % path.strip())
    header, data = first_table(section_lines(read(full), title.strip()))
    members = [row for row in data if not is_total(row)]
    parts = []
    for index in range(1, len(header)):
        cells = [row[index] for row in members if index < len(row)]
        total = column_total(cells)
        if total is not None:
            parts.append('«%s» — %d' % (header[index], total))
    if not parts:
        raise Refusal('ни одна колонка таблицы не выводима')
    return 'Итог таблицы: %s.' % ', '.join(parts)


def gen_passes(argument):
    prefix = argument.strip()
    folder = os.path.join(ROOT, os.path.dirname(prefix))
    if not os.path.isdir(folder):
        raise Refusal('папки «%s» нет' % os.path.dirname(prefix))
    base = os.path.basename(prefix)
    names = os.listdir(folder)
    rounds = [name for name in names
              if name.startswith(base + '-critique') and name.endswith('.md')]
    checks = [name for name in names
              if name.startswith(base + '-verification') and name.endswith('.md')]
    if not rounds and not checks:
        raise Refusal('отчётов заходов с префиксом «%s» нет' % base)
    return 'Заходов узла: раундов критики — %d, верификаций — %d.' % (
        len(rounds), len(checks))


GENERATORS = {'rows': gen_rows, 'sum': gen_sum, 'passes': gen_passes}


def emit(spec):
    kind, _, argument = spec.partition(':')
    if kind not in GENERATORS:
        raise Refusal('генератор «%s» не объявлен' % kind)
    return GENERATORS[kind](argument)


# --------------------------------------------------------------- свипы


def carriers(root):
    found = []
    for name in SCAN_FILES:
        path = os.path.join(root, name)
        if os.path.isfile(path):
            found.append(path)
    for top in SCAN_ROOTS:
        for folder, dirs, files in os.walk(os.path.join(root, top)):
            dirs[:] = [d for d in dirs if not d.startswith('.git')]
            if any(part in folder + os.sep for part in SKIP_PARTS):
                continue
            for name in files:
                if name.endswith('.md'):
                    found.append(os.path.join(folder, name))
    return sorted(set(found))


def check_blocks(path, text, defects):
    """Ось 1-2: тело блока равно выводу генератора; генератор объявлен."""
    lines = text.split('\n')
    seen = 0
    index = 0
    while index < len(lines):
        opened = BLOCK_OPEN.match(lines[index])
        if not opened:
            index += 1
            continue
        spec = opened.group(1)
        body = []
        cursor = index + 1
        while cursor < len(lines) and not BLOCK_CLOSE.match(lines[cursor]):
            body.append(lines[cursor])
            cursor += 1
        if cursor >= len(lines):
            raise Refusal('%s:%d — блок «%s» не закрыт' % (path, index + 1, spec))
        seen += 1
        produced = emit(spec)
        written = '\n'.join(body).strip()
        if written != produced:
            defects.append('%s:%d — тело блока «%s» разошлось с выводом.\n'
                           '        написано: %s\n        выведено: %s'
                           % (path, index + 1, spec, written, produced))
        index = cursor + 1
    return seen


def check_totals(path, text, defects):
    """Ось 3: итоговая строка сходится с выведенным по колонке."""
    seen = skipped = 0
    lines = text.split('\n')
    table = []
    for line in lines + ['']:
        stripped = line.strip()
        if stripped.startswith('|'):
            table.append([cell.strip() for cell in stripped.strip('|').split('|')])
            continue
        if len(table) > 2:
            header = [strip_markup(cell) for cell in table[0]]
            data = [row for row in table[2:]]
            totals = [row for row in data if is_total(row)]
            members = [row for row in data if not is_total(row)]
            for total in totals:
                seen += 1
                for index in range(1, min(len(header), len(total))):
                    written = strip_markup(total[index])
                    if not re.match(r'^\d+$', written):
                        skipped += 1
                        continue
                    cells = [row[index] for row in members if index < len(row)]
                    derived = column_total(cells)
                    if derived is None:
                        skipped += 1
                        continue
                    if int(written) != derived:
                        defects.append(
                            '%s — итог колонки «%s»: написано %s, выведено %d'
                            % (path, header[index], written, derived))
        table = []
    return seen, skipped


def headings_and_leads(text):
    names = set()
    for line in text.split('\n'):
        if line.startswith('#'):
            names.add(strip_markup(line.lstrip('#')))
        lead = re.match(r'^[-*]?\s*\*\*(.+?)\*\*', line)
        if lead:
            names.add(strip_markup(lead.group(1)))
    return names


def state_words(root):
    """Закрытый словарь состояний — из строки таблицы дома правила.

    Копии здесь нет намеренно: перечень, скопированный рядом с измерителем,
    делает измеряемым себя, а не дом. Отказ (код 2), если строка не найдена
    или слов в ней нет: молчаливое «дефектов 0» на невыведенном словаре
    означало бы, что ось 4 не мерила ничего.
    """
    full = os.path.join(root, RULE_DOC)
    if not os.path.isfile(full):
        raise Refusal('нет дома словаря состояний %s' % RULE_DOC)
    with io.open(full, encoding='utf-8') as handle:
        for line in handle:
            if not line.startswith('|') or STATE_ROW not in line:
                continue
            group = re.search(r'закрытого словаря\s*\(([^)]*)\)', line)
            if not group:
                break
            words = [word.lower() for word in re.findall(r'`([^`]+)`', group.group(1))]
            if not words:
                break
            return tuple(words)
    raise Refusal('в %s не разобрана строка «%s» — словарь состояний выводить '
                  'не из чего' % (RULE_DOC, STATE_ROW))


def check_states(path, text, defects, state_words):
    """Ось 4: слово состояния из закрытого словаря; `записан` называет раздел."""
    seen = 0
    names = headings_and_leads(text)
    lines = text.split('\n')
    table = []
    for line in lines + ['']:
        stripped = line.strip()
        if line.startswith('|'):
            table.append([cell.strip() for cell in stripped.strip('|').split('|')])
            continue
        if len(table) > 2:
            header = [strip_markup(cell) for cell in table[0]]
            if (header and header[-1] in STATE_HEADERS
                    and header[0] in STATE_SUBJECTS):
                for row in table[2:]:
                    if len(row) < len(header) or is_total(row):
                        continue
                    cell = row[len(header) - 1]
                    plain = strip_markup(cell)
                    lowered = plain.lower()
                    word = next((w for w in state_words
                                 if lowered.startswith(w)), None)
                    seen += 1
                    if word is None:
                        defects.append(
                            '%s — состояние «%s» вне закрытого словаря'
                            % (path, plain[:50]))
                        continue
                    if word == 'записан':
                        address = ADDRESS.search(plain)
                        if not address:
                            defects.append(
                                '%s — состояние `записан` не называет раздела: «%s»'
                                % (path, plain[:60]))
                        elif address.group(1) not in names:
                            defects.append(
                                '%s — состояние `записан` называет раздел «%s», которого в файле нет'
                                % (path, address.group(1)))
        table = []
    return seen


def scan(root):
    defects = []
    blocks = totals = skipped = states = 0
    words = state_words(root)
    files = carriers(root)
    for path in files:
        text = read(path)
        relative = os.path.relpath(path, root).replace(os.sep, '/')
        blocks += check_blocks(relative, text, defects)
        seen_totals, seen_skipped = check_totals(relative, text, defects)
        totals += seen_totals
        skipped += seen_skipped
        states += check_states(relative, text, defects, words)
    return files, blocks, totals, skipped, states, defects


# -------------------------------------------------------------- батарея


def battery():
    """Падающая проба на каждую объявленную ось плюс контроли на ложное."""
    failures = []
    seen = {'падающих': 0, 'контролей': 0}
    holder = tempfile.mkdtemp(prefix='self-count-')
    docs = os.path.join(holder, 'docs')
    os.makedirs(docs)

    # Дом словаря состояний в песочнице — свой, минимальный: команда выводит
    # словарь из него, а не из копии, и проба это доказывает.
    rule = os.path.join(holder, os.path.dirname(RULE_DOC))
    os.makedirs(rule)

    def home(words):
        with io.open(os.path.join(holder, RULE_DOC), 'w', encoding='utf-8') as handle:
            handle.write('| %s | слово из закрытого словаря (%s) |\n'
                         % (STATE_ROW, ' / '.join('`%s`' % w for w in words)))

    home(('не начат', 'записан'))

    def page(name, body):
        path = os.path.join(docs, name)
        with io.open(path, 'w', encoding='utf-8') as handle:
            handle.write(body)
        return path

    table = ('## Перечень\n\n| # | Что |\n|---|---|\n| 1 | раз |\n| 2 | два |\n'
             '| — | снята первая |\n')

    def probe(name, body, axis, must_fail):
        seen['падающих' if must_fail else 'контролей'] += 1
        page(name, body)
        saved = globals()['ROOT']
        globals()['ROOT'] = holder
        try:
            _, _, _, _, _, defects = scan(holder)
        except Refusal as refusal:
            defects = ['отказ: %s' % refusal]
        finally:
            globals()['ROOT'] = saved
        caught = bool(defects)
        if caught != must_fail:
            failures.append('ось %s: проба «%s» %s' % (
                axis, name, 'не поймана' if must_fail else 'ложно сработала'))
        os.remove(os.path.join(docs, name))

    # Ось 1 — тело блока разошлось с выводом.
    probe('axis1.md', table + '\n<!-- счёт: rows:docs/axis1.md#Перечень -->\n'
          'Членов перечня: заведено — 9, снято — 0, живых — 9.\n<!-- /счёт -->\n',
          1, True)
    # Ось 1 — контроль: верное тело не срабатывает.
    probe('axis1ok.md', table + '\n<!-- счёт: rows:docs/axis1ok.md#Перечень -->\n'
          'Членов перечня: заведено — 2, снято — 1, живых — 1.\n<!-- /счёт -->\n',
          1, False)
    # Ось 2 — неизвестный генератор отказывает.
    probe('axis2.md', '<!-- счёт: выдумка:что-нибудь -->\nтело\n<!-- /счёт -->\n',
          2, True)
    # Ось 3 — итоговая строка разошлась с суммой колонки.
    probe('axis3.md', '## И\n\n| Узел | Гейтящих |\n|---|---|\n| 1 | 4 |\n'
          '| 2 | 1 |\n| **Итого** | **9** |\n', 3, True)
    # Ось 3 — контроль: верный итог не срабатывает.
    probe('axis3ok.md', '## И\n\n| Узел | Гейтящих |\n|---|---|\n| 1 | 4 |\n'
          '| 2 | 1 |\n| **Итого** | **5** |\n', 3, False)
    # Ось 3 — контроль: колонка без вывода пропускается, а не объявляется дефектом.
    probe('axis3skip.md', '## И\n\n| Узел | Что |\n|---|---|\n| 1 | проза раз |\n'
          '| **Итого** | 7 |\n', 3, False)
    # Ось 4 — состояние вне словаря.
    probe('axis4.md', '## З\n\n| Заход | Состояние |\n|---|---|\n| 1 | почти готов |\n',
          4, True)
    # Ось 4 — `записан` называет несуществующий раздел.
    probe('axis4b.md', '## З\n\n| Заход | Состояние |\n|---|---|\n'
          '| 1 | `записан` — §«Заход 1. Нечто» |\n', 4, True)
    # Ось 4 — контроль: `записан` с существующим разделом и `не начат` молчат.
    probe('axis4ok.md', '## З\n\n| Заход | Состояние |\n|---|---|\n'
          '| 1 | `записан` — §«Заход 1. Нечто» |\n| 2 | `не начат` |\n\n'
          '## Заход 1. Нечто\n\nтело\n', 4, False)

    # Ось 4 — контроль: та же таблица ОТСТУПОМ кодового блока — иллюстрация
    # формы, а не клейм; различает расположение, а не объявление.
    probe('axis4ill.md', '## З\n\nформа:\n\n'
          '    | Заход | Состояние |\n    |---|---|\n'
          '    | 1 | `записан` — §«Заход 1. Нечто» |\n', 4, False)

    # Ось 4a — словарь ВЫВЕДЕН из дома, а не скопирован: слово, которого дом
    # не объявляет, ловится, хотя прежняя копия его пропускала.
    home(('не начат', 'записан'))
    probe('axis4c.md', '## З\n\n| Заход | Состояние |\n|---|---|\n| 1 | остановлен |\n',
          '4a', True)
    # Ось 4a — контроль: дом объявил слово — оно принимается, регистр не
    # различается (отчёты пишут состояние заглавными).
    home(('не начат', 'записан', 'остановлен'))
    probe('axis4d.md', '## З\n\n| Заход | Состояние |\n|---|---|\n| 1 | **ОСТАНОВЛЕН** — так |\n',
          '4a', False)
    # Ось 4b — дом словаря не разобран: замер ОТКАЗЫВАЕТ, а не молчит.
    seen['падающих'] += 1
    os.remove(os.path.join(holder, RULE_DOC))
    saved = globals()['ROOT']
    globals()['ROOT'] = holder
    try:
        scan(holder)
        failures.append('ось 4b: дом словаря снят, а проверка отчиталась')
    except Refusal:
        pass
    finally:
        globals()['ROOT'] = saved
    home(('не начат', 'записан', 'остановлен', 'закрыт'))

    for name in os.listdir(docs):
        os.remove(os.path.join(docs, name))
    os.rmdir(docs)
    os.remove(os.path.join(holder, RULE_DOC))
    os.rmdir(rule)
    os.rmdir(os.path.join(holder, '.claude'))
    os.rmdir(holder)
    return failures, seen


# ----------------------------------------------------------------- main


def main():
    if len(sys.argv) > 2 and sys.argv[1] == '--emit':
        try:
            sys.stdout.write(emit(sys.argv[2]) + '\n')
        except Refusal as refusal:
            sys.stderr.write('ОТКАЗ: %s\n' % refusal)
            return 2
        return 0

    failures, seen = battery()
    if failures:
        sys.stderr.write('БАТАРЕЯ НЕ ПРОШЛА — мерить нечем:\n')
        for failure in failures:
            sys.stderr.write('  %s\n' % failure)
        return 2
    # Счёт проб выводится из перечня проб, а не пишется рядом с ним: команда,
    # энфорсящая правило самоописания, ему же и подчиняется.
    sys.stdout.write('Батарея: проб — %d (падающих — %d, контролей — %d) — прошла.\n'
                     % (seen['падающих'] + seen['контролей'],
                        seen['падающих'], seen['контролей']))

    try:
        files, blocks, totals, skipped, states, defects = scan(ROOT)
    except Refusal as refusal:
        sys.stderr.write('ОТКАЗ: %s\n' % refusal)
        return 2

    sys.stdout.write(
        'Носителей просмотрено: %d. Порождённых блоков: %d. Итоговых строк: %d '
        '(колонок пропущено — %d). Ячеек состояния: %d.\n'
        % (len(files), blocks, totals, skipped, states))
    if defects:
        sys.stderr.write('Дефектов самоописания: %d\n' % len(defects))
        for defect in defects:
            sys.stderr.write('  %s\n' % defect)
        return 1
    sys.stdout.write('Дефектов самоописания: 0.\n')
    return 0


if __name__ == '__main__':
    sys.exit(main())

# -*- coding: utf-8 -*-
"""Предмет проверки: команда, которую живой отчёт объявляет воспроизводимой,
ВОСПРОИЗВОДИМА — она не стоит в клетке markdown-таблицы (оси 1-4), её
-P-шаблон не разорван переносом строки внутри аргумента (оси 7-8) и всякое имя,
от которого она зависит, связано в самом блоке (ось 9).

ПРЕДМЕТ РАСШИРЕН 2026-09-08, и снятая граница названа. Прежняя редакция шапки
объявляла предметом только НОСИТЕЛЯ команды («не стоит в клетке») и относила
перенос строки к неизмеряемому, с доводом «форм у этого класса больше одной;
мерится та, что даёт РАЗРЕШАЮЩИЙ отказ и встречена». Довод сохранён, а его
посылка опровергнута замером: форма с переносом ВСТРЕЧЕНА (находка П1 пятого
прогона гейта выхода из GAPS_CLOSE_4 шага 10 фазы 2) и отказ у неё тоже
РАЗРЕШАЮЩИЙ — не на прогоне, а на ЧТЕНИИ. grep отвечает кодом 2, но читатель
отчёта видит объявленную исполненной команду и засчитывает свип; ровно так
строка чек-листа «потребители обойдены двумя формами — да» простояла зелёной
у единицы, которая обошла их одной.

ЗАЧЕМ. Клетка таблицы сырого столбового знака не несёт, поэтому альтернация
шаблона записывается в ней экранированной. В PCRE экранированная черта —
ЛИТЕРАЛЬНАЯ вертикальная черта, а не альтернация: команда, скопированная из
клетки дословно, ищет одну длинную строку с чертой внутри, не находит ничего и
возвращает код 1. Отказ неотличим от чистого исхода: пустой вывод свипа читается
как «живых носителей снятой редакции нет» — ровно тот результат, который свип и
обязан доказывать. Ошибка поэтому РАЗРЕШАЮЩАЯ: она не роняет прогон, а
подтверждает то, чего не проверяла.

Измерено: первый прогон гейта выхода из GAPS_CLOSE_4 шага 10 фазы 2 признал узел
зелёным по этому предмету — он команды ЧИТАЛ, а не исполнял. Класс нашёлся
только тогда, когда следующий прогон их запустил, и поражены оказались все
единицы, ставившие команду в таблицу; не поражена была та, чьи команды стояли
блоками кода.

ПРАВИЛО, КОТОРОЕ КОМАНДА ЭНФОРСИТ. Дом —
`.claude/processes/roadmap-step-execution.md`, пассаж «Команда, объявленная
воспроизводимой, стоит блоком кода, а не клеткой таблицы»: команда — блоком
кода, в клетке остаётся исход.

ОСИ ПРОВЕРКИ (каждая доказана падающей пробой батареи; батарея исполняется ЭТИМ
ЖЕ прогоном). Номера 5 и 6 в перечне ниже не пропущены — это оси ОТКАЗА
(область не найдена, область пуста), и они живут пробами батареи:

  1. строка markdown-таблицы, несущая в обратных кавычках команду поиска с
     экранированной альтернацией внутри, — дефект;
  2. строка опознаётся только С НАЧАЛА СТРОКИ: носитель, ВВОДЯЩИЙ форму
     примером, пишет её отступом кодового блока, и дефектом она тогда не
     считается — та же конвенция, что у `self-count-check.py` и
     `edit-kind-check.py`;
  3. объявленный долг (`tools/command-carrier-debt.txt`, строка
     «носитель<TAB>начало команды») дефектом не считается, но строка долга,
     которой в корпусе БОЛЬШЕ НЕТ, — дефект: долг не консервируется. Механика и
     гранулярность те же, что у `anchor-debt.txt`: долг адресует КЛЕТКУ, а не
     файл, — иначе новая дефектная клетка в носителе с объявленным долгом
     поглощалась бы молча. Долг адресует и КЛЕТКУ (ось 1), и СТРОКУ БЛОКА
     (оси 7, 9): гранулярность одна — «носитель + начало команды», — и
     накопленное осью 9 объявлено ею же при вводе. Устаревшего маркера (ось 8)
     долг не покрывает: он не команда, а исключение, и консервировать его
     нечем;
  4. область — живые отчёты `.claude/work/progress/**`; `history/` описывает
     прошлое и не правится (`.claude/rules/curation.md`, чек-лист свипа).
     Область выбрана ЗАМЕРОМ, а не умолчанием: тот же предикат по всему корпусу
     (docs/**, .claude/**, tools/**) даёт вхождения ТОЛЬКО в отчётах — живых и
     архивных, — и ни одного в правилах, скиллах, решениях или продуктовых
     доках. Расширять область на носитель, где класс не встречен, значило бы
     писать норму на воображаемый случай (`.claude/rules/design-simplicity.md`);
     условие возврата — первое вхождение вне отчётов;

  7. строка ВНУТРИ ограждённого блока, объявляющая -P-команду grep и несущая
     НЕЧЁТНОЕ число одиночных кавычек, — дефект: аргумент-шаблон продолжается
     на следующей строке, то есть внутри него стоит настоящий перенос, grep
     читает его как ВТОРОЙ шаблон и отвечает «-P supports only a single
     pattern» с кодом 2. Признак статический: исполнять блоки markdown команда
     не вправе — это запускало бы произвольный код из носителя знания;
  8. объявленное исключение — строка-маркер `<!-- command-carrier: <чем> -->`
     ПЕРЕД ограждённым блоком: блок тогда дефектом не считается. Маркер, перед
     которым дефектного блока нет, — сам дефект: исключение не консервируется,
     механика та же, что у строки долга (ось 3). Исключённые блоки прогон
     печатает ПОИМЁННО — молчаливого пропуска нет (тот же приём у
     `anchor-check.py`). Маркер накрывает блок ЦЕЛИКОМ — и ось 7, и ось 9:
     носитель, предъявляющий дефектную команду, предъявляет её как есть;

  9. строка ВНУТРИ ограждённого блока, читающая `$ИМЯ` либо `${ИМЯ}`, которое ни
     одна строка ТОГО ЖЕ блока не связывает и которого нет в объявленном перечне
     KNOWN_NAMES, — дефект. Признак статический, как и у оси 7.

ЗАЧЕМ ОСЬ 9. Отказ здесь ХУЖЕ, чем у осей 1 и 7, и это довод, на котором она
введена: он НЕВИДИМ. Пустого шаблона у `grep` не бывает — `grep -c ""` совпадает
с КАЖДОЙ строкой файла, — поэтому блок, читающий несвязанное имя, отвечает кодом
0 и правдоподобным числом. Оси 1 и 7 дают хотя бы код 1 и код 2; здесь прогон
зелен, а измерено не то, что написано. Класс встречен (находка Ш2 шестого
прогона гейта выхода из GAPS_CLOSE_4 шага 10 фазы 2) и не единично: сверх неё
проба дала строки двух отчётов верификации узла 1, читающие имя, связанное
только прозой над блоком; они объявлены долгом. Счёт их здесь не пишется — его
печатает сам прогон.

ЛОЖНЫЕ СРАБАТЫВАНИЯ ОСИ 9 ПЕРЕЧИСЛИМЫ, и каждое снято замером по живому
корпусу, а не рассуждением:

  * связывание в теле функции (`measure() { d="$1"`) и связывание с отступом
    внутри `do` — BIND_RE берёт имя после начала строки с отступом, после
    `;`/`&`/`|`/`(`/`{` и после `do`/`then`/`else`/`export`/`local`/`declare`;
  * связывание циклом и чтением — FORIN_RE (`for ИМЯ in`) и READ_RE
    (`read ИМЯ …`);
  * вставленная в блок ВЫДАЧА, а не команда (`group-id: bff-${random.uuid}` в
    напечатанном ответе `grep`): мерятся только строки, несущие токен известной
    команды (CMDLINE_RE). Строка вывода такого токена не несёт.

Позиционные параметры (`$1`, `$?`, `$@`) осью не мерятся по построению: REF_RE
берёт только имена, начинающиеся с буквы или подчёркивания.

ЗАЧЕМ МАРКЕР. Носитель, ПРЕДЪЯВЛЯЮЩИЙ дефектную команду (раздел находки), обязан
воспроизвести её дословно — иначе дефекта не предъявить, — и правка сделала бы
такой блок ложным. Прозаической фразы «это цитата» машине недостаточно: её
читает человек и не читает прогон.

ЧЕГО КОМАНДА НЕ МЕРИТ (названо, а не умолчано):

  * команду БЕЗ экранированной альтернации в клетке. Такая клетка
    воспроизводима дословно, и дефекта в ней нет: правило адресует
    невоспроизводимость, а не расположение само по себе;
  * ИСПОЛНИМОСТЬ команды как таковую. Оси 7 и 9 мерят ДВЕ формы
    невоспроизводимости блока — разрыв -P-шаблона переносом и несвязанное имя, —
    обе встреченные и статически различимые. Прочие причины отказа (усечение
    многоточием, обратные кавычки в теле, несуществующий путь, отсутствующий
    инструмент) остаются на прогоне отчёта: ловить их значило бы ИСПОЛНЯТЬ
    блоки markdown. Названный остаток, а не забытый; условие возврата — третья
    встреченная форма, различимая без исполнения;
  * ВЕРНОСТЬ команды и её вывода — это читает критик, как и у популяции.

КОДЫ ВОЗВРАТА: 0 — сошлось; 1 — дефекты; 2 — измерить нечем (область не найдена
или пуста, отказ батареи).
"""

import io
import os
import re
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SCAN_REL = os.path.join('.claude', 'work', 'progress')
DEBT_REL = os.path.join('tools', 'command-carrier-debt.txt')

BS = chr(92)
BAR = chr(124)
TICK = chr(96)
ESCAPED_BAR = BS + BAR
CODE_RE = re.compile(TICK + '([^' + TICK + ']*)' + TICK)
CMD_RE = re.compile(r'\b(grep|rg|sed|awk|py|python3?)\b')

QUOTE = chr(39)
FENCE = TICK * 3
# -P-команда grep: имя, затем флаг, чья буквенная гроздь кончается на P.
GREP_P_RE = re.compile(r'\bgrep\b[^' + QUOTE + r'"]*?-[A-Za-z]*P\b')
MARKER_RE = re.compile(r'^<!--\s*command-carrier:\s*(.+?)\s*-->\s*$')

# Ось 9. Чтение имени, его связывание и перечень имён, которые связывать не надо.
DOLLAR = chr(36)
REF_RE = re.compile(re.escape(DOLLAR) + r'\{?([A-Za-z_][A-Za-z0-9_]*)\}?')
BIND_RE = re.compile(r'(?:^\s*|[;&' + BAR + r'({]\s*|\bdo\s+|\bthen\s+|\belse\s+'
                     r'|\bexport\s+|\blocal\s+|\bdeclare\s+)'
                     r'([A-Za-z_][A-Za-z0-9_]*)=')
FORIN_RE = re.compile(r'\bfor\s+([A-Za-z_][A-Za-z0-9_]*)\s+in\b')
READ_RE = re.compile(r'\bread\s+((?:[A-Za-z_][A-Za-z0-9_]*\s+)*[A-Za-z_][A-Za-z0-9_]*)')
# Строка мерится осью 9, только если похожа на КОМАНДУ: вставленная в блок выдача
# командного токена не несёт, и без этого условия она давала бы ложный дефект.
CMDLINE_RE = re.compile(r'\b(grep|rg|sed|awk|py|python3?|git|ls|echo|cat|wc|sort'
                        r'|uniq|tr|head|tail|bash|sh|for|while|if|test|env|find'
                        r'|diff|comm|xargs|printf|mvn|bats)\b')
# Перечень ЗАКРЫТ и объявлен: имена окружения и встроенные имена awk, которые
# блок связывать не обязан. Имя вне перечня и вне блока — дефект.
KNOWN_NAMES = frozenset('''
HOME PWD OLDPWD PATH USER SHELL LANG LC_ALL LC_CTYPE IFS TMPDIR TMP TEMP
RANDOM SECONDS LINENO BASH_SOURCE FUNCNAME PS1 PS2 EDITOR JAVA_HOME MAVEN_OPTS
NF NR FS OFS ORS RS FILENAME SUBSEP RSTART RLENGTH CONVFMT OFMT ENVIRON
'''.split())


def evaluate_block(rel, block):
    """Оси 7 и 9 на одном ограждённом блоке: [(rel, номер, род, начало строки)].

    Ось 7 — -P-команда с нечётным числом одиночных кавычек: аргумент-шаблон
    продолжается на следующей строке. Ось 9 — чтение имени, которое ни одна
    строка ТОГО ЖЕ блока не связывает.
    """
    bound = set()
    for _, line in block:
        bound.update(BIND_RE.findall(line))
        bound.update(FORIN_RE.findall(line))
        for group in READ_RE.findall(line):
            bound.update(group.split())
    found = []
    for number, line in block:
        if GREP_P_RE.search(line) and line.count(QUOTE) % 2 == 1:
            found.append((rel, number, 'break', line.strip()[:70]))
        if not CMDLINE_RE.search(line):
            continue
        for name in REF_RE.findall(line):
            if name in bound or name in KNOWN_NAMES:
                continue
            found.append((rel, number, 'unbound', line.strip()[:70]))
            break
    return found


def scan_blocks(path, rel):
    """Оси 7-9: (дефекты, исключённые блоки).

    Блок читается ЦЕЛИКОМ и оценивается на закрывающей ограде: связывание имени
    законно и НИЖЕ его чтения не бывает только у человека, а не у оболочки —
    зато маркер исключения (ось 8) обязан накрывать блок целиком, по обеим осям
    сразу, и посточечная оценка этого не дала бы.
    """
    defects = []
    exempt = []
    in_fence = False
    pending = None
    fence_mark = None
    fence_line = 0
    block = []
    for number, line in enumerate(read(path).split('\n'), 1):
        if line.startswith(FENCE):
            if in_fence:
                in_fence = False
                found = evaluate_block(rel, block)
                if fence_mark:
                    if found:
                        exempt.append((rel, fence_line, fence_mark[1]))
                    else:
                        defects.append(
                            (rel, fence_mark[0], 'marker',
                             'маркер исключения, перед которым нет дефектного '
                             'блока: %s' % fence_mark[1][:50]))
                else:
                    defects.extend(found)
                fence_mark = None
            else:
                in_fence = True
                fence_mark = pending
                fence_line = number
                pending = None
                block = []
            continue
        if not in_fence:
            marker = MARKER_RE.match(line)
            if marker:
                pending = (number, marker.group(1))
            elif line.strip():
                pending = None
            continue
        block.append((number, line))
    return defects, exempt


def read(path):
    with io.open(path, encoding='utf-8') as handle:
        return handle.read()


def write(path, text):
    directory = os.path.dirname(path)
    if directory and not os.path.isdir(directory):
        os.makedirs(directory)
    with io.open(path, 'w', encoding='utf-8', newline='\n') as handle:
        handle.write(text)


def scan_file(path, rel):
    """Клетки-нарушители файла: (rel, номер строки, начало команды)."""
    found = []
    for number, line in enumerate(read(path).split('\n'), 1):
        if not line.startswith(BAR):
            continue
        for match in CODE_RE.finditer(line):
            body = match.group(1)
            if ESCAPED_BAR not in body:
                continue
            if not CMD_RE.search(body):
                continue
            found.append((rel, number, body.strip()[:70]))
    return found


def collect(scan):
    if not os.path.isdir(scan):
        return None, 'область не найдена: %s' % scan
    hits = []
    broken = []
    exempt = []
    files = 0
    for base, _, names in os.walk(scan):
        for name in sorted(names):
            if not name.endswith('.md'):
                continue
            files += 1
            path = os.path.join(base, name)
            rel = os.path.relpath(path, scan).replace(os.sep, '/')
            hits.extend(scan_file(path, rel))
            block_defects, block_exempt = scan_blocks(path, rel)
            broken.extend(block_defects)
            exempt.extend(block_exempt)
    if not files:
        return None, 'область пуста: носителей .md нет в %s' % scan
    return (files, hits, broken, exempt), None


def load_debt(path):
    """Объявленный долг: пары (носитель, начало команды). Номеров строк нет
    намеренно — номер дрейфует от любой правки, и реестр начал бы врать раньше,
    чем долг погасится (тот же приём, что у `anchor-debt.txt`)."""
    if not os.path.isfile(path):
        return set()
    declared = set()
    for line in read(path).split('\n'):
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        parts = line.split('\t')
        if len(parts) < 2:
            continue
        declared.add((parts[0].strip(), parts[1].strip()))
    return declared


def measure(scan, debt_path):
    payload, refusal = collect(scan)
    if refusal:
        return 2, refusal, 0, [], [], []
    files, hits, broken, exempt = payload
    declared = load_debt(debt_path)
    live = set((rel, body) for rel, _, body in hits)
    live |= set((rel, body) for rel, _, kind, body in broken if kind != 'marker')
    defects = []
    for rel, number, body in sorted(hits):
        if (rel, body) in declared:
            continue
        defects.append('%s:%d — команда в клетке таблицы: %s' % (rel, number, body))
    for rel, body in sorted(declared - live):
        defects.append('%s\t%s — строка долга, которой в корпусе больше нет'
                       % (rel, body))
    wording = {'break': '-P-шаблон разорван переносом строки',
               'unbound': 'имя не связано в блоке'}
    for rel, number, kind, body in sorted(broken):
        if kind == 'marker':
            defects.append('%s:%d — %s' % (rel, number, body))
            continue
        if (rel, body) in declared:
            continue
        defects.append('%s:%d — %s: %s' % (rel, number, wording[kind], body))
    return (1 if defects else 0), None, files, hits, defects, exempt


HEADER = '# Отчёт\n\n| Редакция | Команда | Исход |\n|---|---|---|\n'
BAD_BODY = "grep -rlUzP '(?si)(альфа" + ESCAPED_BAR + "бета)' docs/"
BAD_CELL = '| «редакция» | ' + TICK + BAD_BODY + TICK + ' | ноль вхождений |\n'
GOOD_CELL = ('| «редакция» | ' + TICK + "grep -rn 'альфа' docs/" + TICK +
             ' | ноль вхождений |\n')
PROSE_CELL = ('| форма | клетка несёт ' + TICK + ESCAPED_BAR + TICK +
              ' и имени команды не называет | пояснение |\n')


def sandbox(rows, debt=None):
    box = tempfile.mkdtemp()
    scan = os.path.join(box, 'scan')
    write(os.path.join(scan, 'a.md'), HEADER + rows)
    debt_path = os.path.join(box, 'debt.txt')
    if debt is not None:
        write(debt_path, debt)
    return scan, debt_path


# Пробы осей 7-8. Разрыв записан СБОРКОЙ строк, а не литералом с переносом:
# исходник этой команды сам лежит в области её будущих родственников, и
# литеральный перенос в нём был бы ровно тем, что ось мерит.
BROKEN_CMD = ("grep -rlUzP '(?si)альфа[^" + BAR + "\n" + "]{0,60}бета' docs/")
WHOLE_CMD = "grep -rlUzP '(?si)альфа[^" + BAR + "]{0,60}бета' docs/"
MARK = '<!-- command-carrier: цитата дефектной команды — проба -->'
BROKEN_BLOCK = FENCE + '\n' + BROKEN_CMD + '\n' + FENCE + '\n'
WHOLE_BLOCK = FENCE + '\n' + WHOLE_CMD + '\n' + FENCE + '\n'
MARKED_BLOCK = MARK + '\n\n' + BROKEN_BLOCK
STALE_MARKER = MARK + '\n\n' + WHOLE_BLOCK
BROKEN_PROSE = BROKEN_CMD + '\n'

# Пробы оси 9. Знак доллара собран из кода: исходник этой команды сам лежит рядом
# с носителями, которые она мерит, и литерал в нём читался бы как её же предмет.
D = chr(36)
UNBOUND_CMD = 'grep -c "' + D + 'PAT" docs/alpha.md'
UNBOUND_BLOCK = FENCE + '\n' + UNBOUND_CMD + '\n' + FENCE + '\n'
BOUND_BLOCK = (FENCE + '\n' + "PAT='альфа'\n" + UNBOUND_CMD + '\n' + FENCE + '\n')
LOOP_BLOCK = (FENCE + '\n' + 'for f in docs/alpha.md; do grep -c "' + D + 'f" "'
              + D + 'f"; done\n' + FENCE + '\n')
OUTPUT_BLOCK = (FENCE + '\n' + 'docs/alpha.md:27:      group-id: bff-' + D +
                '{random.uuid}\n' + FENCE + '\n')
ENV_BLOCK = FENCE + '\n' + 'echo "' + D + 'HOME"\n' + FENCE + '\n'
FUNC_BLOCK = (FENCE + '\n' + 'measure() { d="' + D + '1"\n  grep -c альфа "' + D +
              'd"; }\n' + FENCE + '\n')
MARKED_UNBOUND = MARK + '\n\n' + UNBOUND_BLOCK


def block_sandbox(body):
    box = tempfile.mkdtemp()
    scan = os.path.join(box, 'scan')
    write(os.path.join(scan, 'a.md'), '# Отчёт\n\n' + body)
    debt_path = os.path.join(box, 'debt.txt')
    write(debt_path, '# долг\n')
    return scan, debt_path


def battery():
    """Каждая ось — падающей пробой; контроли — на ложное срабатывание."""
    outcomes = []

    def axis(name, condition, detail):
        outcomes.append((name, condition, detail))

    scan, debt = sandbox(BAD_CELL)
    code, _, _, _, defects, _ = measure(scan, debt)
    axis('1. команда с экранированной альтернацией в клетке — дефект',
         code == 1 and len(defects) == 1 and 'команда в клетке' in defects[0],
         '; '.join(defects) or 'дефектов нет')

    scan, debt = sandbox(GOOD_CELL)
    code, _, _, _, defects, _ = measure(scan, debt)
    axis('2. контроль: воспроизводимая команда в клетке дефектом не объявляется',
         code == 0 and not defects, 'дефектов %d' % len(defects))

    scan, debt = sandbox(PROSE_CELL)
    code, _, _, _, defects, _ = measure(scan, debt)
    axis('2a. контроль: клетка без имени команды не мерится',
         code == 0 and not defects, 'дефектов %d' % len(defects))

    box = tempfile.mkdtemp()
    scan = os.path.join(box, 'scan')
    write(os.path.join(scan, 'a.md'), '# Отчёт\n\nформа:\n\n    ' + BAD_CELL)
    code, _, _, _, defects, _ = measure(scan, os.path.join(box, 'debt.txt'))
    axis('3. контроль: иллюстрация формы отступом кодового блока не мерится',
         code == 0 and not defects, 'дефектов %d' % len(defects))

    scan, debt = sandbox(BAD_CELL, debt='# долг\na.md\t' + BAD_BODY + '\n')
    code, _, _, _, defects, _ = measure(scan, debt)
    axis('4. клетка объявленного долга дефектом не объявляется',
         code == 0 and not defects, 'дефектов %d' % len(defects))

    scan, debt = sandbox(BAD_CELL, debt='# долг\na.md\tgrep -rn "иная команда"\n')
    code, _, _, _, defects, _ = measure(scan, debt)
    axis('4b. долг адресует клетку, а не файл: другая клетка того же носителя — дефект',
         code == 1 and any('команда в клетке' in d for d in defects),
         '; '.join(defects) or 'дефектов нет')

    scan, debt = sandbox(GOOD_CELL, debt='# долг\nб.md\tgrep -rn "бета"\n')
    code, _, _, _, defects, _ = measure(scan, debt)
    axis('4a. строка долга без вхождения в корпусе — дефект',
         code == 1 and any('которой в корпусе больше нет' in d for d in defects),
         '; '.join(defects) or 'дефектов нет')

    box = tempfile.mkdtemp()
    code, refusal, _, _, _, _ = measure(os.path.join(box, 'нет-такой'),
                                     os.path.join(box, 'd.txt'))
    axis('5. область не найдена — проверка отказывает', code == 2, refusal or '')

    box = tempfile.mkdtemp()
    empty = os.path.join(box, 'пусто')
    os.makedirs(empty)
    code, refusal, _, _, _, _ = measure(empty, os.path.join(box, 'd.txt'))
    axis('6. область пуста — проверка отказывает', code == 2, refusal or '')

    scan, debt = block_sandbox(BROKEN_BLOCK)
    code, _, _, _, defects, exempt = measure(scan, debt)
    axis('7. -P-шаблон, разорванный переносом внутри аргумента, — дефект',
         code == 1 and any('разорван переносом' in d for d in defects)
         and not exempt, '; '.join(defects) or 'дефектов нет')

    scan, debt = block_sandbox(WHOLE_BLOCK)
    code, _, _, _, defects, _ = measure(scan, debt)
    axis('7a. контроль: однострочный -P-шаблон дефектом не объявляется',
         code == 0 and not defects, 'дефектов %d' % len(defects))

    scan, debt = block_sandbox(BROKEN_PROSE)
    code, _, _, _, defects, _ = measure(scan, debt)
    axis('7b. контроль: та же строка ВНЕ ограждённого блока не мерится',
         code == 0 and not defects, 'дефектов %d' % len(defects))

    scan, debt = block_sandbox(MARKED_BLOCK)
    code, _, _, _, defects, exempt = measure(scan, debt)
    axis('8. блок с маркером исключения дефектом не объявляется и печатается',
         code == 0 and not defects and len(exempt) == 1,
         'дефектов %d; исключено %d' % (len(defects), len(exempt)))

    scan, debt = block_sandbox(STALE_MARKER)
    code, _, _, _, defects, _ = measure(scan, debt)
    axis('8a. маркер, перед которым нет дефектного блока, — дефект',
         code == 1 and any('маркер исключения' in d for d in defects),
         '; '.join(defects) or 'дефектов нет')

    scan, debt = block_sandbox(UNBOUND_BLOCK)
    code, _, _, _, defects, _ = measure(scan, debt)
    axis('9. имя, читаемое в блоке и не связанное в нём, — дефект',
         code == 1 and any('имя не связано в блоке' in d for d in defects),
         '; '.join(defects) or 'дефектов нет')

    scan, debt = block_sandbox(BOUND_BLOCK)
    code, _, _, _, defects, _ = measure(scan, debt)
    axis('9a. контроль: имя, связанное в том же блоке, дефектом не объявляется',
         code == 0 and not defects, 'дефектов %d' % len(defects))

    scan, debt = block_sandbox(LOOP_BLOCK)
    code, _, _, _, defects, _ = measure(scan, debt)
    axis('9b. контроль: имя, связанное циклом и чтением того же блока, не мерится',
         code == 0 and not defects, 'дефектов %d' % len(defects))

    scan, debt = block_sandbox(OUTPUT_BLOCK)
    code, _, _, _, defects, _ = measure(scan, debt)
    axis('9c. контроль: вставленная в блок ВЫДАЧА (строка без командного токена) '
         'не мерится', code == 0 and not defects, 'дефектов %d' % len(defects))

    scan, debt = block_sandbox(ENV_BLOCK)
    code, _, _, _, defects, _ = measure(scan, debt)
    axis('9d. контроль: имя из объявленного перечня окружения не мерится',
         code == 0 and not defects, 'дефектов %d' % len(defects))

    scan, debt = block_sandbox(FUNC_BLOCK)
    code, _, _, _, defects, _ = measure(scan, debt)
    axis('9e. контроль: имя, связанное в теле функции того же блока, не мерится',
         code == 0 and not defects, 'дефектов %d' % len(defects))

    box = tempfile.mkdtemp()
    scan = os.path.join(box, 'scan')
    write(os.path.join(scan, 'a.md'), '# Отчёт\n\n' + UNBOUND_BLOCK)
    debt = os.path.join(box, 'debt.txt')
    write(debt, '# долг\na.md\t' + UNBOUND_CMD + '\n')
    code, _, _, _, defects, _ = measure(scan, debt)
    axis('9f. строка блока, объявленная долгом, дефектом не объявляется',
         code == 0 and not defects, 'дефектов %d' % len(defects))

    scan, debt = block_sandbox(MARKED_UNBOUND)
    code, _, _, _, defects, exempt = measure(scan, debt)
    axis('9g. маркер накрывает и ось 9: блок с несвязанным именем исключён',
         code == 0 and not defects and len(exempt) == 1,
         'дефектов %d; исключено %d' % (len(defects), len(exempt)))

    return outcomes


def main():
    outcomes = battery()
    broken = [entry for entry in outcomes if not entry[1]]
    for name, ok, detail in outcomes:
        print('  %s: %s — %s' % ('доказана' if ok else 'ПРОВАЛЕНА', name, detail))
    if broken:
        print('БАТАРЕЯ ОТКАЗАЛА: осей провалено %d — измерение не проводилось'
              % len(broken))
        return 2

    scan = os.path.join(ROOT, SCAN_REL)
    debt_path = os.path.join(ROOT, DEBT_REL)
    code, refusal, files, hits, defects, exempt = measure(scan, debt_path)
    if code == 2:
        print('ОТКАЗ: %s' % refusal)
        return 2
    for defect in defects:
        print('  ДЕФЕКТ: %s' % defect)
    for rel, number, reason in sorted(exempt):
        print('  исключён блок: %s:%d — %s' % (rel, number, reason))
    print('носителей просмотрено: %d; клеток с командой найдено: %d; '
          'объявлено долгом: %d; блоков исключено маркером: %d; дефектов: %d'
          % (files, len(hits), len(load_debt(debt_path)), len(exempt),
             len(defects)))
    return code


if __name__ == '__main__':
    sys.exit(main())

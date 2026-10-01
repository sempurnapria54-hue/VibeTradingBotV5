#!/usr/bin/env python3
"""Предмет проверки: указатель на величину исполнимой спеки называет ЕЁ ДОМ.

Величина объявляется ровно в одной спеке (`.claude/rules/structure.md`,
строка `docs/spec/`). Указатель на величину обязан называть ту спеку, где
величина и объявлена: перенос величины в новый дом оставляет указатели на
старый, и расхождение не ловится ни прогоном примеров, ни детектором
областей видимости — оба смотрят внутрь спек, а указатель живёт в прозе.

ФОРМЫ ПРЕДМЕТА, КОТОРЫЕ ДЕТЕКТОР ВИДИТ (объявлено, доказано осями батареи).
Указатель — ссылка на файл спеки плюс имя величины в обратных кавычках,
стоящее в том же предложении:

  1. скобочная            `docs/spec/x.json` (`имяВеличины`)
  2. §-именная            `docs/spec/x.json` §`имяВеличины`  (docs/concept.md §Ссылки)
  3. поясняющая после     `docs/spec/x.json`, величина `имяВеличины`
  4. поясняющая перед     `имяВеличины` — величина `docs/spec/x.json`
  5. предлог перед        `имяВеличины` в `docs/spec/x.json`
  6. запятая-имя          `docs/spec/x.json`, `имяВеличины`
  7. имя перед скобкой    `имяВеличины` (`docs/spec/x.json`)

Поясняющее слово формы 3-4 — любое из: величина, операнд, значение,
предикат, нота, гейт, признак (со склонениями). Перечень имён после
разделителя берётся целиком: `спека` (`a`, `b`, `c`) — три указателя.
Форма 7 введена по замеру: ею стояли 19 мест прозы (30 указателей), и
детектор их не мерил. Приоритет формы в конфликте — сразу перед формой 6
(порядок перечня FORMS_RE; номер формы с приоритетом разведён).

ЧТО ЗА ИМЕНЕМ — ДВА РОДА, оба видимы:

  — ИМЯ величины либо операнда: camelCase-идентификатор или имя, известное
    индексу спек;
  — ПРОПИСНОЕ_ИМЯ (`REFUSED` в `docs/spec/x.json`) — ветвь либо термин
    спеки: литерал выражения (`'REFUSED'`), значение примера или слово ноты
    (`MARKET_PHASE_IS` — тип правила, чьи предикаты спека держит). Указатель
    честен, пока слово в тексте спеки звучит. Прежняя редакция отсекала
    такое имя фильтром camelCase, и указатель на ветвь был невидим даже в
    канонической форме. Мера у рода слабее, чем у имени: звучит ли слово,
    а не объявлено ли оно ветвью, — литерал выражения от термина ноты
    детектор не отличает.

ОБЛАСТЬ — ДВЕ, обе видимы:

  — проза `docs/**/*.md` и `.claude/**/*.md` (архивные каталоги — SKIP);
  — ноты самих спек `docs/spec/*.json`: все строковые листья файла (ноты
    величин, операндов, примеров, популяций). В ноте ни ссылка, ни имя
    обратных кавычек обычно не несут, поэтому текст ноты НОРМАЛИЗУЕТСЯ до
    разбора: голая ссылка `docs/spec/x.json` и голое имя (camelCase либо
    известное индексу) заключаются в кавычки — вне уже закавыченного;
    соседние листья разделяются стоп-знаком, и смежность через границу двух
    нот не складывается. Голое ПРОПИСНОЕ слово ноты в кавычки НЕ
    заключается: в ноте оно чаще термин, чем ветвь, и нормализация дала бы
    ложных носителей — то есть прописной род в нотах видим только
    закавыченным.

Форма, которой в этом перечне нет, детектором НЕ измерена — клейма
полноты он на неё не даёт. Прежняя редакция видела ОДНУ форму из четырёх
(скобочную) и печатала число, выглядевшее полным: 106 указателей при 173
в корпусе. Вне перечня по чтению нот остаются, в частности, «`имя`
объявлена в `спека`», «(`имя`, дом — `спека`)» и «(`имя`, `спека`)».
Присваивание «`a` = `b` (`спека`)» форма 7 читает как указатель на `b`.

ОБЪЯВЛЕННЫЙ ДОЛГ (храповик). Указатели, красные на момент ввода области
нот и формы 7, перечислены в `tools/spec-pointer-debt.txt` тройками
«файл<TAB>спека<TAB>имя» без номеров строк (номер дрейфует от любой
правки). Прогон падает на НОВОМ дефекте и на строке долга, которой в
корпусе больше нет: погашенная строка обязана уйти из реестра. Ключ — тройка,
а не вхождение: второй такой же указатель в том же файле проходит молча под
уже объявленной строкой. Пересборка — `py tools/spec-pointer-check.py
--emit-debt` (весь реестр в stdout; сверка и приёмка — рукой перед записью).

ФОРМЫ ПУТИ — ОБЕ: SKIP-фильтр архивных каталогов сверяется по пути с
нормализованным разделителем ('/' и '\\' Windows-glob — оси 22-23);
прежде подстрочная сверка со '/'-шаблонами на Windows была мертва, и 42
архивных дефекта делали детектор перманентно красным (E3 `DOCS_CHECK_33`).

ПРИВЯЗКА СМЕЖНАЯ, НЕ ОКОННАЯ. Между ссылкой и именем стои́т только
разделитель формы (скобка, §, поясняющее слово, запятая, предлог). Замер
отверг привязку «по ближайшей ссылке в окне 160 символов»: имя, названное
в прозе рядом с чужой ссылкой («дом матрицы — спека A (`x`, `y`); дом
резолва — спека B»), приписывалось не своей спеке — все девятнадцать
находок такого прогона оказались ложными.

БАТАРЕЯ ОСЕЙ ИСПОЛНЯЕТСЯ ЭТОЙ ЖЕ КОМАНДОЙ, до проверки: инструмент,
чьи оси доказываются отдельным скриптом, принимается по факту, что скрипт
когда-то прогоняли.

ТРИ КЛАССА ДЕФЕКТА, и второй сильнее первого:

  A. указатель называет НЕ ТУ спеку — величина объявлена, но в другом доме;
     имя, которое в названной спеке стои́т лишь операндом, а величиной
     объявлено в другой, — тоже класс A: дом у величины один;
  B. указатель называет имя, которого в корпусе НЕТ ВОВСЕ — ни величиной,
     ни операндом: величина снята или переименована. Прежняя редакция
     такое имя молча пропускала (`if name not in known: continue`), то
     есть не видела сильнейший случай своего же предмета: удаление
     величины не ловилось ничем, а `docs/concept.md` §Ссылки обещает
     обратное — «сломать такую ссылку может только переименование самой
     величины, и оно ловится прогоном»;
  C. указатель называет ПРОПИСНОЕ слово, которого в названной спеке нет:
     ветвь или термин сняты, переименованы либо живут в другой спеке.

Коды возврата: 0 — новых дефектов нет и реестр долга точен; 1 — есть
новые дефекты либо устаревшие строки долга; 2 — ПРОВЕРКА НЕ ПРОВОДИЛАСЬ
(ось не доказана, каталог спек не найден, спека либо файл области не
разобраны, реестр долга не прочитан, индекс домов пуст, корпус пуст).
"""
import glob
import json
import os
import re
import sys
import tempfile

# Печать не зависит от кодировки консоли вызывающего: на cp1251-консоли
# объявленной среды вывод падал UnicodeEncodeError (класс описан в backlog
# у anchor-check; тот же ремонт исполнимости, DOCS_CHECK_33 узел 9).
# LF и на Windows: `--emit-debt` пишет реестр перенаправлением stdout.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", newline="\n")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

SPEC_DIR = 'docs/spec'
# Область: проза корпуса и ноты самих спек (шапка, «ОБЛАСТЬ — ДВЕ»).
ROOTS = ['docs/**/*.md', '.claude/**/*.md', SPEC_DIR + '/*.json']
# Объявленный долг: красное на момент ввода области нот и формы 7 (шапка).
DEBT_FILE = 'tools/spec-pointer-debt.txt'
SKIP = ('/history/', '/library/', '.claude-archive', '/progress/')


def skipped(path):
    """Архивный ли путь. Разделитель нормализуется до '/': на Windows glob
    возвращает '\\'-пути, и подстрочная сверка со '/'-шаблонами SKIP была
    мертва — 42 ложных дефекта из архивных отчётов (E3 `DOCS_CHECK_33`)."""
    normal = '/' + path.replace('\\', '/')
    return any(skip in normal for skip in SKIP)

SPEC_REF = re.compile(r'`docs/spec/([\w-]+\.json)`')
FILE_REF = re.compile(r'`?docs/spec/[\w-]+\.json`?')
TOKEN = r'[A-Za-z][A-Za-z0-9._]*'
NAME = re.compile(r'`(' + TOKEN + r')`')
SPEC = r'`docs/spec/([\w-]+\.json)`'
CARRIER = (r'(?:величина|величины|величине|операнд|операнда|значение|значения|предикат|предиката'
           r'|нота|ноты|гейт|гейта|признак|признака)')
NAMES = r'(`' + TOKEN + r'`(?:\s*[,/]\s*`' + TOKEN + r'`)*)'

# Письменные формы указателя. Привязка СМЕЖНАЯ: между ссылкой и именем
# стои́т только разделитель формы. Порядок перечня — приоритет разрешения
# конфликта: одно и то же имя может стоять между двумя ссылками («`a` —
# операнд спеки A, `b` — величина спеки B»), и тогда выигрывает форма,
# стоящая в перечне раньше. Номер формы (шапка) и её приоритет разведены:
# форма 7 стои́т перед формой 6 — «`имя` (`спека`)» явнее «запятой-имени».
# (регулярка, группа-спека, группа-имена, имя формы)
FORMS_RE = [
    (re.compile(SPEC + r'\s*\(([^)]{0,200})\)'), 1, 2, '1. скобочная'),
    (re.compile(SPEC + r'\s*§' + NAMES), 1, 2, '2. §-именная'),
    (re.compile(SPEC + r'\s*[,—-]?\s*' + CARRIER + r'\s+' + NAMES), 1, 2, '3. поясняющая после ссылки'),
    (re.compile(NAMES + r'\s*[—-]?\s*' + CARRIER + r'\s+(?:из\s+|в\s+)?' + SPEC), 2, 1, '4. поясняющая перед ссылкой'),
    (re.compile(NAMES + r'\s+(?:в|из|у)\s+' + SPEC), 2, 1, '5. предлог перед ссылкой'),
    (re.compile(NAMES + r'\s*\(\s*' + SPEC), 2, 1, '7. имя перед скобочной ссылкой'),
    (re.compile(SPEC + r'\s*,\s*' + NAMES), 1, 2, '6. запятая-имя'),
]

# Прописное слово спеки: ветвь выражения (`'REFUSED'`), значение примера либо
# термин ноты (`MARKET_PHASE_IS` — тип правила, чьи предикаты спека держит).
SPEC_WORD = re.compile(r'(?<![\w])([A-Z][A-Z0-9_]*)(?![\w])')


def spec_files(spec_dir):
    """Спеки каталога, разобранные. Неразобранный файл — отказ, а не дефект."""
    loaded = []
    for path in sorted(glob.glob(spec_dir + '/*.json')):
        with open(path, encoding='utf-8') as handle:
            loaded.append((os.path.basename(path), json.load(handle)))
    return loaded


def homes(spec_dir):
    """Индекс «имя величины → спеки, где она объявлена»."""
    index = {}
    for base, spec in spec_files(spec_dir):
        for value in spec.get('values', []):
            index.setdefault(value['name'], set()).add(base)
    return index


def operand_names(spec_dir):
    """Имена операндов по спекам: указатель законно называет и операнд."""
    index = {}
    for base, spec in spec_files(spec_dir):
        for key in spec.get('operands', {}):
            clean = key.replace('[]', '').replace('{}', '')
            index.setdefault(clean, set()).add(base)
            for segment in clean.split('.'):
                if segment:
                    index.setdefault(segment, set()).add(base)
    return index


def spec_words(spec_dir):
    """Индекс «прописное слово → спеки, в чьём тексте оно звучит».

    Текст спеки — все её строковые листья: выражения, значения примеров,
    ноты. Прописное имя в позиции указателя — ветвь либо термин спеки, и
    указатель на него честен, пока слово в спеке звучит.
    """
    index = {}
    for base, spec in spec_files(spec_dir):
        for leaf in string_leaves(spec):
            for word in SPEC_WORD.findall(leaf):
                index.setdefault(word, set()).add(base)
    return index


CAMEL = re.compile(r'^[a-z][A-Za-z0-9]*[A-Z][A-Za-z0-9.]*$')
LITERAL = re.compile(r'^[A-Z][A-Z0-9_]*$')
# Функции языка спецификаций — законные имена в тексте про спеку, но не величины.
LANGUAGE = {'floorTo', 'isNull', 'notNull', 'coalesce', 'min', 'max', 'abs', 'in', 'if', 'not'}


def candidate_kind(token, known):
    """Род токена в позиции указателя: 'name', 'literal' либо None.

    Имя — camelCase-идентификатор либо известное индексу. Фильтр по форме
    нужен затем, чтобы удаление величины ловилось: имя, которого в корпусе
    нет, по индексу не опознаётся, и без формы его пришлось бы пропускать —
    то есть не видеть сильнейший случай предмета. Однословные строчные
    токены (`type`, `size`, `status`) отсеиваются: это поля строк
    операндного контракта, а не имена величин.

    Литерал ветви — ПРОПИСНОЕ_ИМЯ (`REFUSED`): прежде его отсекал фильтр
    camelCase, и указатель на ветвь был невидим в любой форме.
    """
    if token in LANGUAGE:
        return None
    if token in known or CAMEL.match(token):
        return 'name'
    if LITERAL.match(token):
        return 'literal'
    return None


def pointers(text, known):
    """Тройки (спека, токен, род) по всем письменным формам указателя.

    Привязка синтаксическая, по смежности; конфликт двух форм на одном
    имени разрешается приоритетом формы. Привязка «по ближайшей ссылке в
    окне» была отвергнута замером: имя, названное в прозе рядом с чужой
    ссылкой, приписывалось не своей спеке — все девятнадцать находок такого
    прогона оказались ложными.
    """
    flat = re.sub(r'\s*\n\s*', ' ', text)
    claims = {}
    for rank, (pattern, spec_group, names_group, _form) in enumerate(FORMS_RE):
        for match in pattern.finditer(flat):
            spec = match.group(spec_group)
            base = match.start(names_group)
            for name in NAME.finditer(match.group(names_group)):
                kind = candidate_kind(name.group(1), known)
                if kind is None:
                    continue
                span = base + name.start()
                if span not in claims or claims[span][0] > rank:
                    claims[span] = (rank, spec, name.group(1), kind)
    return [(spec, name, kind) for _rank, spec, name, kind in claims.values()]


# Нормализация ноты спеки: голая ссылка и голое имя получают кавычки прозы.
BARE_SPEC = re.compile(r'(?<![\w/.-])(docs/spec/[\w-]+\.json)(?![\w-])')
BARE_TOKEN = re.compile(r'(?<![\w./-])([A-Za-z][A-Za-z0-9._]*[A-Za-z0-9])(?![\w-])')
# Стоп-знак между листьями: ни одна форма через него смежности не складывает.
LEAF_BREAK = '\n¶\n'
# Разделитель кусков внутри листа: в тексте ноты не встречается.
CUT = '\x00'


def string_leaves(node):
    """Все строковые листья разобранного JSON в порядке обхода."""
    if isinstance(node, str):
        yield node
    elif isinstance(node, dict):
        for child in node.values():
            yield from string_leaves(child)
    elif isinstance(node, list):
        for child in node:
            yield from string_leaves(child)


def note_text(spec, known):
    """Текст нот спеки в форме прозы: кавычки ставятся только вне кавычек.

    Голое имя получает кавычки, только когда оно — имя (camelCase либо
    известное индексу); прописное слово ноты остаётся голым: в ноте оно чаще
    термин, чем ветвь, и нормализация плодила бы ложных носителей.
    """
    def quote(match):
        token = match.group(1)
        return '`%s`' % token if candidate_kind(token, known) == 'name' else token

    leaves = []
    for leaf in string_leaves(spec):
        parts = leaf.split('`')
        for index in range(0, len(parts), 2):
            chunks = BARE_SPEC.sub(CUT + r'`\1`' + CUT, parts[index]).split(CUT)
            for chunk in range(0, len(chunks), 2):
                chunks[chunk] = BARE_TOKEN.sub(quote, chunks[chunk])
            parts[index] = ''.join(chunks)
        leaves.append('`'.join(parts))
    return LEAF_BREAK.join(leaves)


def scan(spec_dir, roots):
    """Разбор области: файл `.json` читается нотами (`note_text`), прочие — прозой."""
    try:
        index = homes(spec_dir)
        operands = operand_names(spec_dir)
        literals = spec_words(spec_dir)
    except (OSError, ValueError, KeyError, AttributeError, TypeError) as failure:
        return None, 'спека не разобрана — %s' % failure
    if not index:
        return None, 'индекс домов величин пуст — проверять нечего'
    files = [path for pattern in roots for path in glob.glob(pattern, recursive=True)
             if not skipped(path)]
    if not files:
        return None, 'ни одного файла корпуса не найдено — проверять нечего'
    bad, checked = [], 0
    known = set(index) | set(operands)
    for path in files:
        try:
            with open(path, encoding='utf-8') as handle:
                text = note_text(json.load(handle), known) if path.endswith('.json') else handle.read()
        except (OSError, ValueError) as failure:
            return None, 'файл области не прочитан — %s: %s' % (path, failure)
        for spec, name, kind in pointers(text, known):
            checked += 1
            if kind == 'literal':
                if spec not in literals.get(name, set()):
                    bad.append((path, spec, name, sorted(literals.get(name, set())) or [
                        'прописного слова нет ни в одной спеке — ветвь или термин сняты либо переименованы']))
                continue
            if name not in index:
                if spec in operands.get(name, set()):
                    continue
                bad.append((path, spec, name, ['имени нет ни величиной, ни операндом — '
                                               'величина снята или переименована']))
                continue
            if spec not in index[name]:
                bad.append((path, spec, name, sorted(index[name])))
    return (checked, bad), None


# --- объявленный долг --------------------------------------------------------

def debt_key(item):
    """Ключ строки долга: файл (разделитель '/'), спека, имя. Номера строки нет."""
    path, spec, name, _home = item
    return (path.replace('\\', '/'), spec, name)


def declared_debt(path=DEBT_FILE):
    """Объявленный долг как множество троек «файл, спека, имя»."""
    debt = set()
    if not os.path.exists(path):
        return debt
    with open(path, encoding='utf-8') as handle:
        for line in handle:
            line = line.rstrip('\n')
            if not line.strip() or line.startswith('#'):
                continue
            fields = [field.strip() for field in line.split('\t')]
            if len(fields) != 3:
                raise ValueError('строка долга не разобрана (нужно три поля через TAB): %r' % line)
            debt.add(tuple(fields))
    return debt


def ratchet(bad, debt):
    """Храповик долга: (новые дефекты вне реестра, строки реестра без вхождения)."""
    observed = {debt_key(item) for item in bad}
    fresh = [item for item in bad if debt_key(item) not in debt]
    return fresh, sorted(debt - observed)


# --- батарея осей ------------------------------------------------------------

FORMS = [
    ('1. скобочная форма', 'Форма — `docs/spec/{spec}` (`{name}`).'),
    ('2. §-именная форма', 'Форма — `docs/spec/{spec}` §`{name}`.'),
    ('3. поясняющая форма', 'Форма — `docs/spec/{spec}`, величина `{name}`.'),
    ('4. поясняющая перед ссылкой', 'Форма — `{name}` — величина `docs/spec/{spec}`.'),
    ('5. предлог перед ссылкой', 'Форма — `{name}` в `docs/spec/{spec}`.'),
    ('6. форма «запятая-имя»', 'Форма — `docs/spec/{spec}`, `{name}`.'),
    ('7. имя перед скобочной ссылкой', 'Форма — `{name}` (`docs/spec/{spec}`).'),
]


def battery():
    """Оси детектора: каждая объявленная форма ловится и не даёт ложного срабатывания."""
    axes = []
    try:
        index = homes(SPEC_DIR)
        words = spec_words(SPEC_DIR)
        operands = operand_names(SPEC_DIR)
    except (OSError, ValueError, KeyError, AttributeError, TypeError) as failure:
        return [('фикстура батареи: индекс домов', False, 'спека не разобрана — %s' % failure)]
    if not index or not words or not operands:
        return [('фикстура батареи: индекс домов', False, 'каталог спек не разобран')]
    name = 'lossAtStopPerUnit' if 'lossAtStopPerUnit' in index else sorted(index)[0]
    home = sorted(index[name])[0]
    specs = [os.path.basename(p) for p in sorted(glob.glob(SPEC_DIR + '/*.json'))]
    alien = next(spec for spec in specs if spec != home)
    word = 'REFUSED' if 'REFUSED' in words else sorted(words)[0]
    word_home = sorted(words[word])[0]
    word_alien = next(spec for spec in specs if spec not in words[word])
    with tempfile.TemporaryDirectory() as work:
        page = os.path.join(work, 'проба.md')
        note = os.path.join(work, 'проба.json')
        prose = [os.path.join(work, '*.md')]
        notes = [os.path.join(work, '*.json')]

        def probe(text):
            with open(page, 'w', encoding='utf-8') as handle:
                handle.write('# П\n\n' + text + '\n')
            return scan(SPEC_DIR, prose)[0]

        def probe_note(*leaves):
            if os.path.exists(page):
                os.remove(page)
            with open(note, 'w', encoding='utf-8') as handle:
                json.dump({'values': [{'name': 'x', 'note': leaf} for leaf in leaves]},
                          handle, ensure_ascii=False)
            return scan(SPEC_DIR, notes)[0]

        for axis, template in FORMS:
            checked, bad = probe(template.format(spec=alien, name=name))
            axes.append((axis + ' — указатель на чужую спеку', bool(bad),
                         'найдено чужих: %d при проверенных %d' % (len(bad), checked)))
            checked, bad = probe(template.format(spec=home, name=name))
            axes.append((axis + ' — контроль: указатель на дом', checked == 1 and not bad,
                         'найдено чужих: %d при проверенных %d' % (len(bad), checked)))
        # ось класса B: имя, которого в корпусе нет вовсе (величина снята)
        checked, bad = probe('Форма — `docs/spec/%s` (`someRemovedValueName`).' % home)
        axes.append(('8. имя, которого в корпусе нет вовсе (величина снята)', bool(bad),
                     'найдено: %d при проверенных %d' % (len(bad), checked)))
        # контроль: имя операнда той же спеки дефектом не считается
        operand = sorted(operands)[0]
        owner = sorted(operands[operand])[0]
        checked, bad = probe('Форма — `docs/spec/%s`, операнд `%s`.' % (owner, operand))
        axes.append(('9. контроль: имя операнда своей спеки дефектом не считается', not bad,
                     'найдено: %d при проверенных %d' % (len(bad), checked)))
        # контроль: функция языка спецификаций именем величины не считается
        checked, bad = probe('Форма — `docs/spec/%s` (`floorTo`).' % home)
        axes.append(('10. контроль: функция языка спецификаций не имя величины', not bad,
                     'найдено: %d при проверенных %d' % (len(bad), checked)))
        # ось привязки: имя достаётся БЛИЖАЙШЕЙ ссылке, а не предыдущей
        checked, bad = probe('Операнд стои́т в `docs/spec/{alien}`, `{name}` — величина '
                             '`docs/spec/{home}`.'.format(alien=alien, home=home, name=name))
        axes.append(('11. конфликт форм: явная форма перебивает «запятую-имя»', not bad,
                     'найдено чужих: %d при проверенных %d' % (len(bad), checked)))
        # род «литерал ветви»: прописное имя в канонической форме
        checked, bad = probe('Ветвь `%s` в `docs/spec/%s`.' % (word, word_alien))
        axes.append(('12. литерал ветви — указатель на спеку, где слово не звучит', bool(bad),
                     'найдено: %d при проверенных %d' % (len(bad), checked)))
        checked, bad = probe('Ветвь `%s` в `docs/spec/%s`.' % (word, word_home))
        axes.append(('13. литерал ветви — контроль: указатель на спеку, где слово звучит',
                     checked == 1 and not bad,
                     'найдено: %d при проверенных %d' % (len(bad), checked)))
        checked, bad = probe('Ветвь `SOME_REMOVED_BRANCH` в `docs/spec/%s`.' % word_home)
        axes.append(('14. литерал ветви, которого нет ни в одной спеке (ветвь снята)', bool(bad),
                     'найдено: %d при проверенных %d' % (len(bad), checked)))
        # область «ноты спек»: голые ссылка и имя нормализуются до прозы
        checked, bad = probe_note('Значение берётся из дома-спеки: docs/spec/%s, величина %s.'
                                  % (alien, name))
        axes.append(('15. нота спеки: голый указатель на чужую спеку', bool(bad),
                     'найдено чужих: %d при проверенных %d' % (len(bad), checked)))
        checked, bad = probe_note('Значение берётся из дома-спеки: docs/spec/%s, величина %s.'
                                  % (home, name))
        axes.append(('16. нота спеки — контроль: голый указатель на дом', checked == 1 and not bad,
                     'найдено чужих: %d при проверенных %d' % (len(bad), checked)))
        checked, bad = probe_note('Форма — docs/spec/%s §`%s`.' % (alien, name))
        axes.append(('17. нота спеки: голая ссылка при имени в кавычках', bool(bad),
                     'найдено чужих: %d при проверенных %d' % (len(bad), checked)))
        checked, bad = probe_note('Дом — docs/spec/%s' % alien, '(%s) — начало соседней ноты.' % name)
        axes.append(('18. нота спеки — контроль: смежность через границу нот не складывается',
                     checked == 0, 'проверено указателей: %d' % checked))
        os.remove(note)
        # ось базового гейта: каталога спек нет
        _, refusal = scan(os.path.join(work, 'нет-такого'), prose)
        axes.append(('19. каталог спек не найден — проверка отказывает', bool(refusal),
                     refusal or 'проверка отчиталась'))
        # ось базового гейта: корпус пуст
        _, refusal = scan(SPEC_DIR, [os.path.join(work, 'нет-такого', '*.md')])
        axes.append(('20. корпус пуст — проверка отказывает', bool(refusal),
                     refusal or 'проверка отчиталась'))
        # системный отказ: неразобранная спека — отказ, а не дефект и не трассировка
        broken = os.path.join(work, 'битая')
        os.makedirs(broken)
        with open(os.path.join(broken, 'x.json'), 'w', encoding='utf-8') as handle:
            handle.write('{"values": [')
        _, refusal = scan(broken, prose)
        axes.append(('21. спека не разобрана — проверка отказывает', bool(refusal),
                     refusal or 'проверка отчиталась'))
        # ось формы пути: SKIP обязан работать на ОБЕИХ письменных формах
        # разделителя — '/' (POSIX) и '\\' (Windows-glob); фикстуры выше
        # строятся os.path.join и на POSIX эту форму не предъявляют
        axes.append(('22. SKIP отбрасывает архивный путь Windows-формы (\\)',
                     skipped('.claude\\work\\progress\\x\\п.md')
                     and skipped('.claude/work/progress/x/п.md'),
                     'обе формы разделителя отброшены'))
        axes.append(('23. контроль: живой файл корпуса SKIP не отбрасывает',
                     not skipped('docs\\rules\\п.md') and not skipped('docs/rules/п.md'),
                     'обе формы разделителя пропущены'))
        # храповик долга
        found = [('docs\\a.md', alien, name, [home]), ('docs/b.md', alien, name, [home])]
        fresh, stale = ratchet(found, {('docs/a.md', alien, name), ('docs/c.md', alien, name)})
        axes.append(('24. дефект вне реестра долга роняет прогон',
                     [debt_key(item) for item in fresh] == [('docs/b.md', alien, name)],
                     'новых: %d' % len(fresh)))
        axes.append(('25. строка долга без вхождения роняет прогон',
                     stale == [('docs/c.md', alien, name)], 'устаревших: %r' % (stale,)))
        registry = os.path.join(work, 'долг.txt')
        with open(registry, 'w', encoding='utf-8') as handle:
            handle.write('# шапка\ndocs/a.md\t%s\t%s\n' % (alien, name))
        debt = declared_debt(registry)
        axes.append(('26. реестр долга читается тройками через TAB, шапка пропущена',
                     debt == {('docs/a.md', alien, name)}, 'прочитано: %r' % (debt,)))
    return axes


def emit_debt(bad):
    """Реестр долга на текущем состоянии — в stdout."""
    print('# Объявленный долг указателей на величины спек — тройки, красные на момент')
    print('# ввода области нот и формы 7 tools/spec-pointer-check.py (формы и области —')
    print('# его шапка). Формат: <файл>\\t<спека>\\t<имя>. Номеров строк нет намеренно:')
    print('# номер дрейфует от любой правки. Погашенная тройка обязана уйти отсюда —')
    print('# прогон падает на устаревшей строке. Пересборка:')
    print('# py tools/spec-pointer-check.py --emit-debt')
    for key in sorted({debt_key(item) for item in bad}):
        print('\t'.join(key))


def main():
    emit = '--emit-debt' in sys.argv
    if not os.path.isdir(SPEC_DIR):
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: %s не найден' % SPEC_DIR)
        return 2
    axes = battery()
    if not emit:
        print('--- батарея осей детектора (исполняется той же командой)')
        for axis, passed, observed in axes:
            print('  %s: %s — %s' % ('доказана' if passed else 'НЕ ДОКАЗАНА', axis, observed))
    broken = [axis for axis, passed, _ in axes if not passed]
    if broken:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: недоказанных осей %d — число указателей '
              'ничего не удостоверяло бы' % len(broken))
        return 2

    result, refusal = scan(SPEC_DIR, ROOTS)
    if refusal:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: ' + refusal)
        return 2
    checked, bad = result
    if emit:
        emit_debt(bad)
        return 0
    try:
        debt = declared_debt()
    except (OSError, ValueError) as failure:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: реестр долга %s не прочитан — %s' % (DEBT_FILE, failure))
        return 2
    fresh, stale = ratchet(bad, debt)
    print('указателей на величины проверено: %d; ВЕДУТ НЕ В ДОМ: %d '
          '(из них объявленным долгом %s: %d; новых: %d); устаревших строк долга: %d'
          % (checked, len(bad), DEBT_FILE, len(bad) - len(fresh), len(fresh), len(stale)))
    for path, spec, name, home in fresh:
        print('  ДЕФЕКТ: %s → %s (`%s`) — дом величины %s' % (path, spec, name, ', '.join(home)))
    for path, spec, name in stale:
        print('  УСТАРЕВШАЯ СТРОКА ДОЛГА: %s → %s (`%s`) — вхождения нет, строку снять'
              % (path, spec, name))
    return 1 if fresh or stale else 0


if __name__ == '__main__':
    sys.exit(main())

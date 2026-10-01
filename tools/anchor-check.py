#!/usr/bin/env python3
"""Предмет проверки: разрешимость §-адресов пассажей в корпусе `.md` и в деревьях кода.

Адрес `§«Имя»` обязан разрешаться в заголовок либо лид-жирный пассаж
целевого файла (`.claude/rules/structure.md`). Целевой файл — путь,
названный в том же абзаце; при его отсутствии адрес внутрифайловый.

ДВЕ ОБЛАСТИ, ОДНИ ПРАВИЛА РАЗРЕШЕНИЯ. Первая — корпус `.md` (ниже —
«ОБЛАСТЬ»): каталоги `.claude` и `docs` плюс `.md` корня репозитория
(`README.md`, `CLAUDE.md`). Вторая — деревья кода и манифестов
`services/**`, `tests/**`, `web/**`, `deploy/**`: адрес пассажа, написанный
в javadoc, комментарии или литерале, прежде не мерил ни один прогон —
`doc-pointer-check.py` мерит существование файла, а не пассажа. Абзац кода
разбирается той же процедурой, что абзац `.md`, — второй набор правил
разошёлся бы с первым при первой же правке. Обе области мерит прогон по
умолчанию; с явным каталогом-аргументом мерится только он, как область `.md`.

`docs/**` В ОБЛАСТИ — РАЗРЕШИМОСТЬ, А НЕ ЗАКОННОСТЬ. Продуктовый корпус
адресов пассажей не использует (`docs/concept.md` §Ссылки): стоящие там
адреса — долг формы, и счёт его держит команда концепции, а не этот
реестр. Детектор мерит у них только разрешимость: адрес долга, ведущий в
никуда, погашение долга формы от живого не отличает (замер вне области
2026-09-03 дал таких тринадцать). Разрешимый адрес в `docs/**` законным от
этого не становится.

НЕ ИЗМЕРЯЕТСЯ (объявлено, а не умолчано):

  tools/**          носители самих проверок. Литералы их батарей несут
                    битые адреса НАМЕРЕННО (фикстуры осей, шаблоны
                    регулярок), docstring Python формой литерала
                    склеивался бы без разделителей, а маркера фикстур у
                    чужих детекторов нет: вписанное в долг было бы
                    фикстурами, а не долгом. Класс заводится формой
                    носителя `.py`/`.sh` с маркером фикстур и своими осями
  docs/spec/*.json  ноты спек — не `.md`
  history/ и др.    записи прошлого — см. ОБЛАСТЬ ниже

ФОРМЫ НОСИТЕЛЯ В КОДЕ (объявлено, доказано осями К батареи). Абзац кода —
подряд идущие строки одного носителя; кончается он на пустой строке
комментария, на смене носителя (другой блочный комментарий, код между
строчными), на `<p>` и блочных тегах javadoc:

  .java   блочный /* */ и javadoc, строчный //, литерал "…" (склейка
          `"…" + "…"` через перенос собирается, как её собирает компилятор);
          разметка javadoc в имени (`{@code X}`, `<b>`) снимается до разрешения
  .sql    строчный --, блочный /* */
  .yaml   строчный # (.yml тоже)
  .xml    блочный <!-- -->
  .md     абзацы, как в первой области

Файл дерева кода со знаком § и расширением вне перечня НЕ ИЗМЕРЕН — прогон
печатает его поимённо и падает кодом 1: форма носителя заводится, а не
пропускается молча. Раздел ВНЕШНЕГО стандарта («RFC 9110 §7.6.1») адресом
корпуса не считается.

ФОРМЫ ПРЕДМЕТА, КОТОРЫЕ ДЕТЕКТОР ВИДИТ (объявлено, доказано осями батареи):

  1. кавычечная  §«Имя пассажа»       — имя целиком, границы явные
  2. голая       §Имя, §Имя пассажа   — имя без кавычек, граница по разбору
  3. ординальная §3, §6a, §AG1.5      — номер заголовка вместо его имени
  4. величина     §`имяВеличины`      — имя величины исполнимой спеки
                                        (docs/concept.md §Ссылки); цель — .json
     её голый вид  §имяВеличины при пути docs/spec/*.json в том же абзаце —
                   так её пишет javadoc, где обратных кавычек нет; разрешается
                   перечнем величин, снятое имя остаётся дефектом

Прежняя редакция видела ОДНУ форму из трёх и печатала «адресов проверено:
272» при 619 в своей же области: две трети предмета в число не входили, а
число выглядело полным.

ГРАНИЦА ГОЛОЙ ФОРМЫ. У кавычечной формы имя ограничено кавычками, у голой —
нет, и «сколько слов после § входят в имя» решается разбором: берётся до
семи слов до ближайшего разделителя (точка-конец-предложения, запятая,
точка с запятой, двоеточие, скобка, кавычка, обратная кавычка, звёздочка,
конец строки), после чего пробуются префиксы от длинного к короткому.
Разрешился хоть один — адрес разрешим. Не разрешился ни один — дефект,
и в перечень идёт однословная форма как минимальный клейм.

ХВОСТ ИМЕНИ (скобочный либо после « — ») в адресе опускается законно:
он несёт метаданные пассажа, а не его имя.

ИЛЛЮСТРАЦИЯ ФОРМЫ. Адрес, взятый в обратные кавычки целиком (`§«Имя»`),
разговор о форме, а не адрес: конвенция корпуса — адрес кавычками не
окружается (`.claude/rules/structure.md`). Сверх того абзац, вводящий форму
адреса, говорит о ней примерами
(«§«Длинное имя пассажа» вместо …»); разрешать их не по чему. Абзац
объявляет исключение маркером `<!-- anchor-check: иллюстрации формы -->`,
и число таких абзацев печатает сам прогон. Отдельно от маркера снимаются
имена-заполнители («Имя», «Имя пассажа», «…»): целью они не бывают по
построению. Тот же приём у продуктового корпуса — `docs/concept.md`
§Ссылки исключает собственные § как иллюстрации формы.

РОДОВОЕ УПОМИНАНИЕ. Адрес, у которого в абзаце не назван внешний файл, а
имя пассажа стои́т заголовком в трёх и более файлах корпуса, — упоминание
РОДА раздела («§Персистентность доменного дока»), а не адрес к цели:
разрешать его не по чему, и дефектом он не считается. На ординальную
форму послабление НЕ распространяется: номер родом раздела не бывает, и
«§13» без названной цели — адрес в никуда. Относительные
указания («§ниже», «§выше», «§шапка» — шапка своего же файла) адресом не
считаются вовсе.

ОРДИНАЛ. Заголовок, начинающийся с номера («### 3. Закрытие пробелов»,
«## AG1.5 Горизонт фандинга»), регистрирует и сам номер: `§3` — законная
форма адреса к нему.

БАТАРЕЯ ОСЕЙ ИСПОЛНЯЕТСЯ ЭТОЙ ЖЕ КОМАНДОЙ, до проверки: инструмент, чьи
оси доказываются отдельным скриптом, принимается по факту, что скрипт
когда-то прогоняли.

ОБЛАСТЬ. Свипаются `.md` каталогов области (либо каталога-аргумента) и
корня репозитория за вычетом `history/`, `library/`, `progress/` и
архива: там живут записи прошлого, чьи адреса ведут в раскладку того
момента. Состав области печатает сам прогон — числом файлов по каталогу и
числом исключённых по маркеру. Фильтр сверяет «/»-форму пути: glob на
Windows отдаёт «\\», и без нормализации фильтр мёртв МОЛЧА — архив уходит
в перечень дефектов, а не в исключённые (ось 23 — фильтр на «\\»-форме,
ось 24 — фильтр применён самим прогоном области).

ОБЪЯВЛЕННЫЙ ДОЛГ (храповик). Адреса, неразрешимые на момент ввода
расширенного детектора, перечислены в `tools/anchor-debt.txt` парами
«файл + имя». Прогон падает на **новом** неразрешимом адресе и на строке
долга, которой в корпусе больше нет (погашенный адрес обязан уйти из
реестра, иначе реестр гниёт). Долг не прячется: его размер печатает
каждая строка итога. У области кода реестр свой — `tools/anchor-code-debt.txt`
той же механики: адрес кода чинит правка дерева кода, а не курация.
Реестры адресуют прогон по умолчанию; у явного каталога-аргумента долга
нет, и неразрешимое там печатается целиком.

КЛЮЧ ДОЛГА — ПАРА С ЧИСЛОМ ВХОЖДЕНИЙ. Строка реестра разрешает ОДНО
вхождение пары; пара, стоящая в файле дважды, объявляется двумя строками.
Прежний ключ-множество пропускал молча новый битый адрес того же имени в
том же файле: пара уже была объявлена, и второе вхождение в неё растворялось
(ось 25). Обратная сторона — вхождений стало меньше, чем строк, — строка
устарела (ось 26).

Коды возврата: 0 — новых неразрешимых нет и оба реестра долга точны;
1 — есть новые неразрешимые, устаревшие строки долга либо файл кода вне
объявленных форм носителя;
2 — ПРОВЕРКА НЕ ПРОВОДИЛАСЬ (ось не доказана, каталог области не найден
либо пуст, индекс имён пуст, ни одного дерева кода нет либо в них нет ни
одного файла со знаком §).
"""
from collections import Counter
import glob
import os
import re
import sys
import tempfile

# Печать не зависит от кодировки консоли вызывающего: на cp1251-консоли
# объявленной среды вывод падал UnicodeEncodeError (класс описан в backlog
# у anchor-check; тот же ремонт исполнимости, DOCS_CHECK_33 узел 9).
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

SKIP = ('/history/', '/library/', '/progress/', '/.claude-archive/')
# Каталоги корпуса `.md`, которые мерит прогон по умолчанию; `.md` корня
# репозитория (README.md, CLAUDE.md) входят в область сверх них.
MD_AREAS = ('.claude', 'docs')

# Объявленное исключение: файл-запись наблюдения на дату описывает КОРПУС ТОГО
# МОМЕНТА, и его §-адреса ведут в раскладку, которой больше нет. Чинить их
# нечем — как и ссылки в history/ (`.claude/rules/curation.md` §Чек-лист, п. 6).
# Исключение объявляется в самом файле маркером ниже; молчаливого пропуска нет,
# и число исключённых файлов печатает сам прогон.
RETROSPECTIVE = '<!-- anchor-check: описывает прошлое'

# Абзац, ВВОДЯЩИЙ форму адреса, говорит о ней примерами: разрешать их не по
# чему. Исключение объявляется в самом абзаце маркером ниже — молчаливого
# пропуска нет, и число исключённых абзацев печатает сам прогон. Тот же приём
# у продуктового корпуса: docs/concept.md §Ссылки исключает собственные § как
# иллюстрации формы.
ILLUSTRATION = '<!-- anchor-check: иллюстрации формы -->'
# Имя-заполнитель («Имя», «Имя пассажа», «…») целью не бывает по построению.
PLACEHOLDERS = {'имя', 'имя пассажа', 'n', '…', 'x'}
# Адрес, взятый в обратные кавычки ЦЕЛИКОМ, — разговор О ФОРМЕ, а не адрес:
# так о ней говорят правила, которые её вводят. Различение механическое —
# см. `.claude/rules/structure.md`: адрес обратными кавычками не окружается.
CODE_SPAN_RE = re.compile(r'`§[^`]*`')

# Объявленный долг: адреса, неразрешимые на момент ввода расширенного
# детектора. Храним ПАРАМИ «файл + имя» без номеров строк — номер дрейфует от
# любой правки текста, и реестр начал бы врать раньше, чем долг погасится.
# Смысл храповика: НОВЫЙ неразрешимый адрес роняет прогон, объявленный —
# нет, а строка долга, которой в корпусе больше нет, роняет прогон тоже:
# иначе реестр гниёт и перестаёт означать долг.
DEBT_FILE = 'tools/anchor-debt.txt'
# Долг области кода — отдельным реестром той же механики: адреса кода чинит
# правка дерева кода, а не курация `.claude/**`, и владелец погашения другой.
CODE_DEBT_FILE = 'tools/anchor-code-debt.txt'
# Деревья кода: сервисы и общие артефакты, сквозной набор, фронт, манифесты.
CODE_ROOTS = ('services', 'tests', 'web', 'deploy')


PATH_RE = re.compile(r'[\w./-]*[\w-]+\.(?:md|json)')
BARE_RE = re.compile(r'`([a-z][a-z0-9-]{3,})`')
QUOTED_RE = re.compile(r'§«(?P<name>[^»]{2,200})»')
# Четвёртая форма: §`имяВеличины` — адрес к ВЕЛИЧИНЕ исполнимой спеки.
# Санкционирована docs/concept.md §Ссылки: величина опознаётся по имени, а её
# единственность энфорсится детектором областей видимости. Цель у неё — .json,
# а не .md, поэтому разрешается она по перечню объявленных величин, а не по
# заголовкам: прежняя редакция формы не видела вовсе, а в кавычечной записи
# давала на неё ЛОЖНУЮ находку (цель .json из кандидатов отбрасывалась).
VALUE_RE = re.compile(r'§`(?P<name>[A-Za-z][A-Za-z0-9.]*)`')
# Относительные указания — не имена пассажей: «ниже», «выше» и им подобные
# адресуют положение в тексте, а не пассаж, и разрешать их не по чему.
RELATIVE = {'ниже', 'выше', 'далее', 'ранее', 'там', 'тут', 'секция', 'секции',
            'шапка', 'шапке', 'шапку', 'шапки'}
# Голая форма к ВЕЛИЧИНЕ спеки: §имяВеличины при пути `docs/spec/*.json`,
# названном в том же абзаце. Обратных кавычек у javadoc нет, и форма 4 в коде
# пишется без них; разрешается она тем же перечнем объявленных величин, а
# снятая либо переименованная величина остаётся дефектом.
SPEC_PATH_RE = re.compile(r'docs/spec/[\w.-]+\.json')
# Адрес раздела ВНЕШНЕГО стандарта («RFC 9110 §7.6.1») — ссылка вне корпуса:
# разрешать его не по чему, и дефектом он не является.
EXTERNAL_STANDARD_RE = re.compile(r'(?<![\w])RFC\s*\d+\s*$')

# Голая форма: имя начинается с буквы или цифры (§-адрес — это слово «§-адрес»,
# а не адрес) и тянется до разделителя; точка внутри токена (AG1.5) не граница.
PLAIN_RE = re.compile(r'§(?![«`\W])(?P<name>[^\s,;:!?()«»`*\n]+'
                      r'(?:\s+[^\s,;:!?()«»`*\n]+){0,6})')
ORDINAL_RE = re.compile(r'^([A-Za-zА-Яа-яЁё]{0,4}\d+(?:\.\d+)*[a-zа-яё]?)[.)]?\s')


def norm(text):
    text = re.sub(r'\s+', ' ', text).strip().strip('.:;,—- ')
    for char in '`«»„“”"':
        text = text.replace(char, '')
    return text.replace('ё', 'е').lower()


def trim_tail(text):
    """Имя без метаданного хвоста: до первой скобки либо до « — »."""
    text = re.split(r'\s*\(', text)[0]
    text = re.split(r'\s+—\s+', text)[0]
    return norm(text)


def spec_values(base='.'):
    """Имена величин исполнимых спек: цель адресов формы §`имяВеличины`."""
    import json
    names = set()
    for path in glob.glob(os.path.join(base, 'docs', 'spec', '*.json')):
        try:
            with open(path, encoding='utf-8') as handle:
                for value in json.load(handle).get('values', []):
                    names.add(value['name'])
        except (OSError, ValueError):
            continue
    return names


def to_slash(path):
    """'/'-форма пути. glob на Windows возвращает '\\'-пути, а адреса корпуса,
    SKIP-шаблоны и строки долга несут '/': без нормализации SKIP мёртв,
    сравнение с долгом не сходится, и здоровый корпус давал тысячи ложных
    «неразрешимых» плюс весь долг «устаревшим» (backlog §«Средовой дефицит
    автономного RUN тестов»; починено `GAPS_CLOSE_33`, узел 9)."""
    return path.replace('\\', '/')


def skipped(path, patterns=SKIP):
    """Путь вне области: сверка по '/'-форме, какую бы форму ни отдал glob."""
    return any(skip in '/' + to_slash(path) for skip in patterns)


def index_files(base='.'):
    """Файлы, по которым разрешаются имена целей (по умолчанию — весь репозиторий)."""
    patterns = ('**/*.md', '**/*.json', '.claude/**/*.md', '.claude/**/*.json')
    return [to_slash(path) for pattern in patterns
            for path in glob.glob(os.path.join(base, pattern), recursive=True)
            if not to_slash(os.path.relpath(path, base)).startswith(('.git/', 'target/', '.claude-archive/'))]


class Resolver:
    """Индекс имён пассажей и целевых файлов."""

    def __init__(self, files):
        self.by_base = {}
        for path in files:
            base = os.path.basename(path)
            self.by_base.setdefault(base, []).append(path)
            self.by_base.setdefault(os.path.splitext(base)[0], []).append(path)
        self.cache = {}
        self.common_cache = {}
        self.by_base['__all__'] = [path for path in files if path.endswith('.md')]

    def targets(self, path):
        if path in self.cache:
            return self.cache[path]
        names = set()
        try:
            with open(path, encoding='utf-8') as handle:
                text = handle.read()
        except OSError:
            self.cache[path] = names
            return names
        passages = set()
        for match in re.finditer(r'^#{1,6}\s+(.+?)\s*$', text, re.M):
            passages.add(match.group(1))
        for match in re.finditer(r'^\s*(?:[-*]\s+|\|\s*|\d+\.\s+)?\*\*(.+?)\*\*', text, re.M | re.S):
            if len(match.group(1)) < 220:
                passages.add(match.group(1))
        for passage in passages:
            names.add(norm(passage))
            names.add(trim_tail(passage))
            ordinal = ORDINAL_RE.match(passage)
            if ordinal:
                names.add(norm(ordinal.group(1)))
        self.cache[path] = names
        return names

    def candidates(self, path, paragraph):
        found = {path}
        for match in list(PATH_RE.finditer(paragraph)) + list(BARE_RE.finditer(paragraph)):
            token = match.group(1) if match.re is BARE_RE else match.group(0)
            if os.path.exists(token):
                found.add(token)
                continue
            hits = self.by_base.get(os.path.basename(token)) or self.by_base.get(token) or []
            if len(hits) == 1:
                found.update(hits)
        return {candidate for candidate in found if candidate.endswith('.md')}

    def resolves(self, name, candidates):
        return any(name in self.targets(candidate) for candidate in candidates)

    ORDINAL_ONLY = re.compile(r'[A-Za-zА-Яа-яЁё]{0,4}\d+(?:\.\d+)*[a-zа-яё]?')

    def common(self, name):
        """Имя — РОД раздела, а не адрес: встречается заголовком во многих файлах.

        Родовое упоминание («§Персистентность доменного дока») называет не
        файл, а класс файлов, и разрешать его в конкретную цель не по чему.
        Порог — три файла: имя, стоящее в одном-двух, родом не является.
        """
        if self.ORDINAL_ONLY.fullmatch(name):
            # Номер родом раздела не бывает: «§13» без названной цели —
            # адрес в никуда, а не упоминание класса файлов.
            return False
        if name in self.common_cache:
            return self.common_cache[name]
        hits = 0
        for path in self.by_base.get('__all__', []):
            if name in self.targets(path):
                hits += 1
                if hits >= 3:
                    break
        self.common_cache[name] = hits >= 3
        return self.common_cache[name]


def prefixes(name):
    """Префиксы голого имени от длинного к короткому; хвост-точка снимается."""
    words = name.split()
    while words and words[-1].endswith('.') and not re.search(r'\d\.$', words[-1]):
        words[-1] = words[-1][:-1]
        if not words[-1]:
            words.pop()
    for length in range(len(words), 0, -1):
        yield ' '.join(words[:length])


def scan(roots, index_base='.', top_level=None):
    """Разбор всех адресов области. (проверено, дефекты, прошлое, иллюстрации,
    файлов по каталогу) либо отказ.

    `roots` — каталог либо кортеж каталогов; `top_level` — каталог, чьи
    `.md` верхнего уровня входят в область сверх них (корень репозитория).
    Каталог области, которого нет либо в котором нет ни одного файла, —
    отказ: область, сузившаяся молча, неотличима от чистой.

    Индекс имён строится по `index_base` — по умолчанию по всему
    репозиторию: адрес из `.claude/**` законно указывает в `docs/**`.
    Фикстура батареи индексируется своим каталогом, иначе её имена
    разрешались бы живым корпусом и ось ничего бы не доказывала.
    """
    roots = (roots,) if isinstance(roots, str) else tuple(roots)
    for root in roots:
        if not os.path.isdir(root):
            return None, 'каталог %s не найден' % root
    resolver = Resolver(index_files(index_base))
    values = spec_values(index_base)
    if not resolver.by_base:
        return None, 'индекс имён пуст — разрешать не по чему'
    pages, composition = [], []
    for root in roots:
        found = [to_slash(path) for path in sorted(glob.glob(root + '/**/*.md', recursive=True))
                 if not skipped(path)]
        if not found:
            return None, 'в области %s нет ни одного файла — проверять нечего' % root
        pages.extend(found)
        composition.append((to_slash(root), len(found)))
    if top_level is not None:
        found = [to_slash(os.path.normpath(path))
                 for path in sorted(glob.glob(os.path.join(top_level, '*.md')))]
        pages.extend(found)
        composition.append(('корень', len(found)))
    bad, total, retrospective, illustrations = [], 0, [], 0
    for path in pages:
        with open(path, encoding='utf-8') as handle:
            text = handle.read()
        if RETROSPECTIVE in text[:2000]:
            retrospective.append(path)
            continue
        for start_line, paragraph in md_paragraphs(text):
            counted, illustrated = check_paragraph(path, start_line, paragraph,
                                                   resolver, values, bad)
            total += counted
            illustrations += illustrated
    return (total, bad, retrospective, illustrations, composition), None


def md_paragraphs(text):
    """Абзацы markdown-файла: (номер первой строки, текст)."""
    line = 1
    for paragraph in re.split(r'\n\s*\n', text):
        yield line, paragraph
        line += paragraph.count('\n') + 2


def check_paragraph(path, start_line, paragraph, resolver, values, bad):
    """Разбор адресов одного абзаца; дефекты — в `bad`.

    Возвращает (адресов посчитано, иллюстраций исключено). Одна процедура на
    обе области: адрес кода разрешается по тем же правилам, что адрес
    `.claude/**` (`.claude/rules/structure.md` §Принципы), — второй набор
    правил разошёлся бы с первым при первой же правке.
    """
    flat = re.sub(r'\s*\n\s*', ' ', paragraph)
    candidates = resolver.candidates(path, flat)
    if ILLUSTRATION in flat:
        return 0, 1
    flat, quoted = CODE_SPAN_RE.subn('`…`', flat)
    total = 0
    # Внешний файл в абзаце не назван: у markdown-файла кандидат — он сам,
    # у файла кода кандидатов нет вовсе (он не .md).
    generic = not (candidates - {path})
    for match in QUOTED_RE.finditer(flat):
        total += 1
        name = norm(match.group('name'))
        if name in PLACEHOLDERS:
            continue
        if resolver.resolves(name, candidates):
            continue
        if generic and resolver.common(name):
            continue
        bad.append((path, start_line, '«%s»' % match.group('name')))
    for match in VALUE_RE.finditer(flat):
        total += 1
        if match.group('name') not in values:
            bad.append((path, start_line, '`%s`' % match.group('name')))
    names_spec = SPEC_PATH_RE.search(flat) is not None
    for match in PLAIN_RE.finditer(flat):
        raw = match.group('name')
        if raw.split()[0].lower().rstrip('.') in RELATIVE:
            continue
        if norm(raw) in PLACEHOLDERS:
            continue
        if EXTERNAL_STANDARD_RE.search(flat[:match.start()]):
            continue
        total += 1
        if names_spec and raw.split()[0].rstrip('.') in values:
            continue
        if any(resolver.resolves(norm(prefix), candidates) for prefix in prefixes(raw)):
            continue
        if generic and any(resolver.common(norm(prefix)) for prefix in prefixes(raw)):
            continue
        bad.append((path, start_line, raw.split()[0]))
    return total, quoted


# --- область кода ------------------------------------------------------------

# Носители комментария по расширению: маркер строчного комментария, пара
# блочного, кавычки строкового литерала. Литерал разбирается затем, чтобы
# маркер комментария внутри строки («http://») не открыл комментарий, и затем,
# что адрес в сообщении проверки (`.as("… §«Имя»")`) — такой же адрес.
CODE_FORMS = {
    '.java': ('//', ('/*', '*/'), '"\''),
    '.sql': ('--', ('/*', '*/'), "'"),
    '.yaml': ('#', None, ''),
    '.yml': ('#', None, ''),
    '.xml': (None, ('<!--', '-->'), ''),
}
CODE_SKIP = ('/target/', '/node_modules/', '/dist/', '/build/')
# Граница абзаца внутри одного javadoc помимо пустой строки: абзацный тег и
# блочные теги — у каждого свой предмет, и путь, названный в одном, другому
# целью не служит.
JAVADOC_BREAK_RE = re.compile(r'^(?:<p>|@(?:param|return|throws|see)\b)')
# Разметка javadoc в имени пассажа — форма записи, а не имя: `{@code ERROR}`
# в javadoc есть `ERROR` в markdown-заголовке, `<b>` — жирный. Снимается до
# разрешения, как снимаются обратные кавычки в самом имени (norm).
JAVADOC_INLINE_RE = re.compile(r'\{@(?:code|link|linkplain|literal)\s+([^{}]*)\}')
HTML_TAG_RE = re.compile(r'</?(?:p|b|i|em|strong|code|tt|ul|ol|li|br|pre)\b[^>]*>')


def unmark_javadoc(text):
    """Текст javadoc без встроенных тегов и HTML-разметки."""
    return HTML_TAG_RE.sub('', JAVADOC_INLINE_RE.sub(r'\1', text))


def lex(text, line_marker, block, quotes):
    """Куски комментариев и литералов: (строка, род, id носителя, содержимое).

    Род — 'line' (строчный комментарий), 'block' (строка блочного) либо
    'string' (литерал). id различает носители: два блочных комментария подряд
    — два абзаца, а не один.
    """
    pieces = []
    i, n, line, ident = 0, len(text), 1, 0
    while i < n:
        char = text[i]
        if char == '\n':
            line += 1
            i += 1
            continue
        if line_marker and text.startswith(line_marker, i) and \
                (line_marker != '#' or i == 0 or text[i - 1] in ' \t\n'):
            end = text.find('\n', i)
            end = n if end < 0 else end
            ident += 1
            pieces.append((line, 'line', ident, text[i + len(line_marker):end]))
            i = end
            continue
        if block and text.startswith(block[0], i):
            end = text.find(block[1], i + len(block[0]))
            end = n if end < 0 else end
            body = text[i + len(block[0]):end]
            ident += 1
            for offset, part in enumerate(body.split('\n')):
                pieces.append((line + offset, 'block', ident, part))
            line += body.count('\n')
            i = end + len(block[1])
            continue
        if quotes and text.startswith('"""', i) and '"' in quotes:
            end = text.find('"""', i + 3)
            end = n if end < 0 else end
            body = text[i + 3:end]
            ident += 1
            for offset, part in enumerate(body.split('\n')):
                pieces.append((line + offset, 'string', ident, part))
            line += body.count('\n')
            i = end + 3
            continue
        if char in quotes:
            j = i + 1
            while j < n and text[j] not in (char, '\n'):
                j += 2 if text[j] == '\\' else 1
            pieces.append((line, 'string', None, text[i + 1:j]))
            i = j + 1 if j < n and text[j] == char else j
            continue
        i += 1
    return pieces


def clean_comment(kind, content):
    """Текст строки комментария без маркеров: `*` javadoc, `/` у `///`."""
    content = content.strip()
    if kind == 'block':
        content = content.lstrip('*').strip()
    else:
        content = content.lstrip('/-#').strip()
    return content


def code_paragraphs(text, form):
    """Абзацы файла кода: (номер первой строки, текст).

    Абзац комментария кончается на пустой строке комментария, на смене
    носителя (другой блочный комментарий, код между строчными), на `<p>` и
    блочных тегах javadoc. Абзац литералов — подряд идущие строки с
    литералами, склеенные без разделителя.
    """
    paragraphs = []
    current, current_line, previous = [], None, None
    strings, strings_line, strings_previous = [], None, None

    def flush():
        if current:
            paragraphs.append((current_line, '\n'.join(current)))

    for line, kind, ident, content in lex(text, *form):
        if kind == 'string':
            if strings and line not in (strings_previous, strings_previous + 1):
                paragraphs.append((strings_line, ''.join(strings)))
                strings = []
            if not strings:
                strings_line = line
            strings.append(content)
            strings_previous = line
            continue
        cleaned = clean_comment(kind, content)
        key = (kind, ident if kind == 'block' else None)
        adjacent = previous is not None and previous[0] == key and \
            line in (previous[1], previous[1] + 1)
        if not cleaned or not adjacent or JAVADOC_BREAK_RE.match(cleaned):
            flush()
            current, current_line = [], line
        if cleaned:
            if not current:
                current_line = line
            current.append(cleaned)
        previous = (key, line)
    flush()
    if strings:
        paragraphs.append((strings_line, ''.join(strings)))
    return paragraphs


def code_pages(roots):
    """Файлы деревьев кода, несущие знак §: (измеримые, вне объявленных форм)."""
    measured, unmeasured = [], []
    for root in roots:
        for path in sorted(glob.glob(os.path.join(root, '**', '*'), recursive=True)):
            path = to_slash(path)
            if not os.path.isfile(path) or skipped(path, CODE_SKIP):
                continue
            try:
                with open(path, encoding='utf-8') as handle:
                    text = handle.read()
            except (OSError, UnicodeDecodeError):
                continue
            if '§' not in text:
                continue
            extension = os.path.splitext(path)[1].lower()
            if extension == '.md' or extension in CODE_FORMS:
                measured.append((path, extension, text))
            else:
                unmeasured.append(path)
    return measured, unmeasured


def scan_code(roots, index_base='.'):
    """Разбор адресов деревьев кода. (проверено, дефекты, файлов, вне форм) либо отказ."""
    present = [root for root in roots if os.path.isdir(root)]
    if not present:
        return None, 'ни одного дерева кода из %s не найдено' % ', '.join(roots)
    resolver = Resolver(index_files(index_base))
    values = spec_values(index_base)
    measured, unmeasured = code_pages(present)
    if not measured and not unmeasured:
        return None, 'в деревьях кода нет ни одного файла со знаком § — проверять нечего'
    bad, total = [], 0
    for path, extension, text in measured:
        if extension == '.md':
            paragraphs = md_paragraphs(text)
        else:
            paragraphs = code_paragraphs(text, CODE_FORMS[extension])
            if extension == '.java':
                paragraphs = [(line, unmark_javadoc(body)) for line, body in paragraphs]
        for start_line, paragraph in paragraphs:
            if '§' not in paragraph:
                continue
            counted, _illustrated = check_paragraph(path, start_line, paragraph,
                                                    resolver, values, bad)
            total += counted
    return (total, bad, len(measured), unmeasured), None


# --- батарея осей ------------------------------------------------------------

def battery():
    axes = []
    with tempfile.TemporaryDirectory() as work:
        def area(name, files):
            root = os.path.join(work, name)
            os.makedirs(root, exist_ok=True)
            for filename, body in files.items():
                with open(os.path.join(root, filename), 'w', encoding='utf-8') as handle:
                    handle.write(body)
            return root

        def axis(title, root, expect_bad):
            result, refusal = scan(root, root)
            if refusal:
                axes.append((title, not expect_bad and False, 'отказ: ' + refusal))
                return
            total, bad = result[0], result[1]
            axes.append((title, bool(bad) == expect_bad,
                         'адресов %d, неразрешимых %d' % (total, len(bad))))

        axis('1. кавычечная форма: пассажа нет',
             area('a', {'f.md': '# Ф\n\nСм. §«Пассажа такого нет вовсе».\n'}), True)
        axis('2. кавычечная форма, чужой файл, названный в абзаце',
             area('b', {'target.md': '# Ц\n\n## Настоящий пассаж\n',
                        'src.md': '# И\n\nДом — `target.md` §«Выдуманный пассаж».\n'}), True)
        axis('3. голая форма: пассажа нет',
             area('c', {'f.md': '# Ф\n\nСм. §Небывалый.\n'}), True)
        axis('4. голая форма многословная: ни один префикс не разрешается',
             area('d', {'f.md': '# Ф\n\n## Есть такой пассаж\n\nСм. §Совсем другой пассаж тут.\n'}), True)
        axis('5. ординальная форма: заголовка с таким номером нет',
             area('e', {'f.md': '# Ф\n\n## 3. Настоящий раздел\n\nСм. §7.\n'}), True)
        axis('6. сокращение НЕ по границе хвоста',
             area('g', {'target.md': '# Ц\n\n## Длинное имя пассажа продолжается дальше\n',
                        'src.md': '# И\n\n`target.md` §«Длинное имя пассажа».\n'}), True)
        axis('7. контроль: заголовок разрешается',
             area('h', {'f.md': '# Ф\n\n## Есть такой пассаж\n\nСм. §«Есть такой пассаж».\n'}), False)
        axis('8. контроль: лид-жирный пассаж разрешается',
             area('i', {'f.md': '# Ф\n\n**Жирный лид.** Текст.\n\nСм. §«Жирный лид».\n'}), False)
        axis('9. контроль: законный хвост опускается',
             area('j', {'f.md': '# Ф\n\n## Имя пассажа (решение держателя, 2026-08-26)\n\nСм. §«Имя пассажа».\n'}), False)
        axis('10. контроль: голая форма разрешается заголовком',
             area('k', {'f.md': '# Ф\n\n## Персистентность\n\nСм. §Персистентность.\n'}), False)
        axis('11. контроль: многословная голая форма разрешается длинным префиксом',
             area('l', {'f.md': '# Ф\n\n## Структура доменной модели\n\nСм. §Структура доменной модели, далее по тексту.\n'}), False)
        axis('12. контроль: ординал разрешается номером заголовка',
             area('m', {'f.md': '# Ф\n\n## 6a. Пост-хок гейт\n\nСм. §6a.\n'}), False)
        # оси формы §`имяВеличины` — на фикстуре со своим каталогом спек
        spec_area = os.path.join(work, 'величины')
        os.makedirs(os.path.join(spec_area, 'docs', 'spec'), exist_ok=True)
        with open(os.path.join(spec_area, 'docs', 'spec', 'проба.json'), 'w', encoding='utf-8') as handle:
            handle.write('{"subject": "проба", "values": [{"name": "declaredValueName", "expr": "1"}]}')
        with open(os.path.join(spec_area, 'f.md'), 'w', encoding='utf-8') as handle:
            handle.write('# Ф\n\nФорма — `docs/spec/проба.json` §`missingValueName`.\n')
        result, refusal = scan(spec_area, spec_area)
        axes.append(('12d. форма §`имяВеличины`: величины с таким именем нет',
                     bool(result and result[1]),
                     'неразрешимых %d' % (len(result[1]) if result else -1)))
        with open(os.path.join(spec_area, 'f.md'), 'w', encoding='utf-8') as handle:
            handle.write('# Ф\n\nФорма — `docs/spec/проба.json` §`declaredValueName`.\n')
        result, refusal = scan(spec_area, spec_area)
        axes.append(('12e. контроль: объявленная величина разрешается',
                     bool(result and not result[1]),
                     'неразрешимых %d' % (len(result[1]) if result else -1)))

        axis('12a. имя-заполнитель адресом не считается',
             area('s', {'f.md': '# Ф\n\nФормы адреса — `§«Имя»` и `§Имя`.\n'}), False)
        axis('12c. адрес в обратных кавычках — разговор о форме, не адрес',
             area('v', {'f.md': '# Ф\n\nФорма адреса — `§«Такого пассажа нет»`.\n'}), False)
        axis('12b. объявленный маркер исключает абзац-иллюстрацию',
             area('u', {'f.md': '# Ф\n\n<!-- anchor-check: иллюстрации формы -->\n'
                                'Битым будет §«Такого пассажа нет».\n'}), False)
        axis('13a. объявленный маркер исключает файл-запись прошлого',
             area('r', {'f.md': '<!-- anchor-check: описывает прошлое -->\n\n# Ф\n\nСм. §Небывалый.\n'}), False)
        axis('13. контроль: §-слово адресом не считается',
             area('n', {'f.md': '# Ф\n\nРечь о §-адресах и §-якорях как о словах.\n'}), False)
        axis('14. контроль: относительное указание адресом не считается',
             area('o', {'f.md': '# Ф\n\nПеречень §ниже, а довод §выше.\n'}), False)
        axis('15. родовое упоминание не считается дефектом, а адресное — считается',
             area('p', {'один.md': '# О\n\n## Персистентность\n',
                        'два.md': '# Д\n\n## Персистентность\n',
                        'три.md': '# Т\n\n## Персистентность\n',
                        'src.md': '# И\n\nСм. §Персистентность доменного дока.\n'}), False)
        axis('16. родовое имя, но файл назван — адрес проверяется по нему',
             area('q', {'один.md': '# О\n\n## Персистентность\n',
                        'два.md': '# Д\n\n## Персистентность\n',
                        'три.md': '# Т\n\n## Персистентность\n',
                        'цель.md': '# Ц\n\n## Другое\n',
                        'src.md': '# И\n\nСм. `цель.md` §Персистентность.\n'}), True)

        # оси храповика долга — на синтетическом перечне, без обращения к корпусу
        sample = [('ф.md', 7, 'Имя пассажа')]
        pair = ('ф.md', 'Имя пассажа')
        fresh, stale = ratchet(sample, Counter())
        axes.append(('17. новый неразрешимый адрес роняет прогон', len(fresh) == 1 and not stale,
                     'новых %d, устаревших %d' % (len(fresh), len(stale))))
        fresh, stale = ratchet(sample, Counter([pair]))
        axes.append(('18. объявленный долг прогон не роняет', not fresh and not stale,
                     'новых %d, устаревших %d' % (len(fresh), len(stale))))
        fresh, stale = ratchet([], Counter([pair]))
        axes.append(('19. строка долга без вхождения в корпусе роняет прогон',
                     not fresh and len(stale) == 1,
                     'новых %d, устаревших %d' % (len(fresh), len(stale))))
        fresh, stale = ratchet(sample + [('ф.md', 40, 'Имя пассажа')], Counter([pair]))
        axes.append(('25. второй битый адрес в паре, объявленной одной строкой, роняет прогон',
                     len(fresh) == 1 and fresh[0][1] == 40 and not stale,
                     'новых %d, устаревших %d' % (len(fresh), len(stale))))
        fresh, stale = ratchet(sample, Counter([pair, pair]))
        axes.append(('26. пара объявлена дважды, стоит однажды — строка долга устарела',
                     not fresh and len(stale) == 1,
                     'новых %d, устаревших %d' % (len(fresh), len(stale))))
        # чтение реестра: повтор строки — второе вхождение, а не дубль
        debt_probe = os.path.join(work, 'долг.txt')
        with open(debt_probe, 'w', encoding='utf-8') as handle:
            handle.write('# шапка\nф.md\tИмя пассажа\nф.md\tИмя пассажа\n')
        read = declared_debt(debt_probe)
        axes.append(('25a. повтор строки реестра читается вторым вхождением пары',
                     read[pair] == 2, 'вхождений пары в прочитанном долге: %d' % read[pair]))

        # ось формы пути: источники дефектов и сверка с долгом — '/'-форма
        # на любой платформе (Windows-glob отдаёт '\\'; см. to_slash)
        nested_root = os.path.join(work, 'формапути')
        os.makedirs(os.path.join(nested_root, 'вложено'), exist_ok=True)
        with open(os.path.join(nested_root, 'вложено', 'f.md'), 'w', encoding='utf-8') as handle:
            handle.write('# Ф\n\nСм. §«Пассажа такого нет вовсе».\n')
        result, refusal = scan(nested_root, nested_root)
        path_ok = bool(result) and len(result[1]) == 1 and \
            all('\\' not in source for source, _line, _name in result[1])
        axes.append(('22. источник дефекта — «/»-форма пути на любой платформе',
                     path_ok,
                     'дефектов %d; форм с обратной косой: %d'
                     % (len(result[1]) if result else -1,
                        sum(1 for s, _l, _n in (result[1] if result else []) if '\\' in s))))

        # оси фильтра области: «\\»-форма пути (строкой, а не glob'ом — ось
        # падает на любой платформе, а не только там, где glob её отдаёт) и
        # применение фильтра самим прогоном области
        archive_path = '.claude\\work\\history\\2026-01-01-задача\\f.md'
        live_path = '.claude\\rules\\f.md'
        axes.append(('23. фильтр области отбрасывает архив в «\\»-форме пути и не трогает живое',
                     skipped(archive_path) and not skipped(live_path),
                     'архив отброшен: %s; живое отброшено: %s'
                     % (skipped(archive_path), skipped(live_path))))
        skip_root = os.path.join(work, 'фильтр')
        os.makedirs(os.path.join(skip_root, 'history'), exist_ok=True)
        with open(os.path.join(skip_root, 'history', 'f.md'), 'w', encoding='utf-8') as handle:
            handle.write('# Ф\n\nСм. §«Пассажа такого нет вовсе».\n')
        with open(os.path.join(skip_root, 'живое.md'), 'w', encoding='utf-8') as handle:
            handle.write('# Ж\n\n## Есть такой пассаж\n\nСм. §«Есть такой пассаж».\n')
        result, refusal = scan(skip_root, skip_root)
        axes.append(('24. прогон области не несёт архива в перечень дефектов',
                     bool(result) and not result[1] and result[4] == [(to_slash(skip_root), 1)],
                     refusal or 'дефектов %d, файлов области %s' % (len(result[1]), result[4])))
        # ось корня: `.md` верхнего уровня входят в область сверх каталогов
        top_root = os.path.join(work, 'корень')
        os.makedirs(os.path.join(top_root, 'каталог'), exist_ok=True)
        with open(os.path.join(top_root, 'каталог', 'f.md'), 'w', encoding='utf-8') as handle:
            handle.write('# Ф\n\nТекст.\n')
        with open(os.path.join(top_root, 'README.md'), 'w', encoding='utf-8') as handle:
            handle.write('# Р\n\nСм. §«Пассажа такого нет вовсе».\n')
        result, refusal = scan(os.path.join(top_root, 'каталог'), top_root, top_level=top_root)
        axes.append(('27. `.md` корня репозитория в области: битый адрес README найден',
                     bool(result) and len(result[1]) == 1,
                     refusal or 'дефектов %d' % len(result[1])))

        result, refusal = scan(os.path.join(work, 'нет-такого'), work)
        axes.append(('20. каталог не найден — проверка отказывает', bool(refusal),
                     refusal or 'проверка отчиталась'))
        empty = os.path.join(work, 'пусто')
        os.makedirs(empty, exist_ok=True)
        result, refusal = scan(empty, empty)
        axes.append(('21. область пуста — проверка отказывает', bool(refusal),
                     refusal or 'проверка отчиталась'))
        axes.extend(code_battery(work))
    return axes


def code_battery(work):
    # Оси области кода: каждая форма носителя и каждая граница абзаца.
    axes = []
    target = '# Ц\n\n## Настоящий пассаж кода\n\n## Заявка в `ERROR` читается наблюдением\n\n' \
             '**Жирный лид кода.** Текст.\n'

    def case(title, name, files, expect_bad, expect_unmeasured=False):
        root = os.path.join(work, 'код-' + name)
        for filename, body in dict({'цель-кода.md': target}, **files).items():
            full = os.path.join(root, filename)
            os.makedirs(os.path.dirname(full), exist_ok=True)
            with open(full, 'w', encoding='utf-8') as handle:
                handle.write(body)
        result, refusal = scan_code((root,), root)
        if refusal:
            axes.append((title, False, 'отказ: ' + refusal))
            return
        total, bad, _files, unmeasured = result
        passed = bool(bad) == expect_bad and bool(unmeasured) == expect_unmeasured
        axes.append((title, passed, 'адресов %d, неразрешимых %d, вне форм %d'
                     % (total, len(bad), len(unmeasured))))

    case('К1. javadoc: пассажа в названном файле нет', 'к1',
         {'A.java': '/**\n * См. цель-кода.md §«Выдуманный пассаж».\n */\nclass A {}\n'}, True)
    case('К2. контроль: javadoc-адрес через перенос строки разрешается заголовком', 'к2',
         {'A.java': '/**\n * См. цель-кода.md §«Настоящий\n * пассаж кода».\n */\nclass A {}\n'}, False)
    case('К3. контроль: {@code} в имени пассажа — форма записи, а не имя', 'к3',
         {'A.java': '/**\n * цель-кода.md §«Заявка в {@code ERROR} читается\n'
                    ' * наблюдением».\n */\nclass A {}\n'}, False)
    case('К4. контроль: лид-жирный пассаж разрешается', 'к4',
         {'A.java': '/** цель-кода.md §«Жирный лид кода». */\nclass A {}\n'}, False)
    case('К5. строчный комментарий //: пассажа нет', 'к5',
         {'A.java': 'class A {\n    // цель-кода.md §«Выдуманный пассаж»\n}\n'}, True)
    case('К6. литерал, склеенный через перенос: пассажа нет', 'к6',
         {'A.java': 'class A {\n    String s = "цель-кода.md §«Выдуманный "\n'
                    '        + "пассаж»";\n}\n'}, True)
    case('К7. контроль: литерал, склеенный через перенос, разрешается', 'к7',
         {'A.java': 'class A {\n    String s = "цель-кода.md §«Настоящий "\n'
                    '        + "пассаж кода»";\n}\n'}, False)
    case('К8. граница абзаца <p>: файл, названный в соседнем абзаце, целью не служит', 'к8',
         {'A.java': '/**\n * Дом — цель-кода.md.\n *\n * <p>См. §«Настоящий пассаж кода».\n'
                    ' */\nclass A {}\n'}, True)
    case('К9. граница носителя: файл из другого javadoc целью не служит', 'к9',
         {'A.java': '/** Дом — цель-кода.md. */\nclass A {\n'
                    '    /** См. §«Настоящий пассаж кода». */\n    int x;\n}\n'}, True)
    case('К10. SQL, комментарий --: пассажа нет', 'к10',
         {'V1.sql': '-- цель-кода.md §«Выдуманный пассаж»\ncreate table t (id int);\n'}, True)
    case('К11. YAML, комментарий #: пассажа нет', 'к11',
         {'a.yaml': 'a:\n  # цель-кода.md §«Выдуманный пассаж»\n  b: 1\n'}, True)
    case('К12. XML, комментарий <!-- -->: пассажа нет', 'к12',
         {'pom.xml': '<project>\n  <!-- цель-кода.md\n  §«Выдуманный пассаж» -->\n</project>\n'}, True)
    spec_body = '{"subject": "проба", "values": [{"name": "declaredValueName", "expr": "1"}]}'
    case('К13. голая форма к величине при пути .json: величины нет', 'к13',
         {'docs/spec/проба.json': spec_body,
          'A.java': '/** docs/spec/проба.json §missingValueName. */\nclass A {}\n'}, True)
    case('К14. контроль: голая форма к объявленной величине разрешается', 'к14',
         {'docs/spec/проба.json': spec_body,
          'A.java': '/** docs/spec/проба.json §declaredValueName. */\nclass A {}\n'}, False)
    case('К15. контроль: раздел внешнего стандарта адресом корпуса не считается', 'к15',
         {'A.java': '/** Заголовки соединения (RFC 9110 §7.6.1). */\nclass A {}\n'}, False)
    case('К18. контроль: «§шапка» — указание на шапку своего файла, не адрес', 'к18',
         {'A.java': 'class A {\n    // рёбра пишет полный выход (§шапка класса).\n}\n'}, False)
    case('К16. файл со знаком § вне объявленных форм — не измерен, и это названо', 'к16',
         {'a.ts': '// цель-кода.md §«Настоящий пассаж кода»\n'}, False, True)
    result, refusal = scan_code((os.path.join(work, 'нет-дерева-кода'),), work)
    axes.append(('К17. деревьев кода нет — проверка отказывает', bool(refusal),
                 refusal or 'проверка отчиталась'))
    return axes


def ratchet(bad, debt):
    """Храповик долга: (новые неразрешимые, устаревшие строки долга).

    Долг — мультимножество пар: строка реестра разрешает одно вхождение.
    Вхождение сверх объявленного числа — новое (в порядке файла новым
    считается последнее); строка сверх числа вхождений — устаревшая.
    """
    observed = Counter((source, name) for source, _line, name in bad)
    seen, fresh = Counter(), []
    for source, line, name in bad:
        seen[(source, name)] += 1
        if seen[(source, name)] > debt[(source, name)]:
            fresh.append((source, line, name))
    return fresh, sorted((debt - observed).elements())


def declared_debt(debt_file=DEBT_FILE):
    """Объявленный долг: мультимножество пар «файл, имя» — строка на вхождение."""
    debt = Counter()
    if not os.path.exists(debt_file):
        return debt
    with open(debt_file, encoding='utf-8') as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            path, _, name = line.partition('\t')
            debt[(path.strip(), name.strip())] += 1
    return debt


def report(fresh, stale, debt, debt_file):
    """Печать новых дефектов и устаревших строк долга."""
    for source, line, name in fresh:
        declared = debt[(source, name)]
        tail = ' (пара объявлена в долге %d раз — вхождение сверх объявленного)' % declared \
            if declared else ''
        print('  НОВЫЙ ДЕФЕКТ: %s:%d → §%s%s' % (source, line, name, tail))
    for source, name in stale:
        print('  СТРОКА ДОЛГА УСТАРЕЛА (адрес погашен — снять строку из %s): %s → §%s'
              % (debt_file, source, name))


def main():
    explicit = len(sys.argv) > 1
    axes = battery()
    print('--- батарея осей детектора (исполняется той же командой)')
    for title, passed, observed in axes:
        print('  %s: %s — %s' % ('доказана' if passed else 'НЕ ДОКАЗАНА', title, observed))
    broken = [title for title, passed, _ in axes if not passed]
    if broken:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: недоказанных осей %d — число адресов '
              'ничего не удостоверяло бы' % len(broken))
        return 2

    if explicit:
        result, refusal = scan(sys.argv[1])
    else:
        result, refusal = scan(MD_AREAS, top_level='.')
    if refusal:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: ' + refusal)
        return 2
    total, bad, retrospective, illustrations, composition = result
    print('--- область .md: %s' % '; '.join('%s — файлов %d' % part for part in composition))
    # Реестр долга корпуса адресует область по умолчанию; у явного
    # каталога-аргумента долга нет — неразрешимое там печатается целиком.
    debt = Counter() if explicit else declared_debt()
    fresh, stale = ratchet(bad, debt)
    print('адресов проверено: %d; неразрешимых: %d (из них объявленным долгом: %d); '
          'НОВЫХ НЕРАЗРЕШИМЫХ: %d; строк долга, которых в корпусе больше нет: %d'
          % (total, len(bad), len(bad) - len(fresh), len(fresh), len(stale)))
    print('файлов-записей прошлого исключено по маркеру: %d; '
          'иллюстраций формы (абзацев и адресов в кавычках): %d' % (len(retrospective), illustrations))
    for path in retrospective:
        print('  исключён по маркеру: %s' % path)
    report(fresh, stale, debt, DEBT_FILE)
    verdict = 1 if fresh or stale else 0
    if explicit:
        # Явный каталог-аргумент — прогон одной области .md; деревья кода
        # мерит прогон по умолчанию.
        return verdict

    print('--- деревья кода: %s' % ', '.join(CODE_ROOTS))
    result, refusal = scan_code(CODE_ROOTS)
    if refusal:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: ' + refusal)
        return 2
    total, bad, files, unmeasured = result
    code_debt = declared_debt(CODE_DEBT_FILE)
    fresh, stale = ratchet(bad, code_debt)
    print('файлов кода со знаком §: %d; адресов проверено: %d; неразрешимых: %d '
          '(из них объявленным долгом: %d); НОВЫХ НЕРАЗРЕШИМЫХ: %d; строк долга, '
          'которых в коде больше нет: %d; файлов вне объявленных форм: %d'
          % (files, total, len(bad), len(bad) - len(fresh), len(fresh), len(stale),
             len(unmeasured)))
    for path in unmeasured:
        print('  НЕ ИЗМЕРЕН (расширение вне CODE_FORMS — завести форму носителя): %s' % path)
    report(fresh, stale, code_debt, CODE_DEBT_FILE)
    return 1 if verdict or fresh or stale or unmeasured else 0


if __name__ == '__main__':
    sys.exit(main())

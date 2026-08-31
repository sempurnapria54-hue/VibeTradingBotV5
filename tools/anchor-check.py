#!/usr/bin/env python3
"""Предмет проверки: разрешимость §-адресов пассажей в `.claude/**`.

Адрес `§«Имя»` обязан разрешаться в заголовок либо лид-жирный пассаж
целевого файла (`.claude/rules/structure.md`). Целевой файл — путь,
названный в том же абзаце; при его отсутствии адрес внутрифайловый.

ФОРМЫ ПРЕДМЕТА, КОТОРЫЕ ДЕТЕКТОР ВИДИТ (объявлено, доказано осями батареи):

  1. кавычечная  §«Имя пассажа»       — имя целиком, границы явные
  2. голая       §Имя, §Имя пассажа   — имя без кавычек, граница по разбору
  3. ординальная §3, §6a, §AG1.5      — номер заголовка вместо его имени
  4. величина     §`имяВеличины`      — имя величины исполнимой спеки
                                        (docs/concept.md §Ссылки); цель — .json

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
указания («§ниже», «§выше») адресом не считаются вовсе.

ОРДИНАЛ. Заголовок, начинающийся с номера («### 3. Закрытие пробелов»,
«## AG1.5 Горизонт фандинга»), регистрирует и сам номер: `§3` — законная
форма адреса к нему.

БАТАРЕЯ ОСЕЙ ИСПОЛНЯЕТСЯ ЭТОЙ ЖЕ КОМАНДОЙ, до проверки: инструмент, чьи
оси доказываются отдельным скриптом, принимается по факту, что скрипт
когда-то прогоняли.

ОБЛАСТЬ. Свипаются `.md` каталога-аргумента (по умолчанию `.claude`) за
вычетом `history/`, `library/`, `progress/` и архива: там живут записи
прошлого, чьи адреса ведут в раскладку того момента. Область печатает сам
прогон — числом исключённых файлов и каталогов.

ОБЪЯВЛЕННЫЙ ДОЛГ (храповик). Адреса, неразрешимые на момент ввода
расширенного детектора, перечислены в `tools/anchor-debt.txt` парами
«файл + имя». Прогон падает на **новом** неразрешимом адресе и на строке
долга, которой в корпусе больше нет (погашенный адрес обязан уйти из
реестра, иначе реестр гниёт). Долг не прячется: его размер печатает
каждая строка итога.

Коды возврата: 0 — новых неразрешимых нет и реестр долга точен;
1 — есть новые неразрешимые либо устаревшие строки долга;
2 — ПРОВЕРКА НЕ ПРОВОДИЛАСЬ (ось не доказана, каталог не найден, индекс
имён пуст).
"""
import glob
import os
import re
import sys
import tempfile

SKIP = ('/history/', '/library/', '/progress/', '/.claude-archive/')

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
RELATIVE = {'ниже', 'выше', 'далее', 'ранее', 'там', 'тут', 'секция', 'секции'}

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


def index_files(base='.'):
    """Файлы, по которым разрешаются имена целей (по умолчанию — весь репозиторий)."""
    patterns = ('**/*.md', '**/*.json', '.claude/**/*.md', '.claude/**/*.json')
    return [path for pattern in patterns
            for path in glob.glob(os.path.join(base, pattern), recursive=True)
            if not os.path.relpath(path, base).startswith(('.git/', 'target/', '.claude-archive/'))]


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


def scan(root, index_base='.'):
    """Разбор всех адресов области. Возвращает (проверено, дефекты) либо отказ.

    Индекс имён строится по `index_base` — по умолчанию по всему
    репозиторию: адрес из `.claude/**` законно указывает в `docs/**`.
    Фикстура батареи индексируется своим каталогом, иначе её имена
    разрешались бы живым корпусом и ось ничего бы не доказывала.
    """
    if not os.path.isdir(root):
        return None, 'каталог %s не найден' % root
    resolver = Resolver(index_files(index_base))
    values = spec_values(index_base)
    if not resolver.by_base:
        return None, 'индекс имён пуст — разрешать не по чему'
    pages = [path for path in sorted(glob.glob(root + '/**/*.md', recursive=True))
             if not any(skip in '/' + path for skip in SKIP)]
    if not pages:
        return None, 'в области %s нет ни одного файла — проверять нечего' % root
    bad, total, retrospective, illustrations = [], 0, [], 0
    for path in pages:
        with open(path, encoding='utf-8') as handle:
            text = handle.read()
        if RETROSPECTIVE in text[:2000]:
            retrospective.append(path)
            continue
        line = 1
        for paragraph in re.split(r'\n\s*\n', text):
            start_line = line
            line += paragraph.count('\n') + 2
            flat = re.sub(r'\s*\n\s*', ' ', paragraph)
            candidates = resolver.candidates(path, flat)
            if ILLUSTRATION in flat:
                illustrations += 1
                continue
            flat, quoted = CODE_SPAN_RE.subn('`…`', flat)
            illustrations += quoted
            generic = candidates == {path}
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
            for match in PLAIN_RE.finditer(flat):
                raw = match.group('name')
                if raw.split()[0].lower().rstrip('.') in RELATIVE:
                    continue
                if norm(raw) in PLACEHOLDERS:
                    continue
                total += 1
                if any(resolver.resolves(norm(prefix), candidates) for prefix in prefixes(raw)):
                    continue
                if generic and any(resolver.common(norm(prefix)) for prefix in prefixes(raw)):
                    continue
                bad.append((path, start_line, raw.split()[0]))
    return (total, bad, retrospective, illustrations), None


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
            total, bad, _skipped, _illustrated = result
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
        fresh, stale = ratchet(sample, set())
        axes.append(('17. новый неразрешимый адрес роняет прогон', len(fresh) == 1 and not stale,
                     'новых %d, устаревших %d' % (len(fresh), len(stale))))
        fresh, stale = ratchet(sample, {('ф.md', 'Имя пассажа')})
        axes.append(('18. объявленный долг прогон не роняет', not fresh and not stale,
                     'новых %d, устаревших %d' % (len(fresh), len(stale))))
        fresh, stale = ratchet([], {('ф.md', 'Имя пассажа')})
        axes.append(('19. строка долга без вхождения в корпусе роняет прогон',
                     not fresh and len(stale) == 1,
                     'новых %d, устаревших %d' % (len(fresh), len(stale))))

        result, refusal = scan(os.path.join(work, 'нет-такого'), work)
        axes.append(('20. каталог не найден — проверка отказывает', bool(refusal),
                     refusal or 'проверка отчиталась'))
        empty = os.path.join(work, 'пусто')
        os.makedirs(empty, exist_ok=True)
        result, refusal = scan(empty, empty)
        axes.append(('21. область пуста — проверка отказывает', bool(refusal),
                     refusal or 'проверка отчиталась'))
    return axes


def ratchet(bad, debt):
    """Храповик долга: (новые неразрешимые, устаревшие строки долга)."""
    observed = {(source, name) for source, _line, name in bad}
    fresh = [(source, line, name) for source, line, name in bad if (source, name) not in debt]
    return fresh, sorted(debt - observed)


def declared_debt():
    """Объявленный долг как множество пар «файл, имя»."""
    if not os.path.exists(DEBT_FILE):
        return set()
    debt = set()
    with open(DEBT_FILE, encoding='utf-8') as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            path, _, name = line.partition('\t')
            debt.add((path.strip(), name.strip()))
    return debt


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else '.claude'
    axes = battery()
    print('--- батарея осей детектора (исполняется той же командой)')
    for title, passed, observed in axes:
        print('  %s: %s — %s' % ('доказана' if passed else 'НЕ ДОКАЗАНА', title, observed))
    broken = [title for title, passed, _ in axes if not passed]
    if broken:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: недоказанных осей %d — число адресов '
              'ничего не удостоверяло бы' % len(broken))
        return 2

    result, refusal = scan(root)
    if refusal:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: ' + refusal)
        return 2
    total, bad, retrospective, illustrations = result
    debt = declared_debt()
    fresh, stale = ratchet(bad, debt)
    print('адресов проверено: %d; неразрешимых: %d (из них объявленным долгом: %d); '
          'НОВЫХ НЕРАЗРЕШИМЫХ: %d; строк долга, которых в корпусе больше нет: %d'
          % (total, len(bad), len(bad) - len(fresh), len(fresh), len(stale)))
    print('файлов-записей прошлого исключено по маркеру: %d; '
          'иллюстраций формы (абзацев и адресов в кавычках): %d' % (len(retrospective), illustrations))
    for path in retrospective:
        print('  исключён по маркеру: %s' % path)
    for source, line, name in fresh:
        print('  НОВЫЙ ДЕФЕКТ: %s:%d → §%s' % (source, line, name))
    for source, name in stale:
        print('  СТРОКА ДОЛГА УСТАРЕЛА (адрес погашен — снять строку из %s): %s → §%s'
              % (DEBT_FILE, source, name))
    return 1 if fresh or stale else 0


if __name__ == '__main__':
    sys.exit(main())

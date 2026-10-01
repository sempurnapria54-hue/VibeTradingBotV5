#!/usr/bin/env python3
"""Предмет проверки: существование файлов, на которые указывает путь в тексте.

Корпус и тело кода адресуют файлы репозитория путём — `docs/...`,
`.claude/...`, `tools/...`, `deploy/...`. Путь, которому в репозитории не
соответствует файл, — битый указатель: читатель уходит за обоснованием и не
находит его.

ПОЧЕМУ ИНСТРУМЕНТ ЗАВЕДЁН. Класс измерялся руками дважды (курационный свип
2026-09-02 по дереву монолита; фокус `conventions` шага 6 фазы 2 по
`services/market-data`), и оба раза возвращался в области, которой замер не
касался: каждый следующий сервис заводит свою порцию заново. Бэклог
предсказал возврат и назвал приоритетом заведение измерения, а не третий
разбор руками (`.claude/work/backlog.md` §«Указатели javadoc в
несуществующие доки»). Md-область добавлена 2026-09-30: `.claude/rules/closed-work-transfer.md`
§«Ссылочная целостность» обязывала переносящего чинить входящие ссылки, но
держалась его дисциплиной — падающего инструмента для md-носителей не было.

ОБЛАСТИ — ДВЕ, и каждая печатается своим числом:

  код  — `*.java` живых деревьев `services/**` и `web/**` за вычетом `target/`.
         Дерево монолита фазы 1 выпало вместе с самим монолитом (2026-09-16,
         .claude/decisions/source-api-contour-retired.md).
  доки — `*.md` живых `.claude/**` и `docs/**`. Исключены:
         · `.claude/work/history/**` — записи прошлого (в том числе прежние
           снапшоты): их указатели ведут в раскладку своего момента и не
           чинятся (`.claude/rules/curation.md` §«Чек-лист свипа», п. 6);
         · файл, объявивший себя записью прошлого первой строкой
           `<!-- anchor-check: описывает прошлое — … -->`: объявление одно на
           оба детектора, потому что утверждение у него одно — «раскладка того
           момента»; число таких файлов печатает прогон поимённо.
         `.claude-archive/**` в область не входит по построению — это не
         `.claude/`. `.claude/snapshots/` В ОБЛАСТИ: там по правилу лежит только
         актуальный снапшот, а прежние уезжают в `history/`. `.claude/work/progress/`
         в области: живой отчёт — носитель предписаний, его строка долга
         снимается переездом отчёта в `history/`. `.claude/library/` в области:
         repo-путь в нём пишем мы, а не источник.
         Вне области остаются: `*.json` у `docs/spec/` (ноты спек), `README.md`
         деревьев кода и корня, `CLAUDE.md` — названо, а не пропущено.

ЧТО СЧИТАЕТСЯ УКАЗАТЕЛЕМ. Путь от корня репозитория с известным корневым
сегментом (`.claude`, `.claude-archive`, `docs`, `tools`, `deploy`, `services`,
`web`, `tests`, `libs`) и доковым расширением (`.md`, `.json`, `.yaml`, `.yml`,
`.sh`, `.py`, `.txt`). Сегменты пути — буквы любого алфавита (в том числе
кириллица имён заметок), цифры, `_`, `.`, `-`. `libs/` каталогом больше не
существует и оставлен в форме НАМЕРЕННО: отставший указатель на снятое дерево
опознаётся битым, а не пропускается молча. Существование сверяется С ТОЧНЫМ
РЕГИСТРОМ по листингу каталога: файловая система Windows регистр не различает,
и `docs/models/order.md` вместо `Order.md` прошёл бы здесь и сломался бы у
читателя на Linux. §-адрес внутри файла этот детектор не разбирает — его
предмет `tools/anchor-check.py`.

ПИСЬМЕННЫЕ ФОРМЫ, КОТОРЫЕ ДЕТЕКТОР ВИДИТ (каждая доказана осью батареи):

  1. в обратных кавычках   `docs/x.md`
  2. голый путь в прозе     см. docs/x.md и дальше
  3. цель markdown-ссылки   [текст](docs/x.md)
  4. с хвостом адреса       docs/x.md:12, docs/x.md#якорь, docs/x.md §«Имя»
  5. в конце предложения    …описано в docs/x.md.  — точка, за которой нет
                             продолжения пути, концом предложения и считается
                             (прежняя редакция отбрасывала такой указатель
                             негативным просмотром целиком, и так прошёл битый
                             указатель javadoc `StrategyDefinitionValidator`)
  6. имя с кириллицей       .claude/notes/2026-05-26-обкатка-….md
  7. несколько в строке     docs/a.md и docs/b.md — считаются все

ФОРМЫ, КОТОРЫЕ ДЕТЕКТОР НЕ МЕРИТ (клейма полноты на них нет): путь,
разорванный переносом строки; относительный путь (`rules/x.md`, `../x.md`,
`./x.md`); голое имя файла без каталога (`Order.md`); путь с обратной косой;
путь к каталогу без расширения; путь в `.java` за вычетом перечня расширений
(`Foo.java` — предмет компилятора, а не этого детектора).

ШАБЛОН ПУТИ УКАЗАТЕЛЕМ НЕ ЯВЛЯЕТСЯ (каждое исключение доказано осью):

  · плейсхолдер в угловых или фигурных скобках — `docs/rules/<тема>.md`,
    `docs/integrations/{name}/x.md`: скобка обрывает разбор пути;
  · маска со звёздочкой — `docs/spec/*.json`: звёздочка обрывает разбор;
  · многоточие-пропуск — `services/.../Foo.md`, `docs/…/x.md`;
  · заглушка-лексема сегмента: `N`, `vN`, `NNN`, `X`, `x`, `YYYY`, `MM`, `DD`
    (`.claude/snapshots/snapshot-vN.md`, `docs/lifecycles/X.md`,
    `.claude/work/history/YYYY-MM-DD-тема.md`). Лексема — часть сегмента между
    `/`, `.`, `-`, `_`; настоящих файлов с такой лексемой в репозитории нет.

ИЛЛЮСТРАЦИЯ. Абзац md, который приводит путь как пример формы или адрес в
ЧУЖОМ репозитории, объявляет это маркером
`<!-- doc-pointer: иллюстрация — <чем> -->`; указатели абзаца не мерятся,
число таких абзацев печатает прогон. Маркер в абзаце, где нет ни одного
несуществующего пути, — сам дефект: иначе исключение переживает свой повод.

ОБЪЯВЛЕННЫЙ ДОЛГ (храповик). Битые указатели на момент ввода md-области
перечислены в `tools/doc-pointer-debt.txt` парами «файл<TAB>указатель» без
номеров строк (номер дрейфует от любой правки). Прогон падает на НОВОМ битом
указателе и на строке долга, которой в корпусе больше нет: погашенная строка
обязана уйти из реестра, иначе реестр перестаёт означать долг. Пересборка
реестра на текущем состоянии — `py tools/doc-pointer-check.py --emit-debt`
(печатает весь реестр в stdout; сверка и приёмка — рукой перед записью).

ОСИ ДОКАЗАНЫ БАТАРЕЕЙ, исполняемой ЭТОЙ ЖЕ командой: команда, которая
ничего не измерила, обязана быть отличима от команды, которая измерила и не
нашла.

Коды возврата: 0 — новых битых нет и реестр долга точен; 1 — есть новые
битые, устаревшие строки долга либо маркер иллюстрации без повода; 2 —
ПРОВЕРКА НЕ ПРОВОДИЛАСЬ (ось не доказана, дерева области нет, указателей в
области нет).
"""
import os
import re
import shutil
import sys
import tempfile

if hasattr(sys.stdout, 'reconfigure'):
    # LF и на Windows: `--emit-debt` пишет реестр перенаправлением stdout.
    sys.stdout.reconfigure(encoding='utf-8', newline='\n')

# `libs` из области снят вместе с каталогом (решение держателя 2026-09-12):
# общие артефакты живут под `services/common/`, и эта ветка их накрывает.
# В форме САМОГО указателя `libs/` оставлен намеренно — см. шапку.
CODE_ROOTS = ('services', 'web')
DOC_ROOTS = ('.claude', 'docs')
DOC_SKIP = ('.claude/work/history/',)
RETROSPECTIVE = '<!-- anchor-check: описывает прошлое'
ILLUSTRATION = '<!-- doc-pointer: иллюстрация'
DEBT_FILE = 'tools/doc-pointer-debt.txt'

# Хвост: за расширением не идёт ни буква/цифра/дефис (иначе расширение
# чужое — `.mdx`, `.md-old`), ни точка с продолжением пути (`.md.bak`).
# Точка, за которой продолжения нет, — конец предложения (форма 5).
POINTER = re.compile(
    r'(?<![\w/.-])((?:\.claude-archive|\.claude|docs|tools|deploy|libs|services|web|tests)'
    r'/[\w./-]+\.(?:md|json|yaml|yml|sh|py|txt))(?![\w-]|\.[\w/-])')
PLACEHOLDER_TOKENS = {'N', 'vN', 'NNN', 'X', 'x', 'YYYY', 'MM', 'DD'}


def is_template(pointer):
    """Шаблон пути (многоточие либо лексема-заглушка), а не указатель."""
    if '...' in pointer:
        return True
    return any(token in PLACEHOLDER_TOKENS for token in re.split(r'[/._-]', pointer))


def pointers_in(text):
    """Все указатели строка-за-строкой: (номер строки, путь); шаблоны отброшены."""
    found = []
    for number, line in enumerate(text.split('\n'), 1):
        for match in POINTER.findall(line):
            if not is_template(match):
                found.append((number, match))
    return found


class Existence:
    """Существование пути с точным регистром — по листингу каталогов."""

    def __init__(self, base_dir):
        self.base_dir = base_dir
        self.listings = {}

    def _listing(self, directory):
        if directory not in self.listings:
            try:
                self.listings[directory] = set(os.listdir(os.path.join(self.base_dir, directory)))
            except OSError:
                self.listings[directory] = set()
        return self.listings[directory]

    def exists(self, pointer):
        parts = [part for part in pointer.split('/') if part not in ('', '.')]
        directory = ''
        for part in parts:
            if part == '..' or part not in self._listing(directory):
                return False
            directory = part if not directory else directory + '/' + part
        return True


def doc_pages(base_dir):
    """Файлы md-области: (все, исключённые маркером прошлого)."""
    pages = []
    for root in DOC_ROOTS:
        root_path = os.path.join(base_dir, root)
        for directory, _, names in os.walk(root_path):
            relative = os.path.relpath(directory, base_dir).replace(os.sep, '/') + '/'
            if any(relative.startswith(skip) for skip in DOC_SKIP):
                continue
            for name in names:
                if name.endswith('.md'):
                    pages.append(relative + name)
    return sorted(pages)


def scan_docs(base_dir, existence):
    """Md-область: (файлов, указателей, битые, исключённые, иллюстраций, пустые маркеры)."""
    total, broken, retrospective, illustrations, idle = 0, [], [], 0, []
    pages = doc_pages(base_dir)
    for page in pages:
        text = open(os.path.join(base_dir, page), encoding='utf-8', errors='replace').read()
        if text.lstrip('﻿').startswith(RETROSPECTIVE):
            retrospective.append(page)
            continue
        line = 1
        for paragraph in re.split(r'\n[ \t]*\n', text):
            start = line
            line += paragraph.count('\n') + 2
            hits = [(start + number - 1, pointer) for number, pointer in pointers_in(paragraph)]
            if ILLUSTRATION in paragraph:
                illustrations += 1
                if all(existence.exists(pointer) for _, pointer in hits):
                    idle.append((page, start))
                continue
            for number, pointer in hits:
                total += 1
                if not existence.exists(pointer):
                    broken.append((page, number, pointer))
    return len(pages), total, broken, retrospective, illustrations, idle


def scan_code(base_dir, existence):
    """Кодовая область: (файлов, указателей, битые)."""
    total, broken, files = 0, [], 0
    for root in CODE_ROOTS:
        root_path = os.path.join(base_dir, root)
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
                    if not existence.exists(pointer):
                        broken.append((os.path.relpath(path, base_dir).replace(os.sep, '/'),
                                       number, pointer))
    return files, total, broken


def ratchet(broken, debt):
    """Храповик долга: (новые битые, устаревшие строки долга)."""
    observed = {(source, pointer) for source, _line, pointer in broken}
    fresh = [item for item in broken if (item[0], item[2]) not in debt]
    return fresh, sorted(debt - observed)


def declared_debt(base_dir):
    """Объявленный долг как множество пар «файл, указатель»."""
    path = os.path.join(base_dir, DEBT_FILE)
    debt = set()
    if not os.path.exists(path):
        return debt
    for line in open(path, encoding='utf-8'):
        line = line.rstrip('\n')
        if not line.strip() or line.startswith('#'):
            continue
        source, _, pointer = line.partition('\t')
        debt.add((source.strip(), pointer.strip()))
    return debt


def measure(base_dir):
    """Полный замер: словарь исхода либо строка отказа."""
    missing = [root for root in CODE_ROOTS + DOC_ROOTS
               if not os.path.isdir(os.path.join(base_dir, root))]
    if missing:
        return 'дерева области не существует — %s' % ', '.join(missing)
    existence = Existence(base_dir)
    code_files, code_total, code_broken = scan_code(base_dir, existence)
    doc_files, doc_total, doc_broken, retrospective, illustrations, idle = \
        scan_docs(base_dir, existence)
    if code_total == 0 or doc_total == 0:
        return ('указателей в области нет вовсе (код: файлов %d, указателей %d; доки: '
                'файлов %d, указателей %d) — мерить нечего'
                % (code_files, code_total, doc_files, doc_total))
    broken = sorted(code_broken + doc_broken)
    fresh, stale = ratchet(broken, declared_debt(base_dir))
    return {'code': (code_files, code_total, len(code_broken)),
            'docs': (doc_files, doc_total, len(doc_broken)),
            'broken': broken, 'fresh': fresh, 'stale': stale,
            'retrospective': retrospective, 'illustrations': illustrations, 'idle': idle}


def battery():
    """Оси детектора, доказанные падающей пробой на каждой."""
    axes = []

    def parse(title, text, expected):
        hit = [pointer for _, pointer in pointers_in(text)]
        axes.append((title, hit == expected, 'разобрано: %r' % (hit,)))

    # Разбор формы указателя.
    parse('1. видит указатель в javadoc-строке', ' * (.claude/rules/codestyle.md §Слои)',
          ['.claude/rules/codestyle.md'])
    parse('2. форма 7: считает несколько указателей одной строки',
          'docs/rules/a.md и docs/rules/b.md', ['docs/rules/a.md', 'docs/rules/b.md'])
    parse('3. не принимает чужое расширение за указатель',
          'см. docs/rules/codestyle.txtx, docs/a.mdx, docs/a.md-old и services/x/Foo.java', [])
    parse('4. не принимает чужой корень за указатель', 'см. vendor/rules/cap.md', [])
    parse('5. не откусывает указатель из середины чужого пути', 'см. a/b/docs/rules/cap.md', [])
    hit = pointers_in('первая\nвторая docs/concept.md')
    axes.append(('6. называет номер строки указателя',
                 hit == [(2, 'docs/concept.md')], 'разобрано: %r' % (hit,)))
    parse('7. форма 5: точка конца предложения не прячет указатель',
          ' * Довод — docs/rules/cap.md.', ['docs/rules/cap.md'])
    parse('8. точка с продолжением пути — чужое расширение, не конец предложения',
          'см. docs/rules/cap.md.bak и docs/rules/y.md.d/z', [])
    parse('9. форма 1: путь в обратных кавычках', 'дом — `docs/rules/cap.md` §«Имя».',
          ['docs/rules/cap.md'])
    parse('10. форма 2: голый путь в прозе', 'дом docs/rules/cap.md держит', ['docs/rules/cap.md'])
    parse('11. форма 3: цель markdown-ссылки', '[правило](docs/rules/cap.md)', ['docs/rules/cap.md'])
    parse('12. форма 4: хвост адреса строкой и якорем',
          'docs/a.md:12 и docs/b.md#якорь', ['docs/a.md', 'docs/b.md'])
    parse('13. форма 6: кириллица в имени файла',
          '`.claude/notes/2026-05-26-обкатка.md`', ['.claude/notes/2026-05-26-обкатка.md'])
    parse('14. `.json` и корни tests/.claude-archive — указатели',
          '`docs/spec/a.json`, `tests/README.md`, `.claude-archive/old/y.md`',
          ['docs/spec/a.json', 'tests/README.md', '.claude-archive/old/y.md'])
    for number, (form, text) in enumerate((
            ('угловой плейсхолдер', '`docs/rules/<тема>.md`'),
            ('фигурный плейсхолдер', '`docs/integrations/{name}/rules/x-y.md`'),
            ('маска со звёздочкой', '`docs/spec/*.json`'),
            ('многоточие', '`services/common/.../Foo.md`, `docs/…/x.md`'),
            ('лексема vN', '`.claude/snapshots/snapshot-vN.md`'),
            ('лексема X', '`docs/lifecycles/X.md`'),
            ('лексема YYYY-MM-DD', '`.claude/work/history/YYYY-MM-DD-тема.md`')), 15):
        parse('%d. шаблон пути не указатель: %s' % (number, form), text, [])

    # Обход областей, долг и отказы — на временном репозитории.
    work = tempfile.mkdtemp(prefix='doc-pointer-battery-')
    try:
        def put(path, text):
            full = os.path.join(work, path)
            os.makedirs(os.path.dirname(full), exist_ok=True)
            with open(full, 'w', encoding='utf-8') as handle:
                handle.write(text)

        refusal = measure(work)
        axes.append(('22. дерева области нет — проверка отказывает, называя деревья',
                     isinstance(refusal, str) and refusal.startswith('дерева области не существует'),
                     'исход: %r' % (refusal,)))
        put('services/s/A.java', '// нет указателей\n')
        put('web/README.md', 'x\n')
        put('docs/concept.md', 'без указателей\n')
        put('.claude/rules/r.md', 'без указателей\n')
        refusal = measure(work)
        axes.append(('23. указателей нет — проверка отказывает',
                     isinstance(refusal, str), 'исход: %r' % (refusal,)))

        put('services/s/A.java', '/** Довод — docs/concept.md и docs/нет-кода.md. */\n')
        put('.claude/rules/r.md', 'см. `docs/concept.md`\n\nи `docs/нет-правила.md`\n')
        put('docs/models/Order.md', 'x\n')
        put('docs/d.md', 'см. `docs/models/order.md` и `.claude/rules/r.md`\n')
        put('.claude/work/history/h.md', 'см. `docs/нет-истории.md`\n')
        put('.claude/work/progress/p.md', 'см. `docs/нет-отчёта.md`\n')
        put('.claude/snapshots/snapshot-v1.md', 'см. `docs/нет-снапшота.md`\n')
        put('.claude/notes/2026-01-01-n.md',
            '<!-- anchor-check: описывает прошлое — проба -->\nсм. `docs/нет-заметки.md`\n')
        put('.claude/skills/s.md',
            '<!-- doc-pointer: иллюстрация — чужой репозиторий -->\n'
            'файл `docs/endpointFunctionList.md` в его репозитории\n\n'
            '<!-- doc-pointer: иллюстрация — повод исчез -->\nсм. `docs/concept.md`\n')
        result = measure(work)
        found = {(source, pointer) for source, _l, pointer in result['broken']} \
            if isinstance(result, dict) else set()

        def area(title, source, pointer, expected):
            axes.append((title, ((source, pointer) in found) == expected,
                         'битых в фикстуре: %d' % len(found)))

        area('24. область код: битый указатель javadoc найден',
             'services/s/A.java', 'docs/нет-кода.md', True)
        area('25. область доки: битый указатель `.claude/**` найден, номер строки второго абзаца',
             '.claude/rules/r.md', 'docs/нет-правила.md', True)
        lines = [line for source, line, _p in (result['broken'] if isinstance(result, dict) else [])
                 if source == '.claude/rules/r.md']
        axes.append(('26. номер строки в md сквозной через абзацы', lines == [3],
                     'строки: %r' % (lines,)))
        area('27. регистр сверяется точно: order.md при Order.md — битый',
             'docs/d.md', 'docs/models/order.md', True)
        area('28. history/ из области исключён', '.claude/work/history/h.md',
             'docs/нет-истории.md', False)
        area('29. progress/ в области', '.claude/work/progress/p.md', 'docs/нет-отчёта.md', True)
        area('30. snapshots/ в области', '.claude/snapshots/snapshot-v1.md',
             'docs/нет-снапшота.md', True)
        area('31. файл с маркером прошлого исключён', '.claude/notes/2026-01-01-n.md',
             'docs/нет-заметки.md', False)
        axes.append(('32. маркер прошлого печатается поимённо',
                     isinstance(result, dict) and
                     result['retrospective'] == ['.claude/notes/2026-01-01-n.md'],
                     'исключено: %r' % (result['retrospective'] if isinstance(result, dict) else None,)))
        area('33. абзац-иллюстрация не мерится', '.claude/skills/s.md',
             'docs/endpointFunctionList.md', False)
        idle = result['idle'] if isinstance(result, dict) else []
        axes.append(('34. маркер иллюстрации без несуществующего пути — дефект',
                     idle == [('.claude/skills/s.md', 4)], 'пустые маркеры: %r' % (idle,)))

        fresh, stale = ratchet(result['broken'], {('docs/d.md', 'docs/models/order.md'),
                                                  ('docs/d.md', 'docs/погашен.md')}) \
            if isinstance(result, dict) else ([], [])
        axes.append(('35. новый битый вне долга роняет прогон',
                     len(fresh) == len(found) - 1, 'новых %d при битых %d' % (len(fresh), len(found))))
        axes.append(('36. строка долга без вхождения роняет прогон',
                     stale == [('docs/d.md', 'docs/погашен.md')], 'устаревших: %r' % (stale,)))
        put(DEBT_FILE, '# шапка\ndocs/d.md\tdocs/models/order.md\n')
        debt = declared_debt(work)
        axes.append(('37. реестр долга читается парами файл<TAB>указатель, шапка пропущена',
                     debt == {('docs/d.md', 'docs/models/order.md')}, 'прочитано: %r' % (debt,)))
    finally:
        shutil.rmtree(work, ignore_errors=True)
    return axes


def emit_debt(result):
    """Реестр долга на текущем состоянии — в stdout."""
    print('# Объявленный долг битых файловых указателей — пары, битые на момент')
    print('# ввода md-области tools/doc-pointer-check.py (области и формы — его шапка).')
    print('# Формат: <файл>\\t<указатель>. Номеров строк нет намеренно: номер дрейфует')
    print('# от любой правки. Погашенная пара обязана уйти отсюда — прогон падает на')
    print('# устаревшей строке. Пересборка: py tools/doc-pointer-check.py --emit-debt')
    for source, pointer in sorted({(source, pointer) for source, _l, pointer in result['broken']}):
        print('%s\t%s' % (source, pointer))


def main():
    args = [arg for arg in sys.argv[1:] if arg != '--emit-debt']
    base_dir = args[0] if args else '.'
    axes = battery()
    if '--emit-debt' not in sys.argv:
        print('--- батарея осей детектора (исполняется той же командой)')
        for title, passed, observed in axes:
            print('  %s: %s — %s' % ('доказана' if passed else 'НЕ ДОКАЗАНА', title, observed))
    undone = [title for title, passed, _ in axes if not passed]
    if undone:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: недоказанных осей %d — число указателей '
              'ничего не удостоверяло бы' % len(undone))
        return 2

    result = measure(base_dir)
    if isinstance(result, str):
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: ' + result)
        return 2
    if '--emit-debt' in sys.argv:
        emit_debt(result)
        return 0

    code, docs = result['code'], result['docs']
    fresh, stale, idle = result['fresh'], result['stale'], result['idle']
    print('область код (services/**, web/** — *.java): файлов %d; указателей %d; битых %d' % code)
    print('область доки (.claude/**, docs/** — *.md, без .claude/work/history/): '
          'файлов %d; указателей %d; битых %d' % docs)
    print('битых всего: %d (из них объявленным долгом: %d); НОВЫХ БИТЫХ: %d; '
          'строк долга, которых в корпусе больше нет: %d; маркеров иллюстрации без повода: %d'
          % (len(result['broken']), len(result['broken']) - len(fresh), len(fresh),
             len(stale), len(idle)))
    print('файлов-записей прошлого исключено по маркеру: %d; абзацев-иллюстраций: %d'
          % (len(result['retrospective']), result['illustrations']))
    for page in result['retrospective']:
        print('  исключён по маркеру: %s' % page)
    for path, number, pointer in fresh:
        print('  БИТЫЙ УКАЗАТЕЛЬ: %s:%d → %s' % (path, number, pointer))
    for source, pointer in stale:
        print('  СТРОКА ДОЛГА УСТАРЕЛА (указатель погашен — снять строку из %s): %s → %s'
              % (DEBT_FILE, source, pointer))
    for page, number in idle:
        print('  МАРКЕР ИЛЛЮСТРАЦИИ БЕЗ ПОВОДА: %s:%d' % (page, number))
    return 1 if fresh or stale or idle else 0


if __name__ == '__main__':
    sys.exit(main())

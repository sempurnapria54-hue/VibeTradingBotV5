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

Поясняющее слово формы 3-4 — любое из: величина, операнд, значение,
предикат, нота, гейт, признак (со склонениями). Перечень имён после
разделителя берётся целиком: `спека` (`a`, `b`, `c`) — три указателя.

Форма, которой в этом перечне нет, детектором НЕ измерена — клейма
полноты он на неё не даёт. Прежняя редакция видела ОДНУ форму из четырёх
(скобочную) и печатала число, выглядевшее полным: 106 указателей при 173
в корпусе.

ПРИВЯЗКА СМЕЖНАЯ, НЕ ОКОННАЯ. Между ссылкой и именем стои́т только
разделитель формы (скобка, §, поясняющее слово, запятая, предлог). Замер
отверг привязку «по ближайшей ссылке в окне 160 символов»: имя, названное
в прозе рядом с чужой ссылкой («дом матрицы — спека A (`x`, `y`); дом
резолва — спека B»), приписывалось не своей спеке — все девятнадцать
находок такого прогона оказались ложными.

БАТАРЕЯ ОСЕЙ ИСПОЛНЯЕТСЯ ЭТОЙ ЖЕ КОМАНДОЙ, до проверки: инструмент,
чьи оси доказываются отдельным скриптом, принимается по факту, что скрипт
когда-то прогоняли.

ДВА КЛАССА ДЕФЕКТА, и второй сильнее первого:

  A. указатель называет НЕ ТУ спеку — величина объявлена, но в другом доме;
  B. указатель называет имя, которого в корпусе НЕТ ВОВСЕ — ни величиной,
     ни операндом: величина снята или переименована. Прежняя редакция
     такое имя молча пропускала (`if name not in known: continue`), то
     есть не видела сильнейший случай своего же предмета: удаление
     величины не ловилось ничем, а `docs/concept.md` §Ссылки обещает
     обратное — «сломать такую ссылку может только переименование самой
     величины, и оно ловится прогоном».

Коды возврата: 0 — все указатели ведут в дом; 1 — есть чужие;
2 — ПРОВЕРКА НЕ ПРОВОДИЛАСЬ (ось не доказана, каталог спек не найден,
индекс домов пуст, корпус пуст).
"""
import glob
import json
import os
import re
import sys
import tempfile

SPEC_DIR = 'docs/spec'
SKIP = ('/history/', '/library/', '.claude-archive', '/progress/')

SPEC_REF = re.compile(r'`docs/spec/([\w-]+\.json)`')
FILE_REF = re.compile(r'`?docs/spec/[\w-]+\.json`?')
NAME = re.compile(r'`([A-Za-z][A-Za-z0-9.]*)`')
SPEC = r'`docs/spec/([\w-]+\.json)`'
CARRIER = (r'(?:величина|величины|величине|операнд|операнда|значение|значения|предикат|предиката'
           r'|нота|ноты|гейт|гейта|признак|признака)')
NAMES = r'(`[A-Za-z][A-Za-z0-9.]*`(?:\s*[,/]\s*`[A-Za-z][A-Za-z0-9.]*`)*)'

# Письменные формы указателя. Привязка СМЕЖНАЯ: между ссылкой и именем
# стои́т только разделитель формы. Ранг — приоритет разрешения конфликта:
# одно и то же имя может стоять между двумя ссылками («`a` — операнд спеки A,
# `b` — величина спеки B»), и тогда выигрывает более явная форма.
# (регулярка, группа-спека, группа-имена, ранг, имя формы)
FORMS_RE = [
    (re.compile(SPEC + r'\s*\(([^)]{0,200})\)'), 1, 2, 1, 'скобочная'),
    (re.compile(SPEC + r'\s*§' + NAMES), 1, 2, 2, '§-именная'),
    (re.compile(SPEC + r'\s*[,—-]?\s*' + CARRIER + r'\s+' + NAMES), 1, 2, 3, 'поясняющая после ссылки'),
    (re.compile(NAMES + r'\s*[—-]?\s*' + CARRIER + r'\s+(?:из\s+|в\s+)?' + SPEC), 2, 1, 4, 'поясняющая перед ссылкой'),
    (re.compile(NAMES + r'\s+(?:в|из|у)\s+' + SPEC), 2, 1, 5, 'предлог перед ссылкой'),
    (re.compile(SPEC + r'\s*,\s*' + NAMES), 1, 2, 6, 'запятая-имя'),
]


def homes(spec_dir):
    """Индекс «имя величины → спеки, где она объявлена»."""
    index = {}
    for path in sorted(glob.glob(spec_dir + '/*.json')):
        with open(path, encoding='utf-8') as handle:
            for value in json.load(handle).get('values', []):
                index.setdefault(value['name'], set()).add(os.path.basename(path))
    return index


def operand_names(spec_dir):
    """Имена операндов по спекам: указатель законно называет и операнд."""
    index = {}
    for path in sorted(glob.glob(spec_dir + '/*.json')):
        with open(path, encoding='utf-8') as handle:
            for key in json.load(handle).get('operands', {}):
                clean = key.replace('[]', '').replace('{}', '')
                index.setdefault(clean, set()).add(os.path.basename(path))
                for segment in clean.split('.'):
                    if segment:
                        index.setdefault(segment, set()).add(os.path.basename(path))
    return index


CAMEL = re.compile(r'^[a-z][A-Za-z0-9]*[A-Z][A-Za-z0-9.]*$')
# Функции языка спецификаций — законные имена в тексте про спеку, но не величины.
LANGUAGE = {'floorTo', 'isNull', 'notNull', 'coalesce', 'min', 'max', 'abs', 'in', 'if', 'not'}


def candidate_name(token, known):
    """Имя в позиции указателя: camelCase-идентификатор либо известное имя.

    Фильтр по форме нужен затем, чтобы удаление величины ловилось: имя,
    которого в корпусе нет, по индексу не опознаётся, и без формы его
    пришлось бы пропускать — то есть не видеть сильнейший случай предмета.
    Однословные строчные токены (`type`, `size`, `status`) отсеиваются: это
    поля строк операндного контракта, а не имена величин.
    """
    if token in LANGUAGE:
        return False
    return token in known or bool(CAMEL.match(token))


def pointers(text, known):
    """Пары (спека, имя величины) по всем письменным формам указателя.

    Привязка синтаксическая, по смежности; конфликт двух форм на одном
    имени разрешается рангом формы. Привязка «по ближайшей ссылке в окне»
    была отвергнута замером: имя, названное в прозе рядом с чужой ссылкой,
    приписывалось не своей спеке — все девятнадцать находок такого прогона
    оказались ложными.
    """
    flat = re.sub(r'\s*\n\s*', ' ', text)
    claims = {}
    for pattern, spec_group, names_group, rank, _form in FORMS_RE:
        for match in pattern.finditer(flat):
            spec = match.group(spec_group)
            base = match.start(names_group)
            for name in NAME.finditer(match.group(names_group)):
                if not candidate_name(name.group(1), known):
                    continue
                span = base + name.start()
                if span not in claims or claims[span][0] > rank:
                    claims[span] = (rank, spec, name.group(1))
    return [(spec, name) for _rank, spec, name in claims.values()]


def scan(spec_dir, roots):
    index = homes(spec_dir)
    operands = operand_names(spec_dir)
    if not index:
        return None, 'индекс домов величин пуст — проверять нечего'
    files = [path for pattern in roots for path in glob.glob(pattern, recursive=True)
             if not any(skip in '/' + path for skip in SKIP)]
    if not files:
        return None, 'ни одного файла корпуса не найдено — проверять нечего'
    bad, checked = [], 0
    known = set(index) | set(operands)
    for path in files:
        with open(path, encoding='utf-8') as handle:
            text = handle.read()
        for spec, name in pointers(text, known):
            checked += 1
            if name not in index:
                if spec in operands.get(name, set()):
                    continue
                bad.append((path, spec, name, ['имени нет ни величиной, ни операндом — '
                                               'величина снята или переименована']))
                continue
            if spec not in index[name]:
                bad.append((path, spec, name, sorted(index[name])))
    return (checked, bad), None


# --- батарея осей ------------------------------------------------------------

FORMS = [
    ('1. скобочная форма', 'Форма — `docs/spec/{spec}` (`{name}`).'),
    ('2. §-именная форма', 'Форма — `docs/spec/{spec}` §`{name}`.'),
    ('3. поясняющая форма', 'Форма — `docs/spec/{spec}`, величина `{name}`.'),
    ('4. поясняющая перед ссылкой', 'Форма — `{name}` — величина `docs/spec/{spec}`.'),
    ('5. предлог перед ссылкой', 'Форма — `{name}` в `docs/spec/{spec}`.'),
    ('6. форма «запятая-имя»', 'Форма — `docs/spec/{spec}`, `{name}`.'),
]


def battery():
    """Оси детектора: каждая объявленная форма ловится и не даёт ложного срабатывания."""
    axes = []
    index = homes(SPEC_DIR)
    if not index:
        return [('фикстура батареи: индекс домов', False, 'каталог спек не разобран')]
    name = 'lossAtStopPerUnit' if 'lossAtStopPerUnit' in index else sorted(index)[0]
    home = sorted(index[name])[0]
    alien = next(os.path.basename(p) for p in sorted(glob.glob(SPEC_DIR + '/*.json'))
                 if os.path.basename(p) != home)
    with tempfile.TemporaryDirectory() as work:
        page = os.path.join(work, 'проба.md')
        for axis, template in FORMS:
            with open(page, 'w', encoding='utf-8') as handle:
                handle.write('# П\n\n' + template.format(spec=alien, name=name) + '\n')
            (checked, bad), _ = scan(SPEC_DIR, [os.path.join(work, '*.md')])
            axes.append((axis + ' — указатель на чужую спеку', bool(bad),
                         'найдено чужих: %d при проверенных %d' % (len(bad), checked)))
            with open(page, 'w', encoding='utf-8') as handle:
                handle.write('# П\n\n' + template.format(spec=home, name=name) + '\n')
            (checked, bad), _ = scan(SPEC_DIR, [os.path.join(work, '*.md')])
            axes.append((axis + ' — контроль: указатель на дом', checked == 1 and not bad,
                         'найдено чужих: %d при проверенных %d' % (len(bad), checked)))
        # ось класса B: имя, которого в корпусе нет вовсе (величина снята)
        with open(page, 'w', encoding='utf-8') as handle:
            handle.write('# П\n\nФорма — `docs/spec/%s` (`someRemovedValueName`).\n' % home)
        (checked, bad), _ = scan(SPEC_DIR, [os.path.join(work, '*.md')])
        axes.append(('7. имя, которого в корпусе нет вовсе (величина снята)', bool(bad),
                     'найдено: %d при проверенных %d' % (len(bad), checked)))
        # контроль: имя операнда той же спеки дефектом не считается
        operand = sorted(operand_names(SPEC_DIR))[0]
        owner = sorted(operand_names(SPEC_DIR)[operand])[0]
        with open(page, 'w', encoding='utf-8') as handle:
            handle.write('# П\n\nФорма — `docs/spec/%s`, операнд `%s`.\n' % (owner, operand))
        (checked, bad), _ = scan(SPEC_DIR, [os.path.join(work, '*.md')])
        axes.append(('8. контроль: имя операнда своей спеки дефектом не считается', not bad,
                     'найдено: %d при проверенных %d' % (len(bad), checked)))
        # контроль: функция языка спецификаций именем величины не считается
        with open(page, 'w', encoding='utf-8') as handle:
            handle.write('# П\n\nФорма — `docs/spec/%s` (`floorTo`).\n' % home)
        (checked, bad), _ = scan(SPEC_DIR, [os.path.join(work, '*.md')])
        axes.append(('9. контроль: функция языка спецификаций не имя величины', not bad,
                     'найдено: %d при проверенных %d' % (len(bad), checked)))

        # ось привязки: имя достаётся БЛИЖАЙШЕЙ ссылке, а не предыдущей
        with open(page, 'w', encoding='utf-8') as handle:
            handle.write('# П\n\nОперанд стои́т в `docs/spec/{alien}`, `{name}` — '
                         'величина `docs/spec/{home}`.\n'
                         .format(alien=alien, home=home, name=name))
        (checked, bad), _ = scan(SPEC_DIR, [os.path.join(work, '*.md')])
        axes.append(('10. конфликт форм: явная форма перебивает «запятую-имя»', not bad,
                     'найдено чужих: %d при проверенных %d' % (len(bad), checked)))
        # ось базового гейта: каталога спек нет
        _, refusal = scan(os.path.join(work, 'нет-такого'), [os.path.join(work, '*.md')])
        axes.append(('11. каталог спек не найден — проверка отказывает', bool(refusal),
                     refusal or 'проверка отчиталась'))
        # ось базового гейта: корпус пуст
        _, refusal = scan(SPEC_DIR, [os.path.join(work, 'нет-такого', '*.md')])
        axes.append(('12. корпус пуст — проверка отказывает', bool(refusal),
                     refusal or 'проверка отчиталась'))
    return axes


def main():
    if not os.path.isdir(SPEC_DIR):
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: %s не найден' % SPEC_DIR)
        return 2
    axes = battery()
    print('--- батарея осей детектора (исполняется той же командой)')
    for axis, passed, observed in axes:
        print('  %s: %s — %s' % ('доказана' if passed else 'НЕ ДОКАЗАНА', axis, observed))
    broken = [axis for axis, passed, _ in axes if not passed]
    if broken:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: недоказанных осей %d — число указателей '
              'ничего не удостоверяло бы' % len(broken))
        return 2

    result, refusal = scan(SPEC_DIR, ['docs/**/*.md', '.claude/**/*.md'])
    if refusal:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: ' + refusal)
        return 2
    checked, bad = result
    print('указателей на величины проверено: %d; ВЕДУТ НЕ В ДОМ: %d' % (checked, len(bad)))
    for path, spec, name, home in bad:
        print('  ДЕФЕКТ: %s → %s (`%s`) — дом величины %s' % (path, spec, name, ', '.join(home)))
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())

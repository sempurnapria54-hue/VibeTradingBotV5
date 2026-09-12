#!/usr/bin/env python3
"""Предмет проверки: указатель на дом перечня у компонента содержимого события.

Носитель, называющий компонент содержимого, чьё значение есть ИМЯ ЗНАЧЕНИЯ
ПЕРЕЧНЯ, объявляет его область **указателем на дом**, а состава перечня не
приводит. Дом правила, его радиус и довод —
`docs/architecture/contracts.md` §«Домен значения объявляется УКАЗАТЕЛЕМ на дом
перечня, а состав не переписывается»; здесь они не пересказываются.

ПЕРЕЧЕНЬ КОМПОНЕНТОВ ВЫВОДИТСЯ У ПИСАТЕЛЯ, А НЕ ПО ИМЕНИ КОМПОНЕНТА. Признак
применимости — что кладёт писатель: `name(...)` у фабрики формы либо
`<перечень>.name()` у внешнего конструктора. Читать признак по имени нельзя:
`direction` у решения о заявке несёт `Order.Side`, а у создания сделки —
`StrategyTradeDirection`, и дома у них разные.

ПОЧЕМУ ГРЕП ФОРМЫ НЕ ГОДИТСЯ. Греп указателя находит места, где правило
ИСПОЛНЕНО, и молчит о неисполненных — то есть мерит наоборот. Отсюда порядок
этого прогона: сперва вывести популяцию у писателя, потом спросить указатель у
каждого её члена.

ЧТО МЕРИТСЯ.
  A. У компонента, чьё значение писатель кладёт именем значения перечня, в
     носителе формы есть указатель: связка «имя значения» с названным типом.
  B. Состав перечня у носителя НЕ переписан: два и больше имени значения
     (`ВЕРХНИЙ_РЕГИСТР`) в описании одного компонента есть копия состава,
     которую не мерит ничто.

ЧТО НЕ МЕРИТСЯ, И ЭТО НАЗВАНО. Носителей у компонента несколько — запись формы
у писателя, форма потока периметра, нота операнда спеки, док. Прогон мерит
**запись формы у писателя**: она единственная, из которой популяция и
выводится. Прочие носители остаются за дисциплиной пишущего, и их указатели
по-прежнему не мерит ничто.

ОСИ ДОКАЗАНЫ БАТАРЕЕЙ, исполняемой ЭТОЙ ЖЕ командой. Код возврата 2 — «не
измерялось».
"""
import os
import re
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

FORMS_DIR = os.path.join('libs', 'domain-model', 'src', 'main', 'java')
FORMS_SUFFIX = 'Content.java'
WRITER_TREES = ('services', 'libs')
RECORD = re.compile(r'public record (\w+)\((.*?)\)\s*\{', re.S)
PARAM = re.compile(r'@param\s+(\w+)\s+(.*?)(?=\n\s*\*\s*@param|\n\s*\*/)', re.S)
POINTER = re.compile(r'(имя|имени|именем|имён)\s+(значени\w+|перечн\w+)')
TYPED = re.compile(r'\{@code\s+([\w.]+)\}')
CONSTANT = re.compile(r'\b[A-Z][A-Z_0-9]{2,}\b')
NAME_CALL = re.compile(r'(?:^|[^\w.])name\(|\.name\(\)')


def read(path):
    return open(path, encoding='utf-8', errors='replace').read().replace('\r\n', '\n')


def split_top(text):
    """Аргументы верхнего уровня: запятая внутри скобок разделителем не является."""
    parts = []
    depth = 0
    current = ''
    for char in text:
        if char in '(<':
            depth += 1
        elif char in ')>':
            depth -= 1
        if char == ',' and depth == 0:
            parts.append(current.strip())
            current = ''
        else:
            current += char
    if current.strip():
        parts.append(current.strip())
    return parts


def components_of(text):
    """Имя записи и её компоненты по порядку объявления."""
    match = RECORD.search(text)
    if not match:
        return None, []
    return match.group(1), [part.split()[-1] for part in split_top(match.group(2)) if part.split()]


def arguments_at(text, record):
    """Списки аргументов всех конструкторов записи в тексте."""
    found = []
    for match in re.finditer(r'new\s+' + re.escape(record) + r'\s*\(', text):
        depth = 1
        index = match.end()
        start = index
        while depth > 0 and index < len(text):
            if text[index] == '(':
                depth += 1
            elif text[index] == ')':
                depth -= 1
            index += 1
        found.append(split_top(text[start:index - 1]))
    return found


def java_files(base_dir):
    """Все живые java-носители деревьев, где может стоять писатель формы."""
    found = []
    for tree in WRITER_TREES:
        root = os.path.join(base_dir, tree)
        if not os.path.isdir(root):
            continue
        for directory, _, names in os.walk(root):
            if 'target' in directory.replace(os.sep, '/').split('/'):
                continue
            for name in names:
                if name.endswith('.java'):
                    found.append(os.path.join(directory, name))
    return found


def enumerated_components(record, components, texts):
    """Компоненты, чьё значение писатель кладёт именем значения перечня."""
    marked = set()
    for text in texts:
        for arguments in arguments_at(text, record):
            for index, argument in enumerate(arguments):
                if index >= len(components):
                    continue
                if NAME_CALL.search(argument.replace('\n', ' ')):
                    marked.add(components[index])
    return marked


def described(text):
    """Описания компонентов формы: {компонент: текст его блока `@param`}."""
    blocks = {}
    for match in PARAM.finditer(text):
        blocks[match.group(1)] = re.sub(r'\n\s*\*', ' ', match.group(2))
    return blocks


def pointer_missing(component, body, text):
    """Нет ли у компонента указателя на дом перечня.

    Место указателя внутри носителя свободно, поэтому при пустом блоке `@param`
    ищется абзац носителя, называющий сам компонент: правило запрещает
    переписывать состав, а не предписывает строку у описания компонента.
    """
    if body and POINTER.search(body) and TYPED.search(body):
        return False
    for paragraph in re.split(r'\n\s*\*\s*\n|<p>', text):
        flat = re.sub(r'\n\s*\*', ' ', paragraph)
        if '{@code %s}' % component in flat and POINTER.search(flat) and TYPED.search(flat):
            return False
    return True


def composition_copied(body):
    """Переписан ли состав перечня: два и больше имени значения в описании."""
    return len(set(CONSTANT.findall(body or ''))) >= 2


def battery():
    """Оси детектора, доказанные падающей пробой на каждой."""
    axes = []
    record_text = ('public record ПробаContent(String left, String right) {\n}\n')

    # Ось 1: компоненты записи читаются по порядку объявления.
    name, components = components_of(record_text)
    axes.append(('компоненты записи читаются по порядку',
                 name == 'ПробаContent' and components == ['left', 'right'],
                 'запись: %r; компоненты: %r' % (name, components)))

    # Ось 2: писатель с `.name()` помечает компонент своей ПОЗИЦИИ, а не имени.
    writer = 'new ПробаContent(deal.getInternalId(), signal.getRung().name())'
    marked = enumerated_components('ПробаContent', ['left', 'right'], [writer])
    axes.append(('писатель помечает компонент по позиции аргумента',
                 marked == {'right'}, 'помечено: %r' % (sorted(marked),)))

    # Ось 3: фабричный помощник `name(...)` опознаётся наравне с `.name()`.
    writer = 'new ПробаContent(name(deal.getStatus()), deal.getCode())'
    marked = enumerated_components('ПробаContent', ['left', 'right'], [writer])
    axes.append(('фабричный `name(...)` опознаётся наравне с `.name()`',
                 marked == {'left'}, 'помечено: %r' % (sorted(marked),)))

    # Ось 4: контроль — аргумент без `name` компонент не помечает.
    writer = 'new ПробаContent(deal.getInternalId(), deal.getCode())'
    marked = enumerated_components('ПробаContent', ['left', 'right'], [writer])
    axes.append(('контроль: аргумент без `name` компонента не помечает',
                 marked == set(), 'помечено: %r' % (sorted(marked),)))

    # Ось 5: компонент без указателя — дефект.
    body = 'поднятая ступень'
    axes.append(('компонент без указателя — дефект',
                 pointer_missing('rung', body, ''), 'исход: %r'
                 % (pointer_missing('rung', body, ''),)))

    # Ось 6: указатель без названного типа указателем не считается.
    body = 'поднятая ступень — имя значения перечня, область домовая'
    axes.append(('указатель без названного типа не считается указателем',
                 pointer_missing('rung', body, ''), 'исход: %r'
                 % (pointer_missing('rung', body, ''),)))

    # Ось 7: контроль — полный указатель дефектом не считается.
    body = 'поднятая ступень — имя значения {@code HoldRung}'
    axes.append(('контроль: полный указатель дефектом не считается',
                 not pointer_missing('rung', body, ''), 'исход: %r'
                 % (pointer_missing('rung', body, ''),)))

    # Ось 8: указатель, стоящий абзацем носителя, а не у описания компонента,
    # засчитывается — место указателя внутри носителя свободно.
    text = ('<p>Ступень {@code rung} приезжает именем значения {@code HoldRung};'
            ' область домовая.\n')
    axes.append(('указатель абзацем носителя засчитывается',
                 not pointer_missing('rung', '', text), 'исход: %r'
                 % (pointer_missing('rung', '', text),)))

    # Ось 9: переписанный состав перечня — дефект.
    axes.append(('переписанный состав перечня — дефект',
                 composition_copied('одно из SOFT, HARD, FREEZE'),
                 'исход: %r' % (composition_copied('одно из SOFT, HARD, FREEZE'),)))

    # Ось 10: контроль — одно упоминание значения составом не считается.
    axes.append(('контроль: одно имя значения составом не считается',
                 not composition_copied('пусто ⟺ PRINCIPAL_ABSENT'),
                 'исход: %r' % (composition_copied('пусто ⟺ PRINCIPAL_ABSENT'),)))

    # Ось 11: описания компонентов разбираются поблочно.
    text = ('/**\n * @param a первый\n * @param b второй — имя значения '
            '{@code X}\n */\n')
    blocks = described(text)
    axes.append(('описания компонентов разбираются поблочно',
                 sorted(blocks) == ['a', 'b'] and 'имя значения' in blocks['b'],
                 'блоки: %r' % (sorted(blocks),)))

    return axes


def main():
    base_dir = sys.argv[1] if len(sys.argv) > 1 else '.'
    axes = battery()
    print('--- батарея осей детектора (исполняется той же командой)')
    for title, passed, observed in axes:
        print('  %s: %s — %s' % ('доказана' if passed else 'НЕ ДОКАЗАНА', title, observed))
    undone = [title for title, passed, _ in axes if not passed]
    if undone:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: недоказанных осей %d — исход ничего не '
              'удостоверял бы' % len(undone))
        return 2

    forms_root = os.path.join(base_dir, FORMS_DIR)
    if not os.path.isdir(forms_root):
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: нет дерева форм содержимого — %s' % forms_root)
        return 2

    forms = []
    for directory, _, names in os.walk(forms_root):
        for name in sorted(names):
            if name.endswith(FORMS_SUFFIX):
                forms.append(os.path.join(directory, name))
    if not forms:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: форм содержимого нет вовсе — мерить нечего')
        return 2

    texts = [read(path) for path in java_files(base_dir)]
    total = 0
    defects = []
    for path in sorted(forms):
        text = read(path)
        record, components = components_of(text)
        if record is None:
            continue
        marked = enumerated_components(record, components, texts)
        blocks = described(text)
        relative = os.path.relpath(path, base_dir).replace(os.sep, '/')
        for component in sorted(marked):
            total += 1
            body = blocks.get(component, '')
            if pointer_missing(component, body, text):
                defects.append((relative, component, 'указателя на дом перечня нет'))
            elif composition_copied(body):
                defects.append((relative, component,
                                'состав перечня переписан: %s'
                                % ', '.join(sorted(set(CONSTANT.findall(body))))))

    if total == 0:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: ни один компонент не выведен у писателя '
              '(форм: %d) — мерить нечего' % len(forms))
        return 2

    print('форм содержимого: %d; компонентов, выведенных у писателя: %d; ДЕФЕКТОВ: %d'
          % (len(forms), total, len(defects)))
    for relative, component, reason in defects:
        print('  ДЕФЕКТ: %s → `%s` — %s' % (relative, component, reason))
    return 1 if defects else 0


if __name__ == '__main__':
    sys.exit(main())

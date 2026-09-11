#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Предмет проверки: секция бэклога несёт владельца и машинное условие
возврата, и ни одно сработавшее условие не осталось без исхода.

ЗАЧЕМ. Правило диеты рабочих файлов требовало держать в
`.claude/work/backlog.md` только живое, а живое от закрытого отличал только
читатель: условие возврата было написано словами, его срабатывание видела
лишь память. Измерено 2026-09-11: 5800 строк, из них ~3500 — хроника
исполненного; у 15 парковок оживитель сработал и не был отработан; 55
парковок без дома и причины. Дом правила — `.claude/rules/backlog-section-form.md`.

ЧТО МЕРИТСЯ. Каждая секция `##` (кроме двух вводных) несёт первой непустой
строкой после заголовка маркер
    <!-- backlog: владелец=…; оживит=<условие>; закрыто-когда=<условие> -->
Условия — из закрытого языка (шаг/фаза/рубеж/файл/греп/вопрос — машинные;
держатель/наблюдение/сейчас — немашинные, только считаются). Оси:
  1. секция без маркера; маркер с неизвестным ключом, видом, шагом, фазой;
  2. ОЖИВИТЕЛЬ СРАБОТАЛ — условие `оживит` держится;
  3. ПРЕДМЕТ ПОСТРОЕН — условие `закрыто-когда` держится;
  4. объём единицы (секция плюс подсекции без своего маркера) больше 60 строк;
     маркер провенанса в теле; дубль заголовка;
  5. заголовок внутри ограждённого блока секцией не считается.
Флаг `--статус <фаза>-<шаг>=<статус>` оценивает условия так, как если бы
статус уже стоял: гейт прогоняет его ДО записи статуса, потому что условие
«шаг DONE» срабатывает самой простановкой. `--рубеж=DONE` — то же для
прод-рубежа. `--файл <путь>` — проверить другой файл той же формы (черновик).

ОСИ ДОКАЗАНЫ БАТАРЕЕЙ, исполняемой этой же командой. Код возврата 2 —
«не измерялось» (недоказанная ось, нет файла, ноль секций, ноль разобранных
строк роадмапа); 1 — дефекты есть; 0 — чисто.
"""
import argparse
import glob
import io
import os
import re
import sys

BACKLOG = '.claude/work/backlog.md'
ROADMAP_DIR = '.claude/work/roadmap'
OPEN_QUESTIONS = '.claude/work/questions/open-questions.md'
EXEMPT = ('На какой вопрос отвечает этот файл', 'Связь с роадмапом')
MAX_LINES = 60
MARKER = re.compile(r'^<!--\s*backlog:\s*(.*?)\s*-->\s*$')
HEADING = re.compile(r'^(#{2,4})\s+(.+?)\s*$')
PROVENANCE = re.compile(
    r'\b(?:DOCS_CHECK|GAPS_CLOSE|SYNC_DOCS_FROM_CODE|CODE)_\d+\b'
    r'|[Пп]режн(?:яя|ей|юю|ие|их|им|ими)\s+редакци'
    r'|Как\s+найдено')
KEYS = ('владелец', 'оживит', 'закрыто-когда')
MACHINE = ('шаг', 'фаза', 'рубеж', 'файл', 'нет-файла', 'греп', 'нет-грепа', 'вопрос')
COUNTED = ('держатель', 'наблюдение')
SKIP_DIRS = ('/target/', '/.git/', '/node_modules/')


class Roadmap:
    """Статусы шагов, фаз и прод-рубежа, прочитанные из таблиц роадмапа."""

    def __init__(self, steps, phases, prod):
        self.steps = steps      # {(фаза, шаг): статус}
        self.phases = phases    # {фаза: статус}
        self.prod = prod        # статус строки «Прод-рубеж» либо None

    @staticmethod
    def read(base_dir):
        steps = {}
        phases = {}
        prod = None
        row = re.compile(r'^\|\s*([^|]+?)\s*\|.*\|\s*([A-Z_0-9]+)\s*\|\s*$')
        for path in sorted(glob.glob(os.path.join(base_dir, ROADMAP_DIR, 'phase-*.md'))):
            phase = re.search(r'phase-(\d+)\.md$', path.replace(os.sep, '/'))
            if not phase:
                continue
            for line in open(path, encoding='utf-8').read().split('\n'):
                match = row.match(line)
                if match and match.group(1).isdigit():
                    steps[(int(phase.group(1)), int(match.group(1)))] = match.group(2)
        top = os.path.join(base_dir, ROADMAP_DIR, 'roadmap.md')
        if os.path.exists(top):
            for line in open(top, encoding='utf-8').read().split('\n'):
                match = row.match(line)
                if not match:
                    continue
                if match.group(1).isdigit():
                    phases[int(match.group(1))] = match.group(2)
                elif match.group(1).startswith('Прод-рубеж'):
                    prod = match.group(2)
        return Roadmap(steps, phases, prod)


def parse_marker(text):
    """Строка маркера → ({ключ: значение}, [ошибки])."""
    fields = {}
    errors = []
    for part in text.split(';'):
        part = part.strip()
        if not part:
            continue
        if '=' not in part:
            errors.append('поле без `=`: %r' % part)
            continue
        key, value = part.split('=', 1)
        key = key.strip()
        value = value.strip()
        if key not in KEYS:
            errors.append('неизвестный ключ `%s`' % key)
            continue
        if not value:
            errors.append('пустое значение ключа `%s`' % key)
            continue
        fields[key] = value
    for key in ('владелец', 'оживит'):
        if key not in fields:
            errors.append('нет обязательного ключа `%s`' % key)
    return fields, errors


def parse_condition(text):
    """Условие → (вид, аргумент) либо (None, ошибка)."""
    text = text.strip()
    if text == 'сейчас':
        return 'сейчас', ''
    if ':' not in text:
        return None, 'условие без вида: %r' % text
    kind, arg = text.split(':', 1)
    kind = kind.strip()
    arg = arg.strip()
    if kind not in MACHINE + COUNTED:
        return None, 'неизвестный вид условия `%s`' % kind
    if not arg:
        return None, 'пустой аргумент у `%s`' % kind
    if kind == 'шаг' and not re.match(r'^\d+-\d+:(открыт|CODE|DONE)$', arg):
        return None, 'форма `шаг:<фаза>-<шаг>:открыт|CODE|DONE` нарушена: %r' % text
    if kind == 'фаза' and not re.match(r'^\d+:(открыта|DONE)$', arg):
        return None, 'форма `фаза:<N>:открыта|DONE` нарушена: %r' % text
    if kind == 'рубеж' and arg != 'prod':
        return None, 'единственный рубеж — `prod`: %r' % text
    if kind in ('греп', 'нет-грепа') and not re.match(r'^"(.+)"@(\S+)$', arg):
        return None, 'форма `греп:"<regex>"@<glob>` нарушена: %r' % text
    return kind, arg


def grep_hits(base_dir, regex, pattern):
    """Есть ли попадание regex хотя бы в одном файле по маске."""
    compiled = re.compile(regex, re.S)
    for path in glob.glob(os.path.join(base_dir, pattern), recursive=True):
        normalized = path.replace(os.sep, '/') + '/'
        if any(part in normalized for part in SKIP_DIRS) or not os.path.isfile(path):
            continue
        text = open(path, encoding='utf-8', errors='replace').read()
        if compiled.search(text):
            return True
    return False


def evaluate(kind, arg, base_dir, roadmap, questions):
    """True — держится, False — нет, None — немашинное (не мерится)."""
    if kind in COUNTED or kind == 'сейчас':
        return None
    if kind == 'шаг':
        key, wanted = arg.split(':')
        phase, step = (int(x) for x in key.split('-'))
        status = roadmap.steps.get((phase, step))
        if status is None:
            raise KeyError('шага %d-%d в роадмапе нет' % (phase, step))
        if wanted == 'DONE':
            return status == 'DONE'
        if wanted == 'CODE':
            return status == 'DONE' or status.startswith('CODE')
        return status != 'HOLD'
    if kind == 'фаза':
        number, wanted = arg.split(':')
        status = roadmap.phases.get(int(number))
        if status is None:
            raise KeyError('фазы %s в роадмапе нет' % number)
        if wanted == 'DONE':
            return status in ('DONE', 'FOLDED')
        return status != 'HOLD'
    if kind == 'рубеж':
        return roadmap.prod == 'DONE'
    if kind == 'файл':
        return os.path.exists(os.path.join(base_dir, arg))
    if kind == 'нет-файла':
        return not os.path.exists(os.path.join(base_dir, arg))
    if kind in ('греп', 'нет-грепа'):
        regex, pattern = re.match(r'^"(.+)"@(\S+)$', arg).groups()
        hit = grep_hits(base_dir, regex, pattern)
        return hit if kind == 'греп' else not hit
    if kind == 'вопрос':
        return arg not in questions
    raise KeyError('вид %s' % kind)


def sections_of(text):
    """Секции файла: список словарей (уровень, имя, строка, строки тела)."""
    found = []
    fenced = False
    for number, line in enumerate(text.split('\n'), 1):
        if line.lstrip().startswith('```'):
            fenced = not fenced
            continue
        if fenced:
            continue
        match = HEADING.match(line)
        if match:
            found.append({'level': len(match.group(1)), 'name': match.group(2),
                          'line': number, 'body': []})
        elif found:
            found[-1]['body'].append(line)
    return found


def unit_lines(sections, index):
    """Объём единицы — её строки плюс строки вложенных подсекций БЕЗ своего маркера."""
    total = 1 + len(sections[index]['body'])
    for later in sections[index + 1:]:
        if later['level'] <= sections[index]['level'] or marker_of(later) is not None:
            break
        total += 1 + len(later['body'])
    return total


def marker_of(section):
    """Маркер первой непустой строки тела либо None."""
    for line in section['body']:
        if line.strip():
            match = MARKER.match(line.strip())
            return match.group(1) if match else None
    return None


def check(text, base_dir, roadmap, questions):
    """Разбор файла → (дефекты, немашинные перечни, счёты)."""
    defects = []
    counted = {'сейчас': [], 'держатель': [], 'наблюдение': []}
    sections = sections_of(text)
    units = [s for s in sections if s['level'] == 2 and s['name'] not in EXEMPT]
    seen = {}
    with_marker = 0
    for section in sections:
        if section['name'] in seen and section['level'] == 2:
            defects.append('ДУБЛЬ ЗАГОЛОВКА: «%s» (строки %d и %d)'
                           % (section['name'], seen[section['name']], section['line']))
        if section['level'] == 2:
            seen.setdefault(section['name'], section['line'])
    for index, section in enumerate(sections):
        marker = marker_of(section)
        is_unit = section['level'] == 2 and section['name'] not in EXEMPT
        if marker is None:
            if is_unit:
                defects.append('БЕЗ МАРКЕРА: «%s» (строка %d)' % (section['name'], section['line']))
            continue
        with_marker += 1
        fields, errors = parse_marker(marker)
        for error in errors:
            defects.append('СИНТАКСИС: «%s» — %s' % (section['name'], error))
        for key in ('оживит', 'закрыто-когда'):
            if key not in fields:
                continue
            for raw in fields[key].split('|'):
                kind, arg = parse_condition(raw)
                if kind is None:
                    defects.append('СИНТАКСИС: «%s» — %s' % (section['name'], arg))
                    continue
                if key == 'закрыто-когда' and kind not in MACHINE:
                    defects.append('СИНТАКСИС: «%s» — в `закрыто-когда` законны только '
                                   'машинные условия, не `%s`' % (section['name'], kind))
                    continue
                if kind in counted:
                    counted[kind].append((section['name'], arg))
                    continue
                try:
                    held = evaluate(kind, arg, base_dir, roadmap, questions)
                except KeyError as error:
                    defects.append('СИНТАКСИС: «%s» — %s' % (section['name'], error))
                    continue
                if held:
                    label = 'ОЖИВИТЕЛЬ СРАБОТАЛ' if key == 'оживит' else 'ПРЕДМЕТ ПОСТРОЕН'
                    defects.append('%s: «%s» — %s (строка %d)'
                                   % (label, section['name'], raw.strip(), section['line']))
    for index, section in enumerate(sections):
        if section['name'] in EXEMPT or (section['level'] != 2 and marker_of(section) is None):
            continue
        size = unit_lines(sections, index)
        if size > MAX_LINES:
            defects.append('ОБЪЁМ %d > %d: «%s» (строка %d)'
                           % (size, MAX_LINES, section['name'], section['line']))
        body = '\n'.join(section['body'])
        for later in sections[index + 1:]:
            if later['level'] <= section['level'] or marker_of(later) is not None:
                break
            body += '\n' + '\n'.join(later['body'])
        hit = PROVENANCE.search(body)
        if hit:
            defects.append('ПРОВЕНАНС: «%s» — %r' % (section['name'], hit.group(0)))
    return defects, counted, {'units': len(units), 'markers': with_marker}


def read_questions(base_dir):
    path = os.path.join(base_dir, OPEN_QUESTIONS)
    if not os.path.exists(path):
        return set()
    return set(re.findall(r'^#{2,4}\s+([A-Z]{2,8}-Q\d+)\b', open(path, encoding='utf-8').read(), re.M))


def battery(base_dir):
    """Оси детектора, доказанные падающей пробой на каждой."""
    axes = []
    roadmap = Roadmap({(2, 10): 'GAPS_CLOSE_6', (2, 11): 'HOLD', (2, 7): 'DONE', (2, 9): 'CODE_2'},
                      {2: 'IN_PROGRESS', 3: 'HOLD', 1: 'FOLDED'}, 'HOLD')
    questions = {'ARCH-Q1'}
    here = os.path.relpath(os.path.abspath(__file__), base_dir).replace(os.sep, '/')

    def run(body, **kw):
        text = '# B\n\n## На какой вопрос отвечает этот файл\n\nx\n\n' + body
        return check(text, base_dir, kw.get('roadmap', roadmap), questions)

    good = '## Секция\n\n<!-- backlog: владелец=tester; оживит=шаг:2-11:открыт -->\n\nтело\n'
    defects, _, counts = run(good)
    axes.append(('видит маркер и принимает секцию с ним', defects == [] and counts['markers'] == 1,
                 'дефекты: %r' % (defects,)))

    defects, _, _ = run('## Секция\n\nтело без маркера\n')
    axes.append(('секция без маркера — дефект', any(d.startswith('БЕЗ МАРКЕРА') for d in defects),
                 'дефекты: %r' % (defects,)))

    defects, _, _ = run('## Секция\n\n<!-- backlog: хозяин=x; оживит=шаг:2-11:открыт -->\n')
    axes.append(('неизвестный ключ и пропущенный обязательный — дефект синтаксиса',
                 sum(d.startswith('СИНТАКСИС') for d in defects) == 2, 'дефекты: %r' % (defects,)))

    defects, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=луна:полная -->\n')
    axes.append(('неизвестный вид условия — дефект синтаксиса',
                 any('неизвестный вид' in d for d in defects), 'дефекты: %r' % (defects,)))

    defects, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=шаг:2-99:DONE -->\n')
    axes.append(('шаг, которого нет в роадмапе, — дефект синтаксиса',
                 any('в роадмапе нет' in d for d in defects), 'дефекты: %r' % (defects,)))

    fired, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=шаг:2-7:DONE -->\n')
    quiet, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=шаг:2-11:открыт -->\n')
    code_ok, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=шаг:2-9:CODE -->\n')
    axes.append(('`шаг` срабатывает на DONE и CODE_N, молчит на HOLD',
                 any('ОЖИВИТЕЛЬ' in d for d in fired) and quiet == []
                 and any('ОЖИВИТЕЛЬ' in d for d in code_ok),
                 'DONE: %r; HOLD: %r; CODE: %r' % (fired, quiet, code_ok)))

    fired, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=фаза:1:DONE -->\n')
    quiet, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=фаза:3:открыта|рубеж:prod -->\n')
    axes.append(('`фаза` считает FOLDED закрытой; HOLD-фаза и HOLD-рубеж молчат',
                 any('ОЖИВИТЕЛЬ' in d for d in fired) and quiet == [],
                 'FOLDED: %r; HOLD: %r' % (fired, quiet)))

    fired, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=файл:%s -->\n' % here)
    quiet, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=файл:tools/нет-такого.py -->\n')
    gone, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=нет-файла:tools/нет-такого.py -->\n')
    axes.append(('`файл` срабатывает на существующем пути, `нет-файла` — на отсутствующем',
                 any('ОЖИВИТЕЛЬ' in d for d in fired) and quiet == []
                 and any('ОЖИВИТЕЛЬ' in d for d in gone),
                 'есть: %r; нет: %r; нет-файла: %r' % (fired, quiet, gone)))

    fired, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=греп:"Предмет\\s+проверки"@tools/backlog-check.py -->\n')
    quiet, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=греп:"строки-которой-нет-\\d{9}"@tools/*.py -->\n')
    absent, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=нет-грепа:"строки-которой-нет-\\d{9}"@tools/*.py -->\n')
    axes.append(('`греп` срабатывает на попадании, `нет-грепа` — на его отсутствии',
                 any('ОЖИВИТЕЛЬ' in d for d in fired) and quiet == []
                 and any('ОЖИВИТЕЛЬ' in d for d in absent),
                 'есть: %r; нет: %r; нет-грепа: %r' % (fired, quiet, absent)))

    fired, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=вопрос:ARCH-Q9 -->\n')
    quiet, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=вопрос:ARCH-Q1 -->\n')
    axes.append(('`вопрос` срабатывает, когда заголовка вопроса больше нет',
                 any('ОЖИВИТЕЛЬ' in d for d in fired) and quiet == [],
                 'снят: %r; открыт: %r' % (fired, quiet)))

    defects, counted, _ = run('## А\n\n<!-- backlog: владелец=x; оживит=сейчас -->\n\n'
                              '## Б\n\n<!-- backlog: владелец=x; оживит=держатель:числа -->\n\n'
                              '## В\n\n<!-- backlog: владелец=x; оживит=наблюдение:период -->\n')
    axes.append(('немашинные условия не срабатывают и считаются поимённо',
                 defects == [] and [len(counted[k]) for k in ('сейчас', 'держатель', 'наблюдение')] == [1, 1, 1],
                 'дефекты: %r; счёт: %r' % (defects, counted)))

    defects, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=сейчас; закрыто-когда=держатель:x -->\n')
    axes.append(('немашинное условие в `закрыто-когда` — дефект синтаксиса',
                 any('только машинные' in d for d in defects), 'дефекты: %r' % (defects,)))

    fired, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=сейчас; закрыто-когда=файл:%s -->\n' % here)
    axes.append(('`закрыто-когда` держится — ПРЕДМЕТ ПОСТРОЕН',
                 any(d.startswith('ПРЕДМЕТ ПОСТРОЕН') for d in fired), 'дефекты: %r' % (fired,)))

    defects, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=сейчас -->\n' + 'строка\n' * 59)
    ok, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=сейчас -->\n' + 'строка\n' * 55)
    axes.append(('объём секции больше %d строк — дефект, ровно на границе — нет' % MAX_LINES,
                 any(d.startswith('ОБЪЁМ') for d in defects) and ok == [],
                 'больше: %r; на границе: %r' % (defects, ok)))

    defects, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=сейчас -->\n\nнайдено `GAPS_CLOSE_4`\n')
    ok, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=сейчас -->\n\nвход шага в CODE; прежний номер\n')
    axes.append(('маркер провенанса в теле — дефект, слово CODE и «прежний номер» — нет',
                 any(d.startswith('ПРОВЕНАНС') for d in defects) and ok == [],
                 'провенанс: %r; чисто: %r' % (defects, ok)))

    defects, _, counts = run('## Секция\n\n<!-- backlog: владелец=x; оживит=сейчас -->\n\n```\n## не секция\n```\n')
    axes.append(('заголовок внутри ограждённого блока секцией не считается',
                 defects == [] and counts['units'] == 1, 'дефекты: %r; секций: %d' % (defects, counts['units'])))

    defects, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=сейчас -->\n\n'
                        '## Секция\n\n<!-- backlog: владелец=x; оживит=сейчас -->\n')
    axes.append(('дубль заголовка — дефект', any(d.startswith('ДУБЛЬ') for d in defects),
                 'дефекты: %r' % (defects,)))

    override = Roadmap(dict(roadmap.steps), roadmap.phases, roadmap.prod)
    override.steps[(2, 11)] = 'DONE'
    fired, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=шаг:2-11:DONE -->\n', roadmap=override)
    axes.append(('подменённый статус (`--статус`) меняет вердикт',
                 any('ОЖИВИТЕЛЬ' in d for d in fired), 'дефекты: %r' % (fired,)))

    defects, counted, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=шаг:2-7:DONE -->\n\n'
                              '### Под\n\n<!-- backlog: владелец=y; оживит=держатель:x -->\n')
    big, _, _ = run('## Секция\n\n<!-- backlog: владелец=x; оживит=сейчас -->\n\n'
                    '### Под\n\n<!-- backlog: владелец=y; оживит=сейчас -->\n' + 'строка\n' * 58)
    axes.append(('подсекция со своим маркером не входит в объём родителя, а мерится своим',
                 not any('ОБЪЁМ' in d and '«Секция»' in d for d in big)
                 and any('ОБЪЁМ' in d and '«Под»' in d for d in big), 'дефекты: %r' % (big,)))

    axes.append(('подсекция с маркером мерится как своя единица',
                 any('ОЖИВИТЕЛЬ' in d for d in defects) and len(counted['держатель']) == 1,
                 'дефекты: %r; счёт: %r' % (defects, counted)))

    return axes


def main():
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument('--файл', dest='file', default=None)
    parser.add_argument('--статус', dest='status', action='append', default=[])
    parser.add_argument('--рубеж', dest='prod', default=None)
    parser.add_argument('base_dir', nargs='?', default='.')
    args = parser.parse_args()
    base_dir = args.base_dir

    axes = battery(base_dir)
    print('--- батарея осей детектора (исполняется той же командой)')
    for title, passed, observed in axes:
        print('  %s: %s — %s' % ('доказана' if passed else 'НЕ ДОКАЗАНА', title, observed))
    undone = [title for title, passed, _ in axes if not passed]
    if undone:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: недоказанных осей %d' % len(undone))
        return 2

    path = os.path.join(base_dir, args.file or BACKLOG)
    if not os.path.exists(path):
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: файла %s нет' % path)
        return 2
    roadmap = Roadmap.read(base_dir)
    if not roadmap.steps or not roadmap.phases:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: роадмап не разобран (шагов: %d, фаз: %d) — '
              'условия `шаг`/`фаза` мерить нечем' % (len(roadmap.steps), len(roadmap.phases)))
        return 2
    for item in args.status:
        match = re.match(r'^(\d+)-(\d+)=([A-Z_0-9]+)$', item)
        if not match:
            print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: форма `--статус <фаза>-<шаг>=<статус>` нарушена: %r' % item)
            return 2
        roadmap.steps[(int(match.group(1)), int(match.group(2)))] = match.group(3)
    if args.prod:
        roadmap.prod = args.prod

    text = open(path, encoding='utf-8').read()
    defects, counted, counts = check(text, base_dir, roadmap, read_questions(base_dir))
    if counts['units'] == 0:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: секций в %s нет вовсе — мерить нечего' % path)
        return 2

    print('секций: %d; с маркером: %d; дефектов: %d (файл: %s; подмен статуса: %d)'
          % (counts['units'], counts['markers'], len(defects), path.replace(os.sep, '/'), len(args.status)))
    for defect in defects:
        print('  ' + defect)
    for kind, title in (('сейчас', 'в работе'), ('держатель', 'ждут держателя'),
                        ('наблюдение', 'ждут наблюдения')):
        print('%s (`%s`): %d' % (title, kind, len(counted[kind])))
        for name, arg in counted[kind]:
            print('  «%s»%s' % (name, (' — ' + arg) if arg else ''))
    return 1 if defects else 0


if __name__ == '__main__':
    sys.exit(main())

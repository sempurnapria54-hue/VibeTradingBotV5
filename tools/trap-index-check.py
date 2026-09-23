#!/usr/bin/env python3
"""Предмет проверки: раздел ловушек дома — оглавление, а тело ловушки живёт по адресу.

Решение держателя 2026-09-23 (`.claude/decisions/trap-index.md`): сессия на
старте читает только оглавление ловушек своего дома — строку на ловушку, — а
тело читает адресно, когда споткнулась. Форма и правило чтения —
`.claude/rules/structure.md`, строка `.claude/traps/`.

ПОЧЕМУ ИНСТРУМЕНТ ЗАВЕДЁН. Ловушки всегда писались телом прямо в раздел дома,
и дом рос с каждой сессией: у скилла кода тестов раздел ловушек занимал четыре
пятых файла. Правило без прогона вернуло бы тела туда же, где их всегда писали.

ОБЛАСТЬ. Разделы второго уровня, чей заголовок начинается словом «Ловушки», во
всех `*.md` под `.claude/rules/`, `.claude/skills/`, `.claude/processes/`,
`.claude/agents/`; тела — файлы `.claude/traps/<дом>-traps.md`.

ЧТО ЗАКОННО В РАЗДЕЛЕ ЛОВУШЕК (формы, каждая доказана осью батареи):
  - заголовок подраздела `### …`;
  - вводный абзац, начинающийся `**Оглавление.**`, — один, до первой строки,
    и он обязан назвать файл тел `.claude/traps/<дом>-traps.md`, где `<дом>` —
    имя файла-дома;
  - строка оглавления одной строкой: `- **<область>** · <симптом> → <ID>`,
    где `<ID>` — номер тела, `<ПРЕФИКС>-<три цифры>`, заголовок `### <ID>` в
    файле тел своего дома. Путь в строке не повторяется: его несёт вводный
    абзац, а строка читается на старте каждой сессии;
  - пустые строки.
Всё прочее — тело в оглавлении (код 1).

ЧТО ЕЩЁ ДЕФЕКТ (код 1): номер строки без тела; тело без строки оглавления;
один номер у двух строк либо у двух тел; раздел без вводного абзаца либо с
абзацем, не называющим файл тел своего дома; тело с пустым текстом.

Исключений у области нет: разделы, остававшиеся телами после ввода правила,
переведены в оглавление тем же днём, и долга у прогона нет.

ЧЕГО ПРОГОН НЕ МЕРИТ. Что область и симптом строки верны и что по ним ловушку
найдут, из текста не выводится — это читает критик.

Код возврата: 0 — дефектов нет; 1 — есть; 2 — не измерялось (батарея не
доказана, области нет, ни разделов ловушек, ни тел нет вовсе, файл не читается).
"""
import os
import re
import sys

AREAS = ('.claude/rules', '.claude/skills', '.claude/processes', '.claude/agents')
TRAPS_DIR = '.claude/traps'
SECTION = re.compile(r'^## Ловушки')
INDEX = re.compile(r'^- \*\*(?P<area>[^*]+)\*\* · (?P<symptom>\S.*?) → '
                   r'(?P<id>[A-Z]+-\d{3})$')
INTRO = re.compile(r'^\*\*Оглавление\.\*\*')
BODY_HEAD = re.compile(r'^### (?P<id>[A-Z]+-\d{3})\s*$')


def index_sections(text):
    """Разделы ловушек файла: список (номер строки заголовка, строки раздела)."""
    lines = text.replace('\r\n', '\n').split('\n')
    out = []
    i = 0
    while i < len(lines):
        if SECTION.match(lines[i]):
            j = i + 1
            while j < len(lines) and not lines[j].startswith('## '):
                j += 1
            out.append((i + 1, lines[i + 1:j]))
            i = j
        else:
            i += 1
    return out


def check_section(home_name, start, lines):
    """Строки раздела: (адреса [(id, файл-дом)], дефекты)."""
    refs = []
    defects = []
    seen_index = False
    in_intro = False
    intro_text = ''
    intro_seen = False
    for offset, line in enumerate(lines, 1):
        number = start + offset
        if not line.strip():
            in_intro = False
            continue
        if line.startswith('### '):
            in_intro = False
            continue
        if INTRO.match(line):
            if seen_index:
                defects.append('строка %d: вводный абзац после строк оглавления' % number)
            in_intro = True
            intro_seen = True
            intro_text += line
            continue
        if in_intro and not line.startswith('- '):
            intro_text += ' ' + line
            continue
        in_intro = False
        match = INDEX.match(line)
        if match:
            seen_index = True
            refs.append((match.group('id'), home_name, number))
            continue
        defects.append('строка %d: тело в оглавлении — %r' % (number, line[:90]))
    body_file = '`%s/%s-traps.md`' % (TRAPS_DIR, home_name)
    if not intro_seen:
        defects.append('строка %d: раздел без вводного абзаца «Оглавление.»' % start)
    elif body_file not in intro_text:
        defects.append('строка %d: вводный абзац не называет файл тел %s' % (start, body_file))
    return refs, defects


def body_ids(text):
    """Тела файла: {id: непусто ли тело} и дефекты повтора адреса."""
    lines = text.replace('\r\n', '\n').split('\n')
    ids = {}
    defects = []
    current = None
    for number, line in enumerate(lines, 1):
        head = BODY_HEAD.match(line)
        if head:
            current = head.group('id')
            if current in ids:
                defects.append('строка %d: адрес %s у двух тел' % (number, current))
            ids[current] = False
            continue
        if line.startswith('#'):
            current = None
            continue
        if current and line.strip():
            ids[current] = True
    return ids, defects


def measure(homes, bodies):
    """homes: {путь: текст}; bodies: {имя дома: текст}."""
    defects = []
    refs = []
    sections = 0
    for path, text in sorted(homes.items()):
        home_name = os.path.basename(path)[:-3]
        for start, lines in index_sections(text):
            sections += 1
            found, bad = check_section(home_name, start, lines)
            refs.extend((ref_id, home, path, number) for ref_id, home, number in found)
            defects.extend('%s — %s' % (path, d) for d in bad)
    body_index = {}
    for home, text in sorted(bodies.items()):
        ids, bad = body_ids(text)
        defects.extend('%s/%s-traps.md — %s' % (TRAPS_DIR, home, d) for d in bad)
        for body_id, filled in ids.items():
            body_index[(home, body_id)] = filled
            if not filled:
                defects.append('%s/%s-traps.md — тело %s пусто' % (TRAPS_DIR, home, body_id))
    seen = {}
    for ref_id, home, path, number in refs:
        key = (home, ref_id)
        if key in seen:
            defects.append('%s:%d — адрес %s уже у строки %s' % (path, number, ref_id, seen[key]))
        seen[key] = '%s:%d' % (path, number)
        if key not in body_index:
            defects.append('%s:%d — строка без тела: %s/%s-traps.md §«%s»'
                           % (path, number, TRAPS_DIR, home, ref_id))
    for key in sorted(set(body_index) - set(seen)):
        defects.append('%s/%s-traps.md — тело %s без строки оглавления' % (TRAPS_DIR, key[0], key[1]))
    return len(refs), len(body_index), defects, sections


def battery():
    """Оси детектора и контроли."""
    axes = []
    line = '- **Git Bash** · heredoc рвётся → ENV-001'
    body = '# Т\n\n## Под\n\n### ENV-001\n\n- **Лид.** Тело.\n'
    good = ('# Дом\n\n## Ловушки и обходы\n\n**Оглавление.** Тела — в\n`.claude/traps/home-traps.md`.\n\n' '### Под\n\n' + line + '\n\n## Связи\n')

    def run(homes, bodies):
        return measure(homes, bodies)[2]

    def defect(title, homes, bodies):
        found = run(homes, bodies)
        axes.append((title, bool(found), 'дефектов: %d' % len(found)))

    defect('ось 1: тело пунктом в разделе ловушек — дефект',
           {'x/home.md': good.replace(line, line + '\n- **Лид.** Тело ловушки.')}, {'home': body})
    defect('ось 2: продолжение строки на следующей строке — дефект',
           {'x/home.md': good.replace(line, line + '\n  обход вот такой')}, {'home': body})
    defect('ось 3: абзац без вводной формы — дефект',
           {'x/home.md': good.replace('**Оглавление.**', 'Просто')}, {'home': body})
    defect('ось 4: строка без тела — дефект',
           {'x/home.md': good}, {'home': body.replace('ENV-001', 'ENV-002')})
    defect('ось 5: тело без строки — дефект',
           {'x/home.md': good}, {'home': body + '\n### ENV-002\n\n- **Ещё.** Тело.\n'})
    defect('ось 6: вводный абзац не называет файл тел своего дома — дефект',
           {'x/home.md': good.replace('traps/home-traps.md', 'traps/other-traps.md')},
           {'home': body, 'other': body})
    defect('ось 7: строка без области или симптома — дефект',
           {'x/home.md': good.replace('**Git Bash** · heredoc рвётся', 'heredoc рвётся')},
           {'home': body})
    defect('ось 8: пустое тело — дефект',
           {'x/home.md': good}, {'home': body.replace('- **Лид.** Тело.', '')})
    defect('ось 9: адрес у двух тел — дефект',
           {'x/home.md': good}, {'home': body + '\n### ENV-001\n\n- **Ещё.** Тело.\n'})

    found = run({'x/home.md': good}, {'home': body})
    axes.append(('контроль: оглавление со вводным абзацем — чисто', not found,
                 'дефектов: %d' % len(found)))
    found = run({'x/home.md': good.replace('\n', '\r\n')}, {'home': body.replace('\n', '\r\n')})
    axes.append(('контроль: CRLF — чисто', not found, 'дефектов: %d' % len(found)))
    return axes


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else '.'
    axes = battery()
    print('--- батарея осей детектора (исполняется той же командой)')
    for title, passed, observed in axes:
        print('  %s: %s — %s' % ('доказана' if passed else 'НЕ ДОКАЗАНА', title, observed))
    if not all(p for _, p, _ in axes):
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: батарея не доказана')
        return 2

    homes = {}
    try:
        for area in AREAS:
            root = os.path.join(base, area)
            for directory, _, names in os.walk(root):
                for name in names:
                    if name.endswith('.md'):
                        path = os.path.join(directory, name)
                        rel = os.path.relpath(path, base).replace(os.sep, '/')
                        homes[rel] = open(path, encoding='utf-8').read()
        bodies = {}
        traps_root = os.path.join(base, TRAPS_DIR)
        if os.path.isdir(traps_root):
            for name in os.listdir(traps_root):
                if name.endswith('-traps.md'):
                    bodies[name[:-len('-traps.md')]] = open(os.path.join(traps_root, name), encoding='utf-8').read()
    except (OSError, UnicodeDecodeError) as error:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: файл не читается — %s' % error)
        return 2
    if not homes:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: области %s нет' % ', '.join(AREAS))
        return 2

    refs, body_count, defects, sections = measure(homes, bodies)
    if sections == 0 and body_count == 0:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: ни разделов ловушек, ни тел нет — мерить нечего')
        return 2
    print('разделов ловушек: %d; строк оглавления: %d; тел: %d; ДЕФЕКТОВ: %d'
          % (sections, refs, body_count, len(defects)))
    print('  не мерится (названное ограничение): верность области и симптома строки')
    for text in defects:
        print('  ЛОВУШКА: %s' % text)
    return 1 if defects else 0


if __name__ == '__main__':
    sys.exit(main())

#!/usr/bin/env python3
"""Предмет: касание корпуса оболочкой не обходит правила со scope.

Хук `PreToolUse` на Bash (`.claude/settings.json`). Правило со scope входит в
контекст по чтению инструментом `Read` файла по его маске `paths:`; чтение
оболочкой (`cat`, `sed -n`, `tail`, скрипт в heredoc) масок не включает
(`.claude/rules/structure.md`, строка `.claude/rules/`; замер
`.claude/work/history/2026-09-23-rules-tiering.md`).

ПОЧЕМУ ИНСТРУМЕНТ ЗАВЕДЁН. Цепочка идёт в `bypassPermissions`, и харнесс в этом
режиме направляет чтение файлов в Bash: сессии v403-v410 сделали 1267 вызовов
Bash и 15 вызовов `Read` (`.claude/work/history/2026-09-23-chain-start-breakdown.md`).
Правила формы бэклога, снапшота, хроники и доков до такой сессии не доходили.
Хук не пускает команду, касающуюся корпусного файла, пока правило его области
не пришло, и называет файл, чтение которого инструментом `Read` его доставит.
Пришло — команда идёт, как шла: хук переводит оболочку не на `Read` вообще, а
только на первое касание области.

КАСАНИЕ. Путь к файлу `*.md` или `*.txt` в тексте команды (относительный от
корня репозитория, абсолютный Windows- либо POSIX-формы, маска с `*` —
раскрывается по файлам), кроме сегментов, чья команда только ищет или
перечисляет: `grep`, `rg`, `find`, `ls`, `wc`, `git`, `stat`, `du`, `file`,
`test`, `claude`. Сегмент — часть команды между `|`, `||`, `&&`, `;` и
переводом строки; тело heredoc поэтому читается построчно и касание скриптом
видно. Код (`*.java`, `*.py`, `*.sql`, манифесты) — не корпус и не касание:
его правила задачей не адресуются.

ПРИШЛО. Правило считается пришедшим, когда журнал сессии (`transcript_path`)
несёт вложение `nested_memory` с его файлом либо вызов `Read` файла по его
маске, — или когда хук сам видел такой `Read`. Третий источник обязателен:
харнесс дописывает журнал с запаздыванием, и Bash, поданный сразу после `Read`,
журнал ещё не застаёт (проба: две сессии из одиннадцати получили второй
отказ). Поэтому хук стоит и на `Read`: вызов ничего не останавливает, а
записывает правила, которые чтение доставит, в файл сессии во временном
каталоге (`rule-touch-guard/<session_id>.txt`). `Read` по маске правило
доставляет (замер ярусов), так что запись опережает вложение, но не
расходится с ним.

МАСКИ не копируются: они читаются из шапки `paths:` каждого правила при
каждом вызове. Разбор шапки — тот же, что у `tools/rule-tier-check.py`.

ОТКАЗ ХУКА — пропуск команды. Сломанный хук не останавливает работу; его
исправность мерит батарея `--self-test` (гейт инструментов корпуса).

ЧЕГО ХУК НЕ ПОКРЫВАЕТ, и это названо, а не забыто:
  - запись НОВОГО файла инструментом `Write` правило не подгружает и хуком не
    ловится — как и у сессий с `Read` (замер ярусов);
  - путь, собранный в команде из частей (`"$D/$F"`), без литерала файла;
  - субагент: отказ и доставка правила у самого субагента проверены пробой,
    а случай, когда область уже прочёл родитель, не мерился — запись хука
    ведётся по `session_id`, и общий ли он у родителя и субагента, не
    проверено;
  - текст, лишь называющий корпусный файл (промпт в аргументе команды,
    `echo`), тоже касание: цена — один лишний `Read` на область.

РЕЖИМЫ.
  без аргументов — хук `PreToolUse` на Bash и `Read`: JSON вызова на stdin; у
  Bash — решение `deny` JSON-ом на stdout, у `Read` — запись пришедшего;
  `--self-test` — батарея осей; код 0 — доказаны, 1 — ось не доказана, 2 — не
  измерялось (правил со scope не найдено).
"""
import glob
import json
import os
import re
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RULES_DIR = os.path.join(ROOT, '.claude', 'rules')
STATE_DIR = os.path.join(tempfile.gettempdir(), 'rule-touch-guard')
PROJECT_MARK = 'VibeTradingBotV5/'
CORPUS_PATH = re.compile(r'[A-Za-z0-9_./\\:~*-]*[A-Za-z0-9_*]\.(?:md|txt)(?![A-Za-z0-9_])')
SEGMENT_SPLIT = re.compile(r'\|\|?|&&|;|\n')
LEADING_NOISE = re.compile(r'^(?:[\s({!]|do\b|then\b|else\b|time\b|exec\b)+')
SEARCH_VERBS = {'grep', 'egrep', 'fgrep', 'rg', 'find', 'ls', 'wc', 'git',
                'stat', 'du', 'file', 'test', '[', '[[', 'claude'}
ITEM_LINE = re.compile(r'''^\s+-\s*(?:"([^"]*)"|'([^']*)'|(.*?))\s*$''')


def scoped_rules(rules_dir=RULES_DIR):
    """{имя файла правила: [маски]} у правил с шапкой `paths:`."""
    rules = {}
    for path in sorted(glob.glob(os.path.join(rules_dir, '**', '*.md'), recursive=True)):
        with open(path, encoding='utf-8') as f:
            lines = f.read().replace('\r\n', '\n').split('\n')
        if not lines or lines[0] != '---':
            continue
        masks = []
        in_paths = False
        for line in lines[1:]:
            if line == '---':
                break
            if line.startswith('paths:'):
                in_paths = True
                continue
            m = ITEM_LINE.match(line)
            if in_paths and m:
                masks.append(next(g for g in m.groups() if g is not None))
            else:
                in_paths = False
        if masks:
            rules[os.path.basename(path)] = masks
    return rules


def mask_regex(mask):
    out = []
    i = 0
    while i < len(mask):
        if mask.startswith('**/', i):
            out.append('(?:.*/)?')
            i += 3
        elif mask.startswith('**', i):
            out.append('.*')
            i += 2
        elif mask[i] == '*':
            out.append('[^/]*')
            i += 1
        elif mask[i] == '?':
            out.append('[^/]')
            i += 1
        else:
            out.append(re.escape(mask[i]))
            i += 1
    return re.compile('^' + ''.join(out) + '$')


def relative(path):
    """Путь от корня репозитория либо None, если путь вне его."""
    p = path.replace(chr(92), '/').strip('\'"')
    if PROJECT_MARK in p:
        p = p.split(PROJECT_MARK, 1)[1]
    elif re.match(r'^(?:[A-Za-z]:/|/)', p):
        return None
    while p.startswith('./'):
        p = p[2:]
    return p or None


def touched_paths(command, root=ROOT):
    """Корпусные пути, которых команда касается вне сегментов поиска."""
    found = []
    for segment in SEGMENT_SPLIT.split(command):
        head = LEADING_NOISE.sub('', segment)
        words = head.split()
        while words and re.match(r'^\w+=', words[0]) and len(words) > 1:
            words = words[1:]
        verb = os.path.basename(words[0]).strip('\'"') if words else ''
        if verb in SEARCH_VERBS:
            continue
        for token in CORPUS_PATH.findall(segment):
            rel = relative(token)
            if rel is None:
                continue
            if '*' in rel:
                hits = glob.glob(os.path.join(root, rel))
                rels = [os.path.relpath(h, root).replace(chr(92), '/') for h in hits]
            else:
                rels = [rel]
            for r in rels:
                if r not in found:
                    found.append(r)
    return found


def delivered(transcript_path, rules, compiled):
    """Правила, пришедшие в сессию: вложение `nested_memory` либо `Read` по маске."""
    got = set()
    if not transcript_path or not os.path.isfile(transcript_path):
        return got
    with open(transcript_path, encoding='utf-8', errors='replace') as f:
        for line in f:
            if '"nested_memory"' in line:
                try:
                    a = json.loads(line).get('attachment') or {}
                except ValueError:
                    continue
                name = os.path.basename(str(a.get('path', '')).replace(chr(92), '/'))
                if name in rules:
                    got.add(name)
            elif '"name":"Read"' in line or '"name": "Read"' in line:
                try:
                    content = json.loads(line).get('message', {}).get('content', [])
                except ValueError:
                    continue
                for c in content if isinstance(content, list) else []:
                    if c.get('type') == 'tool_use' and c.get('name') == 'Read':
                        rel = relative(str(c.get('input', {}).get('file_path', '')))
                        if rel is None:
                            continue
                        for name, regs in compiled.items():
                            if any(r.match(rel) for r in regs):
                                got.add(name)
    return got


def state_file(event, state_dir):
    sid = re.sub(r'[^A-Za-z0-9_-]', '', str(event.get('session_id') or ''))
    return os.path.join(state_dir, sid + '.txt') if sid else None


def recorded(event, state_dir):
    path = state_file(event, state_dir)
    if not path or not os.path.isfile(path):
        return set()
    with open(path, encoding='utf-8') as f:
        return {line.strip() for line in f if line.strip()}


def record_read(event, compiled, state_dir):
    """`Read` по маске: записать правила, которые это чтение доставит."""
    path = state_file(event, state_dir)
    rel = relative(str((event.get('tool_input') or {}).get('file_path', '')))
    if not path or rel is None:
        return
    names = [n for n, regs in compiled.items() if any(r.match(rel) for r in regs)]
    if names:
        os.makedirs(state_dir, exist_ok=True)
        with open(path, 'a', encoding='utf-8') as f:
            f.write(''.join(n + '\n' for n in names))


def decide(event, rules, root=ROOT, state_dir=STATE_DIR):
    """Причина отказа либо None — команда идёт."""
    compiled = {n: [mask_regex(m) for m in ms] for n, ms in rules.items()}
    if event.get('tool_name') == 'Read':
        record_read(event, compiled, state_dir)
        return None
    if event.get('tool_name') != 'Bash':
        return None
    command = str((event.get('tool_input') or {}).get('command', ''))
    needs = {}
    for rel in touched_paths(command, root):
        for name, regs in compiled.items():
            if any(r.match(rel) for r in regs):
                needs.setdefault(rel, []).append(name)
    if not needs:
        return None
    got = delivered(event.get('transcript_path'), rules, compiled) | recorded(event, state_dir)
    missing = {}
    for rel, names in needs.items():
        for name in names:
            if name not in got and name not in missing:
                missing[name] = rel
    if not missing:
        return None
    by_file = {}
    for name, rel in missing.items():
        by_file.setdefault(rel, []).append(name[:-3])
    reads = '; '.join(f'`{rel}` — {", ".join(names)}' for rel, names in by_file.items())
    return ('rule-touch-guard: команда касается корпуса оболочкой, а правила его '
            'области в контекст не пришли — чтение через Bash масок `paths:` не '
            f'включает. Сначала прочитай инструментом Read (хватит limit: 1): {reads}. '
            'Затем повтори команду — второй раз она пройдёт.')


def hook():
    try:
        event = json.loads(sys.stdin.buffer.read().decode('utf-8'))
        reason = decide(event, scoped_rules())
    except Exception as e:  # отказ хука — пропуск команды, а не остановка работы
        print(f'rule-touch-guard: сбой, команда пропущена: {e!r}', file=sys.stderr)
        return 0
    if reason:
        sys.stdout.write(json.dumps({'hookSpecificOutput': {
            'hookEventName': 'PreToolUse',
            'permissionDecision': 'deny',
            'permissionDecisionReason': reason}}))
    return 0


def self_test():
    import shutil
    rules = scoped_rules()
    if not rules:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: правил со scope не найдено')
        return 2
    tmp = tempfile.mkdtemp(prefix='rule-touch-guard-')

    def transcript(*records):
        path = os.path.join(tmp, f't{len(os.listdir(tmp))}.jsonl')
        with open(path, 'w', encoding='utf-8') as f:
            for r in records:
                f.write(json.dumps(r, ensure_ascii=False) + '\n')
        return path

    def nested(name):
        return {'type': 'attachment', 'attachment': {
            'type': 'nested_memory', 'path': 'C:\\repo\\.claude\\rules\\' + name}}

    def read_call(path):
        return {'type': 'assistant', 'message': {'content': [
            {'type': 'tool_use', 'name': 'Read', 'input': {'file_path': path}}]}}

    state = os.path.join(tmp, 'state')

    def run(command, t=None, tool='Bash', sid=None):
        return decide({'tool_name': tool, 'tool_input': {'command': command},
                       'transcript_path': t, 'session_id': sid}, rules, state_dir=state)

    def read_first(path, sid):
        return decide({'tool_name': 'Read', 'tool_input': {'file_path': path},
                       'session_id': sid}, rules, state_dir=state) is None

    backlog_rules = [n for n, ms in rules.items()
                     if any(mask_regex(m).match('.claude/work/backlog.md') for m in ms)]
    everything = transcript(*[nested(n) for n in rules])
    all_but_one = transcript(*[nested(n) for n in rules if n != 'backlog-section-form.md'])
    cases = [
        ('1. cat файла по маске, правил нет — отказ, правило названо',
         lambda: 'backlog-section-form' in (run('cat .claude/work/backlog.md') or '')),
        ('2. все правила области пришли вложением — команда идёт',
         lambda: run('cat .claude/work/backlog.md', everything) is None),
        ('3. Read файла по маске в журнале — команда идёт',
         lambda: run('tail -n 5 .claude/work/backlog.md',
                     transcript(read_call('C:\\Users\\x\\VibeTradingBotV5\\.claude\\work\\backlog.md'))) is None),
        ('4. пришли не все — отказ называет ровно недостающее',
         lambda: (lambda r: r and 'backlog-section-form' in r and 'curation' not in r)(
             run('cat .claude/work/backlog.md', all_but_one))),
        ('5. абсолютный Windows-путь опознан',
         lambda: run('cat "C:\\Users\\x\\IdeaProjects\\VibeTradingBotV5\\.claude\\snapshots\\snapshot-v1.md"') is not None),
        ('6. абсолютный POSIX-путь опознан',
         lambda: run('sed -n 1,20p /c/Users/x/IdeaProjects/VibeTradingBotV5/docs/concept.md') is not None),
        ('7. скрипт в heredoc касается корпуса — отказ',
         lambda: run("py -3 - <<'EOF'\ns = open('.claude/work/backlog.md').read()\nEOF") is not None),
        ('8. маска с * раскрыта по файлам',
         lambda: run('cat .claude/rules/*.md') is not None),
        ('9. присваивание пути с последующим чтением — отказ',
         lambda: run('f=".claude/work/backlog.md"; wc -l "$f"; tail -n 40 "$f"') is not None),
        ('10. путь внутри подстановки — отказ',
         lambda: run('x=$(cat docs/concept.md)') is not None),
        ('11. Read, увиденный хуком, пропускает Bash до записи журнала',
         lambda: read_first('C:\\Users\\x\\VibeTradingBotV5\\.claude\\work\\backlog.md', 's11')
         and run('cat .claude/work/backlog.md', sid='s11') is None),
        ('12. Read вне масок ничего не записывает — отказ остаётся',
         lambda: read_first('C:\\Users\\x\\VibeTradingBotV5\\services\\bff\\pom.xml', 's12')
         and run('cat .claude/work/backlog.md', sid='s12') is not None),
        ('13. запись одной сессии не пропускает другую',
         lambda: read_first('C:\\Users\\x\\VibeTradingBotV5\\.claude\\work\\backlog.md', 's13a')
         and run('cat .claude/work/backlog.md', sid='s13b') is not None),
    ]
    controls = [
        ('к1. grep по корпусу — не касание', lambda: run('grep -n "## " .claude/work/backlog.md') is None),
        ('к2. git по корпусу — не касание', lambda: run('git add .claude/work/backlog.md') is None),
        ('к3. код — не касание', lambda: run('cat services/bff/src/main/java/A.java tools/backlog-check.py') is None),
        ('к4. файл вне масок — не касание', lambda: run('cat web/src/notes.md') is None),
        ('к5. не Bash — не проверяется', lambda: run('cat .claude/work/backlog.md', tool='Read') is None),
        ('к6. поиск в конвейере, чтение без файла — не касание',
         lambda: run('grep -n X .claude/work/backlog.md | head -5') is None),
        ('к7. журнал недоступен — отказ, а не пропуск', lambda: run('cat docs/concept.md', '/nonexistent.jsonl') is not None),
    ]
    ok = True
    if not backlog_rules:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: ни одна маска не берёт `.claude/work/backlog.md` — батарея стоит на нём')
        return 2
    for title, check in cases + controls:
        try:
            good = bool(check())
        except Exception as e:
            good = False
            title += f' — сбой {e!r}'
        ok = ok and good
        print(('доказана: ' if good else 'НЕ ДОКАЗАНА: ') + title)
    print(f'правил со scope: {len(rules)}; осей {len(cases)}, контролей {len(controls)}')
    shutil.rmtree(tmp, ignore_errors=True)
    return 0 if ok else 1


if __name__ == '__main__':
    if sys.argv[1:] == ['--self-test']:
        sys.exit(self_test())
    sys.exit(hook())

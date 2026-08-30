#!/usr/bin/env python3
"""Предмет проверки: разрешимость §-адресов пассажей в `.claude/**`.

Адрес `§«Имя»` обязан разрешаться в заголовок либо лид-жирный пассаж
целевого файла (`.claude/rules/structure.md`). Целевой файл — путь,
названный в том же абзаце; при его отсутствии адрес внутрифайловый.

Хвост имени (скобочный либо после « — ») в адресе опускается законно:
он несёт метаданные пассажа, а не его имя.

Коды возврата: 0 — все адреса разрешимы; 1 — есть неразрешимые;
3 — вход не разобран (каталог не найден).
"""
import re, os, sys, glob

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.claude'
if not os.path.isdir(ROOT):
    print(f'ОШИБКА: каталог {ROOT} не найден', file=sys.stderr); sys.exit(3)

SKIP = ('/history/', '/library/', '/progress/', '/.claude-archive/')
PATH_RE = re.compile(r'[\w./-]*[\w-]+\.(?:md|json)')
BARE_RE = re.compile(r'`([a-z][a-z0-9-]{3,})`')
ADDR_RE = re.compile(r'§«(?P<name>[^»]{2,200})»')

# glob('**') не заходит в каталоги с точкой, поэтому .claude/** перечисляется явно:
# без этого индекс имён пуст на половине корпуса, а адреса в него молча не разрешаются
ALL = [q for pat in ('**/*.md', '**/*.json', '.claude/**/*.md', '.claude/**/*.json')
       for q in glob.glob(pat, recursive=True)
       if not q.startswith(('.git/', 'target/', '.claude-archive/'))]
BY_BASE = {}
for p in ALL:
    BY_BASE.setdefault(os.path.basename(p), []).append(p)
    BY_BASE.setdefault(os.path.splitext(os.path.basename(p))[0], []).append(p)

def norm(x):
    x = re.sub(r'\s+', ' ', x).strip().strip('.:;,—- ')
    for ch in '`«»„“”"':
        x = x.replace(ch, '')
    return x.replace('ё', 'е').lower()

def trim_tail(x):
    """Имя без метаданного хвоста: до первой скобки либо до « — »."""
    x = re.split(r'\s*\(', x)[0]
    x = re.split(r'\s+—\s+', x)[0]
    return norm(x)

CACHE = {}
def targets(path):
    if path in CACHE:
        return CACHE[path]
    out = set()
    try:
        txt = open(path, encoding='utf-8').read()
    except OSError:
        CACHE[path] = out; return out
    for m in re.finditer(r'^#{1,6}\s+(.+?)\s*$', txt, re.M):
        out.add(m.group(1))
    for m in re.finditer(r'^\s*(?:[-*]\s+|\|\s*|\d+\.\s+)?\*\*(.+?)\*\*', txt, re.M | re.S):
        if len(m.group(1)) < 220:
            out.add(m.group(1))
    CACHE[path] = {norm(t) for t in out} | {trim_tail(t) for t in out}
    return CACHE[path]

def resolve(tok):
    if os.path.exists(tok):
        return [tok]
    hits = BY_BASE.get(os.path.basename(tok)) or BY_BASE.get(tok) or []
    return hits if len(hits) == 1 else []

bad, total = [], 0
for path in sorted(glob.glob(ROOT + '/**/*.md', recursive=True)):
    if any(s in '/' + path for s in SKIP):
        continue
    for para in re.split(r'\n\s*\n', open(path, encoding='utf-8').read()):
        flat = re.sub(r'\s*\n\s*', ' ', para)
        cands = {path}
        for q in list(PATH_RE.finditer(flat)) + list(BARE_RE.finditer(flat)):
            cands.update(resolve(q.group(1) if q.re is BARE_RE else q.group(0)))
        cands = {c for c in cands if c.endswith('.md')}
        for m in ADDR_RE.finditer(flat):
            total += 1
            name = norm(m.group('name'))
            if not any(name in targets(c) for c in cands):
                bad.append((path, m.group('name')))

print(f'адресов проверено: {total}; НЕРАЗРЕШИМЫХ: {len(bad)}')
for src, name in bad:
    print(f'  ДЕФЕКТ: {src} → §«{name}»')
sys.exit(1 if bad else 0)

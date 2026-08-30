#!/usr/bin/env python3
"""Предмет проверки: указатель «спека (величина)» называет ДОМ этой величины.

Величина объявляется ровно в одной спеке (`.claude/rules/structure.md`,
строка `docs/spec/`). Указатель вида `` `docs/spec/x.json` (`имяВеличины`) ``
обязан называть ту спеку, где величина и объявлена: перенос величины в
новый дом оставляет указатели на старый, и расхождение не ловится ни
прогоном примеров, ни детектором областей видимости — оба смотрят внутрь
спек, а указатель живёт в прозе.

Коды возврата: 0 — все указатели ведут в дом; 1 — есть чужие;
3 — каталог спек не найден.
"""
import glob, json, os, re, sys

SPEC_DIR = 'docs/spec'
if not os.path.isdir(SPEC_DIR):
    print(f'ОШИБКА: {SPEC_DIR} не найден', file=sys.stderr); sys.exit(3)

home = {}
for p in glob.glob(SPEC_DIR + '/*.json'):
    for v in json.load(open(p, encoding='utf-8'))['values']:
        home.setdefault(v['name'], set()).add(os.path.basename(p))
if not home:
    print('ОШИБКА: ни одной величины не разобрано', file=sys.stderr); sys.exit(3)

SKIP = ('/history/', '/library/', '.claude-archive', '/progress/')
PTR = re.compile(r'`docs/spec/([\w-]+\.json)`\s*\(([^)]{0,200})\)')
NAME = re.compile(r'`([A-Za-z][A-Za-z0-9.]*)`')

bad, checked = [], 0
for p in [q for pat in ('docs/**/*.md', '.claude/**/*.md') for q in glob.glob(pat, recursive=True)]:
    if any(s in '/' + p for s in SKIP):
        continue
    txt = re.sub(r'\s*\n\s*', ' ', open(p, encoding='utf-8').read())
    for m in PTR.finditer(txt):
        spec = m.group(1)
        for name in NAME.findall(m.group(2)):
            if name not in home:
                continue
            checked += 1
            if spec not in home[name]:
                bad.append((p, spec, name, sorted(home[name])))

print(f'указателей на величины проверено: {checked}; ВЕДУТ НЕ В ДОМ: {len(bad)}')
for p, spec, name, h in bad:
    print(f'  ДЕФЕКТ: {p} → {spec} (`{name}`) — дом величины {", ".join(h)}')
sys.exit(1 if bad else 0)

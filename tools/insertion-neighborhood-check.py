#!/usr/bin/env python3
"""Предмет проверки: окрестность точки вставки предъявлена, а не пропущена.

Корпусный заход, правящий существующий носитель, обязан предъявить окрестность
каждой своей точки вставки — текст файла-цели, чью истинность или адресность
вставка двигает (`.claude/skills/closure-population.md` §«Четыре пробы
окрестности»). Перечень точек при этом не вспоминается: он выводится из дельты
захода по его отправной метке (`.claude/rules/edit-kind-obligations.md`
§«Предмет обязанностей — дельта захода, а не находки, на которые он отвечает»).

ОСЬ БЫЛА ЗАДУМАНА ПАРОЙ О2/О3 — «анафора либо термин предмета вставки в N
строках от изменённой строки дельты». Построена из неё половина, и обрезка
объявлена, а не умолчана: из пары О2/О3 механический вывод есть только у О2.

ЧТО МЕРИТСЯ — ПРОБА О2, И ТОЛЬКО ОНА. Анафора файла-цели («выше», «ниже»,
«рядом», «далее», «предыдущ», «следующ», «там же», ординальный §-адрес) в
окрестности изменённой строки означает: рядом с точкой вставки стои́т указатель
расположения, чью адресность вставка могла сдвинуть. Такой файл обязан быть
НАЗВАН в отчёте захода. Прогон спрашивает предъявление, а не верность разбора:
верность читает критик — та же граница, что у всех разметочных осей корпуса.

ЧЕГО ПРОГОН НЕ МЕРИТ, И ЭТО НАЗВАНО.
  * ПРОБА О3 (термин предмета вставки) машинного вывода не имеет: термин
    называет пишущий, и вывести его из дельты нечем. Ось объявлена и не
    измеряется; её исход по-прежнему держится дисциплиной захода.
  * ПРОБЫ О1 и О4 не измеряются по построению: О1 исполняется чтением абзаца,
    О4 ищется по ТЕКСТУ ПРАВКИ, а не по файлу-цели.
  * НАЛИЧИЕ МЕТКИ самозаявляемо — прогон мерит объявленное. Отчёт без метки
    ему невидим ровно так же, как заход без маркера `(корпусный)` невидим
    `tools/edit-kind-check.py`.
  * АНАФОРА, ПРИВЕДЁННАЯ КАК СЛОВАРЬ, от живой не отличается. Носитель,
    который сам вводит пробу О2, перечисляет её слова («выше», «ниже»,
    «рядом») как предмет, а не как указатель расположения. Ошибка идёт в
    СТРОГУЮ сторону — файл придётся назвать в отчёте, — и потому оставлена:
    обратная (пропущенная живая анафора) стоит незамеченного дефекта.

ПРОТИВ ЧЕГО ЗАВЕДЕНО. Пробы окрестности держались чтением, и их пропуск был
виден только соседу по мини-петле. Класс предъявлялся тем, что правка, верная
сама по себе, оставляла рядом с собой анафору, адресующую уже не то.

КАК СЧИТАЕТСЯ ДЕЛЬТА ОТЧЁТА. Заход, чей отчёт уже закоммичен и не правится,
считается ПРИЗЕМЛЁННЫМ: его дельта — `метка..HEAD`. Заход, чей отчёт в рабочем
дереве изменён либо не отслеживается, идёт: его дельта — от метки до рабочего
дерева. Признак механический (`git status --porcelain` на самом отчёте), и без
него отчёт приземлённого захода мерился бы дельтой СЛЕДУЮЩЕГО.

ОСИ ДОКАЗАНЫ БАТАРЕЕЙ, исполняемой ЭТОЙ ЖЕ командой. Код возврата 2 — «не
измерялось».
"""
import os
import re
import subprocess
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

REPORTS_DIR = os.path.join('.claude', 'work', 'progress')
CORPUS_PREFIXES = ('docs/', '.claude/')
CORPUS_SUFFIX = '.md'
# РАДИУС ОКРЕСТНОСТИ — калибровочная величина, и направление консервативности
# в сторону БОЛЬШЕГО: пропущенная анафора стоит незамеченного дефекта, лишняя —
# одной строки отчёта. Двенадцать строк есть абзац корпуса с запасом: пробы
# окрестности объявлены абзацем до и абзацем после точки вставки (О1), и радиус
# О2 не может быть у́же того, что объявила соседняя проба.
RADIUS = 12
MARK = re.compile(r'Метка\s+—\s+`([0-9a-f]{7,40})`')
# АНАФОРА ОПОЗНАЁТСЯ С ГРАНИЦЫ СЛОВА, а не подстрокой: без левой границы «ниже»
# находится внутри «понижение», и прогон печатал бы находку на слове, к
# расположению отношения не имеющем. Граница левая, а не обе: у русского слова
# хвост склоняемый («предыдущий», «следующем»), и правая граница отрезала бы
# ровно те формы, ради которых анафора и ищется.
ANAPHORA = re.compile(r'(?<![А-Яа-яЁёA-Za-z])(выше|ниже|рядом|далее|предыдущ|следующ|там же|§\d+)',
                      re.I)
HUNK = re.compile(r'^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@')


def git(args, base_dir):
    """Выдача git-команды строкой; None — команда не исполнилась."""
    try:
        done = subprocess.run(['git'] + args, cwd=base_dir, stdout=subprocess.PIPE,
                              stderr=subprocess.DEVNULL)
    except OSError:
        return None
    if done.returncode != 0:
        return None
    return done.stdout.decode('utf-8', errors='replace')


def reports(base_dir):
    """Отчёты заходов, объявившие метку: (путь, метка)."""
    found = []
    root = os.path.join(base_dir, REPORTS_DIR)
    if not os.path.isdir(root):
        return found
    for name in sorted(os.listdir(root)):
        if not name.endswith('.md'):
            continue
        path = os.path.join(REPORTS_DIR, name).replace(os.sep, '/')
        text = open(os.path.join(base_dir, path), encoding='utf-8', errors='replace').read()
        match = MARK.search(text)
        if match:
            found.append((path, match.group(1), text))
    return found


def landed(report, base_dir):
    """Приземлён ли заход отчёта: сам отчёт закоммичен и не правится."""
    status = git(['status', '--porcelain', '--', report], base_dir)
    return status is not None and status.strip() == ''


def split_files(output):
    """Дельта по файлам: {путь: список строк его куска вывода `git diff`}."""
    chunks = {}
    current = None
    for line in output.split('\n'):
        if line.startswith('diff --git '):
            current = None
            continue
        if line.startswith('+++ b/'):
            current = line[6:].strip()
            chunks.setdefault(current, [])
            continue
        if current is not None:
            chunks[current].append(line)
    return chunks


def is_new_file(lines):
    """Заведён ли носитель с нуля: у нового окрестности нет по построению."""
    return any(line.startswith('@@ -0,0') for line in lines)


def is_pointer_rewrite(lines):
    """Переписан ли в носителе ТОЛЬКО путь указателя.

    Признак механический: снятых и добавленных строк поровну, и после замены
    путеподобных токенов заполнителем они совпадают попарно. Такая правка
    нового утверждения не вносит (её и выделяет фильтр захода-предшественника),
    а значит и адресности соседней анафоры не двигает: указатель уехал вместе
    со своим носителем, текст вокруг остался тем же.
    """
    removed = [line[1:] for line in lines if line.startswith('-') and not line.startswith('---')]
    added = [line[1:] for line in lines if line.startswith('+') and not line.startswith('+++')]
    if not added or len(added) != len(removed):
        return False
    blank = lambda text: re.sub(r'[\w.Ѐ-ӿ-]+(?:/[\w.Ѐ-ӿ-]+)+', '§ПУТЬ', text)
    return all(blank(before) == blank(after) for before, after in zip(removed, added))


def changed_lines(mark, report, base_dir):
    """Точки вставки захода: {файл корпуса: множество номеров новых строк}.

    Из перечня выведены два класса, и оба объявлены корпусом, а не придуманы
    здесь: носитель, заведённый с нуля (окрестности нет по построению), и
    носитель, у которого переписан только путь указателя (нового утверждения
    заход не внёс).
    """
    arguments = ['diff', '-U0', mark]
    if landed(report, base_dir):
        arguments = ['diff', '-U0', mark, 'HEAD']
    output = git(arguments, base_dir)
    if output is None:
        return None, None
    points = {}
    excluded = {'заведён с нуля': 0, 'переписан только путь указателя': 0}
    for path, lines in split_files(output).items():
        if not in_corpus(path):
            continue
        if is_new_file(lines):
            excluded['заведён с нуля'] += 1
            continue
        if is_pointer_rewrite(lines):
            excluded['переписан только путь указателя'] += 1
            continue
        for line in lines:
            hunk = HUNK.match(line)
            if hunk:
                start = int(hunk.group(1))
                count = int(hunk.group(2)) if hunk.group(2) is not None else 1
                for number in range(start, start + max(count, 1)):
                    points.setdefault(path, set()).add(number)
    return points, excluded


def in_corpus(path):
    """Носитель корпуса: доковый файл под `docs/**` либо `.claude/**`."""
    return path.startswith(CORPUS_PREFIXES) and path.endswith(CORPUS_SUFFIX)


def anaphora_near(text, numbers, radius=RADIUS):
    """Анафоры файла-цели в радиусе от изменённых строк: (номер, слово)."""
    hits = []
    lines = text.split('\n')
    for index, line in enumerate(lines, 1):
        match = ANAPHORA.search(line)
        if not match:
            continue
        if any(abs(index - number) <= radius for number in numbers):
            hits.append((index, match.group(1)))
    return hits


def battery(base_dir):
    """Оси детектора, доказанные падающей пробой на каждой."""
    axes = []

    # Ось 1: анафора в радиусе от изменённой строки — точка предъявления.
    text = 'первая\nвторая\nсказано выше\n'
    axes.append(('анафора в радиусе от изменённой строки опознана',
                 anaphora_near(text, {2}, 2) == [(3, 'выше')],
                 'попадания: %r' % (anaphora_near(text, {2}, 2),)))

    # Ось 2: анафора ВНЕ радиуса точкой предъявления не считается.
    text = 'сказано выше\n' + 'строка\n' * 20 + 'вставка\n'
    axes.append(('анафора вне радиуса не считается',
                 anaphora_near(text, {21}, 2) == [],
                 'попадания: %r' % (anaphora_near(text, {21}, 2),)))

    # Ось 3: анафора ВЫШЕ точки вставки считается наравне с анафорой ниже —
    # указатель расположения бывает перед тем, что он адресует.
    text = 'смотри ниже\nвставка\n'
    axes.append(('анафора выше точки вставки считается',
                 anaphora_near(text, {2}, 2) == [(1, 'ниже')],
                 'попадания: %r' % (anaphora_near(text, {2}, 2),)))

    # Ось 4: ординальный §-адрес считается анафорой.
    text = 'см. §3 того же файла\nвставка\n'
    axes.append(('ординальный §-адрес считается анафорой',
                 anaphora_near(text, {2}, 2) == [(1, '§3')],
                 'попадания: %r' % (anaphora_near(text, {2}, 2),)))

    # Ось 4a: анафора внутри другого слова анафорой не считается — «ниже»
    # живёт в «понижении», и без левой границы прогон печатал бы находку на
    # слове, к расположению отношения не имеющем.
    text = 'решение о понижении кандидата\nвставка\n'
    axes.append(('анафора внутри другого слова не считается',
                 anaphora_near(text, {2}, 2) == [],
                 'попадания: %r' % (anaphora_near(text, {2}, 2),)))

    # Ось 4b: контроль — склоняемый хвост анафоре не мешает.
    text = 'сказано в следующем разделе\nвставка\n'
    axes.append(('контроль: склоняемый хвост анафоре не мешает',
                 anaphora_near(text, {2}, 2) == [(1, 'следующ')],
                 'попадания: %r' % (anaphora_near(text, {2}, 2),)))

    # Ось 5: контроль — текст без анафор попаданий не даёт.
    text = 'ровное утверждение\nвставка\n'
    axes.append(('контроль: текст без анафор попаданий не даёт',
                 anaphora_near(text, {2}, 2) == [],
                 'попадания: %r' % (anaphora_near(text, {2}, 2),)))

    # Ось 6: носитель вне корпуса в перечень точек не попадает.
    axes.append(('носитель вне корпуса точкой не считается',
                 not in_corpus('tools/retired-check.py')
                 and not in_corpus('docs/spec/deal-result.json')
                 and in_corpus('docs/rules/risk-policy.md'),
                 'tools: %r; json: %r; md: %r'
                 % (in_corpus('tools/retired-check.py'),
                    in_corpus('docs/spec/deal-result.json'),
                    in_corpus('docs/rules/risk-policy.md'))))

    # Ось 7: метка читается из отчёта объявленной формой.
    found = MARK.search('Метка — `0db71fef98c6ddd1034713004f98f7b6a6d8fd80`.')
    axes.append(('метка читается объявленной формой',
                 bool(found) and found.group(1).startswith('0db71fef'),
                 'метка: %r' % (found.group(1) if found else None,)))

    # Ось 8: отчёт без метки прогону невидим — мерится объявленное.
    axes.append(('отчёт без метки прогону невидим',
                 MARK.search('Отчёт без всякой метки.') is None,
                 'метка: %r' % (MARK.search('Отчёт без всякой метки.'),)))

    # Ось 9: номера строк ханка разбираются вместе с длиной.
    numbers = set()
    for line in ['+++ b/docs/x.md', '@@ -1,0 +7,3 @@']:
        hunk = HUNK.match(line)
        if hunk:
            start = int(hunk.group(1))
            count = int(hunk.group(2)) if hunk.group(2) is not None else 1
            numbers = set(range(start, start + count))
    axes.append(('ханк разбирается вместе с длиной',
                 numbers == {7, 8, 9}, 'строки: %r' % (sorted(numbers),)))

    # Ось 10: ханк без длины означает одну строку.
    hunk = HUNK.match('@@ -4 +4 @@')
    count = int(hunk.group(2)) if hunk and hunk.group(2) is not None else 1
    axes.append(('ханк без длины означает одну строку',
                 bool(hunk) and count == 1, 'длина: %r' % (count,)))

    # Ось 11a: носитель, заведённый с нуля, из перечня точек выведен —
    # окрестности у него нет по построению.
    axes.append(('носитель, заведённый с нуля, точкой не считается',
                 is_new_file(['@@ -0,0 +1,149 @@', '+# Snapshot v241'])
                 and not is_new_file(['@@ -120 +120 @@', '+строка']),
                 'новый: %r; правленый: %r'
                 % (is_new_file(['@@ -0,0 +1,149 @@']),
                    is_new_file(['@@ -120 +120 @@']))))

    # Ось 11b: правка, переписавшая только путь указателя, из перечня выведена —
    # нового утверждения она не вносит.
    rewrite = ['@@ -120 +120 @@',
               '-  `.claude/work/progress/phase-2-step-10-gaps-close-4.md` §«Красное»',
               '+  `.claude/work/history/2026-09-11-audit/phase-2-step-10-gaps-close-4.md` §«Красное»']
    axes.append(('правка одного лишь пути указателя точкой не считается',
                 is_pointer_rewrite(rewrite), 'исход: %r' % (is_pointer_rewrite(rewrite),)))

    # Ось 11c: контроль — правка, изменившая текст рядом с путём, точкой ЯВЛЯЕТСЯ.
    changed = ['@@ -120 +120 @@',
               '-  `.claude/work/progress/a.md` §«Красное»',
               '+  `.claude/work/history/b.md` §«Зелёное»']
    axes.append(('контроль: правка текста рядом с путём точкой считается',
                 not is_pointer_rewrite(changed),
                 'исход: %r' % (is_pointer_rewrite(changed),)))

    # Ось 11d: контроль — добавленная строка без снятой пары точкой считается.
    added_only = ['@@ -120,0 +121 @@', '+  Новое утверждение о предмете.']
    axes.append(('контроль: добавленная строка без пары точкой считается',
                 not is_pointer_rewrite(added_only),
                 'исход: %r' % (is_pointer_rewrite(added_only),)))

    # Ось 12: git доступен — иначе мерить нечем.
    axes.append(('git отвечает на rev-parse',
                 git(['rev-parse', 'HEAD'], base_dir) is not None,
                 'git: %s' % ('есть' if git(['rev-parse', 'HEAD'], base_dir) else 'нет')))

    return axes


def main():
    base_dir = sys.argv[1] if len(sys.argv) > 1 else '.'
    axes = battery(base_dir)
    print('--- батарея осей детектора (исполняется той же командой)')
    for title, passed, observed in axes:
        print('  %s: %s — %s' % ('доказана' if passed else 'НЕ ДОКАЗАНА', title, observed))
    undone = [title for title, passed, _ in axes if not passed]
    if undone:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: недоказанных осей %d — исход ничего не '
              'удостоверял бы' % len(undone))
        return 2

    declared = reports(base_dir)
    if not declared:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: в `%s` нет ни одного отчёта с объявленной '
              'меткой — перечень точек выводить не из чего' % REPORTS_DIR)
        return 2

    total_points = 0
    total_hits = 0
    skipped = {'заведён с нуля': 0, 'переписан только путь указателя': 0}
    defects = []
    for report, mark, text in declared:
        if git(['cat-file', '-e', mark + '^{tree}'], base_dir) is None \
                and git(['cat-file', '-e', mark], base_dir) is None:
            print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: метка отчёта %s недостижима (%s) — '
                  'дельта захода не выводится' % (report, mark))
            return 2
        points, excluded = changed_lines(mark, report, base_dir)
        if points is None:
            print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: дельта по метке %s не получена' % mark)
            return 2
        for path, numbers in sorted(points.items()):
            full = os.path.join(base_dir, path)
            if not os.path.isfile(full):
                continue
            total_points += len(numbers)
            target = open(full, encoding='utf-8', errors='replace').read()
            hits = anaphora_near(target, numbers)
            if not hits:
                continue
            total_hits += len(hits)
            if path not in text:
                defects.append((report, path, hits[:3]))
        for reason, count in excluded.items():
            skipped[reason] += count

    print('отчётов с меткой: %d; точек вставки в корпусе: %d; анафор в '
          'окрестности: %d; ДЕФЕКТОВ: %d'
          % (len(declared), total_points, total_hits, len(defects)))
    print('  носителей вне перечня точек: %s'
          % '; '.join('%s — %d' % (reason, count) for reason, count in skipped.items()))
    for report, path, hits in defects:
        print('  ОКРЕСТНОСТЬ НЕ ПРЕДЪЯВЛЕНА: %s не назван в %s — анафоры %s'
              % (path, report,
                 ', '.join('строка %d (`%s`)' % hit for hit in hits)))
    return 1 if defects else 0


if __name__ == '__main__':
    sys.exit(main())

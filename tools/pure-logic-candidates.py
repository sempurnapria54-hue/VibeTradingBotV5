#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Вывод кандидатов уровня «юнит по критерию чистой логики».

ПРЕДМЕТ. Признак уровня механический — у кода нет ввода-вывода
(`.claude/skills/test-design.md` §«Три уровня и признак каждого»). Команда
печатает по модулю: сколько у него классов `src/main` всего и сколько из них
не упоминает ни одного маркера ввода-вывода, плюс раскладку «чистых» по
пакетам — это и есть перечень кандидатов уровня 2.

ЧТО КОМАНДА НЕ МЕРИТ И ЭТО НАЗВАНО. Маркер ищется текстом в файле класса:
класс, зовущий сосед-бин, который сам ходит в базу, маркера не несёт и в
«чистые» попадёт. Поэтому выдача — КАНДИДАТЫ, а не перечень предметов:
предмет из неё выбирает пишущий кейсы, и его выбор читает ревью
(`.claude/skills/test-review.md`).

Запуск: py tools/pure-logic-candidates.py
Код возврата: 0 — выдача напечатана; 2 — дерева кода нет (мерить нечего).
"""
import collections
import io
import os
import re
import sys

IO_MARKERS = re.compile(
    r'\b(Repository|EntityManager|RestClient|RestTemplate|VaultTemplate'
    r'|KafkaTemplate|DataSource|JdbcTemplate|HttpClient|WebClient'
    r'|DataService|KafkaListener|Scheduled|Transactional)\b')

ROOT = 'services'


def main():
    if not os.path.isdir(ROOT):
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: каталога %s нет' % ROOT)
        return 2
    total = collections.Counter()
    pure = collections.Counter()
    pure_pkgs = collections.defaultdict(list)
    for root, _dirs, files in os.walk(ROOT):
        norm = root.replace(os.sep, '/')
        if '/target/' in norm + '/':
            continue
        if '/src/main/java' not in norm:
            continue
        for name in files:
            if not name.endswith('.java'):
                continue
            path = os.path.join(root, name)
            text = io.open(path, encoding='utf-8', errors='replace').read()
            rel = path.replace(os.sep, '/')
            module = rel.split('/src/main/java/')[0]
            total[module] += 1
            if IO_MARKERS.search(text) is None:
                pure[module] += 1
                pkg = rel.split('/src/main/java/')[1].rsplit('/', 1)[0]
                pure_pkgs[module].append(pkg)
    if not total:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: классов src/main не найдено')
        return 2
    print('модуль: классов всего / без маркеров ввода-вывода')
    for module in sorted(total):
        print('%-46s %4d / %4d' % (module, total[module], pure[module]))
    print('')
    print('пакеты кандидатов по модулям:')
    for module in sorted(pure_pkgs):
        print(module)
        counts = collections.Counter(pure_pkgs[module])
        for pkg, count in sorted(counts.items(), key=lambda kv: (-kv[1], kv[0])):
            print('    %3d  %s' % (count, pkg))
    return 0


if __name__ == '__main__':
    sys.exit(main())

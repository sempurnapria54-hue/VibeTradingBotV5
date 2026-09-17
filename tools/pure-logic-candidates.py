#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Вывод кандидатов уровня «юнит по критерию чистой логики».

ПРЕДМЕТ. Признак уровня механический — у кода нет ввода-вывода
(`.claude/skills/test-design.md` §«Три уровня и признак каждого»). Команда
печатает по модулю: сколько у него классов `src/main` всего, сколько из них
не упоминает ни одного маркера ввода-вывода и сколько из этих последних несёт
хотя бы одну точку ветвления, — плюс поимённый перечень классов с ветвлением.
Он и есть вход отбора предметов уровня 2.

ЗАЧЕМ ВТОРОЙ СТОЛБЕЦ. «Чистый» по маркеру класс бывает данными без поведения:
DTO поверхности, персистентная модель, форма сообщения, конфигурация,
константы, класс исключения. Выхода, выводимого из аргументов, у него нет
вовсе, и предметом уровня 2 он быть не может. Точка ветвления отделяет
поведение от данных: 2026-09-17 из 720 кандидатов её несли 167.

ПИСЬМЕННЫЕ ФОРМЫ ПРЕДМЕТА, КОТОРЫЕ КОМАНДА ВИДИТ. Их две, и обе доказаны
батареей, исполняемой этим же прогоном:

1. ТИПОВОЙ МАРКЕР — имя типа, перед которым конвенция слоёв ставит имя
   сущности (`.claude/rules/codestyle.md` §«Нейминг по слоям»):
   `InstrumentRepository`, `CandleDataService`, `OkxRestClient`. Границы
   слова слева у такого маркера НЕТ — иначе видна только голая форма, а её
   в дереве почти не бывает.
2. АННОТАЦИОННЫЙ МАРКЕР — `@Scheduled`, `@KafkaListener`, `@Transactional`:
   имени сущности перед ним не ставит никто, и границы слова стоят обе.

ТОЧКА ВЕТВЛЕНИЯ — тоже письменная форма, и её формы объявлены: `if (`,
`switch`, `for (`, `while (`, `catch (`, `&&`, `||`, тернарник. Ищутся они в
теле без javadoc и комментариев — иначе поясняющий текст считался бы
поведением. Каждая форма доказана осью батареи ниже.

ЧТО КОМАНДА НЕ МЕРИТ И ЭТО НАЗВАНО. Маркер ищется текстом в файле класса:
класс, зовущий сосед-бин, который сам ходит в базу, маркера не несёт и в
«чистые» попадёт. Так же и с ветвлением: класс, чьё ветвление живёт в лямбде
предиката (`.filter(x -> x.isFoo())`) либо целиком в вызове чужого метода,
ни одной из объявленных форм не несёт и во второй столбец не попадёт.
Поэтому выдача — КАНДИДАТЫ, а не перечень предметов: предмет из неё выбирает
пишущий кейсы, и его выбор читает ревью (`.claude/skills/test-review.md`).

Запуск: py tools/pure-logic-candidates.py
Код возврата: 0 — выдача напечатана; 2 — не измерялось (дерева кода нет
либо батарея форм не сошлась).
"""
import collections
import io
import os
import re
import sys

# Маркеры, перед которыми конвенция ставит имя сущности: границы слева нет.
TYPE_MARKERS = (
    'Repository', 'EntityManager', 'RestClient', 'RestTemplate',
    'VaultTemplate', 'KafkaTemplate', 'DataSource', 'JdbcTemplate',
    'HttpClient', 'WebClient', 'DataService')
# Аннотации: имени сущности перед ними не бывает, границы обе.
ANNOTATION_MARKERS = ('KafkaListener', 'Scheduled', 'Transactional')

IO_MARKERS = re.compile(
    r'(?:(?:%s)\b|\b(?:%s)\b)'
    % ('|'.join(TYPE_MARKERS), '|'.join(ANNOTATION_MARKERS)))

# Точка ветвления: формы объявлены в шапке, каждая — своя ось батареи.
BRANCH_POINT = re.compile(
    r'(?:\bif\s*\(|\bswitch\s*[({]|\bfor\s*\(|\bwhile\s*\('
    r'|\bcatch\s*\(|&&|\|\||(?<![<,]\s)(?<![<,])\?\s)')
BLOCK_COMMENT = re.compile(r'/\*.*?\*/', re.S)
LINE_COMMENT = re.compile(r'//[^\n]*')

# Батарея письменных форм: (ось, образец, ожидание попадания).
FORM_BATTERY = (
    ('типовой маркер с именем сущности слева',
     'private final InstrumentRepository instrumentRepository;', True),
    ('типовой маркер с именем сущности слева (второй частый)',
     'private final CandleDataService candleDataService;', True),
    ('типовой маркер голой формы',
     'import org.springframework.data.repository.Repository;', True),
    ('аннотационный маркер расписания',
     '    @Scheduled(cron = "${market-data.candles.cron}")', True),
    ('аннотационный маркер слушателя',
     '    @KafkaListener(topics = "${neighbours.core.facts}")', True),
    ('контроль на ложное срабатывание: чистый расчёт',
     'public BigDecimal calculate(BigDecimal price) '
     '{ return price.multiply(TWO); }', False),
)

# Батарея форм ветвления: (ось, образец тела класса, ожидание попадания).
BRANCH_BATTERY = (
    ('ветвление формой `if (`',
     '        if (isBlank(name)) { throw new IllegalArgumentException(); }',
     True),
    ('ветвление формой `switch`',
     '        switch (side) { case BUY -> up(); default -> down(); }', True),
    ('ветвление формой `for (`',
     '        for (Candle candle : candles) { sum = sum.add(candle); }',
     True),
    ('ветвление формой `while (`',
     '        while (cursor.hasNext()) { cursor.advance(); }', True),
    ('ветвление формой `catch (`',
     '        } catch (NumberFormatException e) { return null; }', True),
    ('ветвление составным условием `&&`',
     '        return isLive() && nonNull(price);', True),
    ('ветвление составным условием `||`',
     '        return isCovered() || isClosed();', True),
    ('ветвление тернарником',
     '        return isFalse(flag) ? left : right;', True),
    ('контроль: данные без поведения ветвлением не считаются',
     'public record JournalPage(List<AuditRecord> records, String cursor) { }',
     False),
    ('контроль: подстановочный параметр типа тернарником не считается',
     '    private final Map<String, ? extends Fact> facts;', False),
    ('контроль: ветвление внутри комментария не считается',
     '    // if (stale) { … } — так было до правки\n'
     '    private final BigDecimal rate;', False),
)

ROOT = 'services'


def strip_comments(text):
    """Тело без javadoc и построчных комментариев."""
    return LINE_COMMENT.sub('', BLOCK_COMMENT.sub('', text))


def battery():
    """Оси письменных форм. Возвращает перечень несошедшихся осей."""
    failed = []
    for axis, sample, expected in FORM_BATTERY:
        if (IO_MARKERS.search(sample) is not None) != expected:
            failed.append(axis)
    for axis, sample, expected in BRANCH_BATTERY:
        found = BRANCH_POINT.search(strip_comments(sample)) is not None
        if found != expected:
            failed.append(axis)
    return failed


def main():
    failed = battery()
    if failed:
        print('ИЗМЕРЕНИЕ НЕ ПРОВОДИТСЯ: батарея форм не сошлась')
        for axis in failed:
            print('    ось: %s' % axis)
        return 2
    if not os.path.isdir(ROOT):
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: каталога %s нет' % ROOT)
        return 2
    total = collections.Counter()
    pure = collections.Counter()
    branching = collections.Counter()
    branching_classes = collections.defaultdict(list)
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
                if BRANCH_POINT.search(strip_comments(text)) is not None:
                    branching[module] += 1
                    branching_classes[module].append(
                        rel.split('/src/main/java/')[1])
    if not total:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: классов src/main не найдено')
        return 2
    axes = len(FORM_BATTERY) + len(BRANCH_BATTERY)
    print('батарея письменных форм: %d осей, все сошлись' % axes)
    print('модуль: классов всего / кандидатов / из них с точкой ветвления')
    for module in sorted(total):
        print('%-42s %4d / %4d / %4d'
              % (module, total[module], pure[module], branching[module]))
    print('%-42s %4d / %4d / %4d'
          % ('ИТОГО', sum(total.values()), sum(pure.values()),
             sum(branching.values())))
    print('')
    print('кандидаты с точкой ветвления — вход отбора предметов уровня 2:')
    for module in sorted(branching_classes):
        print(module)
        for cls in sorted(branching_classes[module]):
            print('    %s' % cls)
    return 0


if __name__ == '__main__':
    sys.exit(main())

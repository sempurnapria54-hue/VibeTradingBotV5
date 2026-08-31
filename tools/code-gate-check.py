#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Исполнимый критерий выхода в `CODE`: диспозиция находок прогона по классам.

ПРЕДМЕТ. Прежний гейт требовал ЧИСТОГО полнокорпусного прогона. За семь
прогонов чистого не было ни одного, а планка росла вместе с усилением
измерения: закрытие производило ~69 % находок следующего прогона. Условие,
которое не достигается ни при какой добросовестной работе, гейтом не
является — оно означает «не входим в CODE никогда».

Эта команда меряет ДРУГОЕ условие, решением держателя 2026-08-31: гейтящие
находки прогона разложены по классам, классы «блокирует код» (КОД) и
«рисковая механика» (РИСК) — в НОЛЬ незакрытых, а всякая прочая гейтящая
находка ЗАДИСПОЗИЦИОНИРОВАНА — закрыта либо явно припаркована с адресатом и
условием возврата.

Вход — реестр диспозиции `.claude/work/code-gate-ledger.json`; сверяется с
итогом прогона, названного в самом реестре, чтобы гейт нельзя было пройти
умолчанием находки.

ОСИ, КОТОРЫЕ КОМАНДА ОБЪЯВЛЯЕТ (доказаны батареей, исполняемой этим же
прогоном, до замера):
  1. контроль: полный реестр с нулём КОД/РИСК — гейт пройден;
  2. незакрытая находка класса КОД — гейт НЕ пройден;
  3. незакрытая находка класса РИСК — гейт НЕ пройден;
  4. контроль: припаркованная находка прочего класса гейт не роняет;
  5. припарковка без адресата — гейт НЕ пройден;
  6. припарковка без условия возврата — гейт НЕ пройден;
  7. адресат припарковки не разрешается (файла нет либо §-пассажа в нём
     нет) — гейт НЕ пройден;
  8. класс вне закрытого перечня — гейт НЕ пройден;
  9. закрытая находка без указания, ЧЕМ закрыта, — гейт НЕ пройден;
 10. счёт реестра расходится со счётом гейтящих в отчёте прогона — гейт НЕ
     пройден (умолчанная находка не проходит);
 11. счёт расходится ПОЛИНЗОВО при сошедшемся итоге — гейт НЕ пройден;
 12. реестра нет — ЗАМЕР НЕ ПРОВОДИЛСЯ (код 2);
 13. реестр не разобран — ЗАМЕР НЕ ПРОВОДИЛСЯ (код 2);
 14. отчёта прогона, названного реестром, нет — ЗАМЕР НЕ ПРОВОДИЛСЯ (код 2);
 15. в отчёте нет сводки счётом — ЗАМЕР НЕ ПРОВОДИЛСЯ (код 2).

Форма, которой в этом перечне нет, замером НЕ измерена.

Запуск (из корня репозитория):  python3 tools/code-gate-check.py
Код возврата: 0 — критерий выхода в CODE пройден; 1 — не пройден (чем
именно — в stdout); 2 — ЗАМЕР НЕ ПРОВОДИЛСЯ.
"""
import importlib.util
import json
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LEDGER = '.claude/work/code-gate-ledger.json'

# Закрытый перечень классов гейтящей находки. Дом признака каждого класса —
# .claude/skills/classify-code-blocking.md.
BLOCKING = ('КОД', 'РИСК')
OTHER = ('НОСИТЕЛЬ', 'ИЗМЕРЕНИЕ', 'ГРУНТ', 'ПРОЦЕСС')
CLASSES = BLOCKING + OTHER

CLOSED = 'закрыта'
PARKED = 'припаркована'

SUMMARY_ROW = re.compile(r'^\|\s*([A-G])\s*\|[^|]*\|\s*(\d+)\s*\|')
ANCHOR = re.compile(r'§«([^»]+)»')


class Refusal(Exception):
    """Замер не проводится: мерить нечем."""


def _resolver():
    """Разрешатель §-адресов — тот же, что у tools/anchor-check.py: у формы один дом."""
    spec = importlib.util.spec_from_file_location('anchor_check', ROOT / 'tools' / 'anchor-check.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def report_counts(path, root):
    """Гейтящие находки прогона по линзам — из сводки счётом отчёта."""
    file = root / path
    if not file.is_file():
        raise Refusal('отчёта прогона нет: %s' % path)
    counts = {}
    for line in file.read_text(encoding='utf-8').splitlines():
        found = SUMMARY_ROW.match(line.strip())
        if found:
            counts[found.group(1)] = int(found.group(2))
    if not counts:
        raise Refusal('в отчёте %s нет сводки счётом: гейтящие находки не пересчитываются' % path)
    return counts


_RESOLVER_MODULE = None


def anchor_resolves(target, name, root):
    """Разрешается ли §-имя в файле-адресате: заголовок либо лид-жирный пассаж.

    Разрешатель заимствуется у `tools/anchor-check.py` — у формы адреса один
    дом, и второй её реализации здесь не заводится.
    """
    global _RESOLVER_MODULE                                   # noqa: PLW0603 — модуль грузится один раз
    file = root / target
    if not file.is_file():
        return False
    if _RESOLVER_MODULE is None:
        _RESOLVER_MODULE = _resolver()
    path = str(file)
    return _RESOLVER_MODULE.Resolver([path]).resolves(_RESOLVER_MODULE.norm(name), [path])


def check(ledger_path=LEDGER, root=ROOT):
    """Проверка реестра диспозиции. Возвращает список причин непрохождения."""
    file = root / ledger_path
    if not file.is_file():
        raise Refusal('реестра диспозиции нет: %s' % ledger_path)
    try:
        ledger = json.loads(file.read_text(encoding='utf-8'))
    except (ValueError, OSError) as failure:
        raise Refusal('реестр не разобран: %s' % failure)
    report = ledger.get('отчёт')
    if not report:
        raise Refusal('реестр не называет отчёт прогона, с которым сверяется')
    counts = report_counts(report, root)
    reasons = []
    seen = {}
    for entry in ledger.get('находки', []):
        name = entry.get('id', '<без id>')
        lens = entry.get('линза')
        seen[lens] = seen.get(lens, 0) + 1
        klass = entry.get('класс')
        disposition = entry.get('диспозиция')
        if klass not in CLASSES:
            reasons.append('%s: класс «%s» вне закрытого перечня (%s)' % (name, klass, ', '.join(CLASSES)))
            continue
        if disposition == CLOSED:
            if not str(entry.get('чем', '')).strip():
                reasons.append('%s: объявлена закрытой, но не сказано, чем именно' % name)
            continue
        if disposition != PARKED:
            reasons.append('%s: диспозиция «%s» — исходом не является (ожидается «%s» либо «%s»)'
                           % (name, disposition, CLOSED, PARKED))
            continue
        if klass in BLOCKING:
            reasons.append('%s: класс %s припаркован — классы %s обязаны быть в ноль'
                           % (name, klass, ' и '.join(BLOCKING)))
        addressee = str(entry.get('адресат', '')).strip()
        if not addressee:
            reasons.append('%s: припаркована без адресата (владелец + носитель)' % name)
        else:
            target = addressee.split('§')[0].strip().rstrip(' —')
            anchor = ANCHOR.search(addressee)
            if not (root / target).is_file():
                reasons.append('%s: адресат припарковки не разрешается — файла «%s» нет' % (name, target))
            elif anchor and not anchor_resolves(target, anchor.group(1), root):
                reasons.append('%s: адресат припарковки не разрешается — в «%s» нет пассажа «%s»'
                               % (name, target, anchor.group(1)))
        if not str(entry.get('условие-возврата', '')).strip():
            reasons.append('%s: припаркована без условия возврата' % name)
    for lens, expected in sorted(counts.items()):
        actual = seen.get(lens, 0)
        if actual != expected:
            reasons.append('линза %s: отчёт прогона насчитал %d гейтящих, реестр несёт %d — '
                           'умолчанная находка гейт не проходит' % (lens, expected, actual))
    for lens in sorted(set(seen) - set(counts)):
        reasons.append('реестр несёт находки линзы %s, которой в сводке отчёта нет' % lens)
    return reasons, sum(counts.values()), len(ledger.get('находки', []))


# --- батарея осей: исполняется ЭТИМ ЖЕ прогоном, до замера -------------------

REPORT = """# Проба отчёта прогона

## Сводка счётом

| Линза | Предмет | Гейтящих | Прочих |
|---|---|---|---|
| A | ядро | 2 | 3 |
| B | правила | 1 | 0 |
| **Итого** | | **3** | **3** |
"""

BACKLOG = """# Бэклог пробы

## Пункт-адресат

**Название припарковки.** Текст пункта.
"""


def _fixture(mutate=None, report=REPORT):
    """Песочница: реестр, отчёт прогона и файл-адресат припарковки."""
    root = Path(tempfile.mkdtemp())
    (root / '.claude' / 'work').mkdir(parents=True)
    (root / '.claude' / 'work' / 'backlog.md').write_text(BACKLOG, encoding='utf-8')
    (root / 'отчёт.md').write_text(report, encoding='utf-8')
    ledger = {
        'прогон': 'проба',
        'отчёт': 'отчёт.md',
        'находки': [
            {'id': 'A1', 'линза': 'A', 'класс': 'КОД', 'диспозиция': CLOSED, 'чем': 'правило дописано'},
            {'id': 'A2', 'линза': 'A', 'класс': 'РИСК', 'диспозиция': CLOSED, 'чем': 'формула сведена к дому'},
            {'id': 'B1', 'линза': 'B', 'класс': 'НОСИТЕЛЬ', 'диспозиция': PARKED,
             'адресат': '.claude/work/backlog.md §«Название припарковки»',
             'условие-возврата': 'курационный заход после закрытия шага'},
        ],
    }
    if mutate:
        mutate(ledger)
    (root / '.claude' / 'work' / 'code-gate-ledger.json').write_text(
        json.dumps(ledger, ensure_ascii=False, indent=1), encoding='utf-8')
    return root


def _axis(name, expectation):
    try:
        passed, observed = expectation()
    except Exception as failure:                      # noqa: BLE001 — ось меряет ЛЮБОЙ исход
        passed, observed = False, 'ось сама отказала: %r' % failure
    return name, passed, observed


def _fails(mutate, marker, report=REPORT):
    def probe():
        reasons, _, _ = check(LEDGER, _fixture(mutate, report))
        return (any(marker in reason for reason in reasons), '; '.join(reasons) or 'причин нет')
    return probe


def _refuses(prepare):
    def probe():
        try:
            check(LEDGER, prepare())
            return False, 'замер отчитался там, где мерить нечем'
        except Refusal as refusal:
            return True, 'отказ: %s' % str(refusal)[:140]
    return probe


def battery():
    """Оси команды. Каждая — фикстура с заведомо известным исходом."""
    axes = []

    def clean():
        reasons, gating, entries = check(LEDGER, _fixture())
        return (not reasons and gating == 3 and entries == 3,
                'причин непрохождения %d, гейтящих в отчёте %d, записей реестра %d'
                % (len(reasons), gating, entries))
    axes.append(_axis('1. контроль: полный реестр с нулём КОД/РИСК — гейт пройден', clean))

    def park_code(ledger):
        ledger['находки'][0].update({'диспозиция': PARKED, 'чем': '',
                                     'адресат': '.claude/work/backlog.md §«Название припарковки»',
                                     'условие-возврата': 'потом'})
    axes.append(_axis('2. незакрытая находка класса КОД — гейт НЕ пройден',
                      _fails(park_code, 'класс КОД припаркован')))

    def park_risk(ledger):
        ledger['находки'][1].update({'диспозиция': PARKED, 'чем': '',
                                     'адресат': '.claude/work/backlog.md §«Название припарковки»',
                                     'условие-возврата': 'потом'})
    axes.append(_axis('3. незакрытая находка класса РИСК — гейт НЕ пройден',
                      _fails(park_risk, 'класс РИСК припаркован')))

    def parked_other():
        reasons, _, _ = check(LEDGER, _fixture())
        return (not any('B1' in reason for reason in reasons),
                '; '.join(reasons) or 'припаркованная находка прочего класса гейт не роняет')
    axes.append(_axis('4. контроль: припаркованная находка прочего класса гейт не роняет', parked_other))

    axes.append(_axis('5. припарковка без адресата — гейт НЕ пройден',
                      _fails(lambda l: l['находки'][2].pop('адресат'), 'без адресата')))
    axes.append(_axis('6. припарковка без условия возврата — гейт НЕ пройден',
                      _fails(lambda l: l['находки'][2].pop('условие-возврата'), 'без условия возврата')))
    axes.append(_axis('7. адресат припарковки не разрешается — гейт НЕ пройден',
                      _fails(lambda l: l['находки'][2].update(
                          {'адресат': '.claude/work/backlog.md §«Пассажа с таким именем нет»'}),
                          'нет пассажа')))
    axes.append(_axis('8. класс вне закрытого перечня — гейт НЕ пройден',
                      _fails(lambda l: l['находки'][0].update({'класс': 'ПРОЧЕЕ'}),
                             'вне закрытого перечня')))
    axes.append(_axis('9. закрытая находка без указания, чем закрыта, — гейт НЕ пройден',
                      _fails(lambda l: l['находки'][0].update({'чем': '  '}), 'не сказано, чем именно')))
    axes.append(_axis('10. умолчанная находка (счёт реестра меньше отчёта) — гейт НЕ пройден',
                      _fails(lambda l: l['находки'].pop(1), 'умолчанная находка гейт не проходит')))

    def lens_swap(ledger):
        ledger['находки'][1]['линза'] = 'B'
    axes.append(_axis('11. полинзовое расхождение при сошедшемся итоге — гейт НЕ пройден',
                      _fails(lens_swap, 'умолчанная находка гейт не проходит')))

    def no_ledger():
        root = _fixture()
        (root / LEDGER).unlink()
        return root
    axes.append(_axis('12. реестра нет — ЗАМЕР НЕ ПРОВОДИЛСЯ', _refuses(no_ledger)))

    def broken_ledger():
        root = _fixture()
        (root / LEDGER).write_text('{не json', encoding='utf-8')
        return root
    axes.append(_axis('13. реестр не разобран — ЗАМЕР НЕ ПРОВОДИЛСЯ', _refuses(broken_ledger)))

    def no_report():
        root = _fixture()
        (root / 'отчёт.md').unlink()
        return root
    axes.append(_axis('14. отчёта прогона нет — ЗАМЕР НЕ ПРОВОДИЛСЯ', _refuses(no_report)))

    def no_summary():
        return _fixture(report='# Отчёт без сводки счётом\n\nТекст.\n')
    axes.append(_axis('15. в отчёте нет сводки счётом — ЗАМЕР НЕ ПРОВОДИЛСЯ', _refuses(no_summary)))
    return axes


def main(argv):
    ledger = argv[1] if len(argv) > 1 else LEDGER
    axes = battery()
    print('Батарея осей критерия выхода в CODE:')
    for name, passed, observed in axes:
        print('  %s: %s — %s' % ('доказана' if passed else 'НЕ ДОКАЗАНА', name, observed))
    broken = [name for name, passed, _ in axes if not passed]
    if broken:
        print('ЗАМЕР НЕ ПРОВОДИЛСЯ: не доказано осей — %d (%s)' % (len(broken), '; '.join(broken)))
        return 2
    try:
        reasons, gating, entries = check(ledger)
    except Refusal as refusal:
        print('ЗАМЕР НЕ ПРОВОДИЛСЯ: %s' % refusal)
        return 2
    print('Гейтящих находок в отчёте прогона: %d; записей реестра: %d' % (gating, entries))
    for reason in reasons:
        print('  %s' % reason)
    print('КРИТЕРИЙ ВЫХОДА В CODE ПРОЙДЕН' if not reasons
          else 'КРИТЕРИЙ ВЫХОДА В CODE НЕ ПРОЙДЕН: причин %d' % len(reasons))
    return 1 if reasons else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))

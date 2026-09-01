#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Исполнимый критерий выхода в `CODE`: покомпонентный гейт реестра находок.

ПРЕДМЕТ. Прежние две редакции гейта требовали состояния ШАГА: сперва
ЧИСТОГО полнокорпусного прогона (снята 2026-08-31 как недостижимая — за
семь прогонов чистого не было ни одного), затем НУЛЯ незакрытых находок
классов КОД и РИСК до перевода статуса (снята 2026-09-01 как
несходящаяся — девять прогонов подряд закрытие производило больше
половины находок следующего).

Действующая редакция (решение держателя 2026-09-01,
.claude/decisions/code-contact-as-gate.md) меряет НЕ шаг, а КОМПОНЕНТ:

  * КОД   — позиция закрыта до или в момент кодирования СВОЕГО компонента;
            компонент начат при открытой позиции — гейт НЕ пройден;
  * РИСК  — все позиции класса закрыты ПРЕЖДЕ, чем начат любой компонент
            торговой тропы (признак — величина компонента, не суждение);
  * прочие классы — задиспозиционированы (закрыта либо припаркована с
            адресатом и условием возврата);
  * открытый слот гейта грунта гейтит СВОЙ компонент так же, как находка.

Вход — реестр `.claude/work/code-gate-ledger.json`: таблица компонентов
(статус кодирования, признак торговой тропы, ожидаемые слоты грунта) плюс
позиции с привязкой к компонентам. Счёт позиций сверяется с итогом
прогона, названного самим реестром, чтобы гейт нельзя было пройти
умолчанием находки.

ОСИ, КОТОРЫЕ КОМАНДА ОБЪЯВЛЯЕТ (доказаны батареей, исполняемой этим же
прогоном, до замера):
  1. контроль: ни один компонент не начат — гейт пройден при открытых КОД;
  2. КОД открыта, её компонент в работе — гейт НЕ пройден;
  3. КОД открыта, её компонент закодирован — гейт НЕ пройден;
  4. РИСК открыта, начат компонент торговой тропы — гейт НЕ пройден;
  5. контроль: РИСК открыта, начат НЕ торговый компонент — гейт не роняет;
  6. позиция без компонента — гейт НЕ пройден;
  7. позиция называет компонент вне таблицы — гейт НЕ пройден;
  8. «вне компонентов» без названной причины — гейт НЕ пройден;
  9. прочий класс не задиспозиционирован — гейт НЕ пройден;
 10. припарковка без адресата — гейт НЕ пройден;
 11. припарковка без условия возврата — гейт НЕ пройден;
 12. адресат припарковки не разрешается — гейт НЕ пройден;
 13. класс вне закрытого перечня — гейт НЕ пройден;
 14. закрытая позиция без указания, ЧЕМ закрыта, — гейт НЕ пройден;
 15. компонент с открытым слотом грунта начат — гейт НЕ пройден;
 16. статус компонента вне закрытого перечня — гейт НЕ пройден;
 17. умолчанная находка (счёт реестра меньше отчёта) — гейт НЕ пройден;
 18. полинзовое расхождение при сошедшемся итоге — гейт НЕ пройден;
 19. контроль: поздняя таблица отчёта счёт линзы НЕ перебивает (сводка
     читается только из своей секции — ремонт ложно-зелёного F1
     DOCS_CHECK_34);
 20. ожидание грунта со сроком: срок наступил — гейт НЕ пройден;
 21. контроль: срок ожидания грунта не наступил — гейт не роняет;
 22. припарковка со сроком: срок наступил — гейт НЕ пройден;
 23. срок без названного исхода (поле «по-сроку») — гейт НЕ пройден;
 24. срок записан не датой ISO — ЗАМЕР НЕ ПРОВОДИЛСЯ (код 2);
 25. реестра нет — ЗАМЕР НЕ ПРОВОДИЛСЯ (код 2);
 26. реестр не разобран — ЗАМЕР НЕ ПРОВОДИЛСЯ (код 2);
 27. таблицы компонентов нет либо она пуста — ЗАМЕР НЕ ПРОВОДИЛСЯ (код 2);
 28. отчёта прогона, названного реестром, нет — ЗАМЕР НЕ ПРОВОДИЛСЯ (код 2);
 29. в отчёте нет секции сводки счётом — ЗАМЕР НЕ ПРОВОДИЛСЯ (код 2).

СРОК ДИСПОЗИЦИИ (оси 20-24). Диспозиция, выданная НА СРОК, по его
истечении ИСЧЕРПАНА: позиция, которую держали «пока ждём», снова не
задиспозиционирована, и гейт обязан сказать это громко — иначе условие
живёт только в файле, который прочтёт лишь тот, кому оно и так нужно
(класс — .claude/rules/pre-launch-schema-changes.md §«Снятие обеспечено
встречным якорем, а не этой строкой»). Поле `срок` (дата ISO) законно
и у строки компонента рядом с `ждёт-грунта`, и у припаркованной позиции
рядом с `условие-возврата`; парное поле `по-сроку` называет исход —
без него сообщение гейта нечем закрыть. Замер сравнивает срок с
КАЛЕНДАРНЫМ ДНЁМ ПРОГОНА: срок наступает В сам день (today >= срок).

Форма, которой в этом перечне нет, замером НЕ измерена.

Запуск (из корня репозитория):  python3 tools/code-gate-check.py
Прогоняется при переводе шага в `CODE` И перед КАЖДЫМ кодовым заходом,
после простановки статуса кодирования компонента.
Код возврата: 0 — критерий выхода в CODE пройден; 1 — не пройден (чем
именно — в stdout); 2 — ЗАМЕР НЕ ПРОВОДИЛСЯ.
"""
import importlib.util
import json
import re
import sys
import tempfile
from datetime import date, timedelta
from pathlib import Path

# Печать не зависит от кодировки консоли вызывающего: на cp1251-консоли
# объявленной среды вывод падал UnicodeEncodeError (класс описан в backlog
# у anchor-check; тот же ремонт исполнимости, DOCS_CHECK_33 узел 9).
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent
LEDGER = '.claude/work/code-gate-ledger.json'

# Закрытый перечень классов гейтящей находки. Дом признака каждого класса —
# .claude/skills/classify-code-blocking.md.
BLOCKING = ('КОД', 'РИСК')
OTHER = ('НОСИТЕЛЬ', 'ИЗМЕРЕНИЕ', 'ГРУНТ', 'ПРОЦЕСС')
CLASSES = BLOCKING + OTHER

CLOSED = 'закрыта'
PARKED = 'припаркована'
OPEN = 'не закрыта'
DISPOSITIONS = (CLOSED, PARKED, OPEN)

# Статус кодирования компонента. «не начат» — единственный, при котором
# открытая позиция класса КОД гейт не роняет.
NOT_STARTED = 'не начат'
COMPONENT_STATES = (NOT_STARTED, 'в работе', 'закодирован')

OUTSIDE = 'вне компонентов'

# Срок диспозиции и парный исход. Дом действующего срока — реестр; почему
# срок объявлен машине, а не одной строкой решения —
# .claude/decisions/env-wait-deadline.md §«Встречный якорь: чем срок
# срабатывает».
DEADLINE = 'срок'
OUTCOME = 'по-сроку'

# Секция отчёта, в которой живёт сводка счётом. Контракт «сводка отчёта ↔
# реестр» объявлен в доме формата отчёта (.claude/templates/docs/gap-report.md
# §«Сводка счётом — форма, читаемая гейтом»), здесь он исполняется.
# Прежняя редакция брала ЛЮБУЮ строку корпуса, начинающуюся с ячейки A-G,
# и поздняя таблица молча перебивала счёт линзы (ложно-зелёный F1
# DOCS_CHECK_34); теперь разбирается только тело своей секции.
SUMMARY_HEADING = re.compile(r'^##\s+Сводка счётом\s*$')
NEXT_HEADING = re.compile(r'^##\s+')
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
    """Позиции реестра по линзам — из сводки счётом отчёта, из ЕЁ секции."""
    file = root / path
    if not file.is_file():
        raise Refusal('отчёта прогона нет: %s' % path)
    counts = {}
    inside = False
    for line in file.read_text(encoding='utf-8').splitlines():
        if SUMMARY_HEADING.match(line):
            inside = True
            continue
        if inside and NEXT_HEADING.match(line):
            break
        if not inside:
            continue
        found = SUMMARY_ROW.match(line.strip())
        if found:
            counts[found.group(1)] = int(found.group(2))
    if not counts:
        raise Refusal('в отчёте %s нет секции «Сводка счётом» с таблицей линз: '
                      'позиции не пересчитываются' % path)
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


def _components(ledger):
    """Таблица компонентов реестра: имя → запись. Пустая таблица — отказ."""
    rows = ledger.get('компоненты')
    if not rows:
        raise Refusal('реестр не несёт таблицы компонентов: покомпонентный гейт не измерим')
    table = {}
    for row in rows:
        name = str(row.get('имя', '')).strip()
        if not name:
            raise Refusal('в таблице компонентов есть строка без имени')
        table[name] = row
    return table


def _deadline_reasons(entry, name, reasons, today):
    """Срок диспозиции: наступил — диспозиция исчерпана, позиция снова открыта.

    Поля необязательны; объявленный срок обязан быть датой ISO (иначе мерить
    нечем — отказ) и обязан называть исход (иначе сообщение гейта нечем
    закрыть).
    """
    raw = entry.get(DEADLINE)
    if raw is None:
        return
    text = str(raw).strip()
    try:
        due = date.fromisoformat(text)
    except ValueError:
        raise Refusal('%s: срок «%s» — не дата ISO (YYYY-MM-DD); наступил он '
                      'или нет, замер сказать не может' % (name, text))
    outcome = str(entry.get(OUTCOME, '')).strip()
    if not outcome:
        reasons.append('%s: срок %s объявлен без исхода (поле «%s») — по наступлении '
                       'не сказано, что делать' % (name, text, OUTCOME))
    if today >= due:
        reasons.append('%s: срок %s наступил — диспозиция, выданная на срок, исчерпана; '
                       'требуется пере-диспозиция: %s'
                       % (name, text, outcome or 'исход не назван'))


def _component_reasons(table, today):
    """Дефекты самой таблицы компонентов: статус, ожидание грунта и его срок."""
    reasons = []
    for name, row in sorted(table.items()):
        state = row.get('статус')
        if state not in COMPONENT_STATES:
            reasons.append('компонент %s: статус «%s» вне закрытого перечня (%s)'
                           % (name, state, ', '.join(COMPONENT_STATES)))
            continue
        pending = [str(slot) for slot in row.get('ждёт-грунта', []) if str(slot).strip()]
        if pending and state != NOT_STARTED:
            reasons.append('компонент %s: начат при открытых слотах гейта грунта (%s)'
                           % (name, ', '.join(pending)))
        _deadline_reasons(row, 'компонент %s' % name, reasons, today)
    return reasons


def _started(table):
    """Компоненты, кодирование которых начато (любой статус, кроме «не начат»)."""
    return {name for name, row in table.items()
            if row.get('статус') in COMPONENT_STATES and row.get('статус') != NOT_STARTED}


def _trading_started(table):
    """Начатые компоненты торговой тропы — операнд гейта класса РИСК."""
    return sorted(name for name in _started(table) if table[name].get('торговая-тропа'))


def _position_components(entry, table, name, reasons):
    """Компоненты позиции: список имён либо «вне компонентов» с причиной.

    Возвращает множество имён из таблицы (для «вне компонентов» — пустое).
    """
    declared = entry.get('компонент')
    if isinstance(declared, str):
        declared = [declared]
    declared = [str(item).strip() for item in (declared or []) if str(item).strip()]
    if not declared:
        reasons.append('%s: не названо ни одного компонента — непривязанная позиция не гейтит ничего'
                       % name)
        return set()
    if OUTSIDE in declared:
        if len(declared) > 1:
            reasons.append('%s: «%s» вместе с именами компонентов — привязка неоднозначна'
                           % (name, OUTSIDE))
        elif not str(entry.get('причина', '')).strip():
            reasons.append('%s: «%s» без названной причины' % (name, OUTSIDE))
        return set()
    known = set()
    for item in declared:
        if item in table:
            known.add(item)
        else:
            reasons.append('%s: компонент «%s» не назван в таблице компонентов реестра'
                           % (name, item))
    return known


def check(ledger_path=LEDGER, root=ROOT, today=None):
    """Проверка реестра гейтов. Возвращает список причин непрохождения."""
    today = today or date.today()
    file = root / ledger_path
    if not file.is_file():
        raise Refusal('реестра гейтов нет: %s' % ledger_path)
    try:
        ledger = json.loads(file.read_text(encoding='utf-8'))
    except (ValueError, OSError) as failure:
        raise Refusal('реестр не разобран: %s' % failure)
    report = ledger.get('отчёт')
    if not report:
        raise Refusal('реестр не называет отчёт прогона, с которым сверяется')
    table = _components(ledger)
    counts = report_counts(report, root)
    reasons = _component_reasons(table, today)
    started = _started(table)
    trading = _trading_started(table)
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
        bound = _position_components(entry, table, name, reasons)
        if disposition not in DISPOSITIONS:
            reasons.append('%s: диспозиция «%s» — исходом не является (ожидается %s)'
                           % (name, disposition, ', '.join('«%s»' % item for item in DISPOSITIONS)))
            continue
        if disposition == CLOSED:
            if not str(entry.get('чем', '')).strip():
                reasons.append('%s: объявлена закрытой, но не сказано, чем именно' % name)
            continue
        if disposition == PARKED:
            if klass in BLOCKING:
                reasons.append('%s: класс %s припаркован — классы %s закрываются, а не паркуются'
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
            _deadline_reasons(entry, name, reasons, today)
            continue
        # Диспозиция «не закрыта»: гейтит ли она что-нибудь ПРЯМО СЕЙЧАС —
        # зависит от класса и от статуса кодирования её компонентов.
        if klass == 'КОД':
            active = sorted(bound & started)
            if active:
                reasons.append('%s: класс КОД не закрыт, а компонент начат (%s) — закрытие обязано '
                               'предшествовать кодированию либо идти тем же ходом'
                               % (name, ', '.join(active)))
        elif klass == 'РИСК':
            if trading:
                reasons.append('%s: класс РИСК не закрыт, а компонент торговой тропы начат (%s)'
                               % (name, ', '.join(trading)))
        else:
            reasons.append('%s: класс %s не задиспозиционирован (ожидается «%s» либо «%s»)'
                           % (name, klass, CLOSED, PARKED))
    for lens, expected in sorted(counts.items()):
        actual = seen.get(lens, 0)
        if actual != expected:
            reasons.append('линза %s: отчёт прогона насчитал %d позиций, реестр несёт %d — '
                           'умолчанная находка гейт не проходит' % (lens, expected, actual))
    for lens in sorted(set(seen) - set(counts)):
        reasons.append('реестр несёт находки линзы %s, которой в сводке отчёта нет' % lens)
    return reasons, sum(counts.values()), len(ledger.get('находки', []))


# --- батарея осей: исполняется ЭТИМ ЖЕ прогоном, до замера -------------------

REPORT = """# Проба отчёта прогона

## Сводка счётом

| Линза | Предмет | Позиций | Прочих |
|---|---|---|---|
| A | ядро | 2 | 3 |
| B | правила | 2 | 0 |
| **Итого** | | **4** | **3** |

## Узлы гейтящих находок

Поздняя таблица, которая прежней редакцией перебивала счёт линзы:

| A | ядро сделки | 9 | — |
|---|---|---|---|
| B | правила | 9 | — |
"""

BACKLOG = """# Бэклог пробы

## Пункт-адресат

**Название припарковки.** Текст пункта.
"""


def _fixture(mutate=None, report=REPORT):
    """Песочница: реестр с таблицей компонентов, отчёт прогона и файл-адресат."""
    root = Path(tempfile.mkdtemp())
    (root / '.claude' / 'work').mkdir(parents=True)
    (root / '.claude' / 'work' / 'backlog.md').write_text(BACKLOG, encoding='utf-8')
    (root / 'отчёт.md').write_text(report, encoding='utf-8')
    ledger = {
        'прогон': 'проба',
        'отчёт': 'отчёт.md',
        'компоненты': [
            {'имя': 'Каркас', 'заход': 1, 'торговая-тропа': False, 'статус': NOT_STARTED},
            {'имя': 'Тропа', 'заход': 2, 'торговая-тропа': True, 'статус': NOT_STARTED},
            {'имя': 'Движения', 'заход': 3, 'торговая-тропа': True, 'статус': NOT_STARTED,
             'ждёт-грунта': ['п. 2 × AG6.1']},
        ],
        'находки': [
            {'id': 'A1', 'линза': 'A', 'класс': 'КОД', 'компонент': ['Каркас'], 'диспозиция': OPEN},
            {'id': 'A2', 'линза': 'A', 'класс': 'РИСК', 'компонент': ['Тропа'], 'диспозиция': OPEN},
            {'id': 'B1', 'линза': 'B', 'класс': 'НОСИТЕЛЬ', 'компонент': [OUTSIDE],
             'причина': 'машинерия проверки', 'диспозиция': PARKED,
             'адресат': '.claude/work/backlog.md §«Название припарковки»',
             'условие-возврата': 'курационный заход после закрытия шага'},
            {'id': 'B2', 'линза': 'B', 'класс': 'КОД', 'компонент': ['Тропа'],
             'диспозиция': CLOSED, 'чем': 'правило дописано, доки правлены тем же ходом'},
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


def _holds(mutate, marker, report=REPORT):
    """Контроль: мутация НЕ поднимает причину с этим маркером."""
    def probe():
        reasons, _, _ = check(LEDGER, _fixture(mutate, report))
        return (not any(marker in reason for reason in reasons),
                '; '.join(reasons) or 'причин нет')
    return probe


def _refuses(prepare):
    def probe():
        try:
            check(LEDGER, prepare())
            return False, 'замер отчитался там, где мерить нечем'
        except Refusal as refusal:
            return True, 'отказ: %s' % str(refusal)[:140]
    return probe


def _state(ledger, index, state):
    ledger['компоненты'][index]['статус'] = state


def battery():
    """Оси команды. Каждая — фикстура с заведомо известным исходом."""
    axes = []

    def clean():
        reasons, gating, entries = check(LEDGER, _fixture())
        return (not reasons and gating == 4 and entries == 4,
                'причин непрохождения %d, позиций в отчёте %d, записей реестра %d'
                % (len(reasons), gating, entries))
    axes.append(_axis('1. контроль: ни один компонент не начат — гейт пройден при открытых КОД', clean))

    axes.append(_axis('2. КОД открыта, её компонент в работе — гейт НЕ пройден',
                      _fails(lambda l: _state(l, 0, 'в работе'), 'A1: класс КОД не закрыт')))
    axes.append(_axis('3. КОД открыта, её компонент закодирован — гейт НЕ пройден',
                      _fails(lambda l: _state(l, 0, 'закодирован'), 'A1: класс КОД не закрыт')))
    axes.append(_axis('4. РИСК открыта, начат компонент торговой тропы — гейт НЕ пройден',
                      _fails(lambda l: _state(l, 1, 'в работе'), 'A2: класс РИСК не закрыт')))
    axes.append(_axis('5. контроль: РИСК открыта, начат НЕ торговый компонент — гейт не роняет',
                      _holds(lambda l: _state(l, 0, 'в работе'), 'A2: класс РИСК не закрыт')))
    axes.append(_axis('6. позиция без компонента — гейт НЕ пройден',
                      _fails(lambda l: l['находки'][0].update({'компонент': []}),
                             'не названо ни одного компонента')))
    axes.append(_axis('7. позиция называет компонент вне таблицы — гейт НЕ пройден',
                      _fails(lambda l: l['находки'][0].update({'компонент': ['Небывалый']}),
                             'не назван в таблице компонентов')))
    axes.append(_axis('8. «вне компонентов» без названной причины — гейт НЕ пройден',
                      _fails(lambda l: l['находки'][2].pop('причина'), 'без названной причины')))
    axes.append(_axis('9. прочий класс не задиспозиционирован — гейт НЕ пройден',
                      _fails(lambda l: l['находки'][2].update({'диспозиция': OPEN}),
                             'класс НОСИТЕЛЬ не задиспозиционирован')))
    axes.append(_axis('10. припарковка без адресата — гейт НЕ пройден',
                      _fails(lambda l: l['находки'][2].pop('адресат'), 'без адресата')))
    axes.append(_axis('11. припарковка без условия возврата — гейт НЕ пройден',
                      _fails(lambda l: l['находки'][2].pop('условие-возврата'), 'без условия возврата')))
    axes.append(_axis('12. адресат припарковки не разрешается — гейт НЕ пройден',
                      _fails(lambda l: l['находки'][2].update(
                          {'адресат': '.claude/work/backlog.md §«Пассажа с таким именем нет»'}),
                          'нет пассажа')))
    axes.append(_axis('13. класс вне закрытого перечня — гейт НЕ пройден',
                      _fails(lambda l: l['находки'][0].update({'класс': 'ПРОЧЕЕ'}),
                             'вне закрытого перечня')))
    axes.append(_axis('14. закрытая позиция без указания, чем закрыта, — гейт НЕ пройден',
                      _fails(lambda l: l['находки'][3].update({'чем': '  '}), 'не сказано, чем именно')))
    axes.append(_axis('15. компонент с открытым слотом грунта начат — гейт НЕ пройден',
                      _fails(lambda l: _state(l, 2, 'в работе'), 'при открытых слотах гейта грунта')))
    axes.append(_axis('16. статус компонента вне закрытого перечня — гейт НЕ пройден',
                      _fails(lambda l: _state(l, 0, 'почти готов'), 'вне закрытого перечня')))
    axes.append(_axis('17. умолчанная находка (счёт реестра меньше отчёта) — гейт НЕ пройден',
                      _fails(lambda l: l['находки'].pop(1), 'умолчанная находка гейт не проходит')))

    def lens_swap(ledger):
        ledger['находки'][1]['линза'] = 'B'
    axes.append(_axis('18. полинзовое расхождение при сошедшемся итоге — гейт НЕ пройден',
                      _fails(lens_swap, 'умолчанная находка гейт не проходит')))

    def late_table():
        # Отчёт пробы несёт ПОСЛЕ сводки таблицу тех же линз с числом 9.
        # Прежняя редакция брала её и объявляла расхождение (либо, при
        # обратном раскладе, молча пропускала умолчанную находку).
        reasons, gating, _ = check(LEDGER, _fixture())
        return (not any('умолчанная находка' in reason for reason in reasons) and gating == 4,
                'позиций по сводке %d, причин расхождения %d' % (gating, len(reasons)))
    axes.append(_axis('19. контроль: поздняя таблица отчёта счёт линзы НЕ перебивает', late_table))

    # Срок диспозиции. Даты пробы строятся ОТ ДНЯ ПРОГОНА (вчера/послезавтра),
    # поэтому пробы не протухают вместе с календарём.
    passed_day = (date.today() - timedelta(days=1)).isoformat()
    future_day = (date.today() + timedelta(days=30)).isoformat()

    def wait_deadline(ledger, when):
        ledger['компоненты'][2].update({DEADLINE: when, OUTCOME: 'вернуть вопрос держателю'})

    axes.append(_axis('20. ожидание грунта со сроком: срок наступил — гейт НЕ пройден',
                      _fails(lambda l: wait_deadline(l, passed_day), 'наступил — диспозиция')))
    axes.append(_axis('21. контроль: срок ожидания грунта не наступил — гейт не роняет',
                      _holds(lambda l: wait_deadline(l, future_day), 'наступил — диспозиция')))
    axes.append(_axis('22. припарковка со сроком: срок наступил — гейт НЕ пройден',
                      _fails(lambda l: l['находки'][2].update(
                          {DEADLINE: passed_day, OUTCOME: 'вернуться курационным заходом'}),
                          'наступил — диспозиция')))
    axes.append(_axis('23. срок без названного исхода — гейт НЕ пройден',
                      _fails(lambda l: l['компоненты'][2].update({DEADLINE: future_day}),
                             'без исхода')))

    def bad_deadline():
        return _fixture(lambda l: wait_deadline(l, 'через неделю'))
    axes.append(_axis('24. срок записан не датой ISO — ЗАМЕР НЕ ПРОВОДИЛСЯ', _refuses(bad_deadline)))

    def no_ledger():
        root = _fixture()
        (root / LEDGER).unlink()
        return root
    axes.append(_axis('25. реестра нет — ЗАМЕР НЕ ПРОВОДИЛСЯ', _refuses(no_ledger)))

    def broken_ledger():
        root = _fixture()
        (root / LEDGER).write_text('{не json', encoding='utf-8')
        return root
    axes.append(_axis('26. реестр не разобран — ЗАМЕР НЕ ПРОВОДИЛСЯ', _refuses(broken_ledger)))

    def no_components():
        return _fixture(lambda l: l.update({'компоненты': []}))
    axes.append(_axis('27. таблицы компонентов нет — ЗАМЕР НЕ ПРОВОДИЛСЯ', _refuses(no_components)))

    def no_report():
        root = _fixture()
        (root / 'отчёт.md').unlink()
        return root
    axes.append(_axis('28. отчёта прогона нет — ЗАМЕР НЕ ПРОВОДИЛСЯ', _refuses(no_report)))

    def no_summary():
        return _fixture(report='# Отчёт без секции сводки\n\n## Прочее\n\n| A | х | 1 |\n')
    axes.append(_axis('29. в отчёте нет секции сводки счётом — ЗАМЕР НЕ ПРОВОДИЛСЯ', _refuses(no_summary)))
    return axes


def main(argv):
    ledger = argv[1] if len(argv) > 1 else LEDGER
    axes = battery()
    print('Батарея осей покомпонентного критерия выхода в CODE:')
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
    print('Позиций в сводке отчёта: %d; записей реестра: %d' % (gating, entries))
    for reason in reasons:
        print('  %s' % reason)
    print('КРИТЕРИЙ ВЫХОДА В CODE ПРОЙДЕН' if not reasons
          else 'КРИТЕРИЙ ВЫХОДА В CODE НЕ ПРОЙДЕН: причин %d' % len(reasons))
    return 1 if reasons else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))

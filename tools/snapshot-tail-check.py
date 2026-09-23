#!/usr/bin/env python3
"""Предмет проверки: живой снапшот не отсылает к прежним снапшотам как к носителю.

Снапшот, прочитанный вместе с корпусом, не требует открывать прежние снапшоты
(решение держателя 2026-09-23, `.claude/rules/snapshot-format.md` §«Снапшот
отвечает сам»). Постоянное содержимое живёт в домах по своему вопросу, снапшот
несёт дельту и указатели.

ПОЧЕМУ ИНСТРУМЕНТ ЗАВЕДЁН. Формула «остаётся в силе дословно из vN» копилась
незаметно: каждая сессия писала её честно, и никто не видел, что ответ на «где
мы сейчас» размазан по 260 снапшотам замыкания — больше, чем помещается в окно
контекста. Правило без прогона вернуло бы хвост тем же путём.

ОБЛАСТЬ. Файлы `.claude/snapshots/snapshot-v<N>.md`. Прежний — любой снапшот с
номером меньше своего; номер своего берётся из имени файла.

ПИСЬМЕННЫЕ ФОРМЫ УПОМИНАНИЯ, КОТОРЫЕ ДЕТЕКТОР ВИДИТ (каждая доказана осью):
  - номер `vN` строчной латиницей, отдельным словом;
  - диапазон `vN-vM`, `vN–vM`, `vN—vM`, в том числе со второй границей без `v`;
  - имя файла `snapshot-vN` (с путём и расширением или без).
Не видит — и это названное ограничение: хвост без номера снапшота («как
прежде», «см. прошлый снапшот»), заглавную `V` (у миграций `V1__` она своя) и
номер версии с точкой (`v0.33.0`).

ЗАКОННЫ БЕЗ ОГОВОРКИ — якоря дельты, а не носители:
  - строка, начинающаяся с «Сменяет vN»;
  - заголовок «## Что сделано после vN».
ЗАКОННА С ОБЪЯВЛЕНИЕМ — ссылка на прежний снапшот как на датированную запись:
строка-маркер `<!-- snapshot-ref: датированная запись — <что было тогда> -->`
перед абзацем со ссылкой. Маркер без причины либо без ссылки в следующем
абзаце — дефект.

Код возврата: 0 — дефектов нет; 1 — есть; 2 — не измерялось (батарея не
доказана, каталога или снапшота нет, файл не читается как UTF-8).
"""
import os
import re
import sys

SNAP_DIR = os.path.join('.claude', 'snapshots')
NAME = re.compile(r'^snapshot-v(\d+)\.md$')
REF = re.compile(r'(?<![0-9A-Za-zА-Яа-яЁё_.])(?:snapshot-)?v(\d{1,4})'
                 r'(?:\s*[-–—]\s*v?(\d{1,4}))?(?![0-9A-Za-z_])(?!\.\d)')
HEADER_ANCHOR = re.compile(r'^## Что сделано после v\d+\s*$')
LINE_ANCHOR = re.compile(r'^Сменяет v\d+')
MARKER = re.compile(r'^<!--\s*snapshot-ref:\s*(.*?)\s*-->\s*$')
MARKER_REASON = re.compile(r'^датированная запись\s+—\s+\S')


def prior_refs(line, own):
    """Упоминания прежних снапшотов в строке: список (позиция, текст)."""
    found = []
    for match in REF.finditer(line):
        low = int(match.group(1))
        if low < own:
            found.append((match.start(), match.group(0)))
    return found


def check(text, own):
    """Разбор снапшота: (число упоминаний, число объявленных, дефекты)."""
    lines = text.replace('\r\n', '\n').split('\n')
    defects = []
    total = 0
    declared = 0
    exempt_open = None  # номер строки маркера, чей абзац сейчас идёт
    exempt_used = False
    awaiting = None  # маркер ждёт своего абзаца (пустые строки до него)
    for number, line in enumerate(lines, 1):
        marker = MARKER.match(line)
        if marker:
            if exempt_open is not None and not exempt_used:
                defects.append('строка %d: маркер без ссылки на прежний снапшот '
                               'в следующем абзаце' % exempt_open)
            if not MARKER_REASON.match(marker.group(1)):
                defects.append('строка %d: маркер без формы «датированная запись — '
                               '<что было тогда>»' % number)
            awaiting = number
            exempt_open = None
            exempt_used = False
            continue
        if not line.strip():
            if exempt_open is not None:
                if not exempt_used:
                    defects.append('строка %d: маркер без ссылки на прежний снапшот '
                                   'в следующем абзаце' % exempt_open)
                exempt_open = None
            continue
        if awaiting is not None:
            exempt_open = awaiting
            exempt_used = False
            awaiting = None
        refs = prior_refs(line, own)
        if not refs:
            continue
        if HEADER_ANCHOR.match(line):
            refs = refs[1:]
        elif LINE_ANCHOR.match(line):
            refs = refs[1:]
        for _, ref in refs:
            total += 1
            if exempt_open is not None:
                declared += 1
                exempt_used = True
            else:
                defects.append('строка %d: отсылка к прежнему снапшоту «%s» — '
                               'носитель содержимого у снапшота не бывает'
                               % (number, ref))
    if awaiting is not None:
        defects.append('строка %d: маркер в конце файла без абзаца' % awaiting)
    if exempt_open is not None and not exempt_used:
        defects.append('строка %d: маркер без ссылки на прежний снапшот '
                       'в следующем абзаце' % exempt_open)
    return total, declared, defects


def battery():
    """Оси детектора и контроли на ложное срабатывание."""
    axes = []

    def defect(text, title):
        _, _, found = check(text, 411)
        axes.append((title, bool(found), 'дефектов: %d' % len(found)))

    def clean(text, title, declared=0):
        total, got, found = check(text, 411)
        axes.append((title, not found and got == declared,
                     'упоминаний: %d, объявлено: %d, дефектов: %d'
                     % (total, got, len(found))))

    defect('Ловушки v206 остаются в силе.\n', 'ось 1: номер `vN` — дефект')
    defect('Всё, что перечисляли v325-v410, в силе.\n', 'ось 2: диапазон через дефис — дефект')
    defect('Ловушки v206–v410 в силе.\n', 'ось 3: диапазон через тире — дефект')
    defect('См. `.claude/work/history/snapshots/snapshot-v302.md`.\n',
           'ось 4: имя файла снапшота — дефект')
    defect('Сменяет v410. Против v132 не изменилась.\n',
           'ось 5: вторая отсылка в строке якоря — дефект')
    defect('<!-- snapshot-ref: датированная запись — было -->\nТекст без номера.\n',
           'ось 6: маркер без ссылки — дефект')
    defect('<!-- snapshot-ref: как есть -->\nВ v300 было так.\n',
           'ось 7: маркер без формы причины — дефект')
    defect('<!-- snapshot-ref: датированная запись — было -->\nАбзац.\n\nВ v300 было так.\n',
           'ось 8: маркер покрывает только следующий абзац — дефект')

    clean('# Снапшот v411\n\nСменяет v410. Фаза 2.\n\n## Что сделано после v410\n',
          'контроль: собственный номер и якоря дельты — чисто')
    clean('<!-- snapshot-ref: датированная запись — что было на v300 -->\n'
          'На v300 шаг стоял в DOCS.\n', 'контроль: объявленная ссылка — чисто', 1)
    clean('Kind v0.33.0, миграция V1__init, JDK 25.\n',
          'контроль: версия с точкой и заглавная `V` — чисто')
    clean('Заходы 83-130 и Д2002-Д2005.\n', 'контроль: номера без `v` — чисто')
    clean('Сменяет v410.\r\n', 'контроль: CRLF — чисто')
    return axes


def main():
    base_dir = sys.argv[1] if len(sys.argv) > 1 else '.'
    axes = battery()
    print('--- батарея осей детектора (исполняется той же командой)')
    for title, passed, observed in axes:
        print('  %s: %s — %s' % ('доказана' if passed else 'НЕ ДОКАЗАНА', title, observed))
    if not all(passed for _, passed, _ in axes):
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: батарея не доказана')
        return 2

    snap_dir = os.path.join(base_dir, SNAP_DIR)
    if not os.path.isdir(snap_dir):
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: каталога %s нет' % SNAP_DIR)
        return 2
    snaps = sorted(n for n in os.listdir(snap_dir) if NAME.match(n))
    if not snaps:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: в %s нет снапшота' % SNAP_DIR)
        return 2

    defects = []
    for name in snaps:
        own = int(NAME.match(name).group(1))
        try:
            text = open(os.path.join(snap_dir, name), encoding='utf-8').read()
        except (OSError, UnicodeDecodeError) as error:
            print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: %s не читается как UTF-8 — %s' % (name, error))
            return 2
        total, declared, found = check(text, own)
        print('%s: упоминаний прежних снапшотов %d, из них объявлено датированной '
              'записью %d; ДЕФЕКТОВ: %d' % (name, total, declared, len(found)))
        defects.extend('%s — %s' % (name, d) for d in found)
    print('  не мерится (названное ограничение): хвост без номера снапшота')
    for text in defects:
        print('  ХВОСТ: %s' % text)
    return 1 if defects else 0


if __name__ == '__main__':
    sys.exit(main())

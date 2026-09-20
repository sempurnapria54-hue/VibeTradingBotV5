#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Расхождение ОБЪЯВЛЕННЫХ КОПИЙ одной формы, живущих в разных деревьях.

ПРЕДМЕТ. Форма объявлена одна, а кода два-три: перенести её в общий артефакт
нельзя (класс отказа свой у каждого дерева, имя принципала своё, конфигурация
каркаса принадлежит приложению), и копии расходятся не переименованием, а
ПРАВКОЙ ОДНОГО ДЕРЕВА — заход по соседу правит его класс, близнец остаётся
прежним. Ни сборка, ни один прогон корпуса этого не видел: оба дерева
компилируются и проходят свои пробы (.claude/rules/carrier-levels.md;
.claude/tests/cases/platform-shared-logic.md §«U12 — Тождество копий формы»).

ЧТО МЕРИТСЯ — ИСПОЛНЯЕМОЕ ТЕЛО, А НЕ ТЕКСТ ФАЙЛА. Пакет, импорты, пустые
строки и комментарии снимаются: javadoc копий расходится сегодня по существу
(дом поведения у одной из них), и это ОТДЕЛЬНЫЙ дефект носителя со своим
домом — смешав его с телом, прогон отказывал бы на том, чего не чинит.

ЧЕГО ПРОГОН НЕ МЕРИТ, И ЭТО НАЗВАНО. Полнота реестра. Семейство копий,
которого в реестре нет, прогону невидимо: одинаковых простых имён в дереве
восемь десятков, и большинство из них копиями НЕ являются (у каждого сервиса
свой SecurityConfig). Признак «копия одной формы» читается смыслом, а не
файловой системой, — поэтому реестр ведёт пишущий, и это остаток той же
природы, что популяция tools/retired-check.py.

Запуск (из корня репозитория):  py tools/peer-copy-check.py
Код возврата: 0 — измерено, расхождений нет; 1 — измерено, расхождения есть;
2 — ПРОВЕРКА НЕ ПРОВОДИЛАСЬ (нет файла члена семейства, семейство из одного
члена, пустой реестр, недоказанная ось батареи).
"""

import os
import re
import sys
import tempfile

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

TC = "services/trading-core/src/main/java/com/example/tradingcore"
ST = "services/strategies/src/main/java/com/example/strategies"
AU = "services/audit/src/main/java/com/example/audit"
SA = "services/statistics/src/main/java/com/example/statistics"

# --- реестр объявленных семейств копий ----------------------------------
# `различия` — шаблоны строк ИСПОЛНЯЕМОГО тела, расхождение которых
# ОБЪЯВЛЕНО; каждый обязан срабатывать у КАЖДОГО члена, иначе объявление
# мертво и прогон отказывает: мёртвая маска прячет ровно то, ради чего
# заведена.
FAMILIES = [
    {
        "имя": "PeerCall — разбор отказа соседа по ярусу",
        "пути": [
            TC + "/integration/internal/api/PeerCall.java",
            ST + "/integration/internal/api/PeerCall.java",
        ],
        "различия": [],
    },
    {
        "имя": "AsyncActorContextConfigurer — перенос контекста хода в чужой тред",
        "пути": [
            TC + "/config/AsyncActorContextConfigurer.java",
            ST + "/config/AsyncActorContextConfigurer.java",
        ],
        "различия": [],
    },
    {
        "имя": "ServiceTokenProvider — исходящая сервисная идентичность",
        "пути": [
            TC + "/integration/internal/api/ServiceTokenProvider.java",
            ST + "/integration/internal/api/ServiceTokenProvider.java",
        ],
        # Имя принципала равно имени своего сервиса — различие объявлено
        # javadoc обеих копий и docs/architecture/contracts.md §«Контекст
        # тенанта в вызове».
        "различия": [r'^private static final String PRINCIPAL_NAME = ".+";$'],
    },
    # --- форма приёма durable-потребителя -------------------------------
    # Дом формы объявлен сквозным (docs/rules/durable-consumer-reception.md
    # §«Форма исполнителей приёма»), а кода два — по дереву на потребителя.
    # Семейства внесены под-шагом 3 шага 12 фазы 2 вместе с кодом кейсов
    # (.claude/tests/cases/durable-reception.md, группа U15): до них
    # расхождение копий приёма не мерил ни один прогон.
    {
        "имя": "ReceptionPairMoments — моменты пары состояния приёма",
        "пути": [
            AU + "/domain/model/ReceptionPairMoments.java",
            SA + "/domain/model/ReceptionPairMoments.java",
        ],
        "различия": [],
    },
    {
        "имя": "PairLagOperands — операнды алерта на лаг пары",
        "пути": [
            AU + "/domain/model/PairLagOperands.java",
            SA + "/domain/model/PairLagOperands.java",
        ],
        "различия": [],
    },
    {
        "имя": "ReceptionOffsetTracker — ожидаемое смещение партиции",
        "пути": [
            AU + "/integration/internal/event/ReceptionOffsetTracker.java",
            SA + "/integration/internal/event/ReceptionOffsetTracker.java",
        ],
        "различия": [],
    },
    {
        "имя": "TopicRetentionProvider — применённый срок хранения темы",
        "пути": [
            AU + "/integration/internal/event/TopicRetentionProvider.java",
            SA + "/integration/internal/event/TopicRetentionProvider.java",
        ],
        "различия": [],
    },
    {
        "имя": "ConsumerLagProvider — остаток непринятого по теме",
        "пути": [
            AU + "/integration/internal/event/ConsumerLagProvider.java",
            SA + "/integration/internal/event/ConsumerLagProvider.java",
        ],
        "различия": [],
    },
    {
        "имя": "ReceptionHaltMarker — флаг остановки приёма",
        "пути": [
            AU + "/integration/internal/event/ReceptionHaltMarker.java",
            SA + "/integration/internal/event/ReceptionHaltMarker.java",
        ],
        # Тип службы приёма — свой у каждого дерева: у неё своё следствие
        # (строка журнала против факта), и общего носителя у них нет.
        "различия": [r"^private final \w+ReceptionService receptionService;$"],
    },
    {
        "имя": "RebalanceListener — первый момент обнаружения разрыва",
        "пути": [
            AU + "/integration/internal/event/JournalRebalanceListener.java",
            SA + "/integration/internal/event/ReceptionRebalanceListener.java",
        ],
        # Имя класса и тип службы приёма — свои у каждого дерева; прочее
        # тело обязано совпадать дословно.
        "различия": [
            r"^public class \w+RebalanceListener implements ConsumerAwareRebalanceListener \{$",
            r"^private final \w+ReceptionService receptionService;$",
        ],
    },
    {
        "имя": "ReceptionMetrics — ряды экспорта операндов алерта",
        "пути": [
            AU + "/metrics/JournalReceptionMetrics.java",
            SA + "/metrics/ReceptionMetrics.java",
        ],
        # Имя класса и имя его конструктора — своё у каждого дерева; имена
        # самих рядов лежат константами своего сервиса и в тело не входят.
        "различия": [
            r"^public class \w*ReceptionMetrics \{$",
            r"^public \w*ReceptionMetrics\(MeterRegistry registry\) \{$",
        ],
    },
    {
        "имя": "ReceptionStateCompletenessQueries — три запроса полноты",
        "пути": [
            AU + "/persistence/repository/ReceptionStateCompletenessQueries.java",
            SA + "/persistence/repository/ReceptionStateCompletenessQueries.java",
        ],
        # Имя строки состояния — своё у каждого дерева: таблицы две, и
        # каждая принадлежит своей базе.
        "различия": [r"^from \w*ReceptionStateEntity state$"],
    },
]

BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.DOTALL)


def normalize(text, masks):
    """Исполняемое тело: без пакета, импортов, комментариев и пустых строк.

    Строка, подпавшая под объявленную маску, заменяется её номером — так
    объявленное различие перестаёт быть расхождением, не пряча соседних
    строк.
    """
    text = BLOCK_COMMENT.sub("", text)
    out = []
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("//"):
            continue
        if line.startswith("package ") or line.startswith("import "):
            continue
        for i, mask in enumerate(masks):
            if re.match(mask, line):
                line = "<объявленное различие %d>" % i
                break
        out.append(line)
    return out


def mask_hits(text, mask):
    """Сколько раз маска сработала в исполняемом теле."""
    return sum(1 for line in normalize(text, []) if re.match(mask, line))


def compare(bodies):
    """Первое расхождение тел: (индекс члена, номер строки, две строки)."""
    base = bodies[0]
    for member in range(1, len(bodies)):
        other = bodies[member]
        for n in range(max(len(base), len(other))):
            a = base[n] if n < len(base) else "<конец файла>"
            b = other[n] if n < len(other) else "<конец файла>"
            if a != b:
                return member, n + 1, a, b
    return None


def run_family(family, absolute=False):
    """Исход семейства: OK | DIFF:<что> | DEAD:<маска> | ABSENT:<путь> | LONE."""
    paths = family["пути"]
    if len(paths) < 2:
        return "LONE"
    texts = []
    for rel in paths:
        path = rel if absolute else os.path.join(REPO_ROOT, rel)
        if not os.path.isfile(path):
            return "ABSENT:%s" % rel
        with open(path, encoding="utf-8") as handle:
            texts.append(handle.read())
    masks = family["различия"]
    for mask in masks:
        if min(mask_hits(text, mask) for text in texts) == 0:
            return "DEAD:%s" % mask
    found = compare([normalize(text, masks) for text in texts])
    if found is None:
        return "OK"
    member, line, first, second = found
    return "DIFF:член %d, строка %d: «%s» против «%s»" % (member, line, first, second)


# --- батарея осей: исполняется тем же прогоном ---------------------------
CLASS = """package a.b;

import java.util.List;

/** Javadoc, который телом не считается. */
class Form {

    private static final String PRINCIPAL_NAME = "%s";

    // строчный комментарий
    void run() {
        %s
    }
}
"""

PRINCIPAL_MASK = r'^private static final String PRINCIPAL_NAME = ".+";$'


def probe(work, name, members, masks):
    """Гоняет одно синтетическое семейство; отдаёт его исход."""
    paths = []
    for i, body in enumerate(members):
        path = os.path.join(work, "%s%d.java" % (name, i))
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(body)
        paths.append(path)
    return run_family({"имя": name, "пути": paths, "различия": masks}, absolute=True)


def report_axis(title, observed, expected_prefix, failures):
    if observed.startswith(expected_prefix):
        print("  доказана: %s — исход %s" % (title, observed.split(":")[0]))
        return
    print("  НЕ ДОКАЗАНА: %s — ожидался %s, получен %s" % (title, expected_prefix, observed))
    failures.append(title)


def battery():
    failures = []
    work = tempfile.mkdtemp()
    same = CLASS % ("svc", "List.of(1);")
    twin = CLASS % ("svc", "List.of(1);")
    other_principal = CLASS % ("neighbour", "List.of(1);")
    other_body = CLASS % ("svc", "List.of(2);")
    other_comment = same.replace("Javadoc, который телом не считается.", "Другой javadoc.")

    report_axis("1. расхождение исполняемого тела — обнаружено",
                probe(work, "diff", [same, other_body], []), "DIFF", failures)
    report_axis("2. объявленное различие соседних строк НЕ прячет",
                probe(work, "mask", [other_principal, other_body], [PRINCIPAL_MASK]),
                "DIFF", failures)
    report_axis("3. мёртвое объявление различия — отказ",
                probe(work, "dead", [same, twin], [r"^нет такой строки$"]), "DEAD", failures)
    report_axis("4. член семейства отсутствует — не измерялось",
                run_family({"имя": "absent",
                            "пути": ["нет/такого/файла.java", "services/common/platform/pom.xml"],
                            "различия": []}), "ABSENT", failures)
    report_axis("5. семейство из одного члена — не измерялось",
                probe(work, "lone", [same], []), "LONE", failures)
    report_axis("6. контроль: тождественные тела — расхождения нет",
                probe(work, "same", [same, twin], []), "OK", failures)
    report_axis("7. контроль: объявленное различие расхождением не считается",
                probe(work, "declared", [same, other_principal], [PRINCIPAL_MASK]), "OK", failures)
    report_axis("8. контроль: различие ТОЛЬКО в комментарии телом не считается",
                probe(work, "comment", [same, other_comment], []), "OK", failures)
    return failures


def main():
    print("--- батарея осей детектора (исполняется той же командой)")
    if battery():
        print("ПРОВЕРКА НЕ ПРОВОДИТСЯ: есть недоказанная ось — "
              "чистый прогон ничего не удостоверял бы")
        return 2
    if not FAMILIES:
        print("ПРОВЕРКА НЕ ПРОВОДИТСЯ: реестр семейств пуст — мерить нечего")
        return 2

    print("--- объявленные семейства копий: %d" % len(FAMILIES))
    defects = 0
    for family in FAMILIES:
        outcome = run_family(family)
        if outcome == "OK":
            print("  тела совпадают: %s (членов: %d)" % (family["имя"], len(family["пути"])))
            continue
        if outcome.startswith("ABSENT:") or outcome == "LONE":
            print("ПРОВЕРКА НЕ ПРОВОДИТСЯ: %s — %s" % (family["имя"], outcome))
            return 2
        print("  РАСХОЖДЕНИЕ: %s — %s" % (family["имя"], outcome))
        defects += 1

    print("семейств: %d; ДЕФЕКТОВ: %d" % (len(FAMILIES), defects))
    print("не мерится: полнота реестра — см. шапку файла")
    return 1 if defects else 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Сверка объявленного перечня членов популяции с ВЫВЕДЕННЫМ из предмета.

ПРЕДМЕТ. `tools/spec-run.sh` проверяет, что каждый объявленный член популяции
покрыт примером, проверяющим на нём правило. Откуда взят сам перечень, он не
проверяет — и перечень, собранный из того же текста, который он призван
проверить, самореферентен: он согласован с текстом и молчит обо всём, чего в
тексте нет. Измерено прогоном: члены двух популяций совпали один в один с
множеством кортежей, уже присутствовавших в примерах, а законная пара из
эталонного артефакта репозитория роняла прогон как «клейм полноты ложен».

Эта команда меряет ПРОИСХОЖДЕНИЕ перечня: у популяции обязателен ключ
`derive` — либо `command` (команда, выводящая членов из предмета: объявление
enum в src/**, таблица переходов lifecycle-дока, поверхность источника по
контракту, перечень действий эталонного артефакта) плюс `from` (артефакты,
которые команда обязана читать), либо `incomplete` (названная причина, по
которой ось выводима только грунтом). Объявленный перечень обязан совпасть с
выведенным по множеству кортежей.

ОСИ, КОТОРЫЕ КОМАНДА ОБЪЯВЛЯЕТ (доказаны батареей, исполняемой этим же
прогоном, до замера):
  1. перечень совпал с выведенным — расхождения нет;
  2. член объявлен и не выведен — расхождение;
  3. член выведен и не объявлен — расхождение;
  4. команда вывода отказала (ненулевой код) — ЗАМЕР НЕ ПРОВОДИЛСЯ (код 2);
  5. артефакта из `from` нет в репозитории — расхождение;
  6. `from` не упомянут ни командой, ни скриптом, который она зовёт, —
     расхождение (команда, ни на что не ссылающаяся, печатает перечень из
     себя самой);
  7. `incomplete` объявляет неполноту ЯВНО: такая ось печатается поимённо и
     считается, молчаливого пропуска нет;
  8. контроль: `incomplete`-ось не маскирует расходящуюся `command`-ось того
     же корпуса;
  9. в каталоге нет ни одной популяции — ЗАМЕР НЕ ПРОВОДИЛСЯ (код 2);
 10. спека не разобрана — ЗАМЕР НЕ ПРОВОДИЛСЯ (код 2).

Форма, которой в этом перечне нет, замером НЕ измерена — на неё он клейма не
даёт.

Запуск (из корня репозитория):  python3 tools/population-derive-check.py
Код возврата: 0 — перечни совпали; 1 — есть расхождения; 2 — ЗАМЕР НЕ
ПРОВОДИЛСЯ (ось батареи не доказана, команда вывода отказала, спека не
разобрана, мерить нечего).
"""
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEPARATOR = " → "


class Refusal(Exception):
    """Замер не проводится: мерить нечем."""


def _members(population):
    """Объявленные члены популяции — кортежи как строки."""
    out = []
    for entry in population.get("members", []):
        out.append(SEPARATOR.join(str(part) for part in entry.get("member", [])))
    return out


def _command_text(command, root):
    """Текст команды плюс тела скриптов репозитория, которые она зовёт."""
    text = command
    for token in re.findall(r"[A-Za-z0-9_./-]+", command):
        candidate = root / token
        if candidate.is_file():
            try:
                text += "\n" + candidate.read_text(encoding="utf-8")
            except OSError:
                pass
    return text


def derive(population, root):
    """Выводит членов командой популяции. Отказ команды — Refusal."""
    command = population["derive"]["command"]
    try:
        done = subprocess.run(["bash", "-c", command], cwd=str(root),
                              capture_output=True, text=True, timeout=120)
    except OSError as failure:
        raise Refusal("команда вывода не запустилась: %s" % failure) from failure
    if done.returncode != 0:
        raise Refusal("команда вывода отказала (код %d): %s | %s"
                      % (done.returncode, command, done.stderr.strip()[:400]))
    out = []
    for line in done.stdout.splitlines():
        if not line.strip():
            continue
        out.append(SEPARATOR.join(part.strip() for part in line.split("\t")))
    if not out:
        raise Refusal("команда вывода не напечатала ни одного члена: %s" % command)
    return out


def check(directory, root=ROOT):
    """Сверка каталога спек. Возвращает (расхождения, объявленные неполноты, число осей)."""
    directory = Path(directory)
    files = sorted(directory.glob("*.json"))
    if not files:
        raise Refusal("в каталоге %s нет ни одной спецификации" % directory)
    failures, incomplete, axes = [], [], 0
    for file in files:
        try:
            raw = json.loads(file.read_text(encoding="utf-8"))
        except (ValueError, OSError) as failure:
            raise Refusal("%s — спецификация не разобрана: %s" % (file.name, failure))
        for population in raw.get("populations", []):
            axes += 1
            axis = "%s / «%s»" % (file.stem, population.get("axis"))
            origin = population.get("derive") or {}
            if origin.get("incomplete"):
                incomplete.append("%s — %s" % (axis, origin["incomplete"]))
                continue
            if not origin.get("command"):
                failures.append("%s: происхождение перечня не объявлено (ключ derive)" % axis)
                continue
            text = _command_text(origin["command"], root)
            for source in origin.get("from", []):
                if not (root / source).exists():
                    failures.append("%s: артефакт-предмет «%s» в репозитории не найден" % (axis, source))
                elif source not in text:
                    failures.append("%s: команда вывода не читает названный артефакт «%s» — "
                                    "перечень мог быть напечатан из самой команды" % (axis, source))
            derived = derive(population, root)
            declared = _members(population)
            for member in declared:
                if member not in derived:
                    failures.append("%s: член «%s» объявлен, а командой вывода не выведен" % (axis, member))
            for member in derived:
                if member not in declared:
                    failures.append("%s: командой вывода выведен член «%s», которого перечень "
                                    "не объявляет" % (axis, member))
    if axes == 0:
        raise Refusal("в каталоге %s не объявлено ни одной популяции — мерить нечего" % directory)
    return failures, incomplete, axes


# --- батарея осей: исполняется ЭТИМ ЖЕ прогоном, до замера -------------------

FIXTURE = {
    "subject": "probe-derive",
    "question": "Ловит ли сверка расхождение объявленного перечня с выведенным",
    "home": "tools/population-derive-check.py",
    "values": [{"name": "probeRule", "expr": "amount * 2"}],
    "populations": [{
        "axis": "рёбра пробы",
        "rule": ["probeRule"],
        "keys": ["from", "to"],
        "derive": {"command": "cat предмет.txt", "from": ["предмет.txt"]},
        "members": [{"member": ["A", "B"]}, {"member": ["A", "C"]}],
    }],
    "examples": [],
}

SUBJECT = "A\tB\nA\tC\n"


def _sandbox(mutate=None, subject=SUBJECT):
    """Каталог-фикстура: спека плюс артефакт-предмет, который читает команда."""
    root = Path(tempfile.mkdtemp())
    (root / "предмет.txt").write_text(subject, encoding="utf-8")
    raw = json.loads(json.dumps(FIXTURE))
    if mutate:
        mutate(raw)
    corpus = root / "spec"
    corpus.mkdir()
    (corpus / "probe-derive.json").write_text(
        json.dumps(raw, ensure_ascii=False, indent=1), encoding="utf-8")
    return root, corpus


def _axis(name, expectation):
    """Одна ось: имя, ожидание и его исход. Ось засчитана — исход совпал."""
    try:
        passed, observed = expectation()
    except Exception as failure:                      # noqa: BLE001 — ось меряет ЛЮБОЙ исход
        passed, observed = False, "ось сама отказала: %r" % failure
    return name, passed, observed


def _measure(root, corpus):
    return check(corpus, root=root)


def battery():
    """Оси команды. Каждая — фикстура с заведомо известным исходом."""
    axes = []

    def clean():
        root, corpus = _sandbox()
        failures, incomplete, count = _measure(root, corpus)
        return (not failures and not incomplete and count == 1,
                "расхождений %d, объявленных неполнот %d, осей %d" % (len(failures), len(incomplete), count))
    axes.append(_axis("1. контроль: объявленный перечень совпал с выведенным", clean))

    def declared_not_derived():
        root, corpus = _sandbox(subject="A\tB\n")
        failures, _, _ = _measure(root, corpus)
        return (any("объявлен, а командой вывода не выведен" in f for f in failures),
                "; ".join(failures) or "расхождений нет")
    axes.append(_axis("2. член объявлен и не выведен — расхождение", declared_not_derived))

    def derived_not_declared():
        root, corpus = _sandbox(subject="A\tB\nA\tC\nB\tA\n")
        failures, _, _ = _measure(root, corpus)
        return (any("которого перечень не объявляет" in f for f in failures),
                "; ".join(failures) or "расхождений нет")
    axes.append(_axis("3. член выведен и не объявлен — расхождение", derived_not_declared))

    def command_refuses():
        def broken(raw):
            raw["populations"][0]["derive"]["command"] = "cat предмет.txt; exit 3"
        root, corpus = _sandbox(broken)
        try:
            _measure(root, corpus)
            return False, "замер отчитался на отказавшей команде вывода"
        except Refusal as refusal:
            return True, "отказ: %s" % str(refusal)[:120]
    axes.append(_axis("4. команда вывода отказала — ЗАМЕР НЕ ПРОВОДИЛСЯ", command_refuses))

    def source_missing():
        def gone(raw):
            raw["populations"][0]["derive"]["from"] = ["нет-такого-предмета.txt"]
            raw["populations"][0]["derive"]["command"] = "cat нет-такого-предмета.txt || cat предмет.txt"
        root, corpus = _sandbox(gone)
        failures, _, _ = _measure(root, corpus)
        return (any("в репозитории не найден" in f for f in failures),
                "; ".join(failures) or "расхождений нет")
    axes.append(_axis("5. артефакта из from нет в репозитории — расхождение", source_missing))

    def source_unread():
        def hardcoded(raw):
            raw["populations"][0]["derive"]["command"] = "printf 'A\\tB\\nA\\tC\\n'"
        root, corpus = _sandbox(hardcoded)
        failures, _, _ = _measure(root, corpus)
        return (any("не читает названный артефакт" in f for f in failures),
                "; ".join(failures) or "расхождений нет")
    axes.append(_axis("6. команда печатает перечень из себя самой — расхождение", source_unread))

    def incomplete_named():
        def ground(raw):
            raw["populations"][0]["derive"] = {"incomplete": "поверхность источника выводима только грунтом"}
        root, corpus = _sandbox(ground)
        failures, incomplete, _ = _measure(root, corpus)
        return (not failures and len(incomplete) == 1,
                "объявленных неполнот %d: %s" % (len(incomplete), "; ".join(incomplete)))
    axes.append(_axis("7. incomplete объявляет неполноту явно и печатается поимённо", incomplete_named))

    def incomplete_does_not_mask():
        root, corpus = _sandbox(subject="A\tB\n")
        second = json.loads(json.dumps(FIXTURE))
        second["subject"] = "probe-derive-ground"
        second["populations"][0]["derive"] = {"incomplete": "выводима только грунтом"}
        (corpus / "probe-ground.json").write_text(
            json.dumps(second, ensure_ascii=False, indent=1), encoding="utf-8")
        failures, incomplete, count = _measure(root, corpus)
        return (bool(failures) and len(incomplete) == 1 and count == 2,
                "расхождений %d при %d объявленных неполнотах" % (len(failures), len(incomplete)))
    axes.append(_axis("8. контроль: incomplete-ось не маскирует расходящуюся command-ось",
                      incomplete_does_not_mask))

    def nothing_to_measure():
        root, corpus = _sandbox()
        (corpus / "probe-derive.json").unlink()
        (corpus / "пусто.json").write_text('{"subject": "пусто", "values": [], "examples": []}',
                                           encoding="utf-8")
        try:
            _measure(root, corpus)
            return False, "замер отчитался на каталоге без единой популяции"
        except Refusal as refusal:
            return True, "отказ: %s" % str(refusal)[:120]
    axes.append(_axis("9. в каталоге нет ни одной популяции — ЗАМЕР НЕ ПРОВОДИЛСЯ", nothing_to_measure))

    def broken_spec():
        root, corpus = _sandbox()
        (corpus / "probe-derive.json").write_text("{не json", encoding="utf-8")
        try:
            _measure(root, corpus)
            return False, "замер отчитался на неразобранной спеке"
        except Refusal as refusal:
            return True, "отказ: %s" % str(refusal)[:120]
    axes.append(_axis("10. спека не разобрана — ЗАМЕР НЕ ПРОВОДИЛСЯ", broken_spec))

    return axes


def main(argv):
    directory = argv[1] if len(argv) > 1 else "docs/spec"
    axes = battery()
    print("Батарея осей сверки происхождения перечней:")
    for name, passed, observed in axes:
        print("  %s: %s — %s" % ("доказана" if passed else "НЕ ДОКАЗАНА", name, observed))
    broken = [name for name, passed, _ in axes if not passed]
    if broken:
        print("ЗАМЕР НЕ ПРОВОДИЛСЯ: не доказано осей — %d (%s)" % (len(broken), "; ".join(broken)))
        return 2
    try:
        failures, incomplete, count = check(directory)
    except Refusal as refusal:
        print("ЗАМЕР НЕ ПРОВОДИЛСЯ: %s" % refusal)
        return 2
    print("Осей популяций: %d; из них перечень объявлен неполным: %d" % (count, len(incomplete)))
    for line in incomplete:
        print("  НЕПОЛНОТА ОБЪЯВЛЕНА: %s" % line)
    for line in failures:
        print("  %s" % line)
    print("ПЕРЕЧНИ СОШЛИСЬ С ВЫВЕДЕННЫМИ" if not failures else "РАСХОЖДЕНИЙ: %d" % len(failures))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

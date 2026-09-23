#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Цифры запуска цепочки сессий: стоимость, приземления, доля закрытия,
ходы на ожидание.

ПРЕДМЕТ. Отчёт по запуску `tools/session-loop.sh` четырьмя числами на
сессию — теми, по которым держатель мерит две меры 2026-09-22
(`.claude/decisions/session-wait-and-continuation.md`): ожидание прогона не
тратит ходов, сессия внутри единицы продолжает группой за группой.

ОТКУДА ЧТО БЕРЁТСЯ — и где граница, названная, а не умолчанная:
  * стоимость, число ходов, `landings` — конверт сессии (последняя строка
    `result` файла `raw/<запуск>-<n>.ndjson`); `landings` — самоотчёт
    сессии, как и `gates_green`;
  * ходы на ожидание — блоки `tool_use` того же потока: `Monitor` целиком и
    вызовы оболочки, чья команда начинается `sleep`, `echo .`, `ps -W`,
    `until`, `while`, либо читает файл вывода фоновой задачи (`/tasks/…`)
    или лог реактора (`reactor*.log|txt`). Опрос, написанный иначе, не
    берётся — это граница шаблона, а не клейм полноты;
  * длительность сессии — журнал (`## Сессия n/N — начало → конец`);
  * доля закрытия — боковой файл ленты `raw/<запуск>-<n>.feed.txt` (строки
    ленты с временем): закрытие начинается последним прогоном реактора,
    без него — последним прогоном `spec-run.sh` (начало гейта), без обоих —
    последней правкой снапшота; запуски до ввода бокового файла доли не
    имеют, и клетка печатается прочерком.

Инструмент отчёта, не проверка: трёх исходов у него нет, батареи осей нет.
Код 0 — напечатано; 2 — запуск не найден или нет ни одного потока.

Вызов:
    py -3 tools/session-stats.py                # последний запуск
    py -3 tools/session-stats.py 20260921-222559
"""
import datetime
import glob
import json
import os
import re
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

LOOP_DIR = os.environ.get("SESSION_LOOP_DIR") or os.path.join(
    os.environ.get("LOCALAPPDATA") or os.path.expanduser("~"), "vibetrading-stand", "sessions")
RAW_DIR = os.path.join(LOOP_DIR, "raw")
JOURNAL = os.path.join(LOOP_DIR, "journal.md")

POLL_HEAD = re.compile(r"^\s*(sleep\b|echo \.\s*(;|$)|ps -W\b|until\b|while\b)")
POLL_READ = re.compile(r"/tasks/[\w-]+\.output|reactor\w*\.(log|txt)")
SESSION_ROW = re.compile(r"^## Сессия (\d+)/\d+ — (\S+ \S+) → (\S+ \S+)")
LAUNCH_ROW = re.compile(r"^# Запуск (\S+)")
STAMP = re.compile(r"^(\d\d):(\d\d) ")


def read_stream(path):
    cost = turns = landings = None
    calls = polls = 0
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                record = json.loads(line)
            except ValueError:
                continue
            kind = record.get("type")
            if kind == "assistant":
                for block in (record.get("message") or {}).get("content") or []:
                    if not isinstance(block, dict) or block.get("type") != "tool_use":
                        continue
                    calls += 1
                    name = block.get("name")
                    inp = block.get("input") or {}
                    if name == "Monitor":
                        polls += 1
                    elif name in ("Bash", "PowerShell"):
                        command = str(inp.get("command") or "")
                        first = command.strip().split("\n", 1)[0]
                        if POLL_HEAD.search(first) or POLL_READ.search(first):
                            polls += 1
            elif kind == "result":
                cost = record.get("total_cost_usd")
                turns = record.get("num_turns")
                landings = (record.get("structured_output") or {}).get("landings")
    return cost, turns, landings, calls, polls


def journal_spans(launch):
    spans = {}
    if not os.path.exists(JOURNAL):
        return spans
    current = None
    with open(JOURNAL, encoding="utf-8") as handle:
        for line in handle:
            found = LAUNCH_ROW.match(line)
            if found:
                current = found.group(1)
                continue
            found = SESSION_ROW.match(line)
            if found and current == launch:
                fmt = "%Y-%m-%d %H:%M:%S"
                spans[int(found.group(1))] = (
                    datetime.datetime.strptime(found.group(2), fmt),
                    datetime.datetime.strptime(found.group(3), fmt))
    return spans


def closure_share(feed_path, started, finished):
    """Доля закрытия во времени сессии по боковому файлу ленты."""
    if not os.path.exists(feed_path) or started is None:
        return None
    marks = {"reactor": None, "gate": None, "snapshot": None}
    day = started.date()
    previous = None
    with open(feed_path, encoding="utf-8") as handle:
        for line in handle:
            found = STAMP.match(line)
            if not found:
                continue
            moment = datetime.datetime.combine(day, datetime.time(int(found.group(1)), int(found.group(2))))
            if previous and moment < previous:
                day += datetime.timedelta(days=1)
                moment += datetime.timedelta(days=1)
            previous = moment
            if "tools/reactor-test.sh" in line and "прогон" in line:
                marks["reactor"] = moment
            elif "tools/spec-run.sh" in line and "прогон" in line:
                marks["gate"] = moment
            elif "правит: .claude/snapshots/" in line:
                marks["snapshot"] = moment
    start = marks["reactor"] or marks["gate"] or marks["snapshot"]
    if start is None:
        return None
    total = (finished - started).total_seconds()
    if total <= 0:
        return None
    return max(0.0, min(1.0, (finished - start).total_seconds() / total))


def main(argv):
    if argv:
        launch = argv[0]
    else:
        probes = sorted(glob.glob(os.path.join(RAW_DIR, "*-probe.json")))
        if not probes:
            print("ОТЧЁТ НЕ СОСТАВЛЕН: в %s нет ни одного запуска" % RAW_DIR, file=sys.stderr)
            return 2
        launch = os.path.basename(probes[-1])[: -len("-probe.json")]
    streams = sorted(
        glob.glob(os.path.join(RAW_DIR, "%s-*.ndjson" % launch)),
        key=lambda p: int(re.search(r"-(\d+)\.ndjson$", p).group(1)))
    if not streams:
        print("ОТЧЁТ НЕ СОСТАВЛЕН: у запуска %s нет ни одного потока сессии" % launch, file=sys.stderr)
        return 2
    spans = journal_spans(launch)
    print("Запуск %s — %d сессий" % (launch, len(streams)))
    print("| сессия | $ | ходов | вызовов | на ожидание | приземлений | мин | закрытие |")
    print("|---|---|---|---|---|---|---|---|")
    total_cost = total_polls = 0
    for path in streams:
        n = int(re.search(r"-(\d+)\.ndjson$", path).group(1))
        cost, turns, landings, calls, polls = read_stream(path)
        started, finished = spans.get(n, (None, None))
        minutes = "—" if started is None else "%d" % ((finished - started).total_seconds() // 60)
        share = closure_share(path[: -len(".ndjson")] + ".feed.txt", started, finished)
        total_cost += cost or 0
        total_polls += polls
        print("| %d | %s | %s | %d | %d | %s | %s | %s |" % (
            n, "—" if cost is None else "%.2f" % cost, "—" if turns is None else turns, calls, polls,
            "—" if landings is None else landings, minutes,
            "—" if share is None else "%d %%" % round(share * 100)))
    print("| Итого | %.2f | | | %d | | | |" % (total_cost, total_polls))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

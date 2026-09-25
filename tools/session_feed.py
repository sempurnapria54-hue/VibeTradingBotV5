#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Лента хода сессии в консоли цикла `tools/session-loop.sh`.

ПРЕДМЕТ. Пока сессия `claude -p` работает, держатель видит в консоли, чем
она занята: границы сессии, действия (чтения пачками, правки, прогоны) и
пульс при тишине. Уровень один, ручки подробности нет. Дом формата —
`.claude/skills/session-chain.md` §«Лента хода сессии в консоли».

ИСТОЧНИК — ПОТОК СОБЫТИЙ САМОЙ СЕССИИ, а не отчёт модели. Цикл запускает
`claude -p … --output-format stream-json --verbose` и ведёт stdout сюда:
каждая строка потока — JSON-событие (`system`, `assistant` с блоками
`tool_use`, `user` с `tool_result`, итоговый `result`). Стоячий промпт не
трогается: ни маркеров, ни «отчитайся» — лента выводится из того, что сессия
и так делает. Поток при этом ПИШЕТСЯ В ФАЙЛ ОТВЕТА ЦЕЛИКОМ (`--raw`):
итоговый конверт лежит его последней строкой, и `tools/session_envelope.py`
читает его оттуда. Строки самой ленты — с временем — пишутся рядом, в
боковой файл `<ответ>.feed.txt` (для `raw/x-3.ndjson` — `raw/x-3.feed.txt`):
по ним `tools/session-stats.py` считает долю закрытия во времени сессии;
в потоке событий времени нет вовсе.

ЧТО ВЫВОДИТСЯ ИЗ ЧЕГО — и где граница вывода, названная, а не умолчанная:
  * `читает:` — инструменты Read/Glob/Grep/WebFetch/WebSearch и команды
    Bash, чьё первое слово из перечня читающих (`cat`, `sed -n`, `grep`,
    `git diff`, …). Имена — базовые, пачкой: два первых, дальше `+N`.
  * `правит:` — Edit/Write/NotebookEdit по `file_path`, а также Bash с
    перенаправлением в файл (`cat > файл <<EOF` в том числе), `sed -i`,
    `mv`/`cp`/`rm`/`git mv`. Скрипт, пишущий файл ИЗНУТРИ (`py x.py`, где
    x.py делает `open(…, 'w')`), как правка НЕ ОПОЗНАЁТСЯ — он идёт строкой
    `bash:` с текстом команды; это граница вывода по потоку, а не пропуск.
  * `прогон:` — Bash, зовущий `tools/*.py|*.sh`, `mvn`, `docker`, `kubectl`,
    `kind`, `helm`; исход — `ок` либо `код N` из результата инструмента.
  * `бэкграунд:` — прогон с `run_in_background`: исход приходит не результатом
    инструмента, и здесь его нет.
  * `агент:` — запуск субагента; его собственные действия идут строками с
    префиксом `↳`.
  * всё прочее — `bash:` с началом команды либо именем инструмента.
  * `идёт:` — ВСЯКАЯ команда оболочки, чей результат не пришёл за пять
    минут, — опознанный прогон, правка перенаправлением, `bash:` — строкой
    раз в пять минут, независимо от прочих действий сессии:
    `… 37 мин, идёт: mvn -o -am -pl services/bff verify (12 мин; лог обновлён
    0 мин назад)`. Лог — файл, в который команда пишет перенаправлением
    (`> файл`, `>> файл`, `| tee файл`); время — его последней записи. Лога
    нет (вывод не перенаправлен, путь из переменной `$LOG`, файл не создан) —
    хвост просто `(N мин)`. Отличает прогон от зависания: у живого прогона
    лог свежий, у зависшего — стареет.
  * пульс `…` — при тишине дольше пяти минут, когда долгой команды нет:
    сколько идёт сессия и что последнее.

Первой строкой сессии печатается `модель: … · effort: …`. МОДЕЛЬ — ФАКТ
ПОТОКА (поле `model` события `system/init`), EFFORT — ОБЪЯВЛЕНИЕ ЦИКЛА
(`--effort`, им же переданное сессии): в потоке его нет вовсе, CLI отдаёт
уровень только ВНУТРЬ сессии (`$CLAUDE_EFFORT`). Без `--effort` строка
несёт одну модель — выдумывать уровень лента не станет.

Границы сессии (`▶`/`■`) печатает сам цикл: они зависят от роадмапа и
конверта, а не от потока. Подкоманда `step` печатает ему текущий шаг и его
статус присваиваниями оболочки.

Вызывается своей оболочкой:
    claude -p … --output-format stream-json --verbose | py -3 tools/session_feed.py follow --raw <ответ.ndjson> [--effort <уровень>]
    py -3 tools/session_feed.py step            # текущий шаг: STEP_ID=… STEP_STATUS=…
    py -3 tools/session_feed.py step 2-11       # статус названного шага
"""
import datetime
import glob
import json
import os
import queue
import re
import shlex
import subprocess
import sys
import threading
import time

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

PULSE_SECONDS = 300          # тишина, после которой печатается пульс
READ_BATCH_SECONDS = 20      # пачка чтений печатается не позже чем через столько
COMMAND_WIDTH = 72

READ_TOOLS = {"Read", "Glob", "Grep", "WebFetch", "WebSearch"}
EDIT_TOOLS = {"Edit", "Write", "MultiEdit", "NotebookEdit"}
SHELL_TOOLS = {"Bash", "PowerShell"}
SILENT_TOOLS = {"StructuredOutput"}

READ_WORDS = {
    "cat", "sed", "head", "tail", "grep", "rg", "ls", "find", "wc", "tree",
    "stat", "file", "pwd", "which", "command", "type", "diff", "awk", "cut",
    "sort", "uniq", "less", "more", "df", "du",
}
READ_GIT = {"diff", "log", "status", "show", "grep", "ls-files", "rev-parse",
            "write-tree", "branch", "blame", "config", "describe", "remote"}
RUN_WORDS = {"mvn", "docker", "kubectl", "kind", "helm", "npm", "npx", "gradle"}
RUN_SCRIPT = re.compile(r"tools/[\w./-]+\.(?:py|sh|ps1)")
ENV_ASSIGNMENT = re.compile(r"""^(?:[A-Za-z_][A-Za-z0-9_]*=(?:"[^"]*"|'[^']*'|\S)*\s+)+""")
PATH_TOKEN = re.compile(
    r"[\w~./\\-]+\.(?:md|json|java|py|sh|ps1|yaml|yml|xml|txt|sql|properties|"
    r"svg|html|css|js|ts|toml|cfg|ini|ndjson|csv|hcl|env)\b")
REDIRECT = re.compile(r"(?<![0-9&])>>?\s*([^\s|;&]+)")
HEAD_TOKEN = re.compile(r"""^(?:"([^"]+)"|'([^']+)'|(\S+))""")
TEE = re.compile(r"\|\s*tee\s+(?:-a\s+)?([^\s|;&]+)")
GIT_BASH_DRIVE = re.compile(r"^/([A-Za-z])/(.*)$")
EXIT_CODE = re.compile(r"[Ee]xit code (\d+)")

# Статус шага несёт НЕОБЯЗАТЕЛЬНЫЙ счёт под-шага (`CODE·2/3`) — дом формы
# `.claude/processes/roadmap-step-execution.md` §«Машина шага». Лента и
# `STEP_STATUS` отдают его дословно: держатель читает под-шаг там же, где статус.
STEP_ROW = re.compile(r"^\|\s*(\d+)\s*\|.*\|\s*([A-Z_0-9]+(?:·\d+/\d+)?)\s*\|\s*$")
PHASE_FILE = os.path.join(".claude", "work", "roadmap", "phase-%s.md")
ROADMAP_FILE = os.path.join(".claude", "work", "roadmap", "roadmap.md")


# ----------------------------------------------------------------- шаг


def read_rows(path):
    rows = {}
    if not os.path.exists(path):
        return rows
    for line in open(path, encoding="utf-8").read().split("\n"):
        match = STEP_ROW.match(line)
        if match:
            rows[int(match.group(1))] = match.group(2)
    return rows


def current_step(step_id=None):
    """Шаг, который берёт сессия, и его статус: (`2-11`, `HOLD`).

    Без аргумента — первый не-`DONE` шаг первой фазы `IN_PROGRESS` (её нет —
    первой `HOLD`). С аргументом `Ф-Ш` — статус названного шага.
    """
    if step_id:
        phase, step = step_id.split("-", 1)
        status = read_rows(PHASE_FILE % phase).get(int(step))
        return step_id, status or "?"
    phases = read_rows(ROADMAP_FILE)
    for wanted in ("IN_PROGRESS", "HOLD"):
        for phase in sorted(phases):
            if phases[phase] != wanted:
                continue
            steps = read_rows(PHASE_FILE % phase)
            for step in sorted(steps):
                if steps[step] != "DONE":
                    return "%d-%d" % (phase, step), steps[step]
    return "?", "?"


def step(argv):
    step_id, status = current_step(argv[0] if argv else None)
    print("STEP_ID=%s" % shlex.quote(step_id))
    print("STEP_STATUS=%s" % shlex.quote(status))
    return 0


# ---------------------------------------------------------------- лента


def clock():
    return datetime.datetime.now().strftime("%H:%M")


def shorten(text, width=COMMAND_WIDTH):
    text = " ".join(text.split())
    return text if len(text) <= width else text[: width - 1].rstrip() + "…"


class Feed:
    def __init__(self, raw_path, out=sys.stdout, effort=None):
        self.raw = open(raw_path, "w", encoding="utf-8", newline="")
        side = raw_path[: -len(".ndjson")] if raw_path.endswith(".ndjson") else raw_path
        self.side = open(side + ".feed.txt", "w", encoding="utf-8", newline="")
        self.out = out
        self.effort = effort
        self.cwd = os.getcwd()
        self.started = time.time()
        self.last_action_at = self.started
        self.last_action = None
        self.last_pulse_at = self.started
        self.last_edit = None
        self.cygpaths = {}
        self.pending = {}          # tool_use_id → (метка прогона, момент, префикс)
        self.running = {}        # tool_use_id → {label, started, prefix, log, reported}
        self.batch = None         # {"at": момент, "stamp": ЧЧ:ММ, "names": [], "prefix": str}

    # --- вывод

    def line(self, text, stamp=None):
        row = "%s %s" % (stamp or clock(), text)
        print(row, file=self.out, flush=True)
        self.side.write(row + "\n")
        self.side.flush()

    def action(self, text, prefix="", stamp=None):
        self.flush_batch()
        self.line("  %s%s" % (prefix, text), stamp)
        self.last_action = prefix + text
        self.last_action_at = time.time()
        self.last_pulse_at = self.last_action_at

    def identity(self, model):
        """Модель и effort первой строкой сессии: модель — из потока, effort —
        из объявления цикла (в потоке его нет)."""
        text = "модель: %s" % (model or "не названа")
        if self.effort:
            text += " · effort: %s" % self.effort
        self.line("  " + text)
        self.last_pulse_at = time.time()

    # --- пути

    def relative(self, path):
        if not path:
            return "?"
        text = str(path).replace("\\", "/")
        root = self.cwd.replace("\\", "/").rstrip("/") + "/"
        if text.lower().startswith(root.lower()):
            text = text[len(root):]
        return text

    def base(self, path):
        return self.relative(path).rstrip("/").rsplit("/", 1)[-1]

    def local_path(self, path, base_dir):
        """Путь из текста команды — в путь, который откроет эта машина.
        `/c/…` Git Bash — в `C:/…`; прочий абсолютный путь Git Bash (`/tmp/…`)
        — через `cygpath`; относительный — от каталога команды."""
        path = os.path.expanduser(path.strip("\"'"))
        drive = GIT_BASH_DRIVE.match(path)
        if drive:
            return "%s:/%s" % (drive.group(1).upper(), drive.group(2))
        if path.startswith("/"):
            return self.cygpath(path)
        return path if os.path.isabs(path) else os.path.join(base_dir, path)

    def cygpath(self, path):
        if path not in self.cygpaths:
            try:
                done = subprocess.run(["cygpath", "-w", path], capture_output=True, text=True, timeout=5)
                self.cygpaths[path] = done.stdout.strip() if done.returncode == 0 and done.stdout.strip() else path
            except (OSError, subprocess.SubprocessError):
                self.cygpaths[path] = path
        return self.cygpaths[path]

    def log_file(self, first, base_dir):
        """Файл, в который команда пишет перенаправлением либо `tee`; `None`,
        когда его нет или путь не читается из текста (`$LOG`)."""
        for target in REDIRECT.findall(first) + TEE.findall(first):
            if target in ("&1", "&2") or target.startswith("/dev/") or "$" in target:
                continue
            return self.local_path(target, base_dir)
        return None

    # --- чтения пачкой

    def read(self, name, prefix=""):
        now = time.time()
        if self.batch and self.batch["prefix"] != prefix:
            self.flush_batch()
        if not self.batch:
            self.batch = {"at": now, "stamp": clock(), "names": [], "prefix": prefix}
        if not self.batch["names"] or self.batch["names"][-1] != name:
            self.batch["names"].append(name)
        self.last_action = prefix + "читает " + name
        self.last_action_at = now
        self.last_pulse_at = now

    def flush_batch(self):
        if not self.batch:
            return
        names = self.batch["names"]
        shown = ", ".join(names[:2])
        if len(names) > 2:
            shown += ", +%d" % (len(names) - 2)
        self.line("  %sчитает: %s" % (self.batch["prefix"], shown), self.batch["stamp"])
        self.batch = None

    # --- классификация оболочки

    def shell(self, command, tool_id, prefix, background):
        text = command.strip()
        # Продолжение строки `\` склеивается: перенаправление в лог часто
        # стои́т на последней строке длинной команды.
        first = text.replace("\\\n", " ").split("\n", 1)[0]
        # `cd … &&`, `export … &&`, `date … &&` — обвязка, а не действие:
        # отбрасываются; каталог `cd` запоминается — от него читается
        # относительный путь лога.
        segments = [part.strip() for part in first.split("&&")]
        base_dir = self.cwd
        while len(segments) > 1 and segments[0].split()[:1] and (
                segments[0].split()[0] in ("cd", "export", "set", "date", "echo")
                or not ENV_ASSIGNMENT.sub("", segments[0] + " ").strip()):
            words = segments.pop(0).split(None, 1)
            if words[0] == "cd" and len(words) > 1:
                base_dir = self.local_path(words[1].strip(), self.cwd)
        first = " && ".join(segments)
        # Присваивания среды перед командой (`JAVA_HOME=… mvn …`) — тоже
        # обвязка: без них голова команды — присваивание, а не запускающий.
        assignment = ENV_ASSIGNMENT.match(first)
        while assignment:
            first = first[assignment.end():]
            assignment = ENV_ASSIGNMENT.match(first)
        script = RUN_SCRIPT.search(text)
        words = first.split()
        head = words[0] if words else ""
        # Запускающий, названный путём (`"C:/…/maven3/bin/mvn.cmd" -o …`),
        # опознаётся по имени файла: иначе голова — кусок пути, а не `mvn`.
        quoted = HEAD_TOKEN.match(first)
        if quoted and head not in RUN_WORDS:
            token = next(group for group in quoted.groups() if group)
            name = re.sub(r"\.(?:cmd|exe|bat)$", "", token.replace("\\", "/").rsplit("/", 1)[-1])
            if name in RUN_WORDS:
                head = name
                words = [name] + first[quoted.end():].split()
        if not background and tool_id:
            self.running[tool_id] = {"label": None, "started": time.time(), "prefix": prefix,
                                     "log": self.log_file(first, base_dir), "reported": time.time()}
        if script or head in RUN_WORDS:
            label = shorten(self.run_label(text, script.group(0)) if script
                            else re.split(r"[|>;&]", " ".join(words))[0].strip())
            if background:
                self.action("бэкграунд: %s" % label, prefix)
                return
            if tool_id in self.running:
                self.running[tool_id]["label"] = "прогон " + label
            self.pending[tool_id] = (label, time.time(), prefix, clock())
            self.last_action = prefix + "прогон " + label + " (идёт)"
            self.last_action_at = time.time()
            self.last_pulse_at = self.last_action_at
            self.flush_batch()
            return
        if tool_id in self.running:
            self.running[tool_id]["label"] = shorten(first, 60)
        target = self.write_target(first, words)
        if target:
            key = ("правит", prefix, target)
            if self.last_edit != key:
                self.action("правит: %s" % target, prefix)
                self.last_edit = key
            return
        if self.is_read(words):
            files = [self.base(p) for p in PATH_TOKEN.findall(first)]
            self.read(", ".join(files[:3]) if files else shorten(first, 48), prefix)
            return
        self.action("bash: %s" % shorten(first), prefix)

    @staticmethod
    def run_label(text, script):
        """Метка прогона: запускающий, скрипт и его аргументы до первого
        оператора оболочки — из той строки команды, где скрипт назван."""
        line = next((part for part in text.split("\n") if script in part), text)
        start = line.find(script)
        before = line[:start].split()[-3:]
        runners = [word for word in before if word in ("py", "python", "python3", "bash", "sh")]
        if "py" in runners and "-3" in before:
            runner = "py -3 "
        elif runners:
            runner = runners[-1] + " "
        else:
            runner = ""
        tail = re.split(r"[|>;&]", line[start + len(script):])[0]
        return (runner + script + tail.rstrip()).replace('"', "")

    def write_target(self, first, words):
        if not words:
            return None
        head = words[0]
        if head == "git" and len(words) > 3 and words[1] == "mv":
            return "%s → %s" % (self.relative(words[2]), self.relative(words[3]))
        if head == "sed" and any(w == "-i" or w.startswith("-i") for w in words[1:3]):
            paths = PATH_TOKEN.findall(first)
            return self.relative(paths[-1]) if paths else "sed -i"
        if head in ("mv", "cp") and len(words) >= 3:
            return "%s → %s" % (self.relative(words[-2]), self.relative(words[-1]))
        if head in ("rm", "touch", "mkdir") and len(words) >= 2:
            return "%s %s" % (head, self.relative(words[-1]))
        if head == "tee" and len(words) >= 2:
            return self.relative(words[-1])
        for target in REDIRECT.findall(first):
            if target in ("/dev/null", "&1", "&2") or target.startswith("/dev/"):
                continue
            if "$" in target and "/" not in target:
                continue
            return self.relative(target.strip("\"'"))
        return None

    @staticmethod
    def is_read(words):
        if not words:
            return False
        head = words[0]
        if head == "git":
            return len(words) > 1 and words[1] in READ_GIT
        if head in ("py", "python", "python3") and "-c" in words:
            return True
        return head in READ_WORDS

    # --- события потока

    def tool_use(self, block, prefix, background_hint):
        name = block.get("name") or "?"
        inp = block.get("input") or {}
        tool_id = block.get("id")
        if name in SILENT_TOOLS:
            return
        if name in READ_TOOLS:
            if name == "Read":
                self.read(self.base(inp.get("file_path")), prefix)
            elif name == "Glob":
                self.read("glob %s" % (inp.get("pattern") or "?"), prefix)
            elif name == "Grep":
                self.read("grep «%s»" % shorten(str(inp.get("pattern") or "?"), 40), prefix)
            else:
                self.read("%s %s" % (name.lower(), shorten(str(inp.get("url") or inp.get("query") or ""), 40)), prefix)
            return
        if name in EDIT_TOOLS:
            target = self.relative(inp.get("file_path") or inp.get("notebook_path"))
            key = ("правит", prefix, target)
            if self.last_edit != key:
                self.action("правит: %s" % target, prefix)
                self.last_edit = key
            return
        if name in SHELL_TOOLS:
            self.last_edit = None
            self.shell(str(inp.get("command") or ""), tool_id, prefix, bool(inp.get("run_in_background")))
            return
        self.last_edit = None
        if name == "Agent":
            self.action("агент: %s" % shorten(str(inp.get("description") or inp.get("subagent_type") or ""), 60), prefix)
            return
        if name == "Skill":
            self.action("скилл: %s" % (inp.get("skill") or "?"), prefix)
            return
        self.action("инструмент: %s" % name, prefix)

    def tool_result(self, block, prefix):
        tool_id = block.get("tool_use_id")
        self.running.pop(tool_id, None)
        if tool_id not in self.pending:
            return
        label, started, run_prefix, stamp = self.pending.pop(tool_id)
        content = block.get("content")
        if isinstance(content, list):
            content = " ".join(str(part.get("text", "")) for part in content if isinstance(part, dict))
        content = str(content or "")
        code = EXIT_CODE.search(content)
        if code:
            outcome = "код %s" % code.group(1)
        elif block.get("is_error"):
            outcome = "отказ"
        else:
            outcome = "ок"
        self.action("прогон: %s → %s" % (label, outcome), run_prefix, stamp)

    def event(self, record):
        kind = record.get("type")
        if kind == "system" and record.get("subtype") == "init":
            if record.get("cwd"):
                self.cwd = record["cwd"]
            self.identity(record.get("model"))
            return
        if kind not in ("assistant", "user"):
            return
        prefix = "↳ " if record.get("parent_tool_use_id") else ""
        content = (record.get("message") or {}).get("content")
        if not isinstance(content, list):
            return
        for block in content:
            if not isinstance(block, dict):
                continue
            if block.get("type") == "tool_use":
                self.tool_use(block, prefix, False)
            elif block.get("type") == "tool_result":
                self.tool_result(block, prefix)

    # --- пульс и такт

    def tick(self):
        now = time.time()
        if self.batch and now - self.batch["at"] >= READ_BATCH_SECONDS:
            self.flush_batch()
        minutes = int((now - self.started) // 60)
        # Долгая команда печатается раз в пять минут САМА, а не только при
        # тишине: субагент рядом с ней действует, и пульс тишины молчал бы.
        long_running = sorted((item for item in self.running.values() if now - item["started"] >= PULSE_SECONDS),
                              key=lambda item: item["started"])
        for item in long_running:
            if now - item["reported"] < PULSE_SECONDS:
                continue
            self.line("  … %d мин, идёт: %s%s (%s)" % (minutes, item["prefix"], item["label"], self.age(item, now)))
            item["reported"] = now
            self.last_pulse_at = now
        if long_running:
            return
        if now - self.last_action_at >= PULSE_SECONDS and now - self.last_pulse_at >= PULSE_SECONDS:
            self.line("  … %d мин, последнее: %s" % (minutes, self.last_action or "ничего"))
            self.last_pulse_at = now

    @staticmethod
    def age(item, now):
        """`N мин` команды и, когда лог есть, давность его последней записи."""
        text = "%d мин" % int((now - item["started"]) // 60)
        if item["log"] and os.path.isfile(item["log"]):
            text += "; лог обновлён %d мин назад" % int(max(0, now - os.path.getmtime(item["log"])) // 60)
        return text

    def close(self):
        self.flush_batch()
        for label, started, prefix, stamp in self.pending.values():
            self.line("  %sпрогон: %s → исход не получен" % (prefix, label), stamp)
        self.pending.clear()
        self.raw.close()
        self.side.close()

    # --- ввод

    def follow(self, stream):
        lines = queue.Queue()

        def pump():
            for raw in stream:
                lines.put(raw)
            lines.put(None)

        threading.Thread(target=pump, daemon=True).start()
        while True:
            try:
                raw = lines.get(timeout=5)
            except queue.Empty:
                self.tick()
                continue
            if raw is None:
                break
            text = raw.decode("utf-8", "replace") if isinstance(raw, bytes) else raw
            self.raw.write(text if text.endswith("\n") else text + "\n")
            self.raw.flush()
            stripped = text.strip()
            if not stripped:
                continue
            try:
                record = json.loads(stripped)
            except ValueError:
                continue
            if isinstance(record, dict):
                self.event(record)
            self.tick()
        self.close()
        return 0


def follow(argv):
    if len(argv) not in (2, 4) or argv[0] != "--raw" or (len(argv) == 4 and argv[2] != "--effort"):
        print("вызов: session_feed.py follow --raw <ответ.ndjson> [--effort <уровень>]", file=sys.stderr)
        return 2
    feed = Feed(argv[1], effort=argv[3] if len(argv) == 4 else None)
    return feed.follow(sys.stdin.buffer)


COMMANDS = {"follow": follow, "step": step}

if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] not in COMMANDS:
        print("вызов: session_feed.py {follow --raw <файл> [--effort <уровень>] | step [Ф-Ш]}", file=sys.stderr)
        raise SystemExit(2)
    raise SystemExit(COMMANDS[sys.argv[1]](sys.argv[2:]))

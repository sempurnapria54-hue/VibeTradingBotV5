#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Разбор ответа `claude --output-format json` для цикла сессий.

ПРЕДМЕТ. Тело команды `tools/session-loop.sh` в части чтения конверта
ответа: предполётная проба (загружен ли пайплайн) и поля одной сессии.
Отдельным файлом, а не встроенным here-документом, по той же причине, что
и `tools/deploy_set_image_tag.py`: вложенный here-документ внутри составной
команды остаётся без терминатора, и bash исполняет не то, что написано.

ФОРМА ВЫВОДА `fields` — присваивания для `eval` в оболочке, а не JSON:
цикл читает их напрямую, и разбор в оболочке был бы вторым разбором того
же конверта. Значения экранируются `shlex.quote`, поэтому `summary` с
кавычками и переводами строк доезжает целым.

Вызывается своей оболочкой; напрямую не запускается — вход она проверяет.
"""
import json
import shlex
import sys


def quote(value):
    return shlex.quote("" if value is None else str(value))


def load(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def probe(path):
    """Проба «пайплайн загружен»: ненулевой код — цикл не стартует."""
    structured = load(path).get("structured_output") or {}
    if not structured.get("claude_md_loaded"):
        print(
            "ОТКАЗ: сессия не видит CLAUDE.md — пайплайн не загружен (bare-режим?)",
            file=sys.stderr,
        )
        return 1
    print("проба: пайплайн загружен, проект — %s" % structured.get("project"))
    return 0


def fields(path):
    """Поля конверта одной сессии присваиваниями оболочки."""
    try:
        envelope = load(path)
    except Exception as error:  # конверт негоден целиком — цикл остановится
        print("PARSE_OK=0")
        print("PARSE_ERR=%s" % quote(error))
        return 0

    structured = envelope.get("structured_output") or {}
    print("PARSE_OK=1")
    print("IS_ERROR=%s" % quote(str(envelope.get("is_error")).lower()))
    print("SUBTYPE=%s" % quote(envelope.get("subtype")))
    print("SESSION_ID=%s" % quote(envelope.get("session_id")))
    print("COST=%s" % quote(round(float(envelope.get("total_cost_usd") or 0), 4)))
    print("NUM_TURNS=%s" % quote(envelope.get("num_turns")))
    print("DENIALS=%s" % quote(len(envelope.get("permission_denials") or [])))
    print("RESULT=%s" % quote((envelope.get("result") or "")[:600]))
    print("ST_STATUS=%s" % quote(structured.get("status")))
    print("ST_GATES=%s" % quote(str(structured.get("gates_green")).lower()))
    print("ST_SUMMARY=%s" % quote(structured.get("summary")))
    return 0


COMMANDS = {"probe": probe, "fields": fields}

if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] not in COMMANDS:
        print("вызов: session_envelope.py {probe|fields} <ответ.json>", file=sys.stderr)
        raise SystemExit(2)
    raise SystemExit(COMMANDS[sys.argv[1]](sys.argv[2]))

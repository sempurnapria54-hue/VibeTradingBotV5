#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Правка единственного места тега образа в манифесте окружения.

ПРЕДМЕТ. Тело команды `tools/deploy-set-image-tag.sh`; отдельным файлом, а
не встроенным here-документом, потому что вложенный here-документ внутри
составной команды остаётся без терминатора и bash предупреждает об этом,
исполняя не то, что написано, — класс «команда, провалившаяся молча».

Замена, а не добавление: у сервиса в наборе окружения ровно одно место
тега (docs/architecture/platform.md §Развёртывание). Второе означало бы
двух писателей одной истины, и Argo применил бы то из них, которое
случайно оказалось ниже.

Вызывается своей оболочкой; напрямую не запускается — вход она проверяет.
"""
import io
import re
import sys


def apply(text, name, tag):
    """Возвращает манифест с проставленным тегом образа `name`."""
    entry = "  - name: %s\n    newTag: %s\n" % (name, tag)
    existing = re.compile(r"^  - name: %s\n    newTag: .*\n" % re.escape(name), re.M)
    if existing.search(text):
        return existing.sub(entry, text)
    empty = re.compile(r"^images: \[\]\s*$", re.M)
    if empty.search(text):
        return empty.sub("images:\n" + entry.rstrip("\n"), text, count=1)
    return re.sub(r"^(images:\n)", r"\1" + entry, text, count=1, flags=re.M)


def main(argv):
    if len(argv) != 4:
        print("ПРАВКА НЕ ПРОВОДИЛАСЬ: нужны манифест, имя образа и тег",
              file=sys.stderr)
        return 2
    manifest, name, tag = argv[1], argv[2], argv[3]
    with io.open(manifest, encoding="utf-8") as handle:
        text = handle.read()
    updated = apply(text, name, tag)
    if updated == text:
        print("ПРАВКА НЕ ПРОВОДИЛАСЬ: в %s нет блока images:" % manifest,
              file=sys.stderr)
        return 2
    with io.open(manifest, "w", encoding="utf-8", newline="") as handle:
        handle.write(updated)
    print("тег проставлен: %s -> %s" % (name, tag))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

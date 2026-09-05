#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Сверка раскладки монорепозитория и манифестов с их объявленными домами.

ПРЕДМЕТ. `.claude/rules/structure.md` объявляет: «состав каталога равен
инвентарю — на этом равенстве держится проверяемость раскладки», а
`docs/architecture/platform.md` объявляет закрытый перечень окружений.
Механической проверки ни у одного из двух клеймов не было: клейм полноты
запрещает писателю искать дальше, и неизмеренный он превращает пропуск в
инструкцию. Шаг 2 фазы 2 заводит ВТОРУЮ раскладку того же класса
(манифесты по окружениям), поэтому клейм удваивался, оставаясь
неизмеренным.

ОСИ, КОТОРЫЕ КОМАНДА ОБЪЯВЛЯЕТ (доказаны батареей, исполняемой этим же
прогоном, до замера):
  1. каталог `services/` не содержит единицы, которой нет в инвентаре
     `docs/architecture/services.md`;
  1a. каталог `libs/` не содержит артефакта, которого нет в таблице
     «Общие артефакты монорепозитория» того же дока: клейм тот же
     («состав каталога равен инвентарю»), и мерить его надо тем же;
  2. каталог `deploy/` содержит ровно окружения перечня
     `docs/architecture/platform.md` плюс `base/` — ни больше, ни меньше;
  3. у каждого окружения есть `kustomization.yaml` и `env.yaml`;
  4. `env.yaml` окружения объявляет ВСЕ оси различия, названные домом
     (по одной строке `data:` на ось), и ни одной сверх перечня;
  5. допустимые контуры окружения в `env.yaml` совпадают с матрицей
     `docs/spec/environment-contour.json` — двум носителям одной истины
     разойтись не даётся;
  6. образ, названный в `images:` окружения, принадлежит единице, которая
     в `services/` существует: тег несуществующего сервиса — мусор,
     который Argo применит молча;
  7. отказ: инвентарь не разобран, дом окружений не разобран, каталога
     `deploy/` нет — ЗАМЕР НЕ ПРОВОДИЛСЯ (код 2).

Форма, которой в этом перечне нет, замером НЕ измерена — на неё он клейма
не даёт.

Запуск (из корня репозитория):  python3 tools/deploy-layout-check.py
Код возврата: 0 — раскладка сошлась; 1 — есть расхождения; 2 — ЗАМЕР НЕ
ПРОВОДИЛСЯ (ось не доказана, вход не разобран, мерить нечего).
"""
import io
import json
import os
import re
import sys
import tempfile

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SERVICES_DOC = "docs/architecture/services.md"
PLATFORM_DOC = "docs/architecture/platform.md"
CONTOUR_SPEC = "docs/spec/environment-contour.json"

# Оси различия окружений, которые обязан объявить env.yaml. Перечень выведен
# из дома (PLATFORM_DOC, шапка и первый столбец таблицы) и сверяется с ним
# осью 4: расхождение перечня здесь и там — тот же дубль носителя.
REQUIRED_AXES = ("environment", "admittedContours", "ingressHost",
                 "argocdSync", "vaultPrefix", "resourceProfile",
                 "retentionProfile")


class Refusal(Exception):
    """Замер не проводится: мерить нечем."""


def read(root, relative):
    path = os.path.join(root, relative)
    if not os.path.isfile(path):
        raise Refusal("нет файла %s — сверять не с чем" % relative)
    with io.open(path, encoding="utf-8") as handle:
        return handle.read()


def inventory(root):
    """Единицы развёртывания — из таблицы §«Единицы развёртывания»."""
    text = read(root, SERVICES_DOC)
    section = text.split("## Единицы развёртывания", 1)
    if len(section) < 2:
        raise Refusal("в %s нет раздела «Единицы развёртывания»" % SERVICES_DOC)
    body = section[1].split("\n## ", 1)[0]
    units = set()
    for line in body.splitlines():
        if not line.startswith("|"):
            continue
        first = line.split("|")[1].strip()
        for name in re.findall(r"`([a-z][a-z0-9-]*(?:-<[^>]+>)?)`", first):
            # `connector-<биржа>` — семейство: каталогом станет конкретное имя.
            units.add(name.split("-<")[0] if "-<" in name else name)
    if not units:
        raise Refusal("в %s не разобрана ни одна единица развёртывания"
                      % SERVICES_DOC)
    return units


def shared_artifacts(root):
    """Общие артефакты — из таблицы §«Общие артефакты монорепозитория»."""
    text = read(root, SERVICES_DOC)
    section = text.split("## Общие артефакты монорепозитория", 1)
    if len(section) < 2:
        raise Refusal("в %s нет раздела «Общие артефакты монорепозитория»"
                      % SERVICES_DOC)
    body = section[1].split("\n## ", 1)[0]
    artifacts = set()
    for line in body.splitlines():
        if not line.startswith("|"):
            continue
        first = line.split("|")[1].strip()
        artifacts.update(re.findall(r"`([a-z][a-z0-9-]*)`", first))
    if not artifacts:
        raise Refusal("в %s не разобран ни один общий артефакт" % SERVICES_DOC)
    return artifacts


def environments(root):
    """Окружения — из шапки таблицы §«Чем различаются окружения»."""
    text = read(root, PLATFORM_DOC)
    header = None
    for line in text.splitlines():
        if line.startswith("| Ось |"):
            header = line
            break
    if header is None:
        raise Refusal("в %s нет шапки таблицы окружений" % PLATFORM_DOC)
    names = re.findall(r"`([a-z]+)`", header)
    if not names:
        raise Refusal("в шапке таблицы %s не разобрано ни одного окружения"
                      % PLATFORM_DOC)
    return names


def admitted_from_spec(root):
    """Матрица допуска контуров — из исполнимой спеки, а не из прозы."""
    text = read(root, CONTOUR_SPEC)
    try:
        spec = json.loads(text)
    except ValueError as failure:
        raise Refusal("спека %s не разобрана: %s" % (CONTOUR_SPEC, failure))
    matrix = {}
    for example in spec.get("examples", []):
        state = example.get("state", {})
        environment = state.get("environment")
        contour = state.get("contour")
        if environment is None or contour is None:
            continue
        if example.get("expect", {}).get("contourAdmitted") is True:
            matrix.setdefault(environment, set()).add(contour)
        else:
            matrix.setdefault(environment, set())
    if not matrix:
        raise Refusal("в %s нет ни одного примера допуска" % CONTOUR_SPEC)
    return matrix


def axes_of(text):
    """Оси, объявленные env.yaml: ключи блока data:.

    Разрез — по строке, РАВНОЙ `data:`, а не по подстроке: «metadata:»
    содержит «data:», и наивный split брал ключ `name` блока metadata
    наравне с осями. Дефект внесён самой этой правкой и пойман осью 0 её
    же батареи: контроль на здоровой раскладке дал два расхождения там,
    где их нет, и замер отказался (код 2) вместо ложного зелёного.
    """
    lines = text.splitlines()
    start = None
    for index, line in enumerate(lines):
        if line.rstrip() == "data:":
            start = index + 1
            break
    if start is None:
        return {}
    found = {}
    for line in lines[start:]:
        if line.strip() and not line.startswith(" "):
            break
        match = re.match(r'^\s{2}([A-Za-z][A-Za-z0-9]*):\s*"?([^"\n]*)"?\s*$', line)
        if match:
            found[match.group(1)] = match.group(2).strip()
    return found


def images_of(text):
    """Имена образов из блока images: оверлея."""
    block = re.search(r"^images:\s*(\[\]|\n(?:\s+-.*\n?)*)", text, re.M)
    if not block or block.group(1).strip() == "[]":
        return []
    return re.findall(r"name:\s*([A-Za-z0-9._/-]+)", block.group(1))


def check(root):
    """Сверка раскладки. Возвращает список расхождений."""
    units = inventory(root)
    envs = environments(root)
    matrix = admitted_from_spec(root)
    deploy = os.path.join(root, "deploy")
    if not os.path.isdir(deploy):
        raise Refusal("каталога deploy/ нет — раскладку мерить не на чем")

    defects = []

    # --- ось 1: services/ ⊆ инвентарь
    services = os.path.join(root, "services")
    if os.path.isdir(services):
        for name in sorted(os.listdir(services)):
            if not os.path.isdir(os.path.join(services, name)):
                continue
            if name not in units:
                defects.append("services/%s — единицы нет в инвентаре %s"
                               % (name, SERVICES_DOC))

    # --- ось 1a: libs/ ⊆ общие артефакты
    libs = os.path.join(root, "libs")
    if os.path.isdir(libs):
        artifacts = shared_artifacts(root)
        for name in sorted(os.listdir(libs)):
            if not os.path.isdir(os.path.join(libs, name)):
                continue
            if name not in artifacts:
                defects.append("libs/%s — артефакта нет в таблице «Общие "
                               "артефакты монорепозитория» %s"
                               % (name, SERVICES_DOC))

    # --- ось 2: deploy/ == окружения + base
    present = {name for name in os.listdir(deploy)
               if os.path.isdir(os.path.join(deploy, name))}
    expected = set(envs) | {"base"}
    for extra in sorted(present - expected):
        defects.append("deploy/%s — не окружение перечня %s и не base/"
                       % (extra, PLATFORM_DOC))
    for missing in sorted(expected - present):
        defects.append("deploy/%s — окружение объявлено в %s, каталога нет"
                       % (missing, PLATFORM_DOC))

    for env in envs:
        directory = os.path.join(deploy, env)
        if not os.path.isdir(directory):
            continue
        # --- ось 3: обязательные файлы окружения
        for required in ("kustomization.yaml", "env.yaml"):
            if not os.path.isfile(os.path.join(directory, required)):
                defects.append("deploy/%s/%s — нет" % (env, required))
        env_file = os.path.join(directory, "env.yaml")
        if not os.path.isfile(env_file):
            continue
        with io.open(env_file, encoding="utf-8") as handle:
            axes = axes_of(handle.read())
        # --- ось 4: перечень осей совпадает с объявленным
        for axis in REQUIRED_AXES:
            if axis not in axes:
                defects.append("deploy/%s/env.yaml — нет оси «%s»" % (env, axis))
        for axis in sorted(set(axes) - set(REQUIRED_AXES)):
            defects.append("deploy/%s/env.yaml — ось «%s» вне перечня %s"
                           % (env, axis, PLATFORM_DOC))
        # --- ось 5: контуры совпадают со спекой
        declared = {part.strip() for part in axes.get("admittedContours", "").split(",")
                    if part.strip()}
        if env in matrix and declared != matrix[env]:
            defects.append("deploy/%s/env.yaml — контуры %s против %s в %s"
                           % (env, sorted(declared) or ["—"],
                              sorted(matrix[env]) or ["—"], CONTOUR_SPEC))
        # --- ось 6: образ принадлежит существующей единице
        kustomization = os.path.join(directory, "kustomization.yaml")
        if os.path.isfile(kustomization):
            with io.open(kustomization, encoding="utf-8") as handle:
                for image in images_of(handle.read()):
                    unit = image.rsplit("/", 1)[-1].split(":")[0]
                    if not os.path.isdir(os.path.join(services, unit)):
                        defects.append(
                            "deploy/%s — образ «%s» назван, а services/%s нет"
                            % (env, image, unit))
    return defects


# --- батарея осей ------------------------------------------------------------

MIN_SERVICES = """## Единицы развёртывания

| Единица | Модули | Своя база |
|---|---|---|
| `alpha` | — | нет |
| `beta` | — | да |

## Общие артефакты монорепозитория

| Артефакт | Что несёт | Кто зависит |
|---|---|---|
| `shared-one` | — | все |

## Дальше
"""

MIN_PLATFORM = """## Чем различаются окружения

| Ось | `dev` | `prod` |
|---|---|---|
| допустимые контуры площадки | `DEMO` | `LIVE`, `DEMO` |
"""

MIN_SPEC = json.dumps({
    "subject": "environment-contour",
    "examples": [
        {"state": {"environment": "dev", "contour": "DEMO"},
         "expect": {"contourAdmitted": True}},
        {"state": {"environment": "dev", "contour": "LIVE"},
         "expect": {"contourAdmitted": False}},
        {"state": {"environment": "prod", "contour": "DEMO"},
         "expect": {"contourAdmitted": True}},
        {"state": {"environment": "prod", "contour": "LIVE"},
         "expect": {"contourAdmitted": True}},
    ],
}, ensure_ascii=False)

ENV_YAML = """apiVersion: v1
kind: ConfigMap
metadata:
  name: environment-axes
data:
  environment: "%s"
  admittedContours: "%s"
  ingressHost: "%s.example.invalid"
  argocdSync: "manual"
  vaultPrefix: "%s"
  resourceProfile: "target"
  retentionProfile: "reduced"
"""

KUSTOMIZATION = """apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: %s
resources:
  - ../base
images: %s
"""


def _sandbox(work, mutate=None):
    """Минимальный корпус, на котором оси доказываются поимённо."""
    files = {
        SERVICES_DOC: MIN_SERVICES,
        PLATFORM_DOC: MIN_PLATFORM,
        CONTOUR_SPEC: MIN_SPEC,
        "deploy/base/kustomization.yaml": "resources: []\n",
        "deploy/dev/env.yaml": ENV_YAML % ("dev", "DEMO", "dev", "dev"),
        "deploy/dev/kustomization.yaml": KUSTOMIZATION % ("dev", "[]"),
        "deploy/prod/env.yaml": ENV_YAML % ("prod", "LIVE,DEMO", "prod", "prod"),
        "deploy/prod/kustomization.yaml": KUSTOMIZATION % ("prod", "[]"),
        "services/.keep": "",
        "libs/shared-one/.keep": "",
    }
    if mutate:
        mutate(files)
    for relative, body in files.items():
        path = os.path.join(work, relative.replace("/", os.sep))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with io.open(path, "w", encoding="utf-8") as handle:
            handle.write(body)
    return work


def battery():
    axes = []

    def run(mutate=None):
        with tempfile.TemporaryDirectory() as work:
            _sandbox(work, mutate)
            try:
                return check(work), None
            except Refusal as refusal:
                return None, str(refusal)

    def axis(name, passed, observed):
        axes.append((name, passed, observed))

    defects, refusal = run()
    axis("0. контроль: здоровая раскладка расхождений не даёт",
         refusal is None and not defects, refusal or "дефектов: %d" % len(defects))

    def stray_service(files):
        files["services/gamma/.keep"] = ""
    defects, refusal = run(stray_service)
    axis("1. единица в services/ вне инвентаря — расхождение",
         bool(defects) and any("инвентаре" in d for d in defects or []),
         refusal or "; ".join(defects or []))

    def stray_lib(files):
        files["libs/unknown-lib/.keep"] = ""
    defects, refusal = run(stray_lib)
    axis("1a. артефакт в libs/ вне таблицы общих артефактов — расхождение",
         bool(defects) and any("Общие" in d for d in defects or []),
         refusal or "; ".join(defects or []))

    def stray_env(files):
        files["deploy/qa/env.yaml"] = ENV_YAML % ("qa", "DEMO", "qa", "qa")
    defects, refusal = run(stray_env)
    axis("2a. каталог в deploy/ вне перечня окружений — расхождение",
         bool(defects) and any("не окружение перечня" in d for d in defects or []),
         refusal or "; ".join(defects or []))

    def missing_env(files):
        files.pop("deploy/prod/env.yaml")
        files.pop("deploy/prod/kustomization.yaml")
    defects, refusal = run(missing_env)
    axis("2b. окружение объявлено, каталога нет — расхождение",
         bool(defects) and any("каталога нет" in d for d in defects or []),
         refusal or "; ".join(defects or []))

    def missing_file(files):
        files.pop("deploy/dev/kustomization.yaml")
    defects, refusal = run(missing_file)
    axis("3. у окружения нет обязательного файла — расхождение",
         bool(defects) and any("kustomization.yaml — нет" in d for d in defects or []),
         refusal or "; ".join(defects or []))

    def axis_missing(files):
        files["deploy/dev/env.yaml"] = files["deploy/dev/env.yaml"].replace(
            '  vaultPrefix: "dev"\n', "")
    defects, refusal = run(axis_missing)
    axis("4a. ось различия не объявлена — расхождение",
         bool(defects) and any("нет оси" in d for d in defects or []),
         refusal or "; ".join(defects or []))

    def axis_extra(files):
        files["deploy/dev/env.yaml"] += '  somethingElse: "x"\n'
    defects, refusal = run(axis_extra)
    axis("4b. ось сверх перечня — расхождение",
         bool(defects) and any("вне перечня" in d for d in defects or []),
         refusal or "; ".join(defects or []))

    def contour_drift(files):
        files["deploy/dev/env.yaml"] = files["deploy/dev/env.yaml"].replace(
            'admittedContours: "DEMO"', 'admittedContours: "LIVE,DEMO"')
    defects, refusal = run(contour_drift)
    axis("5. контуры окружения разошлись со спекой — расхождение",
         bool(defects) and any("контуры" in d for d in defects or []),
         refusal or "; ".join(defects or []))

    def phantom_image(files):
        files["deploy/prod/kustomization.yaml"] = KUSTOMIZATION % (
            "prod", "\n  - name: registry.invalid/alpha\n    newTag: v1\n")
    defects, refusal = run(phantom_image)
    axis("6. образ назван, а сервиса в services/ нет — расхождение",
         bool(defects) and any("services/alpha нет" in d for d in defects or []),
         refusal or "; ".join(defects or []))

    def no_inventory(files):
        files[SERVICES_DOC] = "## Другое\n"
    defects, refusal = run(no_inventory)
    axis("7a. инвентарь не разобран — проверка отказывает",
         refusal is not None, refusal or "проверка отчиталась")

    def no_platform(files):
        files[PLATFORM_DOC] = "## Другое\n"
    defects, refusal = run(no_platform)
    axis("7b. дом окружений не разобран — проверка отказывает",
         refusal is not None, refusal or "проверка отчиталась")

    def no_deploy(files):
        for key in list(files):
            if key.startswith("deploy/"):
                files.pop(key)
    defects, refusal = run(no_deploy)
    axis("7c. каталога deploy/ нет — проверка отказывает",
         refusal is not None, refusal or "проверка отчиталась")

    return axes


def main():
    axes = battery()
    print("--- батарея осей детектора (исполняется той же командой)")
    for title, passed, observed in axes:
        print("  %s: %s — %s"
              % ("доказана" if passed else "НЕ ДОКАЗАНА", title, observed))
    broken = [title for title, passed, _ in axes if not passed]
    if broken:
        print("ПРОВЕРКА НЕ ПРОВОДИТСЯ: недоказанных осей %d — чистая сверка "
              "ничего не удостоверяла бы" % len(broken))
        return 2

    try:
        defects = check(ROOT)
    except Refusal as refusal:
        print("ПРОВЕРКА НЕ ПРОВОДИТСЯ: %s" % refusal)
        return 2

    units = inventory(ROOT)
    envs = environments(ROOT)
    print("единиц в инвентаре: %d; окружений: %d (%s); РАСХОЖДЕНИЙ: %d"
          % (len(units), len(envs), ", ".join(envs), len(defects)))
    for defect in defects:
        print("  РАСХОЖДЕНИЕ: %s" % defect)
    return 1 if defects else 0


if __name__ == "__main__":
    sys.exit(main())

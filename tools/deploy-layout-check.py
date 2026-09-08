#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Сверка раскладки монорепозитория и манифестов с их объявленными домами.

ПРЕДМЕТ. `.claude/rules/structure.md` объявляет: «в каталоге нет единицы,
которой нет в инвентаре — это и мерит прогон», а
`docs/architecture/platform.md` объявляет закрытый перечень окружений и
закрытый перечень осей их различия.

Перечни берутся ИЗ ДОМА разбором, а не копией рядом с кодом. Прежняя
редакция держала оси кортежем `REQUIRED_AXES` и объявляла его «выведенным
из дома и сверяемым с ним осью 4», тогда как ось 4 сверяла манифест с этим
же кортежем: копия делала измеряемой себя, а не дом. Проба, на которой это
записано: строка оси, удалённая из таблицы дома, оставляла замер зелёным —
расхождений 0 (A12 `DOCS_CHECK_4`).
Обратного вложения (инвентарь ⊆ каталог) дом не обещает и этот замер его не
мерит: единицы приезжают со своими шагами.
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
     («в каталоге нет артефакта, которого нет в таблице»), и мерить его
     надо тем же;
  2. каталог `deploy/` содержит ровно окружения перечня
     `docs/architecture/platform.md` плюс `base/` — ни больше, ни меньше;
  3. у каждого окружения есть `kustomization.yaml` и `env.yaml`;
  4. `env.yaml` окружения объявляет РОВНО те ключи, которые дом называет в
     колонке «Ключ манифеста», — ни меньше (4a), ни больше (4b); перечень
     ВЫВОДИТСЯ из таблицы дома, своей копии у детектора нет (4c);
  4d. строка дома с прочерком вместо ключа обязательного ключа не заводит
     (КОНТРОЛЬ: ось различает окружения, но живёт не в `env.yaml`);
  4e. клетка ключа, не разобранная ни как ключ, ни как прочерк, —
     расхождение: молчаливо пропущенная строка ушла бы из перечня;
  4f. голый прочерк — расхождение: дом обещает называть носитель значения в
     той же клетке. Мерится НЕПУСТОТА хвоста, а не его содержательность:
     «— TBD» замер проходит, и клейма о смысле хвоста команда не даёт;
  5. допустимые контуры окружения в `env.yaml` совпадают с матрицей
     `docs/spec/environment-contour.json` — двум носителям одной истины
     разойтись не даётся;
  6. образ, названный в `images:` окружения, принадлежит единице, которая
     в `services/` существует: тег несуществующего сервиса — мусор,
     который Argo применит молча;
  7. отказ: инвентарь не разобран, дом окружений не разобран, каталога
     `deploy/` нет — ЗАМЕР НЕ ПРОВОДИЛСЯ (код 2);
  7d. таблица дома перечня осей НЕ ДАЁТ — раздела нет, таблицы в разделе
     нет, колонки ключей нет, ни одного ключа не разобрано: замер
     отказывает по любой из четырёх причин. Проб две, по краям диапазона
     (колонки нет; ключей ни одного) — ветвь у всех четырёх одна, и
     доказывается предикат, а не каждая причина порознь.

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

# Заголовок колонки дома, в которой стои́т машиночитаемое имя оси, и две
# формы её клетки. Перечня осей здесь НЕТ намеренно: он выводится из дома
# функцией required_axes ниже.
AXES_SECTION = "## Чем различаются окружения"
KEY_COLUMN = "Ключ манифеста"
AXIS_KEY_RE = re.compile(r"^`([A-Za-z][A-Za-z0-9]*)`$")
# Прочерк: ось различает окружения, но живёт не в env.yaml. Дом обещает
# называть носитель значения в той же клетке, поэтому голый прочерк —
# расхождение: он неотличим от забытой клетки, ради чего колонка и заведена.
NO_KEY_RE = re.compile(r"^—\s*\S")
BARE_DASH_RE = re.compile(r"^—\s*$")
SEPARATOR_RE = re.compile(r"^:?-{2,}:?$")


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
            # `connector-<биржа>` — СЕМЕЙСТВО, а не единица: каталогом
            # станет конкретное имя (`connector-okx`). Семейство метится
            # хвостом «-», и принадлежность проверяет unit_known ниже —
            # сравнением по префиксу, а не равенством. До первого
            # коннектора в services/ эта ветка не исполнялась ни разу, и
            # разница между «семейство» и «единица» была неотличима:
            # ось на семейной строке не срабатывала.
            units.add(name.split("-<")[0] + "-" if "-<" in name else name)
    if not units:
        raise Refusal("в %s не разобрана ни одна единица развёртывания"
                      % SERVICES_DOC)
    return units


def unit_known(name, units):
    """Каталог назван инвентарём: единицей поимённо либо членом семейства.

    Член семейства — имя с непустым хвостом после «-» (`connector-okx`
    при семействе `connector-`). Пустой хвост членом не считается:
    каталог `connector` — это семейство, объявившее себя единицей, а
    площадки у него нет.
    """
    if name in units:
        return True
    return any(unit.endswith("-") and name.startswith(unit) and len(name) > len(unit)
               for unit in units)


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


def required_axes(root):
    """Обязательные ключи `env.yaml` — из колонки ключей таблицы дома.

    Возвращает тройку: множество ключей, перечень строк с неразобранной
    клеткой и перечень строк с голым прочерком. Второе и третье —
    расхождения, а не отказ: остальной перечень выводится, а неразобранная
    строка молча ушла бы из него, и её отсутствие проявилось бы у соседа —
    ключом «вне перечня».
    """
    text = read(root, PLATFORM_DOC)
    section = text.split(AXES_SECTION, 1)
    if len(section) < 2:
        raise Refusal("в %s нет раздела «%s»"
                      % (PLATFORM_DOC, AXES_SECTION.lstrip("# ")))
    body = section[1].split("\n## ", 1)[0]
    column = None
    keys = set()
    malformed = []
    bare = []
    for line in body.splitlines():
        if not line.strip().startswith("|"):
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if all(SEPARATOR_RE.match(cell) for cell in cells):
            continue
        if column is None:
            if KEY_COLUMN not in cells:
                raise Refusal(
                    "в таблице %s нет колонки «%s» — перечень осей выводить "
                    "не из чего" % (PLATFORM_DOC, KEY_COLUMN))
            column = cells.index(KEY_COLUMN)
            continue
        if len(cells) <= column:
            malformed.append(cells[0] if cells else "?")
            continue
        cell = cells[column]
        match = AXIS_KEY_RE.match(cell)
        if match:
            keys.add(match.group(1))
        elif BARE_DASH_RE.match(cell):
            bare.append(cells[0])
        elif not NO_KEY_RE.match(cell):
            malformed.append(cells[0])
    if column is None:
        raise Refusal("в %s не разобрана шапка таблицы осей" % PLATFORM_DOC)
    if not keys:
        raise Refusal("в %s не разобрано ни одного ключа оси" % PLATFORM_DOC)
    return keys, malformed, bare


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
    required_keys, malformed, bare = required_axes(root)
    matrix = admitted_from_spec(root)
    deploy = os.path.join(root, "deploy")
    if not os.path.isdir(deploy):
        raise Refusal("каталога deploy/ нет — раскладку мерить не на чем")

    defects = []

    # --- ось 4e: клетка ключа в доме не разобрана
    for name in malformed:
        defects.append("%s — у оси «%s» клетка ключа не разобрана: ни ключа "
                       "в обратных кавычках, ни прочерка" % (PLATFORM_DOC, name))

    # --- ось 4f: прочерк не назвал носителя значения
    for name in bare:
        defects.append("%s — у оси «%s» стои́т голый прочерк: носитель "
                       "значения не назван" % (PLATFORM_DOC, name))

    # --- ось 1: services/ ⊆ инвентарь
    services = os.path.join(root, "services")
    if os.path.isdir(services):
        for name in sorted(os.listdir(services)):
            if not os.path.isdir(os.path.join(services, name)):
                continue
            if not unit_known(name, units):
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
        # --- ось 4: ключи манифеста совпадают с выведенными из дома
        for axis in sorted(required_keys - set(axes)):
            defects.append("deploy/%s/env.yaml — нет оси «%s», названной в %s"
                           % (env, axis, PLATFORM_DOC))
        for axis in sorted(set(axes) - required_keys):
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
| `connector-<биржа>` (по одной на площадку) | — | нет |

## Общие артефакты монорепозитория

| Артефакт | Что несёт | Кто зависит |
|---|---|---|
| `shared-one` | — | все |

## Дальше
"""

# Фикстура дома объявляет ТЕ ЖЕ ключи, что несёт фикстура манифеста ниже:
# иначе контрольная ось батареи молчала бы на расхождении дома и манифеста —
# ровно на том дефекте, против которого заведён вывод перечня из дома.
MIN_PLATFORM = """## Чем различаются окружения

| Ось | Ключ манифеста | `dev` | `prod` |
|---|---|---|---|
| имя окружения | `environment` | `dev` | `prod` |
| допустимые контуры площадки | `admittedContours` | `DEMO` | `LIVE`, `DEMO` |
| имя хоста ингресса | `ingressHost` | своё | своё |
| синхронизация Argo CD | `argocdSync` | ручная | ручная |
| префикс путей Vault | `vaultPrefix` | `dev` | `prod` |
| реплики сервиса | — манифест сервиса | 1 | сколько назначил сервис |
| тег образа сервиса | — блок images: оверлея | свой | свой |
| ресурсные лимиты | `resourceProfile` | целевые | целевые |
| глубина хранения рядов | `retentionProfile` | сокращённая | по виду ряда |
| глубина хранения журнала | `journalRetentionProfile` | сокращённая | бессрочно |
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
  journalRetentionProfile: "reduced"
"""

KUSTOMIZATION = """apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: %s
resources:
  - ../base
images: %s
"""


def _files(mutate=None):
    """Файлы минимального корпуса. Отдельно от записи: пробе бывает нужен их
    состав — например, чтобы вывести окружения фикстуры, а не написать их
    число рядом с проверкой."""
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
    return files


def _fixture_envs(mutate=None):
    """Окружения фикстуры — из её же состава."""
    return sorted({name.split("/")[1] for name in _files(mutate)
                   if name.startswith("deploy/") and name.endswith("env.yaml")})


def _sandbox(work, mutate=None):
    """Минимальный корпус, на котором оси доказываются поимённо."""
    files = _files(mutate)
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

    def family_member(files):
        files["services/connector-okx/.keep"] = ""
    defects, refusal = run(family_member)
    axis("1b. КОНТРОЛЬ: член семейства `connector-<биржа>` принимается",
         refusal is None and not defects,
         refusal or "; ".join(defects or []) or "дефектов: 0")

    def bare_family(files):
        files["services/connector/.keep"] = ""
    defects, refusal = run(bare_family)
    axis("1c. голое имя семейства без площадки — расхождение",
         bool(defects) and any("connector" in d for d in defects or []),
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

    def axis_dropped_at_home(files):
        files[PLATFORM_DOC] = files[PLATFORM_DOC].replace(
            "| префикс путей Vault | `vaultPrefix` | `dev` | `prod` |\n", "")
    defects, refusal = run(axis_dropped_at_home)
    hit = {env for env in _fixture_envs(axis_dropped_at_home)
           if any("vaultPrefix" in d and "deploy/%s/" % env in d
                  for d in defects or [])}
    axis("4c. ось убрана ИЗ ДОМА, манифесты не тронуты — расхождение у КАЖДОГО "
         "окружения (перечень выведен из дома, а не скопирован)",
         bool(defects) and hit == set(_fixture_envs(axis_dropped_at_home)),
         refusal or "; ".join(defects or []))

    def axis_without_key(files):
        files[PLATFORM_DOC] = files[PLATFORM_DOC].replace(
            "| ресурсные лимиты | `resourceProfile` |",
            "| ресурсные лимиты | — манифест сервиса |")
        for env in ("dev", "prod"):
            files["deploy/%s/env.yaml" % env] = files[
                "deploy/%s/env.yaml" % env].replace(
                '  resourceProfile: "target"\n', "")
    defects, refusal = run(axis_without_key)
    axis("4d. КОНТРОЛЬ: строка дома с прочерком обязательного ключа не заводит",
         refusal is None and not defects,
         refusal or "; ".join(defects or []) or "дефектов: 0")

    def axis_key_unparsed(files):
        files[PLATFORM_DOC] = files[PLATFORM_DOC].replace(
            "| ресурсные лимиты | `resourceProfile` |",
            "| ресурсные лимиты | resourceProfile |")
    defects, refusal = run(axis_key_unparsed)
    axis("4e. клетка ключа не разобрана — расхождение (строка не исчезает молча)",
         bool(defects) and any("клетка ключа не разобрана" in d
                               for d in defects or []),
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

    def bare_dash(files):
        files[PLATFORM_DOC] = files[PLATFORM_DOC].replace(
            "| — манифест сервиса |", "| — |")
    defects, refusal = run(bare_dash)
    axis("4f. голый прочерк без названного носителя — расхождение",
         bool(defects) and any("голый прочерк" in d for d in defects or []),
         refusal or "; ".join(defects or []))

    def no_keys_at_all(files):
        text = files[PLATFORM_DOC]
        files[PLATFORM_DOC] = re.sub(r"\| `[A-Za-z][A-Za-z0-9]*` \|",
                                     "| — носитель назван |", text)
    defects, refusal = run(no_keys_at_all)
    axis("7d-2. в таблице дома ни одного ключа — проверка отказывает",
         refusal is not None, refusal or "проверка отчиталась")

    def no_key_column(files):
        files[PLATFORM_DOC] = files[PLATFORM_DOC].replace(
            "| Ось | Ключ манифеста |", "| Ось |")
    defects, refusal = run(no_key_column)
    axis("7d-1. в таблице дома нет колонки ключей — проверка отказывает",
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

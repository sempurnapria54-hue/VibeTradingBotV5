#!/usr/bin/env bash
# Выкладка новой сборки сервисов на стенд: реактор → образы → узел
# кластера → тег в манифесте окружения → применение.
#
# ТА ЖЕ ФОРМА, ЧТО У CI. Роль CI хостинга репозитория на стенде исполняет
# эта команда: собрать образ один раз и переставить тег в манифесте
# окружения (docs/architecture/platform.md §Развёртывание). Отличие одно и
# названо: реестра образов на стенде нет, образ кладётся прямо в узел
# кластера (`kind load`), поэтому имя образа остаётся тем же
# `registry.invalid/vibetrading/<сервис>`, а тянуть его неоткуда и не надо.
#
# ТЕГ. Неизменяемость тега держится на коммите: тег — `0.0.1-<sha>`. Пока
# дерево грязное (правки не закоммичены), к тегу добавляется метка времени
# — иначе один и тот же тег означал бы разное содержимое, то есть ровно то,
# против чего правило неизменяемости и заведено.
#
# УБОРКА ПРЕЖНИХ ОБРАЗОВ — часть выкладки, а не отдельная гигиена. Тег
# неизменяем, значит каждая сборка кладёт в узел НОВЫЙ образ (~180 МиБ), а
# прежний остаётся навсегда: узел растёт линейно по числу выкладок, и на
# машине держателя это упирается в системный диск. Цепочка сессий
# (tools/session-loop.sh) выкладывает многократно за запуск, поэтому уборка
# и переехала в саму выкладку. Идёт ПОСЛЕ `rollout status`: пока прежние
# поды живы, образ занят.
#
# УБИРАЕТ `ctr`, А НЕ `crictl` — измерено, а не выбрано по вкусу. `crictl
# rmi` снимает ИМЯ: удалённый тег оставляет то же содержимое безымянным
# (`kind load` даёт каждому образу вторую ссылку `import-<дата>@sha256:…`),
# и место не освобождается вовсе. Прогон на живом узле: `crictl rmi` по
# шести ссылкам — 0 МиБ; `ctr images rm` тех же ссылок плюс `ctr content
# prune references` — 16109 → 14521 МиБ, то есть 1.55 ГиБ, при живых подах.
#
# Запуск:  bash tools/stand/deploy-services.sh [<сервис> ...]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLUSTER="vibetrading"
NODE="$CLUSTER-control-plane"
# MSYS_NO_PATHCONV: без него git-bash переписывает абсолютные пути
# аргументов в windows-форму, и `ctr` внутри узла получает мусор.
node_ctr() { MSYS_NO_PATHCONV=1 docker exec "$NODE" ctr -n k8s.io "$@"; }
ENVIRONMENT="${STAND_ENVIRONMENT:-dev}"
KIND="${KIND:-$LOCALAPPDATA/kind/kind.exe}"
[ -x "$KIND" ] || KIND="kind"

# Дом путей JDK/Maven — .claude/tests/source-api/okx/code-preconditions.md
# §«Среда контура».
#
# JDK НАЗНАЧАЕТСЯ, А НЕ НАСЛЕДУЕТСЯ. В среде уже стои́т JAVA_HOME на JDK 11
# (системный), и подстановка «взять из окружения, иначе документированный»
# молча собирала бы проект не тем компилятором: сборка падает на
# repackage — «class file version 61.0 … recognizes up to 55.0».
# Переопределяется явно: STAND_JDK / STAND_MAVEN.
STAND_JDK="${STAND_JDK:-$HOME/.jdks/corretto-25.0.3}"
STAND_MAVEN="${STAND_MAVEN:-/c/Program Files/JetBrains/IntelliJ IDEA 2026.1/plugins/maven/lib/maven3}"
[ -x "$STAND_JDK/bin/java" ] || { echo "ОТКАЗ: нет JDK в $STAND_JDK" >&2; exit 2; }
[ -x "$STAND_MAVEN/bin/mvn" ] || { echo "ОТКАЗ: нет Maven в $STAND_MAVEN" >&2; exit 2; }
export JAVA_HOME="$STAND_JDK"
export MAVEN_HOME="$STAND_MAVEN"
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

SERVICES=("$@")
if [ ${#SERVICES[@]} -eq 0 ]; then
  SERVICES=(auth connector-okx market-data trading-core)
fi

SHA="$(git -C "$ROOT" rev-parse --short HEAD)"
if [ -n "$(git -C "$ROOT" status --porcelain)" ]; then
  TAG="0.0.1-$SHA-$(date +%Y%m%d%H%M%S)"
  echo "дерево грязное: тег помечен временем — $TAG"
else
  TAG="0.0.1-$SHA"
fi

# Уборка узла: ссылки того же сервиса с иным тегом плюс служебные ссылки
# импорта, затем сборка неиспользуемого содержимого. Отказ удаления
# выкладку не роняет: занятая ссылка уберётся следующим прогоном.
prune_node_images() {
  local svc="$1" keep="$2" ref removed=0
  local prefix="registry.invalid/vibetrading/$svc:"
  for ref in $(node_ctr images ls -q 2>/dev/null \
      | awk -v p="$prefix" -v k="$prefix$keep" 'index($0,p)==1 && $0!=k'); do
    node_ctr images rm "$ref" >/dev/null 2>&1 && removed=$((removed+1)) || echo "  занят, оставлен: $ref"
  done
  # Ссылка `import-<дата>@sha256:…` — бухгалтерия `kind load`: то же
  # содержимое уже названо тегом сервиса, поэтому снятие её ничего живого
  # не задевает (проверено: после снятия всех пятнадцати все развёрнутые
  # образы на месте, поды Running).
  for ref in $(node_ctr images ls -q 2>/dev/null | grep '^import-'); do
    node_ctr images rm "$ref" >/dev/null 2>&1 && removed=$((removed+1)) || true
  done
  node_ctr content prune references >/dev/null 2>&1 || true
  echo "  снято ссылок: $removed"
}

MODULES=""
for svc in "${SERVICES[@]}"; do
  [ -d "$ROOT/services/$svc" ] || { echo "ОТКАЗ: services/$svc не существует" >&2; exit 1; }
  MODULES="$MODULES,services/$svc"
done
MODULES="${MODULES#,}"

echo "=== сборка реактора: $MODULES"
mvn -q -f "$ROOT/pom.xml" -pl "$MODULES" -am package -DskipTests

for svc in "${SERVICES[@]}"; do
  echo "=== образ $svc:$TAG"
  docker build -q -t "registry.invalid/vibetrading/$svc:$TAG" "$ROOT/services/$svc" >/dev/null
  "$KIND" load docker-image "registry.invalid/vibetrading/$svc:$TAG" --name "$CLUSTER" >/dev/null
  bash "$ROOT/tools/deploy-set-image-tag.sh" "$ENVIRONMENT" "$svc" "$TAG"
done

echo "=== применение манифестов окружения $ENVIRONMENT"
kubectl apply -k "$ROOT/deploy/$ENVIRONMENT" >/dev/null

for svc in "${SERVICES[@]}"; do
  kubectl -n "$ENVIRONMENT" rollout status "deploy/$svc" --timeout=300s
  echo "=== уборка прежних образов $svc из узла"
  prune_node_images "$svc" "$TAG"
done

#!/usr/bin/env bash
# Постановка локального стенда: кластер, GitOps, операторы, окружение `dev`.
#
# ПРЕДМЕТ. Стенд — тестовое окружение на машине держателя (решение
# держателя 2026-09-05, docs/architecture/platform.md §Развёртывание).
# Команда идемпотентна: повторный прогон ничего не ломает и доводит до
# того же состояния — этим она и проверяется.
#
# ЧТО ОНА НЕ ДЕЛАЕТ. Образы сервисов не собирает: это отдельный ход
# (tools/stand/deploy-services.sh), потому что пересборка нужна на каждую
# правку кода, а платформа поднимается один раз.
#
# ЕДИНСТВЕННЫЙ РУЧНОЙ ХОД GITOPS — установка самого Argo CD: пока его нет,
# приводить кластер к манифестам некому. Дальше всё едет через него.
#
# Запуск (из корня репозитория):  bash tools/stand/up.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLUSTER="vibetrading"
ENVIRONMENT="${STAND_ENVIRONMENT:-dev}"
# Версия Argo CD закреплена: бутстрап — единственное место, где версия не
# может лежать в манифесте окружения, потому что применяется до того, как
# манифесты кто-то читает.
ARGOCD_VERSION="v3.5.2"
KIND="${KIND:-$LOCALAPPDATA/kind/kind.exe}"
STAND_DIR="${STAND_DIR:-$LOCALAPPDATA/vibetrading-stand}"

say() { printf "\n=== %s\n" "$*"; }

command -v kubectl >/dev/null || { echo "ОТКАЗ: kubectl не найден" >&2; exit 2; }
[ -x "$KIND" ] || command -v kind >/dev/null || { echo "ОТКАЗ: kind не найден ($KIND)" >&2; exit 2; }
[ -x "$KIND" ] || KIND="kind"
docker info >/dev/null 2>&1 || { echo "ОТКАЗ: демон Docker недоступен" >&2; exit 2; }

mkdir -p "$STAND_DIR"

say "1. Кластер"
if "$KIND" get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  echo "кластер $CLUSTER уже есть"
else
  "$KIND" create cluster --config "$ROOT/tools/stand/kind-cluster.yaml" --wait 180s
fi
kubectl config use-context "kind-$CLUSTER" >/dev/null

say "2. Argo CD (единственный ручной ход GitOps)"
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f - >/dev/null
# Серверная форма применения обязательна: CRD Argo CD не помещается в
# аннотацию last-applied-configuration (предел 262144 байта).
kubectl apply -n argocd --server-side --force-conflicts \
  -f "https://raw.githubusercontent.com/argoproj/argo-cd/${ARGOCD_VERSION}/manifests/install.yaml" >/dev/null
kubectl -n argocd rollout status deploy/argocd-server --timeout=300s

say "3. Кластерный слой: операторы и проект Argo CD"
kubectl apply -k "$ROOT/deploy/base/platform"

say "4. Секреты ролей базы (вне репозитория, генерируются один раз)"
kubectl create namespace "$ENVIRONMENT" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
for role in keycloak auth market_data trading_core; do
  secret="postgres-role-$(echo "$role" | tr '_' '-')"
  if kubectl -n "$ENVIRONMENT" get secret "$secret" >/dev/null 2>&1; then
    echo "секрет $secret уже есть"
  else
    password="$(py -3 -c "import secrets,string; a=string.ascii_letters+string.digits; print(''.join(secrets.choice(a) for _ in range(28)))")"
    kubectl -n "$ENVIRONMENT" create secret generic "$secret" \
      --type=kubernetes.io/basic-auth \
      --from-literal=username="$role" \
      --from-literal=password="$password" >/dev/null
    echo "секрет $secret заведён"
  fi
done

say "5. Окружение $ENVIRONMENT"
kubectl apply -k "$ROOT/deploy/$ENVIRONMENT"

say "6. Vault: распечатывание и настройка"
bash "$ROOT/tools/stand/vault-setup.sh"

say "7. Секрет клиента провайдера идентичности"
bash "$ROOT/tools/stand/identity-client-secret.sh"

say "Готово. Состояние:"
kubectl get pods -A --no-headers | grep -Ev "Running|Completed" || echo "все поды Running/Completed"

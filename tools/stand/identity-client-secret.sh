#!/usr/bin/env bash
# Перенос секретов клиентов из провайдера идентичности в секреты кластера.
#
# ПОЧЕМУ ЭТО КОМАНДА. Реалм и клиенты объявлены манифестом
# (deploy/base/services/identity-realm.yaml), а СЕКРЕТ клиента генерирует
# сам провайдер: в манифесте он лежал бы открытым текстом в git. Команда
# читает его у провайдера и кладёт в секрет кластера, откуда его берёт
# сервис своим Deployment'ом. В целевой конструкции этот перенос делает
# Vault (docs/architecture/platform.md §Безопасность).
#
# Идемпотентна: повторный прогон перезаписывает секреты теми же значениями.
#
# Запуск:  bash tools/stand/identity-client-secret.sh
set -euo pipefail

ENVIRONMENT="${STAND_ENVIRONMENT:-dev}"
REALM="vibetrading"
# Клиенты сервисов, у которых есть исходящие межсервисные вызовы.
CLIENTS=("market-data" "trading-core")
POD="platform-identity-0"
# Пути внутри контейнера не должны конвертироваться оболочкой Git Bash в
# windows-пути: без этого exec получает «C:/Program Files/Git/opt/...».
export MSYS_NO_PATHCONV=1

kubectl -n "$ENVIRONMENT" wait --for=condition=Ready "pod/$POD" --timeout=600s >/dev/null
kubectl -n "$ENVIRONMENT" wait --for=condition=Complete "job/$REALM" --timeout=600s >/dev/null

USER_NAME="$(kubectl -n "$ENVIRONMENT" get secret platform-identity-initial-admin -o jsonpath='{.data.username}' | base64 -d)"
PASSWORD="$(kubectl -n "$ENVIRONMENT" get secret platform-identity-initial-admin -o jsonpath='{.data.password}' | base64 -d)"

KC() { kubectl -n "$ENVIRONMENT" exec "$POD" -- /opt/keycloak/bin/kcadm.sh "$@"; }

KC config credentials --server http://localhost:8080 --realm master \
  --user "$USER_NAME" --password "$PASSWORD" >/dev/null

for CLIENT in "${CLIENTS[@]}"; do
  CLIENT_UUID="$(KC get clients -r "$REALM" -q "clientId=$CLIENT" --fields id --format csv --noquotes 2>/dev/null | tr -d '\r' | tail -1)"
  if [ -z "$CLIENT_UUID" ]; then
    echo "ОТКАЗ: клиент $CLIENT в реалме $REALM не найден" >&2
    exit 1
  fi

  VALUE="$(KC get "clients/$CLIENT_UUID/client-secret" -r "$REALM" 2>/dev/null | tr -d '\r' \
    | py -3 -c "import sys,json; print(json.load(sys.stdin).get('value',''))")"
  if [ -z "$VALUE" ]; then
    echo "ОТКАЗ: секрет клиента $CLIENT пуст" >&2
    exit 1
  fi

  kubectl -n "$ENVIRONMENT" create secret generic "oidc-client-$CLIENT" \
    --from-literal=client-id="$CLIENT" \
    --from-literal=client-secret="$VALUE" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null

  echo "секрет oidc-client-$CLIENT обновлён из реалма $REALM"
done

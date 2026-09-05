#!/usr/bin/env bash
# Настройка Vault на стенде: распечатывание, KV окружения, вход по
# Kubernetes, политики и роли сервисов.
#
# ПОЧЕМУ ЭТО КОМАНДА, А НЕ МАНИФЕСТ. Инициализация и распечатывание —
# операции над самим хранилищем, а не желаемое состояние кластера: ключ
# распечатывания существует ровно один раз и в git не попадает никогда.
# Политики и роли манифестом выразимы (оператор Vault это умеет), но их
# ввод — часть того же хардненинга, что стои́т в бэклоге §S1; до него форма
# у них командная и это названо, а не умолчано.
#
# ПУТЬ СЕКРЕТОВ. KV монтируется ПО ИМЕНИ ОКРУЖЕНИЯ (`dev/`), потому что
# сервисы адресуют ключи счёта путём `<окружение>/exchange-accounts/<id>`
# (docs/architecture/platform.md §Безопасность; исполнимая форма —
# libs/domain-model ExchangeAccountKeyPath) и пишут его через VaultTemplate
# без префикса движка. Версия KV — первая: у второй адрес несёт вставку
# `data/`, которой в форме пути нет.
#
# Запуск:  bash tools/stand/vault-setup.sh
set -euo pipefail

ENVIRONMENT="${STAND_ENVIRONMENT:-dev}"
STAND_DIR="${STAND_DIR:-$LOCALAPPDATA/vibetrading-stand}"
INIT_FILE="$STAND_DIR/vault-init.json"
NS="vault-system"
POD="vault-0"

mkdir -p "$STAND_DIR"
kubectl -n "$NS" wait --for=condition=Initialized "pod/$POD" --timeout=300s >/dev/null

status() { kubectl -n "$NS" exec "$POD" -- vault status -format=json 2>/dev/null || true; }

initialized="$(status | py -3 -c "import sys,json; d=sys.stdin.read().strip(); print(json.loads(d)['initialized'] if d else False)" 2>/dev/null || echo False)"
if [ "$initialized" != "True" ]; then
  if [ -f "$INIT_FILE" ]; then
    echo "ОТКАЗ: Vault не инициализирован, но файл $INIT_FILE уже есть." >&2
    echo "Либо хранилище пересоздано (тогда файл надо убрать вручную), либо это чужой стенд." >&2
    exit 1
  fi
  kubectl -n "$NS" exec "$POD" -- vault operator init -key-shares=1 -key-threshold=1 -format=json > "$INIT_FILE"
  echo "Vault инициализирован; ключ распечатывания и корневой токен — $INIT_FILE"
fi

[ -f "$INIT_FILE" ] || { echo "ОТКАЗ: нет $INIT_FILE — распечатать нечем" >&2; exit 1; }
UNSEAL="$(py -3 -c "import json,sys; print(json.load(open(sys.argv[1], encoding='utf-8'))['unseal_keys_b64'][0])" "$INIT_FILE")"
ROOT="$(py -3 -c "import json,sys; print(json.load(open(sys.argv[1], encoding='utf-8'))['root_token'])" "$INIT_FILE")"

sealed="$(status | py -3 -c "import sys,json; d=sys.stdin.read().strip(); print(json.loads(d)['sealed'] if d else True)" 2>/dev/null || echo True)"
if [ "$sealed" = "True" ]; then
  kubectl -n "$NS" exec "$POD" -- vault operator unseal "$UNSEAL" >/dev/null
  echo "Vault распечатан"
fi

V() { kubectl -n "$NS" exec -i "$POD" -- env VAULT_TOKEN="$ROOT" VAULT_ADDR=http://127.0.0.1:8200 "$@"; }

V vault secrets list -format=json | grep -q "\"$ENVIRONMENT/\"" \
  || V vault secrets enable -path="$ENVIRONMENT" -version=1 kv >/dev/null
V vault auth list -format=json | grep -q '"kubernetes/"' \
  || V vault auth enable kubernetes >/dev/null
V sh -c 'vault write auth/kubernetes/config kubernetes_host="https://$KUBERNETES_PORT_443_TCP_ADDR:443" kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt' >/dev/null

# Политики: auth ПИШЕТ ключи счёта, коннектор только ЧИТАЕТ
# (docs/architecture/tenant-and-exchange.md §Ключи).
V sh -c "cat > /tmp/policy-auth.hcl <<POL
path \"$ENVIRONMENT/exchange-accounts/*\" {
  capabilities = [\"create\", \"update\", \"read\"]
}
POL
vault policy write $ENVIRONMENT-auth /tmp/policy-auth.hcl" >/dev/null
V sh -c "cat > /tmp/policy-connector.hcl <<POL
path \"$ENVIRONMENT/exchange-accounts/*\" {
  capabilities = [\"read\"]
}
POL
vault policy write $ENVIRONMENT-connector /tmp/policy-connector.hcl" >/dev/null

# Роль привязана к ServiceAccount сервиса в пространстве имён окружения:
# пространство имён и есть окружение, поэтому «роль на свой префикс» и
# «роль своего окружения» — одно ограничение.
V vault write "auth/kubernetes/role/auth" \
  bound_service_account_names=auth \
  bound_service_account_namespaces="$ENVIRONMENT" \
  policies="$ENVIRONMENT-auth" ttl=1h >/dev/null
V vault write "auth/kubernetes/role/connector-okx" \
  bound_service_account_names=connector-okx \
  bound_service_account_namespaces="$ENVIRONMENT" \
  policies="$ENVIRONMENT-connector" ttl=1h >/dev/null

echo "Vault настроен: KV $ENVIRONMENT/, вход kubernetes, роли auth и connector-okx"

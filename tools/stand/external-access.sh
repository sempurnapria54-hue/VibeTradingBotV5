#!/usr/bin/env bash
# Вход снаружи окружения стенда: учётная запись держателя в реалме и
# сертификат ингресса — то, без чего предъявить себя окружению снаружи нечем.
#
# ПРЕДМЕТ. Токен человека выдаёт провайдер идентичности, а субъекта в реалме
# манифест не заводит: пароль в манифесте лежал бы в git открытым текстом, а
# заведение пользователя — действие держателя, то есть того же единственного
# субъекта поверхности (docs/rules/api-access-policy.md §«Принципал один —
# посылка фазы 1»). На стенде держатель исполняет его этой командой.
#
# ЧТО ОНА ОСТАВЛЯЕТ ВНЕ РЕПОЗИТОРИЯ (в $STAND_DIR, рядом с файлом
# распечатывания Vault):
#   identity-holder-<окружение>.json — имя и пароль учётной записи держателя;
#   ingress-<окружение>.crt          — сертификат ингресса окружения.
# Сертификат самоподписанный (deploy/base/services/bff.yaml, Issuer), поэтому
# доверие к нему предъявляющий получает этим файлом, а не цепочкой центра.
#
# ЧЕГО ОНА НЕ ДЕЛАЕТ. Токена не выдаёт и гранта не меняет: токен берётся той же
# тропой, что у браузера, — кодом авторизации браузерного клиента с PKCE;
# грант пароля у него выключен, и так и остаётся
# (deploy/base/services/identity-realm.yaml).
#
# Идемпотентна: пароль генерируется один раз и дальше берётся из файла;
# повторный прогон переставляет учётной записи тот же пароль — так учётная
# запись, заведённая заново пересозданным кластером, получает прежний.
#
# Запуск:  bash tools/stand/external-access.sh
set -euo pipefail

ENVIRONMENT="${STAND_ENVIRONMENT:-dev}"
# Вне Windows %LOCALAPPDATA% нет — каталог стенда ложится в $HOME, как у
# tools/session-unattended.sh.
STAND_DIR="${STAND_DIR:-${LOCALAPPDATA:-$HOME}/vibetrading-stand}"
REALM="vibetrading"
HOLDER="holder"
POD="platform-identity-0"
CREDENTIALS="$STAND_DIR/identity-holder-$ENVIRONMENT.json"
CERTIFICATE="$STAND_DIR/ingress-$ENVIRONMENT.crt"
# Секрет, который cert-manager выпускает по аннотации Ingress окружения.
TLS_SECRET="perimeter-tls"
# Пути внутри контейнера не должны конвертироваться оболочкой Git Bash в
# windows-пути: без этого exec получает «C:/Program Files/Git/opt/...».
export MSYS_NO_PATHCONV=1

mkdir -p "$STAND_DIR"

kubectl -n "$ENVIRONMENT" wait --for=condition=Ready "pod/$POD" --timeout=600s >/dev/null
kubectl -n "$ENVIRONMENT" wait --for=condition=Complete "job/$REALM" --timeout=600s >/dev/null

ADMIN_NAME="$(kubectl -n "$ENVIRONMENT" get secret platform-identity-initial-admin -o jsonpath='{.data.username}' | base64 -d)"
ADMIN_PASSWORD="$(kubectl -n "$ENVIRONMENT" get secret platform-identity-initial-admin -o jsonpath='{.data.password}' | base64 -d)"

KC() { kubectl -n "$ENVIRONMENT" exec "$POD" -- /opt/keycloak/bin/kcadm.sh "$@"; }

KC config credentials --server http://localhost:8080 --realm master \
  --user "$ADMIN_NAME" --password "$ADMIN_PASSWORD" >/dev/null

# Учётная запись. Имя, почта и фамилия заполнены намеренно: профиль
# пользователя провайдера объявляет их обязательными, и учётная запись без
# них получила бы на первом входе требование дозаполнить профиль — страницу
# вместо переадресации с кодом.
HOLDER_ID="$(KC get users -r "$REALM" -q "username=$HOLDER" -q exact=true --fields id --format csv --noquotes 2>/dev/null | tr -d '\r' | tail -1)"
if [ -z "$HOLDER_ID" ]; then
  KC create users -r "$REALM" \
    -s username="$HOLDER" -s enabled=true -s emailVerified=true \
    -s email="$HOLDER@$ENVIRONMENT.vibetrading.invalid" \
    -s firstName=Holder -s lastName=Stand >/dev/null
  echo "учётная запись $HOLDER заведена в реалме $REALM"
else
  echo "учётная запись $HOLDER уже есть"
fi

if [ -f "$CREDENTIALS" ]; then
  PASSWORD="$(py -3 -c "import json,sys; print(json.load(open(sys.argv[1], encoding='utf-8'))['password'])" "$CREDENTIALS")"
else
  PASSWORD="$(py -3 -c "import secrets,string; a=string.ascii_letters+string.digits; print(''.join(secrets.choice(a) for _ in range(28)))")"
  py -3 -c "import json,sys; json.dump({'username': sys.argv[1], 'password': sys.argv[2]}, open(sys.argv[3], 'w', encoding='utf-8'))" \
    "$HOLDER" "$PASSWORD" "$CREDENTIALS"
  echo "пароль сгенерирован: $CREDENTIALS"
fi
[ -n "$PASSWORD" ] || { echo "ОТКАЗ: пароль учётной записи пуст ($CREDENTIALS)" >&2; exit 1; }
# Пароль постоянный: временный потребовал бы смены на первом входе.
KC set-password -r "$REALM" --username "$HOLDER" --new-password "$PASSWORD" >/dev/null
echo "пароль учётной записи $HOLDER выставлен из $CREDENTIALS"

kubectl -n "$ENVIRONMENT" wait --for=condition=Ready "certificate/$TLS_SECRET" --timeout=300s >/dev/null
kubectl -n "$ENVIRONMENT" get secret "$TLS_SECRET" -o jsonpath='{.data.tls\.crt}' | base64 -d > "$CERTIFICATE"
[ -s "$CERTIFICATE" ] || { echo "ОТКАЗ: сертификат ингресса пуст ($TLS_SECRET)" >&2; exit 1; }
echo "сертификат ингресса окружения $ENVIRONMENT: $CERTIFICATE"

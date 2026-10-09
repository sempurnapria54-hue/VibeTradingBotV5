"""Действия держателя на стенде через вход снаружи: токен тропой PKCE
(клиент `web`, учётная запись `~/vibetrading-stand/identity-holder-dev.json`)
и вызов поверхности периметра.

ПОЧЕМУ КОМАНДА, А НЕ РУЧНОЙ ХОД. Грант пароля у реалма выключен
(docs/rules/api-access-policy.md), и токен человека берётся только кодом
авторизации — форму входа здесь заполняет скрипт. Секреты (пароль, токен,
ключи площадки) на экран не выводятся; ключи в ответе маскируются.

Запуск:
  py tools/stand/holder-api.py context     # контекст; первый вызов заводит тенанта с членством OWNER
  py tools/stand/holder-api.py accounts    # реестр биржевых счетов
  py tools/stand/holder-api.py register <keys.json> <tenantInternalId> <label>
      # регистрация demo-счёта OKX; файл ключей — {"apiKey","secret","passphrase"}
  py tools/stand/holder-api.py get <path>                 # GET поверхности периметра
  py tools/stand/holder-api.py put <path> '<json>'        # PUT с телом
  py tools/stand/holder-api.py post <path> <body.json>    # POST с телом из файла
Факты стенда и порядок — .claude/skills/local-stand.md.
"""
import base64, hashlib, html, http.cookiejar, json, os, re, secrets, ssl, sys
import urllib.parse, urllib.request

HOST = "https://dev.vibetrading.invalid"
STAND = os.path.expanduser("~/vibetrading-stand")
CTX = ssl.create_default_context(cafile=os.path.join(STAND, "ingress-dev.crt"))


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


def token():
    creds = json.load(open(os.path.join(STAND, "identity-holder-dev.json")))
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(
        urllib.request.HTTPSHandler(context=CTX),
        urllib.request.HTTPCookieProcessor(jar), NoRedirect())
    verifier = secrets.token_urlsafe(48)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    redirect = HOST + "/"
    q = urllib.parse.urlencode({"client_id": "web", "response_type": "code", "scope": "openid",
                                "redirect_uri": redirect, "code_challenge": challenge,
                                "code_challenge_method": "S256", "state": secrets.token_hex(8)})
    page = opener.open(HOST + "/realms/vibetrading/protocol/openid-connect/auth?" + q).read().decode()
    action = html.unescape(re.search(r'<form[^>]+action="([^"]+)"', page).group(1))
    body = urllib.parse.urlencode({"username": creds["username"], "password": creds["password"]}).encode()
    try:
        opener.open(action, body)
        raise SystemExit("ОТКАЗ: вход не вернул перенаправления (неверные учётные данные?)")
    except urllib.error.HTTPError as e:
        if e.code not in (302, 303):
            raise
        location = e.headers["Location"]
    code = urllib.parse.parse_qs(urllib.parse.urlparse(location).query)["code"][0]
    data = urllib.parse.urlencode({"grant_type": "authorization_code", "client_id": "web", "code": code,
                                   "redirect_uri": redirect, "code_verifier": verifier}).encode()
    resp = urllib.request.urlopen(urllib.request.Request(
        HOST + "/realms/vibetrading/protocol/openid-connect/token", data), context=CTX)
    return json.load(resp)["access_token"]


def call(method, path, tok, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(HOST + path, data, method=method,
                                 headers={"Authorization": "Bearer " + tok, "Content-Type": "application/json"})
    try:
        r = urllib.request.urlopen(req, context=CTX)
        return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


if __name__ == "__main__":
    tok = token()
    if sys.argv[1] == "context":
        print(*call("GET", "/api/v1/bff/context", tok))
    elif sys.argv[1] == "accounts":
        print(*call("GET", "/api/v1/auth/exchange-accounts", tok))
    elif sys.argv[1] == "get":
        print(*call("GET", sys.argv[2], tok))
    elif sys.argv[1] == "put":
        print(*call("PUT", sys.argv[2], tok, json.loads(sys.argv[3])))
    elif sys.argv[1] == "post":
        print(*call("POST", sys.argv[2], tok, json.load(open(sys.argv[3]))))
    elif sys.argv[1] == "register":
        keys = json.load(open(os.path.expanduser(sys.argv[2])))
        payload = {"tenantInternalId": sys.argv[3], "exchangeCode": "OKX", "label": sys.argv[4],
                   "contour": "DEMO", "apiKey": keys["apiKey"], "secret": keys["secret"],
                   "passphrase": keys["passphrase"]}
        status, body = call("POST", "/api/v1/auth/exchange-accounts", tok, payload)
        for k in ("apiKey", "secret", "passphrase"):
            body = body.replace(keys[k], "***")
        print(status, body)

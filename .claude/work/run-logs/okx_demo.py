#!/usr/bin/env python3
"""Standalone OKX demo signer — inspect/sweep demo account state for the
source-api contour. Replicates OkxSigningInterceptor exactly:
prehash = ts + METHOD + requestPath(+?query) + body ; sign = base64(hmac_sha256(secret, prehash)).
Creds pulled from Vault (test path). Usage:
  okx_demo.py inspect            # pending orders + positions for ETH-USDT-SWAP
  okx_demo.py sweep              # cancel all pending + close position
  okx_demo.py get  <path> [q=v...]
  okx_demo.py post <path> '<json-body>'
"""
import base64, hashlib, hmac, json, sys, urllib.parse, urllib.request
from datetime import datetime, timezone

VAULT = "http://localhost:8200"
INST = "ETH-USDT-SWAP"
BASE = "https://www.okx.com"


def vault_creds(token):
    req = urllib.request.Request(
        f"{VAULT}/v1/secret/data/tradingbot/okx-test",
        headers={"X-Vault-Token": token})
    d = json.load(urllib.request.urlopen(req, timeout=5))["data"]["data"]
    return d["OKX_API_KEY"], d["OKX_SECRET_KEY"], d["OKX_PASSPHRASE"]


def ts():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.") + \
        f"{datetime.now(timezone.utc).microsecond // 1000:03d}Z"


def call(method, path, key, secret, passphrase, query=None, body=None):
    request_path = path
    if query:
        request_path = path + "?" + urllib.parse.urlencode(query)
    body_str = json.dumps(body, separators=(",", ":")) if body is not None else ""
    t = ts()
    prehash = t + method + request_path + body_str
    sign = base64.b64encode(
        hmac.new(secret.encode(), prehash.encode(), hashlib.sha256).digest()).decode()
    headers = {
        "OK-ACCESS-KEY": key,
        "OK-ACCESS-SIGN": sign,
        "OK-ACCESS-TIMESTAMP": t,
        "OK-ACCESS-PASSPHRASE": passphrase,
        "x-simulated-trading": "1",
        "Content-Type": "application/json",
        "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                      "(KHTML, like Gecko) Chrome/124.0 Safari/537.36",
    }
    data = body_str.encode() if body is not None else None
    req = urllib.request.Request(BASE + request_path, data=data, headers=headers, method=method)
    try:
        resp = urllib.request.urlopen(req, timeout=15)
        return resp.status, json.load(resp)
    except urllib.error.HTTPError as e:
        raw = e.read().decode(errors="replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"_raw": raw[:500], "_status": e.code}


def main():
    token = open("/home/romankrdr/IdeaProjects/VibeTradingBotV5/.env.vault.test.local").read()
    token = [l.split('"')[1] for l in token.splitlines() if l.startswith("export VAULT_TOKEN=")][0]
    key, secret, passphrase = vault_creds(token)
    cmd = sys.argv[1] if len(sys.argv) > 1 else "inspect"

    def c(method, path, query=None, body=None):
        return call(method, path, key, secret, passphrase, query, body)

    if cmd == "inspect":
        st, pend = c("GET", "/api/v5/trade/orders-pending", {"instId": INST})
        print("PENDING http=%s code=%s n=%s" % (st, pend.get("code"), len(pend.get("data", []))))
        for o in pend.get("data", []):
            print("  ordId=%s clOrdId=%s state=%s side=%s sz=%s px=%s" % (
                o.get("ordId"), o.get("clOrdId"), o.get("state"), o.get("side"), o.get("sz"), o.get("px")))
        st, pos = c("GET", "/api/v5/account/positions", {"instType": "SWAP", "instId": INST})
        print("POSITIONS http=%s code=%s n=%s" % (st, pos.get("code"), len(pos.get("data", []))))
        for p in pos.get("data", []):
            print("  posId=%s pos=%s avgPx=%s upl=%s" % (
                p.get("posId"), p.get("pos"), p.get("avgPx"), p.get("upl")))
        st, algo = c("GET", "/api/v5/trade/orders-algo-pending", {"instId": INST, "ordType": "conditional"})
        print("ALGO-PENDING(conditional) http=%s code=%s n=%s" % (st, algo.get("code"), len(algo.get("data", []))))
    elif cmd == "sweep":
        st, pend = c("GET", "/api/v5/trade/orders-pending", {"instId": INST})
        ords = pend.get("data", [])
        print("sweep: %d pending orders" % len(ords))
        for o in ords:
            st, r = c("POST", "/api/v5/trade/cancel-order", body={"instId": INST, "ordId": o["ordId"]})
            print("  cancel ordId=%s -> code=%s scode=%s" % (
                o["ordId"], r.get("code"), (r.get("data") or [{}])[0].get("sCode")))
        st, pos = c("GET", "/api/v5/account/positions", {"instType": "SWAP", "instId": INST})
        has_pos = any(p.get("pos") and float(p["pos"]) != 0 for p in pos.get("data", []))
        if has_pos:
            st, r = c("POST", "/api/v5/trade/close-position", body={
                "instId": INST, "mgnMode": "isolated", "posSide": "net", "autoCxl": True, "ccy": "USDT"})
            print("  close-position -> code=%s msg=%s" % (r.get("code"), r.get("msg")))
        else:
            print("  no open position")
    elif cmd == "get":
        path = sys.argv[2]
        q = dict(kv.split("=", 1) for kv in sys.argv[3:])
        st, r = c("GET", path, q or None)
        print(st, json.dumps(r, indent=2))
    elif cmd == "post":
        path = sys.argv[2]
        body = json.loads(sys.argv[3])
        st, r = c("POST", path, body=body)
        print(st, json.dumps(r, indent=2))


if __name__ == "__main__":
    main()

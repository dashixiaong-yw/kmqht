"""尝试获取商品级别 title: erp.item.single.get"""
import hashlib, json, httpx
from datetime import datetime

APP_KEY = "1981991413"
APP_SECRET = "54279f7a085e405ebeb6af0d5e2cd68e"
SESSION = "f9eae7b99b14478ea13e640a1be05fab"
API_BASE = "https://gw.superboss.cc/router"

def sign(params, secret):
    sk = sorted(params.keys())
    ss = secret
    for k in sk:
        ss += f"{k}{params[k]}"
    ss += secret
    return hashlib.md5(ss.encode("utf-8")).hexdigest().upper()

def call(method, extra=None):
    p = {
        "appKey": APP_KEY, "method": method, "session": SESSION,
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "format": "json", "version": "1.0", "sign_method": "md5"
    }
    if extra:
        p.update(extra)
    for k in list(p.keys()):
        if not isinstance(p[k], str):
            p[k] = json.dumps(p[k], ensure_ascii=False)
    p["sign"] = sign(p, APP_SECRET)
    r = httpx.post(API_BASE, data=p, timeout=30)
    r.raise_for_status()
    return r.json()

# 尝试1: erp.item.single.get (用 itemOuterId)
print("=== 尝试1: erp.item.single.get (用 itemOuterId=B-08) ===")
r1 = call("erp.item.single.get", {"outerId": "B-08"})
wrapper = r1.get("erp_item_single_get_response", r1)
print(f"keys: {list(wrapper.keys())}")
for k, v in sorted(wrapper.items()):
    if k != "skus":
        print(f"  {k}: {v!r}")
if "item" in wrapper:
    for k, v in sorted(wrapper["item"].items()):
        print(f"  item.{k}: {v!r}")

# 尝试2: erp.item.single.get (用 sysItemId)
print("\n=== 尝试2: erp.item.single.get (用 sysItemId=5884168438716416) ===")
r2 = call("erp.item.single.get", {"id": "5884168438716416"})
wrapper2 = r2.get("erp_item_single_get_response", r2)
print(f"keys: {list(wrapper2.keys())}")
for k, v in sorted(wrapper2.items()):
    if k != "skus":
        print(f"  {k}: {v!r}")

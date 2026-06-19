"""快速验证 erp.item.single.sku.get 返回是否含 title"""
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

# 查SKU
r = call("erp.item.single.sku.get", {"skuOuterId": "B08-12"})
wrapper = r.get("erp_item_single_sku_get_response", r)
sku_list = wrapper.get("itemSku", [])

if sku_list:
    sku = sku_list[0]
    print("erp.item.single.sku.get 返回的所有字段:")
    for k, v in sorted(sku.items()):
        print(f"  {k}: {v!r}")
else:
    print("未找到SKU数据")
    print(json.dumps(r, ensure_ascii=False, indent=2)[:2000])

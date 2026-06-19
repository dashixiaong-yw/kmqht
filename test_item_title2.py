"""尝试各种方式获取商品title"""
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

# 尝试 erp.item.list.query
print("=== erp.item.list.query (用 outerId) ===")
try:
    r = call("erp.item.list.query", {"outerId": "B-08", "pageNo": "1", "pageSize": "10"})
    wrapper = r.get("erp_item_list_query_response", r)
    if "list" in wrapper:
        items = wrapper["list"]
        if items:
            print(f"找到 {len(items)} 个商品")
            for k, v in sorted(items[0].items()):
                print(f"  {k}: {v!r}")
        else:
            print("空列表")
    else:
        print(f"响应: {json.dumps(r, ensure_ascii=False)[:500]}")
except Exception as e:
    print(f"错误: {e}")

# 尝试直接用title匹配反查
print("\n=== erp.item.list.query (用 title=.) ===")
try:
    r = call("erp.item.list.query", {"title": ".", "pageNo": "1", "pageSize": "5"})
    wrapper = r.get("erp_item_list_query_response", r)
    if "list" in wrapper:
        items = wrapper["list"]
        if items:
            for item in items:
                print(f"  title={item.get('title')}, outerId={item.get('outerId')}, id={item.get('id')}")
        else:
            print("空列表")
    else:
        print(f"响应: {json.dumps(r, ensure_ascii=False)[:300]}")
except Exception as e:
    print(f"错误: {e}")

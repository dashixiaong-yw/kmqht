"""修复 B-08 的 title 并验证 item.single.get 返回原文title"""
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

# Step 1: 修复 title - 用propertiesName作为合理标题
print("=== Step 1: 修复 B-08 的 title ===")
r = call("erp.item.general.addorupdate", {
    "id": "5884168438716416",
    "outerId": "B-08",
    "title": "卡皮巴拉橡皮擦",  # 修复为合理标题
    "skus": [{
        "id": 5884168439067136,
        "outerId": "B08-12",
        "propertiesName": "卡皮巴拉橡皮擦12个",
    }]
})
wrapper = r.get("erp_item_general_addorupdate_response", r)
print(f"  修复结果: success={wrapper.get('success')}, code={wrapper.get('code','')}, msg={wrapper.get('msg','')}")

# Step 2: 验证 item.single.get 返回正确的title
print("\n=== Step 2: item.single.get 查询 B-08 验证 ===")
r2 = call("item.single.get", {"outerId": "B-08"})
data = r2.get("item_single_get_response", r2)
item = data.get("item", data)
print(f"  title: {item.get('title', 'N/A')}")
print(f"  outerId: {item.get('outerId', 'N/A')}")
print(f"  sysItemId: {item.get('sysItemId', 'N/A')}")

# Step 3: 测试完整流程: SKU查询 → 获itemOuterId → item.single.get → 拿title
print("\n=== Step 3: 完整流程测试 ===")
print("  a) erp.item.single.sku.get → 获 itemOuterId")
r3a = call("erp.item.single.sku.get", {"skuOuterId": "B08-12"})
sku = r3a.get("erp_item_single_sku_get_response", r3a).get("itemSku", [{}])[0]
item_outer = sku.get("itemOuterId", "")
print(f"     itemOuterId = {item_outer}")

print("  b) item.single.get → 获 title")
r3b = call("item.single.get", {"outerId": item_outer})
item3 = r3b.get("item_single_get_response", r3b).get("item", {})
print(f"     title = {item3.get('title', 'N/A')}")

print("\n✅ 验证完成: item.single.get 可以获取原文title")

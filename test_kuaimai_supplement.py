"""快麦API补充测试 - 验证 erp.item.general.addorupdate 和 supplierCode 问题"""
import hashlib, json, httpx

APP_KEY = "1981991413"
APP_SECRET = "54279f7a085e405ebeb6af0d5e2cd68e"
SESSION = "f9eae7b99b14478ea13e640a1be05fab"
API_BASE = "https://gw.superboss.cc/router"
SYS_ITEM_ID = 5884168438716416
SYS_SKU_ID = 5884168439067136
ITEM_OUTER_ID = "B-08"

def sign(params: dict, app_secret: str) -> str:
    sorted_keys = sorted(params.keys())
    sign_str = app_secret
    for key in sorted_keys:
        sign_str += f"{key}{params[key]}"
    sign_str += app_secret
    return hashlib.md5(sign_str.encode("utf-8")).hexdigest().upper()

def call_api(method: str, extra_params: dict = None) -> dict:
    from datetime import datetime
    params = {
        "appKey": APP_KEY, "method": method, "session": SESSION,
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "format": "json", "version": "1.0", "sign_method": "md5",
    }
    if extra_params: params.update(extra_params)
    for k in list(params.keys()):
        if not isinstance(params[k], str): params[k] = json.dumps(params[k], ensure_ascii=False)
    params["sign"] = sign(params, APP_SECRET)
    r = httpx.post(API_BASE, data=params, timeout=30); r.raise_for_status()
    result = r.json()
    if "error_response" in result:
        print(f"  ❌ 错误: {result['error_response']}")
        return None
    return result

print("=" * 70)
print("补充测试1: erp.item.general.addorupdate 加 outerId+title")
print("=" * 70)
req = {
    "method": "erp.item.general.addorupdate",
    "id": str(SYS_ITEM_ID),
    "outerId": ITEM_OUTER_ID,
    "title": "卡皮巴拉橡皮擦",
    "skus": json.dumps([{"id": SYS_SKU_ID, "remark": "测试备注-勿删"}], ensure_ascii=False)
}
print(f"参数: {json.dumps(req, ensure_ascii=False)}")
r1 = call_api("erp.item.general.addorupdate", req)
if r1: print(json.dumps(r1, ensure_ascii=False, indent=4))

print()
print("=" * 70)
print("补充测试2: 查询多个商品信息V2 获取商品标题")
print("=" * 70)
r2 = call_api("erp.item.list.get", {"ids": str(SYS_ITEM_ID)})
if r2: print(json.dumps(r2, ensure_ascii=False, indent=4))

print()
print("=" * 70)
print("补充测试3: 用 sysItemIds 查供应商 (看 supplierCode)")
print("=" * 70)
r3 = call_api("item.supplier.list.get", {"sysItemIds": str(SYS_ITEM_ID)})
if r3:
    supps = r3.get("suppliers", [])
    if supps:
        print(json.dumps(supps[0], ensure_ascii=False, indent=4))
        code = supps[0].get("supplierCode", "")
        print(f"\n  supplierCode = '{code}' (空=该字段可能是V1+V2差异)")
    else:
        print("  无供应商数据")
        print(json.dumps(r3, ensure_ascii=False, indent=4))

print()
print("=" * 70)
print("补充测试4: 验证已修改的备注是否写入成功")
print("=" * 70)
r4 = call_api("erp.item.single.sku.get", {"skuOuterId": "B08-12"})
if r4:
    skus = r4.get("itemSku", [])
    if skus:
        s = skus[0]
        print(f"  skuRemark: '{s.get('skuRemark', '')}'")
        print(json.dumps(r4, ensure_ascii=False, indent=4))

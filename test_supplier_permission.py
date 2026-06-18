"""测试供应商采购权限API - supplier.list.query + item.supplier.update"""
import hashlib, json, httpx, time
from datetime import datetime

APP_KEY = "1981991413"
APP_SECRET = "54279f7a085e405ebeb6af0d5e2cd68e"
SESSION = "f9eae7b99b14478ea13e640a1be05fab"
API_BASE = "https://gw.superboss.cc/router"
SYS_ITEM_ID = 5884168438716416
SYS_SKU_ID = 5884168439067136
ITEM_OUTER_ID = "B-08"
SKU_OUTER_ID = "B08-12"

def sign(params, secret):
    sk = sorted(params.keys())
    ss = secret
    for k in sk:
        ss += f"{k}{params[k]}"
    ss += secret
    return hashlib.md5(ss.encode("utf-8")).hexdigest().upper()

def call(method, extra=None, use_multipart=False):
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
    if use_multipart:
        files = {k: (None, str(v)) for k, v in p.items()}
        r = httpx.post(API_BASE, files=files, timeout=30)
    else:
        r = httpx.post(API_BASE, data=p, timeout=30)
    r.raise_for_status()
    return r.json()

PASS = 0
FAIL = 0

def check(name, ok, detail=""):
    global PASS, FAIL
    icon = "✅" if ok else "❌"
    print(f"  {icon} {name}")
    if detail:
        print(f"     {detail}")
    if ok:
        PASS += 1
    else:
        FAIL += 1

print("=" * 65)
print("供应商采购权限API测试")
print("=" * 65)

# ======== T1: supplier.list.query ========
print("\n" + "-" * 65)
print("T1: supplier.list.query (采购-供应商列表)")
print("-" * 65)
r1 = call("supplier.list.query", {"pageNo": "1", "pageSize": "200"}, use_multipart=True)
if "error_response" in r1:
    check("supplier.list.query 调用", False, f"{r1['error_response']}")
elif r1.get("code") == "401":
    check("supplier.list.query 权限不足", False, r1.get("msg"))
else:
    total = r1.get("total", "N/A")
    suppliers = r1.get("list", [])
    check("supplier.list.query 调用成功", True, f"共 {total} 个供应商, 本页 {len(suppliers)} 条")
    if suppliers:
        print()
        print("  供应商列表:")
        for s in suppliers[:20]:
            code = s.get("code", "")
            name = s.get("name", "")
            sid = s.get("id", "")
            print(f"    code={code}, name={name}, id={sid}")

# ======== T2: item.supplier.update ========
print("\n" + "-" * 65)
print("T2: item.supplier.update (商品供应商关系更新)")
print("-" * 65)

# 先查当前供应商
r2a = call("item.supplier.list.get", {"sysSkuIds": str(SYS_SKU_ID)})
old_suppliers = r2a.get("suppliers", []) if r2a else []
old_name = old_suppliers[0]["supplierName"] if old_suppliers else "无"
print(f"  当前供应商: {old_name}")

# 尝试用 item.supplier.update + supplierId
r2b = call("item.supplier.update", {
    "itemId": str(SYS_ITEM_ID),
    "outerId": ITEM_OUTER_ID,
    "suppliers": json.dumps([{"id": 6520908, "itemTitle": "测试供应商"}], ensure_ascii=False)
})
if "error_response" not in r2b and not r2b.get("code"):
    check("item.supplier.update (id) 调用", True, "success")
else:
    check("item.supplier.update (id) 调用", False, r2b.get("msg", ""))

# ======== T3: 完整链路 ========
print("\n" + "-" * 65)
print("T3: 完整链路 — 用 supplier.list.query 拿code → 修改")
print("-" * 65)

# 如果T1成功, 取第一个供应商的code
first_code = ""
first_name = ""
first_id = 0
if r1 and "list" in r1 and r1["list"]:
    first_code = r1["list"][0].get("code", "")
    first_name = r1["list"][0].get("name", "")
    first_id = r1["list"][0].get("id", 0)
    print(f"  选供应商: code={first_code}, name={first_name}, id={first_id}")

if first_code:
    # 用 code 调用 item.supplier.update
    now_str = datetime.now().strftime("%H%M%S")
    test_name = f"测试供应商-{now_str}"
    
    # 尝试用 code
    print(f"\n  尝试1: suppliers[].code={first_code}")
    r3a = call("item.supplier.update", {
        "itemId": str(SYS_ITEM_ID),
        "outerId": ITEM_OUTER_ID,
        "suppliers": json.dumps([{"code": first_code, "itemTitle": test_name}], ensure_ascii=False)
    })
    check("item.supplier.update (code) 调用", "error_response" not in r3a and not r3a.get("code"), json.dumps(r3a))
    
    # 验证
    time.sleep(1)
    r3v = call("item.supplier.list.get", {"sysSkuIds": str(SYS_SKU_ID)})
    new_name = r3v["suppliers"][0]["supplierName"] if r3v.get("suppliers") else "N/A"
    print(f"  写入后回读: name={new_name}")
    check("供应商修改生效", new_name == test_name, f"目标={test_name}, 实际={new_name}")
    
    # 恢复原供应商
    if new_name != old_name and old_suppliers:
        r3r = call("item.supplier.update", {
            "itemId": str(SYS_ITEM_ID),
            "outerId": ITEM_OUTER_ID,
            "suppliers": json.dumps([{"code": first_code, "itemTitle": old_name}], ensure_ascii=False)
        })
        check("恢复原供应商", "error_response" not in r3r and not r3r.get("code"))
    elif old_suppliers:
        # 用id方式恢复
        pass
else:
    check("完整链路: 无可用的供应商code", False)

# ======== 汇总 ========
print("\n" + "=" * 65)
print("测试汇总")
print("=" * 65)
print(f"  ✅ 通过: {PASS}")
print(f"  ❌ 失败: {FAIL}")

"""
测试: erp.item.general.addorupdate 使用 title="."  vs title=null 的行为差异
验证根因: title="." 是否导致快麦API拒绝请求
"""
import hashlib, json, httpx
from datetime import datetime

APP_KEY = "1981991413"
APP_SECRET = "54279f7a085e405ebeb6af0d5e2cd68e"
SESSION = "f9eae7b99b14478ea13e640a1be05fab"
API_BASE = "https://gw.superboss.cc/router"

# 先用 supplier.list.query 拿一个真实商品来测试
# 用之前测试脚本中的已知商品
SYS_ITEM_ID = 5884168438716416
SYS_SKU_ID = 5884168439067136
SKU_OUTER_ID = "B08-12"
ITEM_OUTER_ID = "B-08"


def sign(params, secret):
    sk = sorted(params.keys())
    ss = secret
    for k in sk:
        ss += f"{k}{params[k]}"
    ss += secret
    return hashlib.md5(ss.encode("utf-8")).hexdigest().upper()


def call_erp(method, biz_params, use_multipart=True):
    """调用快麦API（multipart格式，与后端get_supplier_list一致）"""
    p = {
        "appKey": APP_KEY,
        "method": method,
        "session": SESSION,
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "format": "json",
        "version": "1.0",
        "sign_method": "md5",
    }
    # 合并业务参数，值为复杂类型时json序列化
    for k, v in biz_params.items():
        if not isinstance(v, str):
            p[k] = json.dumps(v, ensure_ascii=False)
        else:
            p[k] = v
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
print("备注更新API测试 - title=\".\" vs title不传")
print("=" * 65)

# ======== T0: 先查当前商品信息 ========
print("\n" + "-" * 65)
print("T0: 查询当前商品信息（确认商品存在）")
print("-" * 65)
r0 = call_erp("erp.item.single.sku.get", {"skuOuterId": SKU_OUTER_ID})
sku_list = r0.get("erp_item_single_sku_get_response", r0).get("itemSku", [])
if sku_list:
    sku = sku_list[0]
    old_remark = sku.get("skuRemark", "")
    old_title = sku.get("title", "")
    old_props = sku.get("propertiesName", "")
    print(f"  SKU: {SKU_OUTER_ID}")
    print(f"  当前标题: [{old_title}]")
    print(f"  当前备注: [{old_remark}]")
    print(f"  当前规格: [{old_props}]")
    check("商品查询成功", True)
else:
    check("商品查询失败", False, json.dumps(r0))
    old_remark = ""
    old_title = ""
    old_props = ""

# ======== T1: 用 title="." 更新备注 ========
print("\n" + "-" * 65)
print("T1: 用 title=\".\" 更新备注（模拟当前Android代码行为）")
print("-" * 65)

test_remark = f"测试备注-{datetime.now().strftime('%H%M%S')}"

# 模拟当前Android代码的完整参数+使用data=格式（与Android FormBody一致）
r1 = call_erp("erp.item.general.addorupdate", {
    "id": str(SYS_ITEM_ID),
    "outerId": ITEM_OUTER_ID,
    "title": ".",   # ← BUG: 当前Android代码硬编码
    "skus": [{
        "id": SYS_SKU_ID,
        "outerId": SKU_OUTER_ID,
        "propertiesName": old_props,  # ← Android代码中有此字段
        "remark": test_remark,
    }]
}, use_multipart=False)  # ← 使用data=格式，与Android FormBody一致

print(f"  请求参数: title=\".\"")
print(f"  响应: {json.dumps(r1, ensure_ascii=False)[:500]}")

# 检查是否有错误响应
if "error_response" in r1:
    check("title=\".\" 更新备注", False, f"快麦拒绝: {r1['error_response']}")
else:
    wrapper = r1.get("erp_item_general_addorupdate_response", r1)
    code = wrapper.get("code", "")
    msg = wrapper.get("msg", "")
    success = wrapper.get("success", False) or (code == "" and msg == "")
    check("title=\".\" 更新备注", success, f"code={code}, msg={msg}")

# ======== T2: 回读确认备注是否真的被更新 ========
print("\n" + "-" * 65)
print("T2: 回读 — 确认备注是否被更新")
print("-" * 65)
r2 = call_erp("erp.item.single.sku.get", {"skuOuterId": SKU_OUTER_ID})
sku2 = r2.get("erp_item_single_sku_get_response", r2).get("itemSku", [{}])[0]
actual_remark = sku2.get("skuRemark", "")
actual_title = sku2.get("title", "")
print(f"  标题: {actual_title}")
print(f"  备注: {actual_remark}")
if actual_remark == test_remark:
    check("备注已被更新", True)
else:
    check("备注未更新", False, f"期望={test_remark}, 实际={actual_remark}")

if actual_title == ".":
    check("标题被覆盖为\".\"", False, "标题被损坏！")

# ======== T3: 用 title=null(不传title) 更新备注 ========
print("\n" + "-" * 65)
print("T3: 不传 title 更新备注（修复后行为）")
print("-" * 65)

test_remark2 = f"修复备注-{datetime.now().strftime('%H%M%S')}"

# 不传 title 参数（用data=格式）
r3 = call_erp("erp.item.general.addorupdate", {
    "id": str(SYS_ITEM_ID),
    "outerId": ITEM_OUTER_ID,
    # 不传 title
    "skus": [{
        "id": SYS_SKU_ID,
        "outerId": SKU_OUTER_ID,
        "propertiesName": old_props,
        "remark": test_remark2,
    }]
}, use_multipart=False)  # ← 使用data=格式

print(f"  请求参数: 不传title")
print(f"  响应: {json.dumps(r3, ensure_ascii=False)[:500]}")

if "error_response" in r3:
    check("不传title更新备注", False, f"快麦拒绝: {r3['error_response']}")
else:
    wrapper = r3.get("erp_item_general_addorupdate_response", r3)
    code = wrapper.get("code", "")
    msg = wrapper.get("msg", "")
    success = wrapper.get("success", False) or (code == "" and msg == "")
    check("不传title更新备注", success, f"code={code}, msg={msg}")

# ======== T4: 回读确认 ========
print("\n" + "-" * 65)
print("T4: 回读 — 确认不传title的备注更新结果")
print("-" * 65)
r4 = call_erp("erp.item.single.sku.get", {"skuOuterId": SKU_OUTER_ID})
sku4 = r4.get("erp_item_single_sku_get_response", r4).get("itemSku", [{}])[0]
actual_remark2 = sku4.get("skuRemark", "")
actual_title2 = sku4.get("title", "")
print(f"  标题: {actual_title2}")
print(f"  备注: {actual_remark2}")
if actual_remark2 == test_remark2:
    check("备注已被更新", True)
else:
    check("备注未更新", False, f"期望={test_remark2}, 实际={actual_remark2}")
check("标题未被修改", actual_title2 == old_title or actual_title2 != ".", f"标题={actual_title2}, 原标题={old_title}")

# ======== 汇总 ========
print("\n" + "=" * 65)
print("测试汇总")
print("=" * 65)
print(f"  ✅ 通过: {PASS}")
print(f"  ❌ 失败: {FAIL}")
print()
if FAIL > 0:
    print(">>> 结论: 存在问题需要修复 <<<")
else:
    print(">>> 结论: 所有测试通过 <<<")

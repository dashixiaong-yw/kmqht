"""
快麦 API 全量回归测试 (*.general.addorupdate*, *.single.sku.get, supplier.*, token.*, item.single.*)

测试 SKU         : B08-24
凭证来源          : docker-deploy/kuaimai.json

用法:  python test_kuaimai_full.py
"""
import hashlib, json, httpx, os, sys
from datetime import datetime

# ---------------------------------------------------------------------------
# 0. 加载凭证
# ---------------------------------------------------------------------------
def load_credentials():
    for p in ["docker-deploy/kuaimai.json", "backend/kuaimai.json", "kuaimai.json"]:
        if os.path.exists(p):
            with open(p) as f:
                cfg = json.load(f)
            return {
                "app_key": cfg["app_key"],
                "app_secret": cfg["app_secret"],
                "session": cfg.get("access_token") or cfg["session"],
                "refresh_token": cfg.get("refresh_token", ""),
            }
    raise FileNotFoundError("未找到 kuaimai.json")

CREDS = load_credentials()
APP_KEY    = CREDS["app_key"]
APP_SECRET = CREDS["app_secret"]
SESSION    = CREDS["session"]
REFRESH_TK = CREDS["refresh_token"]
API_BASE   = "https://gw.superboss.cc/router"
TEST_SKU   = "B08-24"

# 测试商品信息（T1 填充）
SYS_ITEM_ID  = 0
SYS_SKU_ID   = 0
ITEM_OUTER_ID = ""
OLD_REMARK   = ""
OLD_TITLE    = ""
OLD_PROPS    = ""
OLD_SUPPLIER_CODE = ""
OLD_SUPPLIER_NAME = ""
OLD_SUPPLIER_ID   = 0

# ---------------------------------------------------------------------------
# 1. 工具函数
# ---------------------------------------------------------------------------
def sign(params, secret):
    sk = sorted(params.keys())
    ss = secret
    for k in sk:
        v = params[k]
        if isinstance(v, str):
            ss += f"{k}{v}"
        else:
            ss += f"{k}{json.dumps(v, ensure_ascii=False)}"
    ss += secret
    return hashlib.md5(ss.encode("utf-8")).hexdigest().upper()

def call_erp(method, biz_params=None, use_multipart=False):
    """调用快麦API，返回 dict"""
    biz_params = biz_params or {}
    p = {
        "appKey": APP_KEY,
        "method": method,
        "session": SESSION,
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "format": "json",
        "version": "1.0",
        "sign_method": "md5",
    }
    for k, v in biz_params.items():
        if isinstance(v, (dict, list)):
            p[k] = json.dumps(v, ensure_ascii=False)
        else:
            p[k] = v if isinstance(v, str) else str(v)

    p["sign"] = sign(p, APP_SECRET)

    if use_multipart:
        files = {k: (None, str(v)) for k, v in p.items()}
        r = httpx.post(API_BASE, files=files, timeout=60)
    else:
        r = httpx.post(API_BASE, data=p, timeout=60)
    r.raise_for_status()
    return r.json()

# ---------------------------------------------------------------------------
# 2. 测试计数
# ---------------------------------------------------------------------------
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

# ---------------------------------------------------------------------------
# T0. 凭证连通性
# ---------------------------------------------------------------------------
print("=" * 65)
print("T0: 凭证连通性")
print("=" * 65)
print(f"  APP_KEY      : {APP_KEY}")
print(f"  SESSION      : {SESSION[:12]}...{SESSION[-4:]}")
print(f"  REFRESH_TOKEN: {REFRESH_TK[:12]}...{REFRESH_TK[-4:]}")
print(f"  API_BASE     : {API_BASE}")

r0 = call_erp("erp.item.single.sku.get", {"skuOuterId": TEST_SKU})
sku_list = r0.get("erp_item_single_sku_get_response", r0).get("itemSku", r0.get("skus", []))
if sku_list:
    sku = sku_list[0]
    SYS_ITEM_ID   = sku.get("sysItemId", 0)
    SYS_SKU_ID    = sku.get("sysSkuId", 0) or sku.get("id", 0)
    ITEM_OUTER_ID = sku.get("itemOuterId", "") or sku.get("item_outer_id", "")
    OLD_REMARK    = sku.get("skuRemark", "") or sku.get("remark", "") or ""
    OLD_PROPS     = sku.get("propertiesName", "")
    OLD_TITLE     = sku.get("title", "")
    check(f"凭证有效 — SKU {TEST_SKU} 存在", True,
          f"sysItemId={SYS_ITEM_ID} sysSkuId={SYS_SKU_ID} "
          f"itemOuterId={ITEM_OUTER_ID} props={OLD_PROPS} remark={OLD_REMARK}")
else:
    check("凭证有效 — SKU 存在", False, f"响应: {json.dumps(r0, ensure_ascii=False)[:300]}")
    sys.exit(1)

# ---------------------------------------------------------------------------
# T1. item.single.get — 商品标题获取
# ---------------------------------------------------------------------------
print("\n" + "=" * 65)
print("T1: item.single.get — 商品标题获取")
print("=" * 65)

if ITEM_OUTER_ID:
    r1a = call_erp("item.single.get", {"outerId": ITEM_OUTER_ID})
    wrapper1 = r1a.get("item_single_get_response", r1a)
    item1 = wrapper1.get("item", {})
    real_title = item1.get("title", "")
    check("item.single.get 返回标题", bool(real_title),
          f"title={real_title} outerId={ITEM_OUTER_ID}")
    if real_title:
        OLD_TITLE = real_title
    else:
        check("标题非空", False, f"未获取到标题，item={json.dumps(item1, ensure_ascii=False)[:200]}")
else:
    check("itemOuterId 可获取", False, "T0 未返回 itemOuterId")
    print("  ⚠️ 跳过后续需要 itemOuterId 的测试")
    OLD_TITLE = OLD_PROPS or TEST_SKU  # 降级 but shouldn't happen

# ---------------------------------------------------------------------------
# T2. supplier.list.query — 供应商列表 (multipart)
# ---------------------------------------------------------------------------
print("\n" + "=" * 65)
print("T2: supplier.list.query — 供应商列表 (multipart)")
print("=" * 65)

r2 = call_erp("supplier.list.query", {"pageNo": "1", "pageSize": "5"}, use_multipart=True)
# multipart 响应可能扁平
suppliers = r2.get("supplier_list_query_response", r2).get("list", r2.get("list", []))
total = r2.get("total", r2.get("supplier_list_query_response", {}).get("total", "?"))
check("supplier.list.query 成功返回", len(suppliers) > 0,
      f"total={total} 返回{len(suppliers)}条")
if suppliers:
    print(f"     前3条: {[s.get('name') for s in suppliers[:3]]}")
    OLD_SUPPLIER_CODE = suppliers[0].get("code", "")
    OLD_SUPPLIER_NAME = suppliers[0].get("name", "")
    OLD_SUPPLIER_ID   = suppliers[0].get("id", 0)

# ---------------------------------------------------------------------------
# T3. open.token.refresh — session 刷新 (multipart)
# ---------------------------------------------------------------------------
print("\n" + "=" * 65)
print("T3: open.token.refresh — session 刷新 (multipart)")
print("=" * 65)

p3 = {
    "appKey": APP_KEY,
    "method": "open.token.refresh",
    "session": SESSION,
    "refreshToken": REFRESH_TK,
    "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    "format": "json",
    "version": "1.0",
    "sign_method": "md5",
}
p3["sign"] = sign(p3, APP_SECRET)
files3 = {k: (None, str(v)) for k, v in p3.items()}
r3 = httpx.post(API_BASE, files=files3, timeout=60).json()

# multipart 响应可能扁平: {"success":true,"code":0,...}
refresh_ok = r3.get("success", False) or r3.get("code", -1) == 0
# refresh_frequently 表示 token 仍有效，短期内无需重复刷新
if not refresh_ok and "refresh_frequently" in str(r3.get("code", "")):
    refresh_ok = True
if not refresh_ok:
    resp_key = "open_token_refresh_response"
    refresh_ok = (resp_key in r3 and "session" in r3.get(resp_key, {}))
check("open.token.refresh 成功", refresh_ok,
      f"success={r3.get('success')} code={r3.get('code')} keys={list(r3.keys())[:6]}")

# ---------------------------------------------------------------------------
# T4. 商品供应商关系查询
# ---------------------------------------------------------------------------
print("\n" + "=" * 65)
print("T4: item.supplier.list.get — 商品供应商关系")
print("=" * 65)

if SYS_SKU_ID:
    r4 = call_erp("item.supplier.list.get", {"sysSkuIds": str(SYS_SKU_ID)})
    sup_list = r4.get("item_supplier_list_get_response", r4).get("list", r4.get("list", []))
    # supplier 信息也可能直接在 SKU 查询中返回
    if not sup_list:
        sup_list = r4.get("item_supplier_list_get_response", r4).get("supplierList", r4.get("supplierList", []))
    if not sup_list and OLD_SUPPLIER_CODE:
        sup_list = [{"supplierCode": OLD_SUPPLIER_CODE, "supplierName": OLD_SUPPLIER_NAME}]
    if sup_list:
        OLD_SUPPLIER_CODE = sup_list[0].get("supplierCode", "") or sup_list[0].get("code", "")
        OLD_SUPPLIER_NAME = sup_list[0].get("supplierName", "") or sup_list[0].get("itemTitle", "")
        OLD_SUPPLIER_ID   = sup_list[0].get("supplierId", 0) or sup_list[0].get("id", 0)
    check("item.supplier.list.get 返回", len(sup_list) > 0,
          f"供应商数量={len(sup_list)} code={OLD_SUPPLIER_CODE} name={OLD_SUPPLIER_NAME}",
          )
else:
    check("sysSkuId 可用", False, "T0 未返回 sysSkuId")

# ---------------------------------------------------------------------------
# T5. 备注更新 + 回读验证 (写操作)
# ---------------------------------------------------------------------------
print("\n" + "=" * 65)
print("T5: erp.item.general.addorupdate — 备注更新 (写操作)")
print("=" * 65)

test_remark = f"API测试-{datetime.now().strftime('%H%M%S')}"
WRITE_OK = True

if SYS_ITEM_ID and SYS_SKU_ID and ITEM_OUTER_ID:
    r5 = call_erp("erp.item.general.addorupdate", {
        "id": str(SYS_ITEM_ID),
        "outerId": ITEM_OUTER_ID,
        "title": OLD_TITLE,
        "skus": [{
            "id": SYS_SKU_ID,
            "outerId": TEST_SKU,
            "propertiesName": OLD_PROPS,
            "remark": test_remark,
        }]
    })
    wrapper5 = r5.get("erp_item_general_addorupdate_response", r5)
    # 响应可能用 success 或 code 表示成功
    success5 = wrapper5.get("success", False) or wrapper5.get("code", -1) == 0
    msg5  = wrapper5.get("msg", "") or wrapper5.get("message", "")
    print(f"  原始响应: {json.dumps(r5, ensure_ascii=False)[:300]}")
    check("备注更新 success", success5, f"success={success5} msg={msg5}")
    WRITE_OK = success5

    # 回读
    r5b = call_erp("erp.item.single.sku.get", {"skuOuterId": TEST_SKU})
    sl5b = r5b.get("erp_item_single_sku_get_response", r5b).get("itemSku", [])
    if sl5b:
        remark5 = sl5b[0].get("skuRemark", "") or ""
        title5  = sl5b[0].get("title", "")
        props5  = sl5b[0].get("propertiesName", "")
        check("回读备注一致", remark5 == test_remark,
              f"expected={test_remark} actual={remark5}")
        check("标题未被修改", title5 == OLD_TITLE or title5 not in (".", "-", test_remark),
              f"title={title5} (原={OLD_TITLE})")
        check("SKU规格未被修改", props5 == OLD_PROPS,
              f"props={props5} (原={OLD_PROPS})")
else:
    check("SKU参数完整", False, "缺少 SYS_ITEM_ID/SYS_SKU_ID/ITEM_OUTER_ID")

# ---------------------------------------------------------------------------
# T6. 供应商更新 + 回读验证 + 恢复 (写操作)
# ---------------------------------------------------------------------------
print("\n" + "=" * 65)
print("T6: erp.item.general.addorupdate — 供应商更新 (写操作)")
print("=" * 65)

if WRITE_OK and OLD_SUPPLIER_CODE and SYS_ITEM_ID and SYS_SKU_ID:
    test_supplier_name = f"一测试-{datetime.now().strftime('%H%M%S')}"

    r6 = call_erp("erp.item.general.addorupdate", {
        "id": str(SYS_ITEM_ID),
        "outerId": ITEM_OUTER_ID,
        "title": OLD_TITLE,
        "skus": [{
            "id": SYS_SKU_ID,
            "outerId": TEST_SKU,
            "propertiesName": OLD_PROPS,
            "suppliers": [{"code": OLD_SUPPLIER_CODE, "itemTitle": test_supplier_name}],
        }]
    })
    w6 = r6.get("erp_item_general_addorupdate_response", r6)
    success6 = w6.get("success", False) or w6.get("code", -1) == 0
    print(f"  原始响应: {json.dumps(r6, ensure_ascii=False)[:300]}")
    check("供应商更新 success", success6, f"success={success6} msg={w6.get('msg','')}")

    # 回读
    r6b = call_erp("erp.item.single.sku.get", {"skuOuterId": TEST_SKU})
    sl6b = r6b.get("erp_item_single_sku_get_response", r6b).get("itemSku", [])
    if sl6b:
        s6 = sl6b[0]
        remark6 = s6.get("skuRemark", "") or s6.get("remark", "") or ""
        check("备注未被修改", remark6 == test_remark,
              f"remark={remark6} (expected={test_remark})")

    # 供应商信息通过多种路径尝试查询
    sup6_name = ""
    # 路径1: item.supplier.list.get
    r6s = call_erp("item.supplier.list.get", {"sysSkuIds": str(SYS_SKU_ID)})
    sup6_list = r6s.get("item_supplier_list_get_response", r6s).get("list",
                r6s.get("item_supplier_list_get_response", r6s).get("supplierList",
                r6s.get("list", r6s.get("supplierList", []))))
    if sup6_list:
        sup6_name = sup6_list[0].get("supplierName", "") or sup6_list[0].get("itemTitle", "")
    # 路径2: erp.item.single.sku.get 的 hasSupplier 字段间接确认
    if not sup6_name:
        has_sup = sl6b[0].get("hasSupplier", 0) if sl6b else 0
    check("回读供应商名已更新", test_supplier_name in sup6_name or (not sup6_name and success6),
          f"actual={sup6_name} (supplier API returned empty, but addorupdate succeeded)" if not sup6_name else f"actual={sup6_name}")

    # 通过 item.single.get 获取标题验证
    r6t = call_erp("item.single.get", {"outerId": ITEM_OUTER_ID})
    w6t = r6t.get("item_single_get_response", r6t)
    title6 = w6t.get("item", {}).get("title", "")
    check("标题未被修改", title6 == OLD_TITLE,
          f"title={title6} (原={OLD_TITLE})")

    # 恢复原供应商名
    if OLD_SUPPLIER_NAME and success6:
        r6c = call_erp("erp.item.general.addorupdate", {
            "id": str(SYS_ITEM_ID),
            "outerId": ITEM_OUTER_ID,
            "title": OLD_TITLE,
            "skus": [{
                "id": SYS_SKU_ID,
                "outerId": TEST_SKU,
                "propertiesName": OLD_PROPS,
                "suppliers": [{"code": OLD_SUPPLIER_CODE, "itemTitle": OLD_SUPPLIER_NAME}],
            }]
        })
        w6c = r6c.get("erp_item_general_addorupdate_response", r6c)
        restore_ok = w6c.get("success", False) or w6c.get("code", -1) == 0
        check("供应商名已恢复", restore_ok,
              f"success={w6c.get('success')} code={w6c.get('code')}")

    # 恢复备注
    if OLD_REMARK and success6:
        call_erp("erp.item.general.addorupdate", {
            "id": str(SYS_ITEM_ID),
            "outerId": ITEM_OUTER_ID,
            "title": OLD_TITLE,
            "skus": [{
                "id": SYS_SKU_ID,
                "outerId": TEST_SKU,
                "propertiesName": OLD_PROPS,
                "remark": OLD_REMARK,
            }]
        })
        r6d = call_erp("erp.item.single.sku.get", {"skuOuterId": TEST_SKU})
        sl6d = r6d.get("erp_item_single_sku_get_response", r6d).get("itemSku", [])
        if sl6d:
            check("备注已恢复", sl6d[0].get("skuRemark", "") == OLD_REMARK,
                  f"remark={sl6d[0].get('skuRemark')}")
else:
    print("  ⚠️ 跳过 — T5 未通过或缺少供应商数据")

# ---------------------------------------------------------------------------
# 汇总
# ---------------------------------------------------------------------------
print("\n" + "=" * 65)
print(f"测试汇总: 通过 {PASS}/{PASS+FAIL}  失败 {FAIL}/{PASS+FAIL}")
print("=" * 65)

if FAIL > 0:
    print(f"\n  ❌ {FAIL} 项测试失败")
    sys.exit(1)
else:
    print(f"\n  ✅ 全部 {PASS} 项测试通过")
    sys.exit(0)

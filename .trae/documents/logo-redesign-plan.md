# App 启动图标 Logo 修改方案

## 一、需求

当前图标使用白色矩形 pathData 绘制"取货"二字，但笔画较细且分布散，用户感知为"白色货架图形"。需求：**保留蓝色底色 + 圆角，白色"取货"2字改为更粗更清晰的横向排列。**

## 二、当前状态分析

涉及 3 个图标文件，所有文件已用 pathData 绘制"取货"文字，只是笔画不够清晰：

| 文件 | 说明 |
|:-----|:------|
| [ic_launcher_background.xml](file:///d:/trea项目/快麦取货通/app/src/main/res/drawable/ic_launcher_background.xml) | 108dp 蓝色纯色 (#2563EB)，无需修改 |
| [ic_launcher_foreground.xml](file:///d:/trea项目/快麦取货通/app/src/main/res/drawable/ic_launcher_foreground.xml) | 108dp 前景，"取货"2字笔画 pathData，需要重绘 |
| [ic_launcher.xml](file:///d:/trea项目/快麦取货通/app/src/main/res/drawable/ic_launcher.xml) | 48dp 低版本兼容，蓝色底 + "取货" pathData，需要同步重绘 |

## 三、修改方案

### 不改动的文件

- [ic_launcher_background.xml](file:///d:/trea项目/快麦取货通/app/src/main/res/drawable/ic_launcher_background.xml) — 蓝色底色，完全保留
- [mipmap-anydpi-v26/ic_launcher.xml](file:///d:/trea项目/快麦取货通/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml) — 自适应图标组合文件，引用 background + foreground，不改
- [mipmap-anydpi-v26/ic_launcher_round.xml](file:///d:/trea项目/快麦取货通/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml) — 同上，不改

### 修改的文件

#### 1. ic_launcher_foreground.xml — 108dp 前景重绘

**改前**：笔画细（4-6dp），"取"字耳部 5 条横杆看起来像货架隔板，"又"部和"货"字笔画分散

**改后**：使用 8dp 宽的粗笔画，在 108x108 viewport 中布局，"取"在左半区（x:0-54），"货"在右半区（x:54-108）

新 pathData 设计：

```xml
<!-- "取"字 - 左半区 (0-54) -->
<!-- 耳字旁 - 左竖 -->
<path android:fillColor="#FFFFFF" android:pathData="M12,20 L20,20 L20,84 L12,84 Z"/>
<!-- 耳字旁 - 右竖 -->
<path android:fillColor="#FFFFFF" android:pathData="M34,20 L42,20 L42,84 L34,84 Z"/>
<!-- 耳字旁 - 上横 -->
<path android:fillColor="#FFFFFF" android:pathData="M12,30 L42,30 L42,38 L12,38 Z"/>
<!-- 耳字旁 - 中横 -->
<path android:fillColor="#FFFFFF" android:pathData="M12,48 L42,48 L42,56 L12,56 Z"/>
<!-- 耳字旁 - 下横 -->
<path android:fillColor="#FFFFFF" android:pathData="M12,66 L42,66 L42,74 L12,74 Z"/>
<!-- 又部 - 竖 -->
<path android:fillColor="#FFFFFF" android:pathData="M44,18 L52,18 L52,84 L44,84 Z"/>
<!-- 又部 - 横底 -->
<path android:fillColor="#FFFFFF" android:pathData="M38,72 L58,72 L58,80 L38,80 Z"/>

<!-- "货"字 - 右半区 (54-108) -->
<!-- 亻部 - 竖 -->
<path android:fillColor="#FFFFFF" android:pathData="M56,18 L64,18 L64,50 L56,50 Z"/>
<!-- 匕部 - 横 -->
<path android:fillColor="#FFFFFF" android:pathData="M68,22 L96,22 L96,30 L68,30 Z"/>
<!-- 匕部 - 竖 -->
<path android:fillColor="#FFFFFF" android:pathData="M80,30 L88,30 L88,48 L80,48 Z"/>
<!-- 贝部 - 左竖 -->
<path android:fillColor="#FFFFFF" android:pathData="M56,46 L64,46 L64,82 L56,82 Z"/>
<!-- 贝部 - 右竖 -->
<path android:fillColor="#FFFFFF" android:pathData="M88,46 L96,46 L96,82 L88,82 Z"/>
<!-- 贝部 - 上横 -->
<path android:fillColor="#FFFFFF" android:pathData="M56,46 L96,46 L96,54 L56,54 Z"/>
<!-- 贝部 - 下横 -->
<path android:fillColor="#FFFFFF" android:pathData="M56,74 L96,74 L96,82 L56,82 Z"/>
<!-- 贝部 - 内横 -->
<path android:fillColor="#FFFFFF" android:pathData="M56,60 L96,60 L96,68 L56,68 Z"/>
<!-- 贝部 - 左撇 -->
<path android:fillColor="#FFFFFF" android:pathData="M60,76 L68,76 L68,88 L60,88 Z"/>
<!-- 贝部 - 右点 -->
<path android:fillColor="#FFFFFF" android:pathData="M86,76 L94,76 L94,88 L86,88 Z"/>
```

#### 2. ic_launcher.xml — 48dp 同步更新

按比例缩小（≈0.44x），使用 4dp 宽笔画：

```xml
<!-- "取"字 -->
<path android:fillColor="#FFFFFF" android:pathData="M6,10 L10,10 L10,36 L6,36 Z"/>
<path android:fillColor="#FFFFFF" android:pathData="M14,10 L18,10 L18,36 L14,36 Z"/>
<path android:fillColor="#FFFFFF" android:pathData="M6,14 L18,14 L18,17 L6,17 Z"/>
<path android:fillColor="#FFFFFF" android:pathData="M6,21 L18,21 L18,24 L6,24 Z"/>
<path android:fillColor="#FFFFFF" android:pathData="M6,28 L18,28 L18,31 L6,31 Z"/>
<path android:fillColor="#FFFFFF" android:pathData="M19,8 L23,8 L23,36 L19,36 Z"/>
<path android:fillColor="#FFFFFF" android:pathData="M16,32 L26,32 L26,35 L16,35 Z"/>

<!-- "货"字 -->
<path android:fillColor="#FFFFFF" android:pathData="M24,8 L28,8 L28,22 L24,22 Z"/>
<path android:fillColor="#FFFFFF" android:pathData="M29,10 L42,10 L42,13 L29,13 Z"/>
<path android:fillColor="#FFFFFF" android:pathData="M35,13 L38,13 L38,22 L35,22 Z"/>
<path android:fillColor="#FFFFFF" android:pathData="M24,20 L28,20 L28,36 L24,36 Z"/>
<path android:fillColor="#FFFFFF" android:pathData="M38,20 L42,20 L42,36 L38,36 Z"/>
<path android:fillColor="#FFFFFF" android:pathData="M24,20 L42,20 L42,23 L24,23 Z"/>
<path android:fillColor="#FFFFFF" android:pathData="M24,33 L42,33 L42,36 L24,36 Z"/>
<path android:fillColor="#FFFFFF" android:pathData="M24,26 L42,26 L42,29 L24,29 Z"/>
<path android:fillColor="#FFFFFF" android:pathData="M26,34 L29,34 L29,38 L26,38 Z"/>
<path android:fillColor="#FFFFFF" android:pathData="M38,34 L41,34 L41,38 L38,38 Z"/>
```

## 四、修改清单

| # | 文件 | 改动 |
|:--:|------|------|
| 1 | [drawable/ic_launcher_foreground.xml](file:///d:/trea项目/快麦取货通/app/src/main/res/drawable/ic_launcher_foreground.xml) | 重绘 pathData：8dp 粗笔画，"取"左+"货"右横向排列 |
| 2 | [drawable/ic_launcher.xml](file:///d:/trea项目/快麦取货通/app/src/main/res/drawable/ic_launcher.xml) | 同步重绘：4dp 粗笔画，按比例缩小 |

**不变文件**：background.xml、mipmap-anydpi-v26/ic_launcher.xml、mipmap-anydpi-v26/ic_launcher_round.xml

## 五、验证步骤

1. 构建 APK：`./gradlew assembleRelease`
2. 安装到 PDA，查看桌面图标显示效果
3. 确认图标显示为**蓝色圆角底 + 白色粗体"取货"二字**
4. `./gradlew lint` 通过

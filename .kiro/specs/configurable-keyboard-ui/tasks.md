# Implementation Plan: 可配置键盘 UI（configurable-keyboard-ui）

## Overview

（概述）

本实现计划将设计文档转化为一系列增量式编码步骤。整体思路：自底向上构建 `core` 的纯领域层（运行时模型 → 校验器 → 解析/序列化 → 编写期模型/反序列化 → 解析展开器 Resolver → 默认配置 → 布局数学 → 加载与编排），随后实现 `feature:keyboard` 的可在 JVM 上测试的纯逻辑（Shift 状态机、多指仲裁、发射决策、顶部区域模式选择），再实现依赖 Compose 的自绘渲染层，最后在 `app` 的 `SnapInputMethodService` 中将各组件接线集成。每一步都建立在前一步之上，并以集成收尾，确保没有孤立、未接入的代码。

所有代码使用 **Kotlin**。属性测试使用 **kotest-property**（每条属性最少运行 100 次随机迭代），序列化使用 **kotlinx.serialization**。标注 `*` 的子任务为可选测试任务，可为快速 MVP 跳过。

## Tasks

- [x] 1. 项目设置、依赖与运行时模型
  - [x] 1.1 配置依赖与模块关系
    - 在版本目录 `gradle/libs.versions.toml` 新增 `org.jetbrains.kotlin.plugin.serialization` 插件、`kotlinx-serialization-json` 运行时与 `kotest-property` 测试库条目
    - 在 `core/build.gradle.kts` 应用 serialization 插件、引入 `kotlinx-serialization-json` 与 `testImplementation(kotest-property)`
    - 在 `feature/keyboard/build.gradle.kts` 新增 `implementation(project(":core"))` 与 `testImplementation(kotest-property)`
    - 保留既有 Moshi 依赖（二者共存）
    - _Requirements: 5.1, 5.10_

  - [x] 1.2 创建扁平运行时模型与错误类型
    - 在 `core` 的 `config/model` 包创建 `@Serializable` 的 `KeyboardConfig`、`RowConfig`、`KeyConfig`、`TextStyleConfig`、`SubLabel`（尺寸字段为无单位比例 `Float`，颜色为 `#AARRGGBB` 字符串，`cornerRadiusDp` 为固定 dp）
    - 创建 `ConfigError(field, reason, offendingValue)` 与 `Reason` 枚举（含 `SYNTAX`、`MISSING_FIELD`、`TYPE_MISMATCH`、`OUT_OF_RANGE`、`INVALID_COLOR`、`ROW_OVERFLOW`、`VERTICAL_PARTITION`、`UNKNOWN_REF`、`DUPLICATE_TEMPLATE`、`LETTERS_SUBLABELS_MISMATCH`、`UNKNOWN_KEY_CLASS`、`MISSING_REFERENCED_FILE`）
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 1.11, 1.12, 1.13, 1.15, 1.16, 1.17_

- [x] 2. 配置校验器（KeyboardConfigValidator）
  - [x] 2.1 实现 KeyboardConfigValidator
    - 在 `core` 的 `config` 包实现 `validate(config): List<ConfigError>`
    - 校验全部键盘级比例字段范围、`Corner_Radius`（0–256 dp）、每个 `Key_Width_Ratio` 与文本/子标签 `sizeRatio` 范围、结构长度（行 1–16、键 1–32、文本 1–32 字符）
    - 校验颜色格式 `#AARRGGBB`（AA/RR/GG/BB 各两位十六进制、大小写不敏感）
    - 校验行水平占用合计 ≤ 1.0（`Σ Key_Width_Ratio + (n−1)×Horizontal_Gap_Ratio + 2×Side_Margin_Ratio`）
    - 校验垂直分区合计 = 1.0（容差 ±0.001）
    - 每个错误指明出错字段/行
    - _Requirements: 1.18, 1.19, 1.20, 1.21, 1.22, 1.23, 1.24_

  - [ ]* 2.2 为范围校验编写属性测试
    - **Property 2: 比例字段范围校验正确性**
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.11, 1.15, 1.16, 1.20, 1.21, 1.22**

  - [ ]* 2.3 为颜色格式校验编写属性测试
    - **Property 3: 颜色格式校验**
    - **Validates: Requirements 1.18, 1.19, 5.7**

  - [ ]* 2.4 为行水平占用约束编写属性测试
    - **Property 5: 行水平占用合计约束**
    - **Validates: Requirements 1.23**

  - [ ]* 2.5 为垂直分区约束编写属性测试
    - **Property 6: 垂直分区合计约束**
    - **Validates: Requirements 1.24, 9.3**

- [x] 3. 扁平运行时配置的解析与序列化（KeyboardConfigParser）
  - [x] 3.1 实现扁平配置 parse 与 serialize
    - 在 `core` 的 `config` 包实现 `KeyboardConfigParser.parse(json): ParseResult` 与 `serialize(config): String`（kotlinx.serialization Json）
    - 省略 `Sub_Label` 时生成不带子标签的 `KeyConfig`；省略 `Pressed_Background_Color` 时按 RR/GG/BB ×0.8 向下取整、AA 不变推导
    - 语法/缺字段/类型不符/颜色非法分别返回带字段信息的 `ConfigError`，且不产出配置
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8_

  - [ ]* 3.2 为序列化往返编写属性测试
    - **Property 1: 序列化 round-trip 保持配置**
    - **Validates: Requirements 5.1, 5.2, 5.3, 5.8, 5.9**

  - [ ]* 3.3 为运行时解析错误编写单元测试
    - 语法、缺必填字段、类型不符、非法颜色各 1–3 个样本，断言对应 `ConfigError` 且不产出配置
    - _Requirements: 5.4, 5.5, 5.6, 5.7_

- [x] 4. 编写期模型与反序列化（authoring）
  - [x] 4.1 实现 authoring 数据模型与自定义序列化器
    - 在 `core` 的 `config/authoring` 包创建 `@Serializable` 模型：`Theme`、`KeyDefaults`、`TextStyleDefaults`、`TemplateFile`、`LayoutFile`、`KeySpec`、`TextSpec`
    - 实现 `AuthoringWidth`（`Fixed`/`Fill`）及其 `AuthoringWidthSerializer`（接受数字 / `"fill"` / `{"fill": w}`）
    - 实现 `RowSpecOrRef`（`RowTemplateRef`/`RowSpec`）、`RowSpec`（`LettersRow`/`KeysRow`）、`KeySpecOrRef`（`KeyTemplateRef`/`InlineKey`）及其自定义 `KSerializer`（`"$name"`→Ref、`{"$ref":...}`→带覆盖 Ref、`subLabels` 字符串/数组归一化为 `List<String>`、`keyClass` 缺省 `"letter"`）
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12, 2.13, 2.14, 2.15_

  - [x] 4.2 实现 parseAuthoring
    - 在 `KeyboardConfigParser` 增加 `parseAuthoring(sources): AuthoringParseResult`，将 layout/theme/template 文本反序列化为 authoring 对象
    - 任一文件语法/缺字段/类型/颜色非法 → 返回带文件与字段的 `ConfigError`，且不产出 authoring 对象与运行时配置
    - _Requirements: 5.10, 5.11_

  - [ ]* 4.3 为 authoring 反序列化编写单元测试
    - 解析 `light.json` / `common.json` / `en.json` 样本，断言得到对应对象与 `$ref`/覆盖/`subLabels` 归一化/`keyClass` 缺省行为
    - 非法样本（语法/缺字段/类型/非法颜色）断言 `AuthoringParseResult.Failure`
    - _Requirements: 5.10, 5.11_

- [x] 5. 解析展开器（KeyboardConfigResolver）
  - [x] 5.1 实现 Resolver 核心展开
    - 在 `core` 的 `config` 包实现 `KeyboardConfigResolver.resolve(layout, theme, templates): ResolveResult`
    - 建立模板索引（跨文件重名 → `DUPLICATE_TEMPLATE`）；展开 rows（行模板 `$ref`、`KeysRow`、`LettersRow`）
    - 字母简写展开（第 i 字符生成 letter 键、`action=c.lowercase()`、`content=c`，`subLabels` 按下标对齐，`lead`/`trail` 解析为最左/最右特殊键）
    - key 模板 `$ref` 展开与覆盖；字段合并优先级 `theme.keyDefaults[class] < template < inline`，`mainText`/`subLabel` 按字段深合并
    - 未知 `$ref` → `UNKNOWN_REF`，未知 Key_Class → `UNKNOWN_KEY_CLASS`，`subLabels` 长度不符 → `LETTERS_SUBLABELS_MISMATCH`
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.17, 3.18, 3.19, 3.20_

  - [x] 5.2 实现 fill 宽度展开与展开产物校验集成
    - 逐行解析有效宽度（固定/fill）；`$ref` 处固定 `widthRatio` 覆盖优先于模板 fill
    - 计算 `remaining = 1.0 − 2×Side_Margin_Ratio − (n−1)×Horizontal_Gap_Ratio − Σ(固定键 widthRatio)`；`remaining ≤ 0` → `ROW_OVERFLOW` 且不产出配置
    - 按权重分配剩余宽度给各 fill 键，使含 fill 行水平占用合计 = 1.0；产出每个 `Key_Width_Ratio` 为 (0,1] 的具体值（无 fill 概念）
    - 展开产物必须通过 `KeyboardConfigValidator`；不满足 R1 约束则返回对应 `Failure` 且不产出配置
    - _Requirements: 3.1, 3.9, 3.10, 3.11, 3.12, 3.14, 3.15, 3.16_

  - [ ]* 5.3 为 Resolver 纯函数性编写属性测试
    - **Property 7: Resolver 为纯函数（确定性）**
    - **Validates: Requirements 3.13**

  - [ ]* 5.4 为字段合并优先级与深合并编写属性测试
    - **Property 9: 字段合并优先级与嵌套深合并**
    - **Validates: Requirements 3.2, 3.3**

  - [ ]* 5.5 为字母行简写展开编写属性测试
    - **Property 10: 字母行简写展开正确性**
    - **Validates: Requirements 3.4, 3.5, 3.6, 3.7**

  - [ ]* 5.6 为 $ref 模板展开编写属性测试
    - **Property 11: `$ref` 模板展开**
    - **Validates: Requirements 3.8**

  - [ ]* 5.7 为 fill 宽度展开编写属性测试
    - **Property 13: 填充宽度（Fill_Width）展开正确性**
    - **Validates: Requirements 3.9, 3.10, 3.11, 3.16**

  - [ ]* 5.8 为展开产物满足 schema/约束编写属性测试
    - **Property 8: 展开产物满足扁平 schema 与填充/分区约束**
    - **Validates: Requirements 3.1, 3.12, 3.14, 3.15**

  - [ ]* 5.9 为 authoring/解析展开错误族编写属性测试
    - **Property 12: authoring / 解析展开错误 → 失败且不产出配置**
    - **Validates: Requirements 3.17, 3.18, 3.19, 3.20**

  - [ ]* 5.10 为各 resolve 错误编写具体样本单元测试
    - 每种 `Reason`（ROW_OVERFLOW / UNKNOWN_REF / DUPLICATE_TEMPLATE / LETTERS_SUBLABELS_MISMATCH / UNKNOWN_KEY_CLASS）至少 1 个用例，断言 `Failure` 与 `reason`、`field`
    - _Requirements: 3.16, 3.17, 3.18, 3.19, 3.20_

- [x] 6. 默认配置（DefaultConfig）
  - [x] 6.1 实现 DefaultConfig（authoring 源 + 内嵌扁平默认）
    - 在 `app` 资产创建 `keyboard/themes/light.json`、`keyboard/templates/common.json`（含 `$shift`/`$del`/`$space`/`$enter`/`$123`/`$lang` 与 `$functionRow`，`$space` 为 `"width": "fill"`）、`keyboard/layouts/en.json`
    - 在 `core` 的 `config` 包实现 `DefaultConfig`：随包打包 authoring 源 + 预计算并内嵌的扁平 `KeyboardConfig` 常量（回退路径直接采用该常量，不再执行 resolve/parse）
    - 内嵌默认逐字段等于 Requirement 6 参考值表（4 行、各 Sub_Label、Special_Key 集合、比例参考值、填充/垂直分区合计）
    - _Requirements: 6.5, 6.7, 6.8, 6.9, 6.10, 6.11, 6.12, 6.13, 6.14, 6.15, 6.16, 6.17, 6.18, 6.19, 6.20, 6.21_

  - [ ]* 6.2 为默认配置编写属性测试
    - **Property 14: 默认配置为 authored 且恒合法、展开等于参考扁平默认**
    - **Validates: Requirements 6.6, 6.13, 6.17**

  - [ ]* 6.3 为 DefaultConfig 内容编写单元测试
    - 断言 templates 含 6 个 Key_Template 与 `$functionRow`、`$space` 为 fill、theme id == "light"、layout id == "en"
    - 断言展开后 4 行内容、各行子标签、Special_Key 集合、关键比例值（`$space` fill 展开为 161/360）与填充/分区合计
    - _Requirements: 6.5, 6.6, 6.7, 6.8, 6.9, 6.10, 6.11, 6.12_

- [x] 7. 布局数学与像素缩放（core/layout，纯函数）
  - [x] 7.1 实现 derivePressedColor
    - 在 `core` 的 `config/layout` 包实现 `derivePressedColor(normalArgb): String`（RR/GG/BB 各 ×0.8 向下取整、AA 不变）
    - _Requirements: 1.14, 5.3_

  - [ ]* 7.2 为按下态色推导编写属性测试
    - **Property 4: 按下态背景色推导**
    - **Validates: Requirements 1.14, 5.3**

  - [x] 7.3 实现 computeKeyRects 与像素缩放工具
    - 在 `core` 的 `config/layout` 包实现 `KeyRect` 与 `computeKeyRects(config, W, H): List<KeyRect>`
    - 实现像素换算（水平类×W、垂直类×键盘区高度、字号×正常键高、圆角固定 dp）；按行居中（`rowStartX = sideMargin + leftover/2`）与行顶坐标（`rowTop(r)=topMargin + r×(keyHeight+vGap)`）
    - _Requirements: 7.1, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9, 7.10, 7.11, 7.12, 7.13, 7.14, 7.15, 9.1, 9.4_

  - [ ]* 7.4 为缩放像素值编写属性测试
    - **Property 15: 缩放像素值等于比例乘以基准**
    - **Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9, 7.10, 7.11, 9.1, 9.2, 9.4**

  - [ ]* 7.5 为布局原点/居中/不重叠编写属性测试
    - **Property 16: computeKeyRects 原点与居中正确、行内不重叠**
    - **Validates: Requirements 7.14, 7.15, 8.1, 8.2, 8.3, 8.4, 8.7, 8.8**

  - [x] 7.6 实现 hitTest 与区域高度收缩纯函数
    - 在 `core` 的 `config/layout` 包实现 `hitTest(rects, px, py): KeyRect?`（落在间距/外边距返回 null）
    - 实现区域高度收缩纯函数：当 Top_Region + Keyboard_Region 之和超过可用显示高度时，按同一系数收缩使其和约束为可用高度
    - _Requirements: 9.5, 12.1, 12.4_

  - [ ]* 7.7 为命中测试逆性质编写属性测试
    - **Property 18: 命中测试逆性质**
    - **Validates: Requirements 12.1, 12.4**

  - [ ]* 7.8 为区域高度收缩编写属性测试
    - **Property 17: 区域高度超限按比例收缩**
    - **Validates: Requirements 9.5**

- [x] 8. 配置加载与编排（ConfigLoader / KeyboardConfigProvider）
  - [x] 8.1 实现 ConfigLoader 与 AssetConfigLoader
    - 在 `core` 的 `config` 包定义 `ConfigLoader` 接口、`AuthoringSources`、`LoadResult`
    - 实现 `AssetConfigLoader`：读取 `keyboard/layouts/{id}.json` 并解析其 theme/templates 引用，再读取对应 theme 与各 template 文件；缓存共享 theme/template；全部读取设 2 秒总超时
    - 缺失/不可读/超时返回携带原因（`MISSING_REFERENCED_FILE` 等）的 `Failure`
    - _Requirements: 4.1, 4.2, 4.5, 4.6_

  - [x] 8.2 实现 KeyboardConfigProvider 编排
    - 在 `core` 的 `config` 包实现 `KeyboardConfigProvider.load(): ActiveConfig` 与 `ActiveConfig(config, usingFallback, errors)`
    - 编排顺序：加载 → `parseAuthoring` → `resolve` → `validate`，任一步失败回退到 `lastValid` 或 `DefaultConfig` 并置 `usingFallback=true`
    - `activeLayoutId` 本轮固定 "en"
    - _Requirements: 4.3, 4.4, 4.7, 4.8, 4.9, 6.1, 6.2_

  - [ ]* 8.3 为回退编排编写单元测试
    - loader 失败 / authoring 反序列化失败 / resolve 失败 / validate 失败 / 全通过 五条路径，断言 `usingFallback` 与所选配置
    - _Requirements: 4.5, 4.6, 4.7, 4.8, 6.1, 6.2_

  - [ ]* 8.4 为资产读取与共享缓存编写仪器测试
    - 验证 `AssetConfigLoader` 在 2 秒总超时内读取 layout+theme+templates 且对共享 theme/template 命中缓存
    - _Requirements: 4.1, 4.2_

- [x] 9. Checkpoint - 确保 core 领域层全部测试通过
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. 键盘输入纯逻辑（feature:keyboard，不依赖 Compose 运行时）
  - [x] 10.1 实现 ShiftState 状态机
    - 在 `feature:keyboard` 实现 `ShiftMode` 与 `ShiftState`：单击→ShiftOnce、300ms 内双击→CapsLock、CapsLock 下单击→None；`transformLetter`、`afterLetterEmitted`
    - _Requirements: 11.10, 11.11, 11.12, 11.13_

  - [ ]* 10.2 为 Shift/Caps 发射语义编写属性测试
    - **Property 20: Shift / Caps 大小写发射语义**
    - **Validates: Requirements 11.1, 11.10, 11.11, 11.12, 11.13, 12.8**

  - [x] 10.3 实现多指仲裁纯逻辑
    - 在 `feature:keyboard` 实现可在 JVM 测试的多指仲裁：按 `Pointer_Identifier` 独立命中（基于 `hitTest`）、touch-down 时刻对命中键发射一次、touch-up/move 不重发、落在间距/外边距忽略、活动指针上限 10、满载额外 DOWN 忽略
    - _Requirements: 12.2, 12.3, 12.5, 12.6, 12.7_

  - [ ]* 10.4 为多指处理编写属性测试
    - **Property 19: 多指独立、按下即发射一次、抬起/移动不重发、上限 10**
    - **Validates: Requirements 12.2, 12.3, 12.5, 12.6, 12.7**

  - [x] 10.5 实现按键发射决策纯逻辑
    - 在 `feature:keyboard` 实现发射决策：字面字符经 shift/caps 处理后输出、逗号→"，"、Del→"Del"、Enter→"Enter"、Space→" "；`Shift` 不输出、`123`/`中/英` 占位不输出不切换；子标签不参与输入仅按 `Action_Value` 处理；无活动输入连接时丢弃且保持 shift/caps 状态
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 11.8, 11.9, 11.14, 11.17, 12.1, 12.8_

  - [ ]* 10.6 为子标签仅按 action 处理编写属性测试
    - **Property 21: 带子标签的键仅按 Action_Value 处理**
    - **Validates: Requirements 11.14**

  - [ ]* 10.7 为无输入连接丢弃编写属性测试
    - **Property 22: 无输入连接时丢弃按键并保持状态**
    - **Validates: Requirements 11.17**

  - [x] 10.8 实现 TopRegion 模式选择纯函数
    - 在 `feature:keyboard` 实现按 `Word_Buffer` 是否为空选择 Toolbar / Prediction_Bar 的纯函数
    - _Requirements: 10.3, 10.4, 10.5_

  - [ ]* 10.9 为顶部区域切换编写属性测试
    - **Property 23: 顶部区域按词缓冲切换模式**
    - **Validates: Requirements 10.3, 10.4, 10.5**

- [x] 11. 自绘渲染层（feature:keyboard，Compose）
  - [x] 11.1 实现 ColorParsing.parseArgbColor
    - 在 `feature:keyboard` 实现 `parseArgbColor("#AARRGGBB"): Color?`，非法返回 null（供渲染期颜色容错使用）
    - _Requirements: 8.13_

  - [x] 11.2 实现 PressedState 与 KeyboardRenderer 单画布自绘
    - 在 `feature:keyboard` 实现 `PressedState`（按 `Pointer_Identifier` 跟踪按下态）与 `KeyboardRenderer` Composable：在单个 `Canvas` 上消费 `computeKeyRects`，`drawRoundRect`（圆角固定 dp 转像素）绘制背景、`drawText` 绘制主文本与子标签、按下态用 `Pressed_Background_Color`
    - 渲染期非法颜色用 `DefaultConfig` 对应颜色容错；0 行配置改用 `DefaultConfig`；`usingFallback=true` 时绘制可见回退提示
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 8.9, 8.10, 8.11, 8.12, 8.13, 8.14, 6.4_

  - [x] 11.3 实现 Modifier.multiTouchKeyboard
    - 在 `feature:keyboard` 通过 `Modifier.pointerInput` + `awaitPointerEvent` 接入 10.3 的仲裁逻辑，驱动 `PressedState` 重绘并上抛 `onAction`
    - _Requirements: 11.15, 12.1, 12.2, 12.3_

  - [x] 11.4 实现 TopRegion Composable
    - 在 `feature:keyboard` 实现 `TopRegion`：高度 = H × `TOP_REGION_HEIGHT_RATIO`（组件内置常量 0.065，非配置字段）；Word_Buffer 空显示 Toolbar（居左 Apps_Entry 占位、居右 Collapse_Keyboard_Button 回调 `onCollapseKeyboard`），非空内嵌 `PredictionBar`
    - 不读取/不依赖 `Keyboard_Config`
    - _Requirements: 9.2, 10.1, 10.2, 10.6, 10.7, 10.8, 10.9_

  - [ ]* 11.5 为顶部区域与按下态编写 UI 测试
    - Toolbar 含居左 Apps_Entry 与居右 Collapse_Keyboard_Button；Apps_Entry 按下仅视觉无功能；Collapse 触发隐藏；按下变色与抬起恢复、主文本/子标签呈现
    - _Requirements: 8.5, 8.9, 8.10, 8.11, 8.12, 10.6, 10.7, 10.8, 10.9_

- [x] 12. 宿主集成（app：SnapInputMethodService）
  - [x] 12.1 集成配置加载与 Compose 视图挂载
    - 在 `SnapInputMethodService.onCreateInputView` 于 IO 线程经 `KeyboardConfigProvider.load()` 得到 `ActiveConfig`，用 `ComposeView` 渲染 `Column { TopRegion(...); KeyboardRenderer(...) }`
    - Top_Region 与 Keyboard_Region 高度按比例计算并接入区域收缩
    - _Requirements: 4.4, 9.1, 9.2, 9.5, 10.1_

  - [x] 12.2 扩展 handleKey 与按键动作接线
    - 接入 10.5 发射决策：字符提交、Del/Enter/Space、`Word_Buffer` 增删并驱动 TopRegion 模式切换、候选词刷新、`Collapse_Keyboard_Button` 调用 `requestHideSelf(0)`
    - `123`/`中/英` 不产生输出；无 `currentInputConnection` 时丢弃按键且不改变 shift/caps
    - _Requirements: 10.5, 10.10, 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 11.8, 11.9, 11.17_

  - [ ]* 12.3 为控制键/占位键映射编写单元测试
    - 断言逗号→"，"、Del→"Del"、Enter→"Enter"、Space→" "；123/中/英 不产生 Action_Value、不切换
    - _Requirements: 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 11.8, 11.9_

- [x] 13. Final checkpoint - 确保全部测试通过并完成集成
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- 标注 `*` 的子任务为可选测试任务（属性测试、单元测试、集成/UI/仪器测试），可为快速 MVP 跳过；顶层任务不标注 `*`。
- 每个任务引用具体的需求子条款以保证可追溯性。
- 属性测试覆盖跨大量输入的全称性质（每条最少 100 次随机迭代），单元测试覆盖具体示例与边界，二者互补。
- 时延（R6.3、R11.15）与 60fps/16ms（R11.16）通过仪器或基准测试验证，不在属性测试范围内，且不属于可由编码代理自动完成的任务，故未列为独立任务。
- 属性测试代码注释格式：`// Feature: configurable-keyboard-ui, Property {编号}: {属性文本}`。
- Checkpoint 用于在合理断点处确保增量验证。

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2"] },
    { "id": 2, "tasks": ["2.1", "3.1", "4.1", "7.1", "7.3", "8.1", "10.1", "10.5", "10.8", "11.1"] },
    { "id": 3, "tasks": ["2.2", "2.3", "2.4", "2.5", "3.2", "3.3", "4.2", "5.1", "6.1", "7.2", "7.4", "7.5", "7.6", "8.4", "10.2", "10.3", "10.6", "10.7", "10.9", "11.2", "11.4"] },
    { "id": 4, "tasks": ["4.3", "5.2", "6.3", "7.7", "7.8", "11.3", "11.5"] },
    { "id": 5, "tasks": ["5.3", "5.4", "5.5", "5.6", "5.7", "5.8", "5.9", "5.10", "6.2", "8.2"] },
    { "id": 6, "tasks": ["8.3", "12.1"] },
    { "id": 7, "tasks": ["12.2"] },
    { "id": 8, "tasks": ["12.3"] }
  ]
}
```

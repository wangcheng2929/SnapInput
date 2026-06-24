# Requirements Document

## Introduction

SnapInput 键盘目前由 IME 服务（`SnapInputMethodService.kt`）托管，并根据硬编码的 QWERTY 布局（`KeyboardLayout.kt`）进行渲染。键盘占据整个屏幕高度，其视觉样式（外边距、按键尺寸、文本外观）在代码中固定，导致呈现效果不佳。

本特性将输入法界面明确划分为自上而下堆叠的两个区域：

1. **顶部区域（Top_Region）**：一个独立组件，其存在、布局与内容**不**受 JSON Keyboard_Config 控制。顶部区域具有两个互斥的显示模式：
   - **工具栏（Toolbar）**：在无活动输入（Word_Buffer 为空）时默认显示。工具栏本轮恰好包含两个元素——应用入口（Apps_Entry，田字格图标，本轮为占位）与收起键盘按钮（Collapse_Keyboard_Button，"∨" 形图标，本轮为功能按钮）。
   - **候选词栏（Prediction_Bar）**：在存在活动输入（Word_Buffer 非空）时显示，呈现候选词预测。当输入被提交或 Word_Buffer 被清空时，顶部区域恢复显示工具栏。
2. **键盘区（Keyboard_Region）**：唯一一块自绘、由 JSON Keyboard_Config 驱动的键盘区域。本特性中的 JSON 配置**仅**用于定义键盘区（布局与外观），顶部区域是独立组件，将其变为可配置项属于后续/范围之外的事项。

**两级配置模型（重要变更）：** 为了减少多语言键盘之间的配置重复（例如英文与法文共享绝大多数按键），本特性引入**两级配置**：

- **编写期配置（Authoring_Config）— 源格式**：由人撰写、按职责分文件组织（Scheme B，分离文件）：
  - `keyboard/themes/{themeId}.json` — 一个 **Theme**，集中定义全部样式比例与各按键类（Key_Class）的默认值（Key_Defaults），可被多个语言布局以 themeId 共享引用。
  - `keyboard/templates/{name}.json` — 一组 **Key_Template**（命名的可复用按键规格，尤其是 Shift/Del/Space/Enter/123/语言切换等特殊键）与 **Row_Template**（命名的可复用整行，例如功能行）。
  - `keyboard/layouts/{layoutId}.json` — 每种语言一个 **Layout**，声明其引用的 theme（一个 themeId）、引用的 templates（模板文件名列表）、以及 rows（行组合）。
- **运行时配置（Keyboard_Config）— 扁平模型**：由 **Resolver（解析展开器）** 将 Authoring_Config（当前生效的 Active_Layout 及其引用的 Theme 与 Templates）确定性地展开（resolve）为一份**字段完全补全的扁平 Keyboard_Config**。扁平 Keyboard_Config 即既有运行时模型，由 Keyboard_Renderer 直接渲染；其模式（schema）与填充/分区约束保持不变（见 Requirement 1）。

复用仅通过共享 Theme 与 Template 文件实现；Layout 之间不互相继承（依赖关系为 layout → theme + templates，无环）。本轮 Active_Layout 固定为默认布局 "en"，语言切换仍为非目标，但该 authoring 格式已为多语言/切换预留（switch-ready）。

**尺寸模型（重要变更）：** 键盘区以像素自绘。本特性采用**比例（ratio）驱动、分辨率自适应**的尺寸模型：所有尺寸类配置值均为**无单位的比例系数**，而非绝对的 dp/sp 值。比例值在制作时以 360×800 的参考分辨率为基准标定，但配置中**仅**存储标定后得到的比例（不存储参考分辨率，参考分辨率隐含在比例中）。运行时根据设备实际屏幕宽度 W 与高度 H（均以像素计）将比例换算为像素：水平类比例乘以键盘宽度（等于 W），垂直类比例乘以键盘区高度（等于 H × Keyboard_Region_Height_Ratio）。唯一的例外是 Corner_Radius，它是一个固定的 dp 值，不随分辨率缩放。文本尺寸亦为比例（相对正常键高），不使用 sp、不跟随系统字号缩放设置，以保证跨设备视觉一致性。在 authoring 层，按键宽度除可写为固定的 widthRatio 外，还可声明为"填充(fill)"（Fill_Width），表示占据该行的剩余宽度；fill 仅为 authoring 语法糖，由 Resolver 确定性地展开（resolve）为具体的 Key_Width_Ratio，运行时扁平 Keyboard_Config 中不存在 fill 概念。

本特性重新架构键盘区 UI 系统，使外部 JSON 配置文件（经编写期格式编写、由 Resolver 展开）同时定义键盘区布局（行与按键）及其外观（外边距比例、键圆角半径、键水平间距比例、行垂直间距比例、每个按键的宽度比例、键盘级统一的正常键高比例、每个按键的正常背景色与可选的按下态背景色、主按键文本的内容/颜色/尺寸比例，以及每个按键上方可选的子标签及其各自的内容/颜色/尺寸比例）。配置从应用资源（assets）中加载，使用 kotlinx.serialization 进行解析，经 Resolver 展开后驱动键盘区渲染。当配置缺失、无效或展开失败时，系统回退到内置的默认配置（Default_Config），以保证键盘区始终能够渲染。键盘区高度由屏幕高度乘以 Keyboard_Region_Height_Ratio 直接定义，而非填满整个屏幕。

键盘区不再由“每个按键一个 Compose 组件”的方式组合而成，而是作为单个自绘组件渲染整张键盘：渲染结果中所有行、按键、主文本与子标签共同构成一张可见键盘，并自行执行命中测试（hit-testing），将触摸位置映射到对应按键的像素矩形。该方式同时支持多指触摸（rollover）：每个触摸点按其指针标识（pointer identifier）被独立跟踪与解析，与主流输入法的连续按键输入行为一致。

所有既有的键盘区输入行为均得到保留：字面字符输出（含逗号 "，"）、特殊按键处理（Shift、Del、123、Space、语言切换键(中/英)、Enter）、shift/caps 处理。其中 123 键与语言切换键(中/英) 本轮为占位按键（有按下态视觉但不执行功能），子标签（Sub_Label）本轮仅用于显示而不参与输入。

## 范围之外（Non-Goals / 非目标）

以下特性不属于本特性范围，将在后续单独规划，本文档不为其定义需求：

- 短按气泡（short-press bubble）：短按按键时在按键上方弹出的预览气泡。
- 长按气泡（long-press bubble）：长按按键时弹出的备选字符（alternate characters）气泡。
- 上滑气泡（swipe-up bubble）：在按键上向上滑动时触发的气泡。
- 滑行输入（glide / slide-to-type）：在键盘上连续滑动以连缀输入整词的输入方式。
- 语音输入（"点击说话" / 语音输入）：工具栏中的语音按钮本轮不包含。
- 应用入口（Apps_Entry，田字格）背后的应用面板功能：本轮 Apps_Entry 仅为占位，按下不执行任何功能。
- 切换到数字/符号键盘布局（"123" 功能）：本轮 123 键仅为占位。
- 输入语言切换功能（"中/英"）：本轮语言切换键仅为占位；Active_Layout 固定为单一默认布局。本轮的 authoring 复用格式（theme/templates/layouts 分文件）已为多语言与语言切换预留（switch-ready），但语言切换的 UI 与运行时切换逻辑不在本期范围内。
- 顶部区域的可配置化：本轮顶部区域不受 Keyboard_Config 控制。

## Glossary

- **Keyboard_UI_System**：键盘 UI 系统，负责加载配置、解析配置、解析展开配置并渲染键盘区的组件集合。
- **Top_Region**：顶部区域，渲染在 Keyboard_Region 上方的独立组件，其存在、布局与内容不受 Keyboard_Config 控制；在工具栏（Toolbar）与候选词栏（Prediction_Bar）两种互斥模式之间切换。
- **Keyboard_Region**：键盘区，唯一一块自绘、由 Keyboard_Config 驱动的键盘区域，渲染在 Top_Region 下方。
- **Toolbar**：工具栏，Top_Region 在无活动输入时默认显示的内容，本轮恰好包含 Apps_Entry 与 Collapse_Keyboard_Button 两个元素。
- **Apps_Entry**：应用入口，工具栏中的田字格（grid）图标元素，本轮为占位元素——显示并响应按下态视觉，但按下不执行任何功能。
- **Collapse_Keyboard_Button**：收起键盘按钮，工具栏中的 "∨"（chevron）图标元素，本轮为功能元素——按下时收起（隐藏）输入法。
- **Prediction_Bar**：候选词栏（预测栏），Top_Region 在存在活动输入时显示的既有候选词组件，呈现候选词预测。
- **Word_Buffer**：词缓冲（正在输入的内容），用户当前正在输入但尚未提交到宿主的字符序列；为空表示无活动输入，非空表示存在活动输入。
- **Authoring_Config**：编写期配置，由人编写的源格式，由 Theme、Templates 与 Layout 三类文件组成；经反序列化与解析展开后得到运行时的扁平 Keyboard_Config。
- **Theme**：样式主题文件，位于 `keyboard/themes/{themeId}.json`，集中定义全部键盘级样式比例字段与 Corner_Radius，并定义按 Key_Class 分类的 Key_Defaults；被 Layout 以 themeId 引用，可跨语言共享。
- **Key_Class**：按键类，Key_Defaults 的分类维度，本轮包含 `letter`（字母键）与 `special`（特殊键）两类，为该类按键提供默认字段。
- **Key_Defaults**：默认字段集合，Theme 中按 Key_Class 提供的一组默认 Key_Config 字段（不含文本内容 content）。
- **Key_Template**：按键模板，Template 文件中命名的可复用按键规格（含 Key_Class、action 及任意可覆盖字段）；行内以 "$name" 引用，可附覆盖字段。
- **Row_Template**：行模板，Template 文件中命名的可复用整行规格；Layout 的 rows 中以 "$name" 引用。
- **Layout**：布局文件，位于 `keyboard/layouts/{layoutId}.json`，单语言布局，声明一个 theme（themeId）、一个 templates 引用列表与一个 rows 组合列表。
- **Resolver**：解析展开器，一个纯函数，将 Authoring_Config（指定的 Active_Layout 及其引用的 Theme 与 Templates）展开为字段完全补全的扁平 Keyboard_Config（既有运行时模型）。
- **Active_Layout**：活动布局，当前生效的 layoutId；本轮固定为默认值 "en"。
- **Letters_Shorthand**：字母行简写，行规格中以 `letters` 字符串（可选附 `subLabels` 字符串或数组，按下标对齐）批量声明一排 `letter` 类按键的形式；可选的 `lead` / `trail` 字段在该行两端放置特殊键（"$ref" 或内联规格）。
- **Config_Loader**：配置加载器，从应用资源（assets）中读取原始配置源（Layout、Theme 与 Template 文件的 JSON 文本）的组件。
- **Config_Parser**：配置解析器，使用 kotlinx.serialization 将 JSON 文本反序列化为 Authoring_Config 对象或运行时 Keyboard_Config 对象的组件。
- **Config_Serializer**：配置序列化器，将运行时 Keyboard_Config 对象重新序列化为 JSON 文本的组件（用于往返校验和工具支持）。
- **Keyboard_Config**：键盘配置（扁平运行时模型），由 Resolver 展开 Authoring_Config 后产生的、字段完全补全的内存模型，包含键盘区的布局与样式数据（比例类尺寸系数、圆角半径、行、按键、背景色、文本/子标签样式）；为 Keyboard_Renderer 直接渲染的权威模型。
- **Keyboard_Renderer**：键盘渲染器，将 Keyboard_Config 渲染为单张可见键盘区的单个自绘组件，渲染整张键盘区的所有行、按键背景、主文本与子标签，并自行执行命中测试与多指触摸跟踪，用于替换硬编码的 QWERTY 布局以及“每个按键一个 Compose 组件”的组合方式。
- **Reference_Resolution**：参考分辨率，制作 Keyboard_Config 时用于标定各比例值的基准分辨率，本轮为 360×800（像素）；该值不写入配置，仅隐含在标定得到的比例中。
- **Screen_Width**：屏幕宽度 W，设备屏幕的实际像素宽度；键盘总宽度等于 Screen_Width。
- **Screen_Height**：屏幕高度 H，设备屏幕的实际像素高度。
- **Top_Region_Height_Ratio**：顶部区域高度比例，Top_Region 高度占 Screen_Height 的分数，取值大于 0 且小于等于 1；本轮为由 Top_Region 组件拥有的内置常量（值为 0.065），**不**是 Keyboard_Config 字段、不由 JSON 配置驱动。
- **Keyboard_Region_Height_Ratio**：键盘区高度比例，Keyboard_Region 高度占 Screen_Height 的分数，取值大于 0 且小于等于 1；Keyboard_Region 高度由 Screen_Height 乘以该比例直接定义。
- **Side_Margin_Ratio**：侧边外边距比例（水平），键盘区左右外边距各自占键盘宽度（Screen_Width）的分数，取值大于等于 0 且小于等于 1。
- **Horizontal_Gap_Ratio**：键水平间距比例，同一行内相邻两按键之间的水平间隔占键盘宽度（Screen_Width）的分数，取值大于等于 0 且小于等于 1。
- **Top_Margin_Ratio**：上外边距比例（垂直），键盘区上外边距占键盘区高度的分数，取值大于等于 0 且小于等于 1。
- **Bottom_Margin_Ratio**：下外边距比例（垂直），键盘区下外边距占键盘区高度的分数，取值大于等于 0 且小于等于 1。
- **Vertical_Gap_Ratio**：行垂直间距比例，相邻两行之间的垂直间隔占键盘区高度的分数，取值大于等于 0 且小于等于 1。
- **Normal_Key_Height_Ratio**：正常键高比例，键盘级统一的按键高度占键盘区高度的分数，取值大于 0 且小于等于 1；本轮所有按键高度一致（键盘级，非每键设置）。
- **Key_Width_Ratio**：按键宽度比例，单个按键的宽度占键盘宽度（Screen_Width）的分数，取值大于 0 且小于等于 1。
- **Fill_Width**：填充宽度，仅存在于 authoring 层的一种按键宽度声明（"fill"），表示该按键占据其所在行的剩余宽度，并可附带一个正的权重（默认权重为 1）用于在同一行的多个 Fill_Width 键之间按权重分配剩余宽度；Fill_Width 由 Resolver 展开为具体的 Key_Width_Ratio，运行时扁平 Keyboard_Config 中不存在 Fill_Width 概念。
- **Corner_Radius**：圆角半径，键盘级配置值，规定每个按键四角圆角的半径，以固定的密度无关像素（dp）表示；该值不是比例、不随分辨率缩放。
- **Normal_Background_Color**：正常背景色，按键未被按下时用于填充该按键的背景色，以十六进制 ARGB 字符串 "#AARRGGBB" 表示。
- **Pressed_Background_Color**：按下态背景色，按键处于按下状态（有 Touch_Point 落于其上且未抬起）时用于填充该按键的背景色，以十六进制 ARGB 字符串 "#AARRGGBB" 表示；可在 Key_Config 中省略，省略时由 Normal_Background_Color 推导。
- **Main_Text_Size_Ratio**：主文本尺寸比例，主文本字号占正常键高（Normal_Key_Height_Ratio × 键盘区高度）的分数，取值大于 0 且小于等于 5。
- **Sub_Label**：子标签，渲染在按键主文本上方的小号次要文本；本轮仅用于显示，不参与输入。
- **Sub_Label_Size_Ratio**：子标签尺寸比例，Sub_Label 字号占正常键高的分数，取值大于 0 且小于等于 5。
- **Default_Config**：默认配置，以 Authoring 格式编译进应用的内置配置（共享 Theme + Templates + 英文 Layout），其 Resolver 展开结果用作回退的扁平 Keyboard_Config。
- **Special_Key**：特殊按键，其动作为控制动作、修饰动作或占位动作（Shift、Del、123、Space、语言切换键(中/英)、Enter）而非字面字符的按键。
- **Action_Value**：动作值，按键被触发并产生输出时向宿主输出的字符串（一个字面字符、" "、"Del" 或 "Enter"）。注：Shift 为修饰键、不产生 Action_Value 输出；123 键与语言切换键(中/英) 本轮为占位按键、不产生 Action_Value 输出。
- **IME_Service**：即托管键盘并将按键动作应用到输入连接（input connection）的 `SnapInputMethodService`。
- **Drawing_Surface**：绘制表面，Keyboard_Renderer 用于自绘整张键盘区的统一渲染表面。
- **Key_Rectangle**：按键矩形，根据缩放模型计算得到的单个 Key_Config 在键盘区上所占据的像素矩形区域（由侧边外边距、水平间距、按键宽度像素、行间距、按键高度像素等计算所得），命中测试在该像素矩形上进行。
- **Touch_Point**：触点，屏幕上的单个接触点（指针），由其指针标识（pointer identifier）唯一标识。
- **Pointer_Identifier**：指针标识，由触摸系统分配、用于在多指触摸期间区分各个 Touch_Point 的标识。

## Requirements

### Requirement 1: 扁平运行时配置模式（Keyboard_Config / 解析展开后的运行时模型）

**User Story:** 作为键盘开发者，我希望有一个以比例（ratio）定义键盘区布局和样式的扁平运行时 Keyboard_Config 模式，以便键盘能够在不修改代码的情况下进行分辨率自适应缩放。

> 说明：本 Requirement 定义的扁平 Keyboard_Config 是 Resolver 展开 Authoring_Config（见 Requirement 2、Requirement 3）后产生的**运行时模型**，也是 Keyboard_Renderer 直接渲染的权威模型。其模式与约束在引入编写期格式后保持不变。

#### Acceptance Criteria

1. THE Keyboard_Config SHALL 定义一个 Keyboard_Region_Height_Ratio，表示为大于 0 且小于等于 1 的数字。
2. THE Keyboard_Config SHALL 定义一个 Side_Margin_Ratio，表示为大于等于 0 且小于等于 1 的数字。
3. THE Keyboard_Config SHALL 定义一个 Horizontal_Gap_Ratio，表示为大于等于 0 且小于等于 1 的数字。
4. THE Keyboard_Config SHALL 定义一个 Top_Margin_Ratio，表示为大于等于 0 且小于等于 1 的数字。
5. THE Keyboard_Config SHALL 定义一个 Bottom_Margin_Ratio，表示为大于等于 0 且小于等于 1 的数字。
6. THE Keyboard_Config SHALL 定义一个 Vertical_Gap_Ratio，表示为大于等于 0 且小于等于 1 的数字。
7. THE Keyboard_Config SHALL 定义一个键盘级的 Normal_Key_Height_Ratio，表示为大于 0 且小于等于 1 的数字，并作为所有按键统一的高度比例。
8. THE Keyboard_Config SHALL 定义一个 Corner_Radius 值，以固定的密度无关像素（dp）表示，为 0 至 256 dp（含）范围内的数字。
9. THE Keyboard_Config SHALL 定义一个有序的 Row_Config 列表，包含 1 至 16 项（含）。
10. THE Row_Config SHALL 定义一个有序的 Key_Config 列表，包含 1 至 32 项（含）。
11. THE Key_Config SHALL 定义一个 Action_Value 和一个 Key_Width_Ratio，其中 Key_Width_Ratio 表示为大于 0 且小于等于 1 的数字。
12. THE Key_Config SHALL 定义一个 Normal_Background_Color，表示为十六进制 ARGB 字符串 "#AARRGGBB"。
13. THE Key_Config SHALL 定义一个可选的 Pressed_Background_Color，表示为十六进制 ARGB 字符串 "#AARRGGBB"。
14. WHERE 某个 Key_Config 省略 Pressed_Background_Color，THE Keyboard_Config SHALL 通过将该按键 Normal_Background_Color 的 RR、GG、BB 三个分量各自乘以 0.8 并向下取整、且保持 AA 分量不变，推导出默认的 Pressed_Background_Color。
15. THE Key_Config SHALL 定义主文本内容（一个 1 至 32 个字符（含）的字符串）、主文本颜色（一个十六进制 ARGB 字符串）和 Main_Text_Size_Ratio（表示为大于 0 且小于等于 5 的数字）。
16. THE Key_Config SHALL 定义一个可选的 Sub_Label，包含子标签内容（一个 1 至 32 个字符（含）的字符串）、子标签颜色（一个十六进制 ARGB 字符串）和 Sub_Label_Size_Ratio（表示为大于 0 且小于等于 5 的数字）。
17. WHERE 某个 Key_Config 省略 Sub_Label，THE Keyboard_Renderer SHALL 在渲染该按键时不带 Sub_Label。
18. THE Keyboard_Config SHALL 将每个颜色值（包括 Normal_Background_Color、Pressed_Background_Color、主文本颜色和 Sub_Label 颜色）表示为匹配十六进制 ARGB 格式 "#AARRGGBB" 的字符串，其中 AA、RR、GG、BB 每段恰好为两位十六进制数字（0-9、A-F，不区分大小写）。
19. IF 某个颜色值不匹配十六进制 ARGB 格式 "#AARRGGBB"，THEN THE Keyboard_Config SHALL 拒绝该配置并产生一个错误，指出出错的颜色值和字段，同时保持先前加载的有效配置不变。
20. IF 某个必需的 Key_Config 字段（Action_Value、Key_Width_Ratio、Normal_Background_Color、主文本内容、主文本颜色或 Main_Text_Size_Ratio）缺失或超出其定义的范围，THEN THE Keyboard_Config SHALL 拒绝该配置并产生一个错误，指出出错的字段，同时保持先前加载的有效配置不变。
21. IF 某个键盘级比例字段（Keyboard_Region_Height_Ratio、Side_Margin_Ratio、Horizontal_Gap_Ratio、Top_Margin_Ratio、Bottom_Margin_Ratio、Vertical_Gap_Ratio 或 Normal_Key_Height_Ratio）超出其定义的范围，THEN THE Keyboard_Config SHALL 拒绝该配置并产生一个错误，指出出错的字段，同时保持先前加载的有效配置不变。
22. IF Corner_Radius 超出其定义的 0 至 256 dp（含）范围，THEN THE Keyboard_Config SHALL 拒绝该配置并产生一个错误，指出出错的字段，同时保持先前加载的有效配置不变。
23. IF 某个 Row_Config 的水平占用合计（该行各 Key_Width_Ratio 之和，加上 (按键数 − 1) × Horizontal_Gap_Ratio，再加上 2 × Side_Margin_Ratio）大于 1.0，THEN THE Keyboard_Config SHALL 拒绝该配置并产生一个错误，指出出错的行，同时保持先前加载的有效配置不变。
24. IF 垂直分区合计（Top_Margin_Ratio + Bottom_Margin_Ratio + 行数 × Normal_Key_Height_Ratio + (行数 − 1) × Vertical_Gap_Ratio）与 1.0 的偏差超过 0.001，THEN THE Keyboard_Config SHALL 拒绝该配置并产生一个错误，指出垂直分区不满足合计为 1.0 的约束，同时保持先前加载的有效配置不变。

#### 数值约束汇总表

| 字段 | 类型 / 基准 | 范围 |
| --- | --- | --- |
| Keyboard_Region_Height_Ratio | 比例（屏幕高度的分数） | 大于 0 且小于等于 1 |
| Side_Margin_Ratio | 比例（键盘宽度的分数） | 大于等于 0 且小于等于 1 |
| Horizontal_Gap_Ratio | 比例（键盘宽度的分数） | 大于等于 0 且小于等于 1 |
| Top_Margin_Ratio | 比例（键盘区高度的分数） | 大于等于 0 且小于等于 1 |
| Bottom_Margin_Ratio | 比例（键盘区高度的分数） | 大于等于 0 且小于等于 1 |
| Vertical_Gap_Ratio | 比例（键盘区高度的分数） | 大于等于 0 且小于等于 1 |
| Normal_Key_Height_Ratio | 比例（键盘区高度的分数） | 大于 0 且小于等于 1 |
| Key_Width_Ratio | 比例（键盘宽度的分数） | 大于 0 且小于等于 1 |
| Corner_Radius | dp（固定，不缩放） | 0 – 256（含） |
| Row_Config 列表长度 | 项 | 1 – 16（含） |
| Row_Config 内 Key_Config 数量 | 项 | 1 – 32（含） |
| 主文本内容长度 | 字符 | 1 – 32（含） |
| Main_Text_Size_Ratio | 比例（正常键高的分数） | 大于 0 且小于等于 5 |
| Sub_Label 内容长度 | 字符 | 1 – 32（含） |
| Sub_Label_Size_Ratio | 比例（正常键高的分数） | 大于 0 且小于等于 5 |
| 行水平占用合计 | 比例（键盘宽度的分数） | 小于等于 1.0 |
| 垂直分区合计 | 比例（键盘区高度的分数） | 等于 1.0（容差 ±0.001） |
| Normal_Background_Color / Pressed_Background_Color / 主文本颜色 / Sub_Label 颜色 | 十六进制 ARGB | 格式 "#AARRGGBB" |

### Requirement 2: 编写期配置格式与文件组织（Authoring）

**User Story:** 作为键盘开发者，我希望以分层（主题/模板/布局分文件）的编写期格式撰写键盘配置，以便多语言键盘共享样式与按键规格、显著减少配置重复。

#### Acceptance Criteria

1. THE Authoring_Config SHALL 由三类源文件组成：位于 `keyboard/themes/{themeId}.json` 的 Theme 文件、位于 `keyboard/templates/{name}.json` 的 Template 文件、以及位于 `keyboard/layouts/{layoutId}.json` 的 Layout 文件。
2. THE Theme SHALL 定义全部键盘级样式比例字段（Keyboard_Region_Height_Ratio、Side_Margin_Ratio、Horizontal_Gap_Ratio、Top_Margin_Ratio、Bottom_Margin_Ratio、Vertical_Gap_Ratio、Normal_Key_Height_Ratio）与 Corner_Radius。
3. THE Theme SHALL 定义 Key_Defaults，且 Key_Defaults SHALL 至少包含 `letter` 与 `special` 两个 Key_Class。
4. THE Key_Defaults[class] SHALL 提供该 Key_Class 的默认 widthRatio、默认 normalBackgroundColor、可选的默认 pressedBackgroundColor、默认 mainText（含 color 与 sizeRatio）与默认 subLabel（含 color 与 sizeRatio）。
5. THE Key_Defaults[class] 中的 mainText 与 subLabel SHALL 仅包含 color 与 sizeRatio 字段，文本内容（content）由行规格或 Key_Template 提供。
6. THE Key_Template SHALL 是一个命名的可复用按键规格，包含 Key_Class、action（Action_Value）以及任意可覆盖的 Key_Config 字段。
7. THE 按键规格（KeySpec，包括 Key_Defaults[class]、Key_Template 与内联按键规格）的宽度在 authoring 层 SHALL 可取以下两者之一：(a) 一个固定的 widthRatio 数字（大于 0 且小于等于 1，表示占键盘宽度的分数）；或 (b) 一个"填充(fill)"声明（Fill_Width），表示占据该按键所在行的剩余宽度，并可附带一个正的权重（默认权重为 1），用于在同一行的多个 fill 键之间按权重分配剩余宽度。
8. THE Key_Template SHALL 承载按键身份（action 与样式），且其宽度 SHALL 可缺省或声明为 fill（Fill_Width）；具体的固定宽度 SHALL 可在 Layout 的引用处（"$ref" 覆盖对象）以 widthRatio 覆盖（per-site override）。
9. THE Row_Template SHALL 是一个命名的可复用整行规格。
10. THE Layout SHALL 声明恰好一个 theme（一个 themeId）、一个 templates 引用列表（Template 文件名的列表）、以及一个有序的 rows 列表。
11. THE Layout 的 rows 列表中的每一项 SHALL 为以下三者之一：一个采用 Letters_Shorthand 形式的行规格、一个采用显式 keys 列表的行规格、或一个对某 Row_Template 的引用（形如 "$name" 的字符串）。
12. THE 显式 keys 列表中的每一项 SHALL 为以下三者之一：一个内联按键规格、一个对某 Key_Template 的引用（形如 "$name" 的字符串）、或一个带覆盖字段的引用对象（含 "$ref" 与覆盖字段）。
13. WHERE 某行规格采用 Letters_Shorthand 形式，THE 行规格 SHALL 提供一个 `letters` 字符串，并可提供一个可选的 `subLabels`（字符串或数组）、一个可选的 `lead` 特殊键（"$ref" 或内联规格）与一个可选的 `trail` 特殊键（"$ref" 或内联规格）。
14. THE Layout SHALL 仅通过其引用的 Theme 与 Template 文件进行复用，且其依赖关系 SHALL 保持为 layout → theme + templates 的有向无环关系（Layout 之间不存在继承或引用关系）。
15. THE Active_Layout SHALL 标识当前生效的单个 layoutId，本轮固定为默认值 "en"。

### Requirement 3: 配置解析展开（Resolution）

**User Story:** 作为键盘开发者，我希望编写期配置被确定性地解析展开为扁平运行时 Keyboard_Config，以便运行时渲染始终基于字段完全补全的完整配置。

#### Acceptance Criteria

1. WHEN 给定 Active_Layout 及其引用的 Theme 与 Templates，THE Resolver SHALL 产出一个扁平 Keyboard_Config，且该 Keyboard_Config 中每个 Key_Config 的每个字段均已补全。
2. WHEN the Resolver 合并某个 Key_Config 的字段时，THE Resolver SHALL 按优先级 Theme.Key_Defaults[class] < Key_Template（经 "$ref" 引用时） < 内联覆盖 进行合并，使更高优先级的来源覆盖更低优先级的来源。
3. WHEN the Resolver 合并 mainText 或 subLabel 嵌套对象时，THE Resolver SHALL 按字段执行深合并，使各嵌套字段（color、sizeRatio、content）分别按优先级覆盖。
4. WHEN the Resolver 展开某个 Letters_Shorthand 行规格的 `letters` 字符串时，THE Resolver SHALL 按字符顺序为每个字符生成一个 `letter` 类按键，使第 i 个按键的 Action_Value 为该字符的小写形式、主文本内容（content）为该字符。
5. WHERE 某个 Letters_Shorthand 行规格提供了 `subLabels`，THE Resolver SHALL 按下标将第 i 个 subLabel 对齐为第 i 个按键的 Sub_Label。
6. WHERE 某个 Letters_Shorthand 行规格提供了 `lead`，THE Resolver SHALL 将 `lead` 解析为该行最左侧的特殊键。
7. WHERE 某个 Letters_Shorthand 行规格提供了 `trail`，THE Resolver SHALL 将 `trail` 解析为该行最右侧的特殊键。
8. WHEN the Resolver 遇到对某 Key_Template 或 Row_Template 的 "$ref" 引用时，THE Resolver SHALL 以对应模板的解析结果（含覆盖字段）替换该引用。
9. WHEN 某个 "$ref" 引用处提供了固定的 widthRatio 覆盖（一个数字），THE Resolver SHALL 使该固定 widthRatio 覆盖优先于被引用 Key_Template 的 fill（Fill_Width）或 width 声明，从而将该处用法解析为固定宽度键。
10. WHEN 某行包含一个或多个宽度为 fill（Fill_Width）的按键，THE Resolver SHALL 计算该行的剩余宽度比例 = 1.0 − 2 × Side_Margin_Ratio − (该行按键数 − 1) × Horizontal_Gap_Ratio − (该行所有固定宽度键的 widthRatio 之和)，并将该剩余宽度比例按各 fill 键的权重比例分配，得到每个 fill 键的具体 Key_Width_Ratio。
11. WHEN the Resolver 展开某个含 fill（Fill_Width）键的行，THE 展开后该行的水平占用合计 SHALL 等于 1.0（即含 fill 键的行恰好填满键盘宽度，不触发居中内缩）。
12. WHEN the Resolver 完成展开，THE 产出的每个 Key_Config SHALL 具有一个具体的、落在 Requirement 1 定义范围内（大于 0 且小于等于 1）的 Key_Width_Ratio（fill 已被解析为具体比例，运行时模型中不存在 fill 概念）。
13. THE Resolver SHALL 为纯函数：对于逐字段相等的输入（相同的 Active_Layout、Theme 与 Templates），THE Resolver SHALL 产出逐字段相等的 Keyboard_Config。
14. WHEN the Resolver 完成展开时，THE 展开得到的 Keyboard_Config SHALL 满足 Requirement 1 定义的全部模式约束与填充/分区约束。
15. IF 展开得到的 Keyboard_Config 不满足 Requirement 1 的某项约束，THEN THE Resolver SHALL 返回一个解析展开错误，指出违反的约束，并 SHALL NOT 产出 Keyboard_Config。
16. IF 某行的剩余宽度比例（1.0 − 2 × Side_Margin_Ratio − (该行按键数 − 1) × Horizontal_Gap_Ratio − 该行所有固定宽度键的 widthRatio 之和）小于等于 0（固定宽度键、间距与边距已占满或超出该行可用宽度），THEN THE Resolver SHALL 返回一个 ROW_OVERFLOW 解析展开错误，指出该行，并 SHALL NOT 产出 Keyboard_Config。
17. IF 某个 "$ref" 引用了不存在的 Key_Template 或 Row_Template，THEN THE Resolver SHALL 返回一个解析展开错误，指出未知的引用名，并 SHALL NOT 产出 Keyboard_Config。
18. IF 被加载的 Template 文件集合中存在重复命名的 Key_Template 或 Row_Template，THEN THE Resolver SHALL 返回一个解析展开错误，指出重复的名称，并 SHALL NOT 产出 Keyboard_Config。
19. IF 某个 Letters_Shorthand 行规格的 `subLabels` 元素数量与 `letters` 字符数量不一致，THEN THE Resolver SHALL 返回一个解析展开错误，指出该行，并 SHALL NOT 产出 Keyboard_Config。
20. IF 某个按键规格引用了未在 Theme.Key_Defaults 中定义的 Key_Class，THEN THE Resolver SHALL 返回一个解析展开错误，指出未知的 Key_Class，并 SHALL NOT 产出 Keyboard_Config。

### Requirement 4: 配置加载

**User Story:** 作为用户，我希望键盘在启动时加载并展开其配置，以便键盘区反映所配置的布局和外观。

#### Acceptance Criteria

1. WHEN the IME_Service 创建输入视图时，THE Config_Loader SHALL 读取 Active_Layout 的 Layout 文件及其引用的 Theme 文件与全部 Template 文件，并在 2 秒内完成全部读取。
2. THE Config_Loader SHALL 复用（缓存）已读取的共享 Theme 文件与 Template 文件，以避免对同一文件的重复读取。
3. WHEN 成功读取 Layout、Theme 与 Template 文件文本时，THE Config_Parser SHALL 将其反序列化为 Authoring_Config（Layout、Theme 与 Template 对象）。
4. WHEN Authoring_Config 成功反序列化时，THE Resolver SHALL 将其展开为运行时 Keyboard_Config，并 THE Keyboard_UI_System SHALL 使用展开得到的 Keyboard_Config 创建输入视图。
5. IF Active_Layout 的 Layout 文件、其引用的任一 Theme 文件或任一 Template 文件缺失或不可读，THEN THE Keyboard_UI_System SHALL 使用 Default_Config 创建输入视图。
6. IF 任一 Authoring 文件的读取未在 2 秒内完成，THEN THE Keyboard_UI_System SHALL 使用 Default_Config 创建输入视图。
7. IF 任一 Authoring 文件反序列化失败，THEN THE Keyboard_UI_System SHALL 使用 Default_Config 创建输入视图。
8. IF the Resolver 返回解析展开错误，THEN THE Keyboard_UI_System SHALL 使用 Default_Config 创建输入视图。
9. THE Keyboard_UI_System SHALL 本轮将 Active_Layout 固定为默认 Layout "en"。

### Requirement 5: 配置解析与序列化

**User Story:** 作为键盘开发者，我希望编写期文件与运行时配置的解析和序列化可靠，以便配置内容被正确解读，并且工具能够生成有效的配置。

> 说明：本 Requirement 的往返（round-trip）属性作用于**扁平运行时 Keyboard_Config**（即 Resolver 展开后的 resolved config）；序列化与解析的对象是 resolved config，而非编写期的 Theme/Template/Layout 文件。

#### Acceptance Criteria

1. WHEN 提供有效的运行时配置 JSON 文本时，THE Config_Parser SHALL 生成一个扁平运行时 Keyboard_Config，包含配置 JSON 文本中存在的每个比例字段（Keyboard_Region_Height_Ratio、Side_Margin_Ratio、Horizontal_Gap_Ratio、Top_Margin_Ratio、Bottom_Margin_Ratio、Vertical_Gap_Ratio、Normal_Key_Height_Ratio）、Corner_Radius、Row_Config 和 Key_Config 值，并保留 Row_Config 和 Key_Config 条目的顺序。
2. WHERE 有效运行时配置 JSON 文本中的某个 Key_Config 省略了 Sub_Label，THE Config_Parser SHALL 生成对应的不带 Sub_Label 的 Key_Config，且不返回解析错误。
3. WHERE 有效运行时配置 JSON 文本中的某个 Key_Config 省略了 Pressed_Background_Color，THE Config_Parser SHALL 生成一个其 Pressed_Background_Color 由 Normal_Background_Color 按 Requirement 1 推导的 Key_Config，且不返回解析错误。
4. IF 运行时配置 JSON 文本在语法上无效，THEN THE Config_Parser SHALL 返回一个指出语法失败的解析错误，并 SHALL NOT 生成 Keyboard_Config。
5. IF 运行时配置 JSON 文本缺少模式定义的某个必需字段，THEN THE Config_Parser SHALL 返回一个标识缺失字段的解析错误，并 SHALL NOT 生成 Keyboard_Config。
6. IF 运行时配置 JSON 文本中某个字段的值类型与模式不匹配，THEN THE Config_Parser SHALL 返回一个标识出错字段的解析错误，并 SHALL NOT 生成 Keyboard_Config。
7. IF 运行时配置 JSON 文本中某个颜色值不匹配 "#AARRGGBB" 格式，THEN THE Config_Parser SHALL 返回一个标识出错字段的解析错误，并 SHALL NOT 生成 Keyboard_Config。
8. THE Config_Serializer SHALL 将扁平运行时 Keyboard_Config 序列化为符合 Requirement 1 中模式的配置 JSON 文本，并包含该 Keyboard_Config 的每个比例字段、Corner_Radius、Row_Config 和 Key_Config 值。
9. FOR ALL 有效的扁平运行时 Keyboard_Config 值，序列化该 Keyboard_Config 然后解析所得到的配置 JSON 文本 SHALL 生成一个与原始 Keyboard_Config 相等的 Keyboard_Config，包括所有比例字段值、Row_Config 和 Key_Config 条目的顺序、所有背景色值以及所有 Sub_Label 值。
10. WHEN 反序列化某个 Theme、Template 或 Layout 文件文本时，THE Config_Parser SHALL 将其反序列化为对应的 Authoring 对象（Theme、Template 或 Layout）。
11. IF 某个 Theme、Template 或 Layout 文件在语法上无效、缺少必需字段、字段类型与模式不匹配、或包含不匹配 "#AARRGGBB" 格式的颜色值，THEN THE Config_Parser SHALL 返回一个标识出错文件与字段的解析错误，并 SHALL NOT 产出该 Authoring 对象，且 SHALL NOT 产出运行时 Keyboard_Config。

### Requirement 6: 无效配置时的回退与默认配置布局

**User Story:** 作为用户，我希望即使配置损坏键盘仍能渲染，以便我始终可以输入。

#### Acceptance Criteria

1. IF the Config_Parser 返回解析错误，或 the Resolver 返回解析展开错误，THEN THE Keyboard_UI_System SHALL 选择 Default_Config 作为活动配置，并在 Default_Config 被渲染之前保留现有已渲染的键盘区。
2. IF 配置源（Layout、Theme 或 Template 文件）缺失、为空或不可读，THEN THE Keyboard_UI_System SHALL 选择 Default_Config 作为活动配置。
3. WHEN the Keyboard_UI_System 选择 Default_Config 时，THE Keyboard_Renderer SHALL 在该选择后 500 毫秒内使用 Default_Config 渲染键盘区。
4. WHEN the Keyboard_Renderer 在无效配置之后使用 Default_Config 渲染键盘区时，THE Keyboard_UI_System SHALL 显示一个可见提示，表明正在使用回退配置。
5. THE Default_Config SHALL 以 Authoring 格式编写，由一个共享 Theme 文件（themeId "light"）、一个 Template 文件（名为 "common"，含 Key_Template $shift、$del、$space、$enter、$123、$lang 与 Row_Template $functionRow）、以及一个英文 Layout 文件（layoutId "en"）组成。
6. WHEN the Resolver 展开 Default_Config 时，THE 展开得到的扁平 Keyboard_Config SHALL 逐字段等于本 Requirement 参考值表所定义的默认配置（4 行布局、各 Sub_Label、Special_Key 集合、比例参考值、填充与垂直分区合计均不变）。
7. THE Default_Config 的展开结果 SHALL 定义一个恰好包含 4 行的 QWERTY 键盘区布局。
8. THE Default_Config 第 1 行 SHALL 依次包含 10 个字面字母按键 Q、W、E、R、T、Y、U、I、O、P，且其 Sub_Label 依次为 "1"、"2"、"3"、"4"、"5"、"6"、"7"、"8"、"9"、"0"。
9. THE Default_Config 第 2 行 SHALL 依次包含 9 个字面字母按键 A、S、D、F、G、H、J、K、L，且其 Sub_Label 依次为 "-"、"/"、":"、";"、"("、")"、"~"、"“"（开引号）、"”"（闭引号）。
10. THE Default_Config 第 3 行 SHALL 依次包含 Shift 键（主文本显示 "⇧"）、7 个字面字母按键 Z、X、C、V、B、N、M、以及 Del 键（主文本显示 "⌫"），其中字母按键的 Sub_Label 依次为 Z→"@"、X→"."、C→"#"、V→"、"（顿号）、B→"?"、N→"!"、M→"…"。
11. THE Default_Config 第 4 行 SHALL 依次包含 "123" 键、逗号键（主文本显示 "，"，Sub_Label 为 "。"）、Space 键、语言切换键(中/英)（主文本显示 "中/英"）、以及 Enter 键（主文本显示 "换行"）。
12. THE Default_Config 的 Special_Key 集合 SHALL 恰好为 {Shift, Del, 123, Space, 语言切换键(中/英), Enter}。
13. THE Keyboard_UI_System SHALL 将 Default_Config 视为始终有效，使得 Default_Config 的展开结果始终通过解析与校验，且选择 Default_Config 永远不会导致解析错误、解析展开错误或渲染失败。
14. THE Default_Config SHALL 采用以下键盘级比例值：Keyboard_Region_Height_Ratio = 0.27；Side_Margin_Ratio = 7/360；Horizontal_Gap_Ratio = 4/360；Top_Margin_Ratio = 4/216；Bottom_Margin_Ratio = 4/216；Vertical_Gap_Ratio = 8/216；Normal_Key_Height_Ratio = 46/216；Corner_Radius = 8 dp。
15. THE Default_Config SHALL 为第 1、2、3 行中的每个字面字母按键设置 Key_Width_Ratio = 31/360。
16. THE Default_Config SHALL 为各 Special_Key 与逗号键设置如下 Key_Width_Ratio（由 360×800 参考分辨率标定，特殊键宽度与文本尺寸比例标注为待高保真校准的占位值）：Shift = 48.5/360；Del = 48.5/360；123 = 46/360；逗号键(，) = 31/360；Space = 161/360；语言切换键(中/英) = 46/360；Enter(换行) = 46/360。
17. THE Default_Config 的 authoring 源中，Space 键（common templates 中的 $space）SHALL 以 fill（Fill_Width）宽度声明（其所在功能行的其余按键为固定宽度），并由 Resolver 在英文默认布局、360×800 参考下展开为具体的 Key_Width_Ratio，且该展开结果 SHALL 等于本 Requirement 参考值表中的 Space = 161/360。
18. THE Default_Config SHALL 使第 1 行、第 3 行、第 4 行的水平占用合计均等于 1.0（填满键盘宽度），并使第 2 行的水平占用合计约等于 0.903（小于 1.0，因而居中缩进）。
19. THE Default_Config SHALL 使垂直分区合计等于 1.0（4/216 + 4/216 + 4 × 46/216 + 3 × 8/216 = 216/216）。
20. THE Default_Config SHALL 为每个按键设置 Main_Text_Size_Ratio ≈ 0.45（占正常键高的分数，占位值，待高保真校准）。
21. THE Default_Config SHALL 为每个带 Sub_Label 的按键设置 Sub_Label_Size_Ratio ≈ 0.22（占正常键高的分数，占位值，待高保真校准）。

#### Default_Config 参考值表（基于 360×800 参考分辨率）

| 配置项 | 参考 dp（@360×800） | 存储的比例值 | 基准 | 备注 |
| --- | --- | --- | --- | --- |
| Top_Region 高度 | 52 | Top_Region_Height_Ratio = 0.065 | 屏幕高度 H | Top_Region 组件内置常量，非 Keyboard_Config 字段（见 Requirement 10） |
| Keyboard_Region 高度 | 216 | Keyboard_Region_Height_Ratio = 0.27 | 屏幕高度 H | — |
| 侧边外边距（左/右） | 7 | Side_Margin_Ratio = 7/360 ≈ 0.01944 | 键盘宽度 W | — |
| 水平间距 | 4 | Horizontal_Gap_Ratio = 4/360 ≈ 0.01111 | 键盘宽度 W | — |
| 上外边距 | 4 | Top_Margin_Ratio = 4/216 ≈ 0.01852 | 键盘区高度 | — |
| 下外边距 | 4 | Bottom_Margin_Ratio = 4/216 ≈ 0.01852 | 键盘区高度 | — |
| 行垂直间距 | 8 | Vertical_Gap_Ratio = 8/216 ≈ 0.03704 | 键盘区高度 | — |
| 正常键高 | 46 | Normal_Key_Height_Ratio = 46/216 ≈ 0.21296 | 键盘区高度 | 键盘级统一 |
| 圆角半径 | 8 | Corner_Radius = 8 dp | 固定 dp | 不缩放 |
| 普通字母键宽 | 31 | Key_Width_Ratio = 31/360 ≈ 0.08611 | 键盘宽度 W | — |
| Shift 键宽 | 48.5 | Key_Width_Ratio = 48.5/360 ≈ 0.13472 | 键盘宽度 W | 占位，待校准 |
| Del 键宽 | 48.5 | Key_Width_Ratio = 48.5/360 ≈ 0.13472 | 键盘宽度 W | 占位，待校准 |
| 123 键宽 | 46 | Key_Width_Ratio = 46/360 ≈ 0.12778 | 键盘宽度 W | 占位，待校准 |
| 逗号键(，) 宽 | 31 | Key_Width_Ratio = 31/360 ≈ 0.08611 | 键盘宽度 W | — |
| Space 键宽 | 161 | Key_Width_Ratio = 161/360 ≈ 0.44722 | 键盘宽度 W | 占位，待校准；为 en 默认功能行 fill 宽度（Fill_Width）经 Resolver 展开的结果 |
| 中/英 键宽 | 46 | Key_Width_Ratio = 46/360 ≈ 0.12778 | 键盘宽度 W | 占位，待校准 |
| Enter(换行) 键宽 | 46 | Key_Width_Ratio = 46/360 ≈ 0.12778 | 键盘宽度 W | 占位，待校准 |
| 主文本尺寸 | — | Main_Text_Size_Ratio ≈ 0.45 | 正常键高 | 占位，待校准 |
| Sub_Label 尺寸 | — | Sub_Label_Size_Ratio ≈ 0.22 | 正常键高 | 占位，待校准 |

行水平占用合计校验（基准为键盘宽度 W）：

| 行 | 按键数 | 键宽合计 | 水平间距合计 | 侧边外边距合计 | 总计 | 结果 |
| --- | --- | --- | --- | --- | --- | --- |
| 第 1 行 | 10 | 310/360 | 9 × 4/360 = 36/360 | 2 × 7/360 = 14/360 | 360/360 = 1.0 | 填满 |
| 第 2 行 | 9 | 279/360 | 8 × 4/360 = 32/360 | 14/360 | 325/360 ≈ 0.903 | 居中缩进 |
| 第 3 行 | 9 | 314/360 | 8 × 4/360 = 32/360 | 14/360 | 360/360 = 1.0 | 填满 |
| 第 4 行 | 5 | 330/360 | 4 × 4/360 = 16/360 | 14/360 | 360/360 = 1.0 | 填满 |

### Requirement 7: 分辨率自适应缩放

**User Story:** 作为用户，我希望键盘根据设备屏幕的实际分辨率按比例缩放，以便键盘在不同尺寸与分辨率的设备上都呈现一致且正确的布局。

#### Acceptance Criteria

1. WHEN the Keyboard_Renderer 渲染键盘区时，THE Keyboard_Renderer SHALL 将键盘总宽度设置为设备屏幕宽度 W（像素）。
2. WHEN the Keyboard_Renderer 计算 Top_Region 高度时，THE Keyboard_Renderer SHALL 将其设置为 H × 内置的 Top_Region_Height_Ratio 常量（像素），其中 H 为设备屏幕高度（像素）。
3. WHEN the Keyboard_Renderer 计算 Keyboard_Region 高度时，THE Keyboard_Renderer SHALL 将其设置为 H × Keyboard_Region_Height_Ratio（像素）。
4. WHEN the Keyboard_Renderer 计算左右侧边外边距像素值时，THE Keyboard_Renderer SHALL 将其各自设置为 Side_Margin_Ratio × W。
5. WHEN the Keyboard_Renderer 计算某个按键的宽度像素值时，THE Keyboard_Renderer SHALL 将其设置为该按键 Key_Width_Ratio × W。
6. WHEN the Keyboard_Renderer 计算同一行内相邻两按键之间的水平间距像素值时，THE Keyboard_Renderer SHALL 将其设置为 Horizontal_Gap_Ratio × W。
7. WHEN the Keyboard_Renderer 计算上、下外边距像素值时，THE Keyboard_Renderer SHALL 将其分别设置为 Top_Margin_Ratio × Keyboard_Region 高度 和 Bottom_Margin_Ratio × Keyboard_Region 高度。
8. WHEN the Keyboard_Renderer 计算相邻两行之间的行垂直间距像素值时，THE Keyboard_Renderer SHALL 将其设置为 Vertical_Gap_Ratio × Keyboard_Region 高度。
9. WHEN the Keyboard_Renderer 计算正常键高像素值时，THE Keyboard_Renderer SHALL 将其设置为 Normal_Key_Height_Ratio × Keyboard_Region 高度，并将该统一高度应用于所有按键。
10. WHEN the Keyboard_Renderer 计算主文本字号时，THE Keyboard_Renderer SHALL 将其设置为 Main_Text_Size_Ratio × 正常键高像素值。
11. WHEN the Keyboard_Renderer 计算 Sub_Label 字号时，THE Keyboard_Renderer SHALL 将其设置为 Sub_Label_Size_Ratio × 正常键高像素值。
12. THE Keyboard_Renderer SHALL 在计算文本字号时不使用缩放无关像素（sp），且 SHALL NOT 应用系统字号缩放设置。
13. WHEN the Keyboard_Renderer 渲染按键圆角时，THE Keyboard_Renderer SHALL 使用 Corner_Radius 的固定 dp 值，且 SHALL NOT 按屏幕分辨率缩放该值。
14. WHEN 某个 Row_Config 的水平占用合计等于 1.0，THE Keyboard_Renderer SHALL 使该行填满键盘宽度，左右不附加额外内缩。
15. WHEN 某个 Row_Config 的水平占用合计小于 1.0，THE Keyboard_Renderer SHALL 将该行水平居中，把剩余宽度（W × (1.0 − 水平占用合计)）平均分配为该行左右两侧的附加内缩。

### Requirement 8: 配置驱动的渲染

**User Story:** 作为用户，我希望键盘区按照配置呈现，以便其渲染结果的布局和样式与所配置的比例值缩放后的结果匹配。

#### Acceptance Criteria

1. WHEN 提供 Keyboard_Config 时，THE Keyboard_Renderer SHALL 使渲染后的键盘区按 Row_Config 在 Keyboard_Config 中出现的顺序自上而下呈现各行。
2. WHEN 渲染某个 Row_Config 时，THE Keyboard_Renderer SHALL 使该行内各按键按 Key_Config 在 Row_Config 中出现的顺序自左向右呈现。
3. WHEN 渲染键盘区时，THE Keyboard_Renderer SHALL 在键盘区左右保留等于 Side_Margin_Ratio × W 的外边距像素值，并在上下保留分别等于 Top_Margin_Ratio × Keyboard_Region 高度 与 Bottom_Margin_Ratio × Keyboard_Region 高度 的外边距像素值。
4. WHEN 渲染某个按键时，THE Keyboard_Renderer SHALL 使该按键的可见宽度等于 Key_Width_Ratio × W、可见高度等于 Normal_Key_Height_Ratio × Keyboard_Region 高度。
5. WHEN 渲染某个按键时，THE Keyboard_Renderer SHALL 使用从 "#AARRGGBB" 格式解析的 Normal_Background_Color 填充该按键的 Key_Rectangle 区域。
6. WHEN 渲染某个按键时，THE Keyboard_Renderer SHALL 使该按键四角呈现为半径等于 Corner_Radius 固定 dp 值的圆角。
7. WHEN 渲染同一行内相邻的两个按键时，THE Keyboard_Renderer SHALL 在该两个按键之间保留等于 Horizontal_Gap_Ratio × W 的水平间隔像素值。
8. WHEN 渲染相邻的两行时，THE Keyboard_Renderer SHALL 在该两行之间保留等于 Vertical_Gap_Ratio × Keyboard_Region 高度 的垂直间隔像素值。
9. WHEN 渲染某个按键时，THE Keyboard_Renderer SHALL 使该按键主文本以 Key_Config 的主文本内容、从 "#AARRGGBB" 格式解析的主文本颜色以及等于 Main_Text_Size_Ratio × 正常键高像素值 的字号呈现。
10. WHERE 某个 Key_Config 定义了 Sub_Label，THE Keyboard_Renderer SHALL 使该 Sub_Label 呈现在主文本上方，并使用 Sub_Label 内容、从 "#AARRGGBB" 格式解析的 Sub_Label 颜色以及等于 Sub_Label_Size_Ratio × 正常键高像素值 的字号。
11. WHILE 某个按键处于按下状态（有 Touch_Point 落于其 Key_Rectangle 内且尚未抬起），THE Keyboard_Renderer SHALL 使该按键以其 Pressed_Background_Color（配置的或推导的）呈现。
12. WHEN 该按键上的 Touch_Point 抬起时，THE Keyboard_Renderer SHALL 使该按键恢复以其 Normal_Background_Color 呈现。
13. IF 某个 Key_Config 主文本颜色值或 Sub_Label 颜色值不是有效的 "#AARRGGBB" 字符串，THEN THE Keyboard_Renderer SHALL 使用对应的 Default_Config 颜色值呈现受影响的文本，并呈现该按键其余内容。
14. IF 提供的 Keyboard_Config 包含零个 Row_Config 条目，THEN THE Keyboard_Renderer SHALL 使用 Default_Config 呈现键盘区。

### Requirement 9: 键盘区高度尺寸

**User Story:** 作为用户，我希望键盘区高度由屏幕高度按比例直接确定，以便它在不同设备上占据稳定的输入区域高度，而不会填满整个屏幕。

#### Acceptance Criteria

1. WHEN the Keyboard_Renderer 渲染键盘区时，THE Keyboard_Renderer SHALL 将键盘区高度定义为 H × Keyboard_Region_Height_Ratio（像素），其中 H 为设备屏幕高度；该值为权威高度，不由各行高度求和推导。
2. WHEN the Keyboard_Renderer 渲染 Top_Region 时，THE Keyboard_Renderer SHALL 将 Top_Region 高度定义为 H × 内置的 Top_Region_Height_Ratio 常量（像素），该常量由 Top_Region 组件拥有，不在 Keyboard_Config 中定义。
3. WHEN the Keyboard_Renderer 在键盘区高度内排布垂直元素时，THE Keyboard_Renderer SHALL 使上外边距、各行键高与各行间距共同分割键盘区高度，并使垂直分区合计在容差 ±0.001 内等于 1.0。
4. WHEN the Keyboard_Renderer 渲染键盘区时，THE Keyboard_Renderer SHALL 将 H × Keyboard_Region_Height_Ratio 作为键盘区高度应用，而非整个可用显示高度。
5. IF Top_Region 高度与 Keyboard_Region 高度之和超过可用显示高度，THEN THE Keyboard_Renderer SHALL 将两者按比例同时收缩，使其之和约束为可用显示高度。

### Requirement 10: 顶部区域（工具栏与候选词栏）

**User Story:** 作为用户，我希望在未输入时看到工具栏、在输入时看到候选词，以便我既能快速使用工具栏功能，又能在输入时获得候选词。

#### Acceptance Criteria

1. THE Top_Region SHALL 作为独立组件渲染在 Keyboard_Region 的正上方，且其存在、布局与内容不受 Keyboard_Config 控制。
2. THE Top_Region SHALL 将自身高度设置为 Screen_Height H × 内置的 Top_Region_Height_Ratio 常量（值为 0.065），该高度比例常量由 Top_Region 组件拥有，不在 Keyboard_Config 中定义。
3. WHILE Word_Buffer 为空，THE Top_Region SHALL 显示 Toolbar。
4. WHILE Word_Buffer 非空，THE Top_Region SHALL 显示 Prediction_Bar，并在 Prediction_Bar 中呈现候选词预测。
5. WHEN 当前输入被提交（commit）或 Word_Buffer 被清空，THE Top_Region SHALL 恢复显示 Toolbar。
6. WHILE Top_Region 显示 Toolbar，THE Toolbar SHALL 恰好包含两个元素：居左的 Apps_Entry 与居右的 Collapse_Keyboard_Button。
7. WHEN Apps_Entry 被按下时，THE Top_Region SHALL 呈现 Apps_Entry 的按下态视觉。
8. WHEN Apps_Entry 被按下时，THE Top_Region SHALL NOT 执行除按下态视觉以外的任何功能（本轮 Apps_Entry 为占位元素）。
9. WHEN Collapse_Keyboard_Button 被按下时，THE Top_Region SHALL 呈现 Collapse_Keyboard_Button 的按下态视觉。
10. WHEN Collapse_Keyboard_Button 被按下时，THE IME_Service SHALL 收起（隐藏）输入法。

### Requirement 11: 保留的键盘区输入行为

**User Story:** 作为用户，我希望既有的键盘区输入行为继续正常工作，以便此次重新架构不会导致功能退化。

#### Acceptance Criteria

1. WHEN 某个字面字符按键（其 Action_Value 为单个可打印字符的按键）被触发时，THE Keyboard_Renderer SHALL 向 IME_Service 输出该按键的 Action_Value。
2. WHEN 逗号键被触发时，THE Keyboard_Renderer SHALL 向 IME_Service 输出 Action_Value "，"。
3. WHEN Del 键被触发时，THE Keyboard_Renderer SHALL 向 IME_Service 输出 Action_Value "Del"。
4. WHEN Enter 键被触发时，THE Keyboard_Renderer SHALL 向 IME_Service 输出 Action_Value "Enter"。
5. WHEN Space 键被触发时，THE Keyboard_Renderer SHALL 向 IME_Service 输出 Action_Value " "。
6. WHEN 123 键被触发时，THE Keyboard_Renderer SHALL 呈现 123 键的按下态视觉。
7. WHEN 123 键被触发时，THE Keyboard_Renderer SHALL NOT 切换键盘区布局，且 SHALL NOT 产生任何 Action_Value 输出（本轮 123 键为占位按键）。
8. WHEN 语言切换键(中/英) 被触发时，THE Keyboard_Renderer SHALL 呈现语言切换键(中/英) 的按下态视觉。
9. WHEN 语言切换键(中/英) 被触发时，THE Keyboard_Renderer SHALL NOT 切换输入语言，且 SHALL NOT 产生任何 Action_Value 输出（本轮语言切换键为占位按键）。
10. WHEN Shift 键被单击（single tap）触发时，THE Keyboard_Renderer SHALL 激活 shift（临时大写，仅作用于其后第一个被触发的字面字母按键）。
11. WHEN Shift 键在 300 毫秒内被双击（double tap）触发时，THE Keyboard_Renderer SHALL 激活 caps lock（持续大写）。
12. WHILE caps lock 处于激活状态，WHEN Shift 键被单击触发时，THE Keyboard_Renderer SHALL 取消激活 caps lock。
13. WHILE shift 处于激活状态或 caps lock 处于激活状态，WHEN 某个字面字母按键被触发时，THE Keyboard_Renderer SHALL 输出该字母 Action_Value 的大写形式。
14. WHEN 某个定义了 Sub_Label 的按键被触发时，THE Keyboard_Renderer SHALL 仅依据该按键的 Action_Value 进行处理，且 SHALL NOT 输入或响应其 Sub_Label（本轮 Sub_Label 仅用于显示）。
15. WHEN 任意按键被触发并产生 Action_Value 时，THE Keyboard_Renderer SHALL 在该次触发被登记后的 100 毫秒内向 IME_Service 输出对应的 Action_Value。
16. WHILE 用户进行连续输入或多指触摸输入，THE Keyboard_Renderer SHALL 在不丢帧的情况下渲染键盘区更新，维持 60 帧每秒的目标（即每帧渲染工作在 16 毫秒内完成）。
17. IF 在 Keyboard_Renderer 与 IME_Service 之间不存在活动输入连接时某个按键被触发，THEN THE Keyboard_Renderer SHALL 丢弃该次触发而不输出 Action_Value，并 SHALL 保留当前的 shift 和 caps lock 状态。

### Requirement 12: 多指触摸输入处理

**User Story:** 作为用户，我希望键盘正确处理多指快速连续按键，以便我可以像在主流输入法上那样以滚动按键（rollover）的方式快速输入。

#### Acceptance Criteria

1. WHEN 某个 Touch_Point 的触摸按下（touch-down）坐标落入某按键的 Key_Rectangle（像素矩形）内，THE Keyboard_Renderer SHALL 在该 touch-down 时刻触发该按键并输出其 Action_Value 恰好一次。
2. WHEN 某个 Touch_Point 抬起（touch-up），THE Keyboard_Renderer SHALL 仅清除其所在按键的按下态视觉，并 SHALL NOT 因该次抬起而输出任何 Action_Value。
3. WHILE 多个 Touch_Point 同时处于按下状态，THE Keyboard_Renderer SHALL 依据各自的 Pointer_Identifier 对每个 Touch_Point 独立执行命中测试，并独立触发各自命中的按键。
4. IF 某个 Touch_Point 的 touch-down 坐标落在侧边外边距、水平间距、行垂直间距或上下外边距区域内（未落入任何 Key_Rectangle），THEN THE Keyboard_Renderer SHALL 忽略该 touch-down，不输出任何 Action_Value，且不影响其他 Touch_Point。
5. IF 某个已触发并已输出 Action_Value 的 Touch_Point 在抬起前移动到另一个 Key_Rectangle 或移出键盘区范围，THEN THE Keyboard_Renderer SHALL NOT 因该次移动再次输出任何 Action_Value。
6. THE Keyboard_Renderer SHALL 最多同时跟踪 10 个 Touch_Point。
7. IF 在已有 10 个 Touch_Point 处于按下状态时发生额外的并发 touch-down，THEN THE Keyboard_Renderer SHALL 忽略该额外的 touch-down，不输出 Action_Value，且不影响已跟踪的 Touch_Point。
8. WHILE shift 处于激活状态且 caps lock 处于未激活状态，WHEN 按 touch-down 顺序最先被触发的字面字母按键被触发时，THE Keyboard_Renderer SHALL 输出该字母 Action_Value 的大写形式，并随后取消激活 shift。

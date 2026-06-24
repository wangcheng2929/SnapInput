# SnapInput

一个基于 Android 的自定义输入法（IME）项目，采用扁平化多模块 + Clean Architecture 分层，使用 Jetpack Compose 构建键盘 UI，Hilt 做依赖注入，Room 做本地词库与词频学习。

- 包名：`com.study.snapinput`
- 最低版本：Android 7.0（API 24）／目标 Android 16（API 36）
- 构建：AGP 9.1.1 + Kotlin 2.3.20（AGP 内置 Kotlin）+ KSP

---

## 模块结构

```
SnapInput/
├── :app                        入口 + IME Service 装配（编排层）
│
├── feature/                    分组目录（自身非 module）
│   ├── :feature:keyboard       键盘 UI（无状态组件）
│   ├── :feature:prediction     候选词 UI（无状态组件）
│   └── :feature:settings       设置界面
│
├── :core                       业务规则：实体 + Repository 接口 + UseCase + IME 工具
├── :data                       Room 数据库 + Repository 实现 + Hilt Module
└── :ui                         Compose 主题与共享组件
```

## 依赖方向

```
                    :app
                 /   |   \   \
   :feature:keyboard │    \    \
   :feature:prediction    │     \
   :feature:settings      │      \
         |   \            |       |
        :ui  :core ←──────┴── :data
       (叶子)(叶子)        (data 实现 core 的接口)
```

| 模块 | 依赖 | 职责 |
|------|------|------|
| `:app` | feature×3, core, data, ui | 组装、`SnapInputMethodService`、`MainActivity`、Hilt 根 |
| `:feature:keyboard` | ui | `KeyboardLayout` / `KeyButton`（无状态，按键上抛宿主） |
| `:feature:prediction` | ui | `PredictionBar`（无状态候选词条） |
| `:feature:settings` | ui | `SettingsScreen` / `SettingsActivity` |
| `:core` | 无（业务核心） | `Word`、`WordRepository` 接口、`GetPredictionsUseCase`、`LearnWordUseCase`、`LanguageManager` |
| `:data` | core | `WordEntity` / `WordDao` / `WordDatabase` / `WordRepositoryImpl` / Hilt Module |
| `:ui` | 无（设计系统叶子） | `SnapInputTheme`、Color、Type |

设计要点：
- 依赖统一向下收敛到 `:core`，`:data` 实现 `:core` 定义的接口（依赖倒置）。
- IME Service 提到 `:app`，可同时拿到键盘 UI 与业务 UseCase，解决了"service 在底层却要用上层 UI"的断裂。
- feature 的 Compose 组件**保持无状态**，宿主无关——既可被 IME Service 挂载，也可被 Activity 复用，规避了 `hiltViewModel()` 在非 Activity 宿主中不可用的问题。

---

## 核心流程

### IME 编排（`:app` → `SnapInputMethodService`）
Service 继承 `InputMethodService`，并实现 `LifecycleOwner` / `ViewModelStoreOwner` / `SavedStateRegistryOwner` 以承载 Compose。通过 Hilt 注入 `GetPredictionsUseCase` / `LearnWordUseCase` / `LanguageManager`，在 `onCreateInputView()` 用 `ComposeView` 挂载候选词条 + 键盘：

```
按键 → KeyboardLayout.onKeyPressed → Service.handleKey
     ├─ 字母      → InputConnection.commitText + 刷新候选词
     ├─ 空格/回车 → 提交 + 学习当前词（LearnWordUseCase）
     └─ 删除      → deleteSurroundingText + 刷新候选词
选中候选词 → 替换前缀文本 + 学习
```

### 词频学习与预测（`:core` UseCase → `:data`）
`GetPredictionsUseCase` / `LearnWordUseCase` 调用 `WordRepository`（接口在 core，实现在 data）。`WordRepositoryImpl` 用 Room 落地：已存在的词 `frequency + 1`，否则插入新词；候选词按词频降序返回。

---

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 / UI | Kotlin / Jetpack Compose + Material3 | 2.3.20 / BOM 2026.03.01 |
| 依赖注入 | Hilt | 2.59.2 |
| 本地数据库 | Room | 2.8.4 |
| 日志 | Timber | 5.0.1 |
| 构建 | AGP / KSP | 9.1.1 / 2.3.6 |

版本由 `gradle/libs.versions.toml`（Version Catalog）统一管理。

---

## 构建与启用

```bash
./gradlew :app:assembleDebug
```

安装后，进入 系统设置 → 语言与输入法 → 启用 **SnapInput** → 在输入框切换。App 首页提供「打开输入法设置」与「SnapInput 设置」快捷入口。

---

## 待完成

- [ ] 数字键盘 / 符号键盘布局（当前 `123` / `Sym` 为占位）
- [ ] 设置项持久化（DataStore）并接入预测/反馈开关
- [ ] 候选词条样式细化、长列表横向滚动
- [ ] 中文拼音组词（当前预测为前缀匹配）
- [ ] 单元测试（UseCase / Repository）

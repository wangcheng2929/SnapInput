package com.study.snapinput.core.config

import com.study.snapinput.core.config.authoring.AuthoringWidth
import com.study.snapinput.core.config.authoring.InlineKey
import com.study.snapinput.core.config.authoring.KeyDefaults
import com.study.snapinput.core.config.authoring.KeySpec
import com.study.snapinput.core.config.authoring.KeySpecOrRef
import com.study.snapinput.core.config.authoring.KeyTemplateRef
import com.study.snapinput.core.config.authoring.LayoutFile
import com.study.snapinput.core.config.authoring.RowSpec
import com.study.snapinput.core.config.authoring.RowSpecOrRef
import com.study.snapinput.core.config.authoring.RowTemplateRef
import com.study.snapinput.core.config.authoring.TemplateFile
import com.study.snapinput.core.config.authoring.TextSpec
import com.study.snapinput.core.config.authoring.Theme
import com.study.snapinput.core.config.model.KeyConfig
import com.study.snapinput.core.config.model.KeyboardConfig
import com.study.snapinput.core.config.model.RowConfig
import com.study.snapinput.core.config.model.SubLabel
import com.study.snapinput.core.config.model.TextStyleConfig

/**
 * 解析展开（resolve）结果。
 *
 * - [Success]：展开成功，携带字段完全补全的扁平 [KeyboardConfig]。
 * - [Failure]：展开失败，携带带原因的 [ConfigError]，且不产出任何配置。
 */
sealed interface ResolveResult {
    /** 展开成功。 */
    data class Success(val config: KeyboardConfig) : ResolveResult
    /** 展开失败，原因见 [error]。 */
    data class Failure(val error: ConfigError) : ResolveResult
}

/**
 * 解析展开器（纯函数）：把 authoring（active layout + 其 theme + templates）
 * 确定性地展开为字段完全补全的扁平 [KeyboardConfig]。
 *
 * 本对象只负责「展开 + 引用/简写/合并的结构错误」：
 * - 模板索引（跨文件重名 → [Reason.DUPLICATE_TEMPLATE]）；
 * - rows 展开（行模板 `$ref` / [RowSpec.KeysRow] / [RowSpec.LettersRow]）；
 * - 字母简写展开（`action = c.lowercase()`、`content = c`、subLabels 按下标对齐、lead/trail）；
 * - key 模板 `$ref` 展开与覆盖；
 * - 字段合并优先级 `theme.keyDefaults[class] < template < inline`，`mainText` / `subLabel` 深合并；
 * - 未知 `$ref` → [Reason.UNKNOWN_REF]；未知 Key_Class → [Reason.UNKNOWN_KEY_CLASS]；
 *   subLabels 长度不符 → [Reason.LETTERS_SUBLABELS_MISMATCH]。
 *
 * 注意：fill 宽度的剩余宽度约束（[Reason.ROW_OVERFLOW]）与对展开产物的
 * [KeyboardConfigValidator] 校验集成已在 [expandFillWidths] / [resolve] 中完成：
 * 含 fill 行 `remaining ≤ 0` → [Reason.ROW_OVERFLOW]；展开产物若不满足 R1 约束，
 * 返回对应 [ResolveResult.Failure] 且不产出配置。
 */
object KeyboardConfigResolver {

    /**
     * 将 [layout] 及其引用的 [theme] 与 [templates] 展开为扁平 [KeyboardConfig]。
     * 任一展开失败返回带原因的 [ConfigError]，并不产出配置。
     */
    fun resolve(layout: LayoutFile, theme: Theme, templates: List<TemplateFile>): ResolveResult {
        return try {
            // 步骤 1：建立模板索引（跨文件重名 → DUPLICATE_TEMPLATE）。
            val index = buildTemplateIndex(templates)

            // 步骤 2-5：逐行展开为「按键草稿」（字段已按优先级合并，宽度记为 Fixed/Fill）。
            val draftRows: List<List<ResolvedKeyDraft>> = layout.rows.map { rowEntry ->
                expandRow(rowEntry, theme, index)
            }

            // 步骤 6-7：键盘级字段直接取自 theme；逐行展开 fill 宽度为具体比例。
            val rows = expandFillWidths(draftRows, theme)

            // 步骤 8：产出扁平 KeyboardConfig。
            val config = KeyboardConfig(
                keyboardRegionHeightRatio = theme.keyboardRegionHeightRatio,
                sideMarginRatio = theme.sideMarginRatio,
                horizontalGapRatio = theme.horizontalGapRatio,
                topMarginRatio = theme.topMarginRatio,
                bottomMarginRatio = theme.bottomMarginRatio,
                verticalGapRatio = theme.verticalGapRatio,
                normalKeyHeightRatio = theme.normalKeyHeightRatio,
                cornerRadiusDp = theme.cornerRadiusDp,
                rows = rows
            )

            // 步骤 9：对展开产物执行 KeyboardConfigValidator 校验（R3.14-R3.15）。
            // 不满足 R1 约束时返回首个 Failure，且不产出配置。
            val validationErrors = KeyboardConfigValidator.validate(config)
            if (validationErrors.isNotEmpty()) {
                return ResolveResult.Failure(validationErrors.first())
            }

            ResolveResult.Success(config)
        } catch (e: ResolveException) {
            ResolveResult.Failure(e.error)
        }
    }

    // ——————————————————————————————————————————————
    // 步骤 1：模板索引
    // ——————————————————————————————————————————————

    /** 合并后的模板索引：按键模板表与行模板表。 */
    private class TemplateIndex(
        val keyTemplates: Map<String, KeySpec>,
        val rowTemplates: Map<String, RowSpec>
    )

    /**
     * 合并 [templates] 中各文件的 keyTemplates / rowTemplates 为两张名称表。
     * 跨文件出现重名的 key/row 模板 → [Reason.DUPLICATE_TEMPLATE]（R3.18）。
     */
    private fun buildTemplateIndex(templates: List<TemplateFile>): TemplateIndex {
        val keyTemplates = mutableMapOf<String, KeySpec>()
        val rowTemplates = mutableMapOf<String, RowSpec>()

        for (file in templates) {
            for ((name, spec) in file.keyTemplates) {
                if (keyTemplates.containsKey(name)) {
                    throw ResolveException(
                        ConfigError("keyTemplates.$name", Reason.DUPLICATE_TEMPLATE, name)
                    )
                }
                keyTemplates[name] = spec
            }
            for ((name, spec) in file.rowTemplates) {
                if (rowTemplates.containsKey(name)) {
                    throw ResolveException(
                        ConfigError("rowTemplates.$name", Reason.DUPLICATE_TEMPLATE, name)
                    )
                }
                rowTemplates[name] = spec
            }
        }
        return TemplateIndex(keyTemplates, rowTemplates)
    }

    // ——————————————————————————————————————————————
    // 步骤 2：rows 展开
    // ——————————————————————————————————————————————

    /**
     * 展开单行：行模板引用（[RowTemplateRef]）/ 显式键行（[RowSpec.KeysRow]）/
     * 字母行简写（[RowSpec.LettersRow]）。
     */
    private fun expandRow(
        entry: RowSpecOrRef,
        theme: Theme,
        index: TemplateIndex
    ): List<ResolvedKeyDraft> {
        return when (entry) {
            is RowTemplateRef -> {
                // 行模板引用：查名缺失 → UNKNOWN_REF（R3.17）。
                val rowSpec = index.rowTemplates[entry.name]
                    ?: throw ResolveException(
                        ConfigError("rows.\$${entry.name}", Reason.UNKNOWN_REF, entry.name)
                    )
                expandRowSpec(rowSpec, theme, index)
            }
            is RowSpec -> expandRowSpec(entry, theme, index)
        }
    }

    /** 展开行规格（[RowSpec.KeysRow] / [RowSpec.LettersRow]）。 */
    private fun expandRowSpec(
        rowSpec: RowSpec,
        theme: Theme,
        index: TemplateIndex
    ): List<ResolvedKeyDraft> {
        return when (rowSpec) {
            is RowSpec.KeysRow -> rowSpec.keys.map { expandKey(it, theme, index) }
            is RowSpec.LettersRow -> expandLettersRow(rowSpec, theme, index)
        }
    }

    // ——————————————————————————————————————————————
    // 步骤 3：字母简写展开
    // ——————————————————————————————————————————————

    /**
     * 字母行简写展开（R3.4-R3.7）：
     * - 第 i 个字符 c 生成一个 [RowSpec.LettersRow.keyClass] 类按键，`action = c.lowercase()`、`mainText.content = c`；
     * - 若提供 subLabels，第 i 个 subLabel 按下标对齐为第 i 键的子标签（长度须等于 letters，否则 [Reason.LETTERS_SUBLABELS_MISMATCH]）；
     * - lead（若有）为最左侧特殊键，trail（若有）为最右侧特殊键。
     */
    private fun expandLettersRow(
        row: RowSpec.LettersRow,
        theme: Theme,
        index: TemplateIndex
    ): List<ResolvedKeyDraft> {
        val letters = row.letters
        val subLabels = row.subLabels

        // subLabels 长度必须等于 letters 字符数（R3.19）。
        if (subLabels != null && subLabels.size != letters.length) {
            throw ResolveException(
                ConfigError("letters[$letters].subLabels", Reason.LETTERS_SUBLABELS_MISMATCH, subLabels.size.toString())
            )
        }

        val result = mutableListOf<ResolvedKeyDraft>()

        // lead：该行最左侧特殊键。
        row.lead?.let { result += expandKey(it, theme, index) }

        // 逐字符生成 letter 键。
        letters.forEachIndexed { i, c ->
            val syntheticSpec = KeySpec(
                keyClass = row.keyClass,
                action = c.lowercase(),
                mainText = TextSpec(content = c.toString()),
                subLabel = subLabels?.let { TextSpec(content = it[i]) }
            )
            result += resolveKeyDraft(theme, userLayers = listOf(syntheticSpec))
        }

        // trail：该行最右侧特殊键。
        row.trail?.let { result += expandKey(it, theme, index) }

        return result
    }

    // ——————————————————————————————————————————————
    // 步骤 4-5：key 展开（$ref + 覆盖）与字段合并
    // ——————————————————————————————————————————————

    /**
     * 展开单个按键条目（[KeySpecOrRef]）：
     * - [KeyTemplateRef]：查模板（缺失 → [Reason.UNKNOWN_REF]），叠加 per-site 覆盖；
     * - [InlineKey]：直接取其规格。
     * 随后按优先级 `theme.keyDefaults[class] < template < inline覆盖` 合并补全。
     */
    private fun expandKey(
        entry: KeySpecOrRef,
        theme: Theme,
        index: TemplateIndex
    ): ResolvedKeyDraft {
        val userLayers: List<KeySpec> = when (entry) {
            is KeyTemplateRef -> {
                val template = index.keyTemplates[entry.name]
                    ?: throw ResolveException(
                        ConfigError("keys.\$${entry.name}", Reason.UNKNOWN_REF, entry.name)
                    )
                // 低优先级在前：模板 < per-site 覆盖。
                listOfNotNull(template, entry.overrides)
            }
            is InlineKey -> listOf(entry.spec)
        }
        return resolveKeyDraft(theme, userLayers)
    }

    /**
     * 按优先级合并字段并补全：`theme.keyDefaults[class] < userLayers（低→高）`。
     *
     * - keyClass 由用户层（模板/内联）决定（缺省 "letter"）；不在 theme.keyDefaults 中 → [Reason.UNKNOWN_KEY_CLASS]（R3.20）。
     * - mainText / subLabel 按字段深合并（content / color / sizeRatio 各自独立按优先级覆盖）。
     * - 宽度按层解析为 Fixed/Fill：同一层内固定 widthRatio 优先于 width；高优先级层覆盖低优先级层
     *   （故 `$ref` 处的固定 widthRatio 覆盖优先于模板 fill，R3.9）。
     */
    private fun resolveKeyDraft(theme: Theme, userLayers: List<KeySpec>): ResolvedKeyDraft {
        // 先合并用户层以确定 keyClass。
        val userSpec = userLayers.reduceOrNull { lower, higher -> mergeKeySpec(lower, higher) }
            ?: KeySpec()
        val keyClass = userSpec.keyClass ?: DEFAULT_KEY_CLASS

        // 查 theme.keyDefaults[keyClass]（未定义 → UNKNOWN_KEY_CLASS）。
        val defaults = theme.keyDefaults[keyClass]
            ?: throw ResolveException(
                ConfigError("keyClass", Reason.UNKNOWN_KEY_CLASS, keyClass)
            )

        // 完整层栈（低→高）：keyDefaults < userLayers。
        val fullLayers = listOf(keyDefaultsAsSpec(defaults)) + userLayers
        val merged = fullLayers.reduce { lower, higher -> mergeKeySpec(lower, higher) }

        // 解析有效宽度：逐层取 layerWidth，高优先级覆盖低优先级。
        val effectiveWidth: AuthoringWidth = fullLayers.fold<KeySpec, AuthoringWidth?>(null) { acc, layer ->
            layerWidth(layer) ?: acc
        } ?: AuthoringWidth.Fill()

        // 主文本：content 缺省 ""，color/sizeRatio 缺省占位（值约束由 5.2 的校验阶段把关）。
        val mainSpec = merged.mainText
        val mainText = TextStyleConfig(
            content = mainSpec?.content ?: "",
            color = mainSpec?.color ?: "",
            sizeRatio = mainSpec?.sizeRatio ?: 0f
        )

        // 子标签：仅当存在 content 时产出（keyDefaults 仅提供 color/sizeRatio，无 content → 不渲染子标签）。
        val subSpec = merged.subLabel
        val subContent = subSpec?.content
        val subLabel = if (subContent != null) {
            SubLabel(
                content = subContent,
                color = subSpec.color ?: "",
                sizeRatio = subSpec.sizeRatio ?: 0f
            )
        } else {
            null
        }

        return ResolvedKeyDraft(
            action = merged.action ?: "",
            width = effectiveWidth,
            normalBackgroundColor = merged.normalBackgroundColor ?: "",
            pressedBackgroundColor = merged.pressedBackgroundColor,
            mainText = mainText,
            subLabel = subLabel
        )
    }

    // ——————————————————————————————————————————————
    // 步骤 7：fill 宽度展开
    // ——————————————————————————————————————————————

    /**
     * 逐行将「按键草稿」的宽度展开为具体 [KeyConfig.widthRatio]，产出扁平 [RowConfig] 列表。
     *
     * - 仅含固定宽度键的行：直接取固定比例。
     * - 含一个或多个 fill 键的行：剩余宽度
     *   `remaining = 1.0 − 2×sideMargin − (n−1)×hGap − Σ(固定键 widthRatio)`，
     *   按权重比例分配给各 fill 键，使该行水平占用合计 = 1.0。
     *
     * 当含 fill 行的 `remaining ≤ 0` 时，抛出 [Reason.ROW_OVERFLOW]（携带行下标），
     * 由 [resolve] 顶层转为 [ResolveResult.Failure] 且不产出配置（R3.16）。
     */
    private fun expandFillWidths(
        draftRows: List<List<ResolvedKeyDraft>>,
        theme: Theme
    ): List<RowConfig> {
        return draftRows.mapIndexed { rowIndex, drafts ->
            val n = drafts.size
            val fillWeightSum = drafts.sumOf { draft ->
                (draft.width as? AuthoringWidth.Fill)?.weight?.toDouble() ?: 0.0
            }

            val keys: List<KeyConfig> = if (fillWeightSum <= 0.0) {
                // 无 fill 键：直接采用各自固定宽度。
                drafts.map { draft -> draft.toKeyConfig(fixedWidthOf(draft)) }
            } else {
                // 含 fill 键：先算固定键占用，再把剩余宽度按权重分配给各 fill 键。
                val fixedSum = drafts.sumOf { draft ->
                    (draft.width as? AuthoringWidth.Fixed)?.ratio?.toDouble() ?: 0.0
                }
                val remaining = 1.0 -
                    2.0 * theme.sideMarginRatio -
                    (n - 1).coerceAtLeast(0) * theme.horizontalGapRatio.toDouble() -
                    fixedSum
                // remaining ≤ 0：固定占用已撑满或溢出，无宽度可分配给 fill 键 → ROW_OVERFLOW。
                if (remaining <= 0.0) {
                    throw ResolveException(
                        ConfigError("rows[$rowIndex]", Reason.ROW_OVERFLOW, remaining.toString())
                    )
                }
                drafts.map { draft ->
                    val width = when (val w = draft.width) {
                        is AuthoringWidth.Fixed -> w.ratio
                        is AuthoringWidth.Fill -> (remaining * w.weight / fillWeightSum).toFloat()
                    }
                    draft.toKeyConfig(width)
                }
            }
            RowConfig(keys = keys)
        }
    }

    /** 取固定宽度键的比例；fill 键在无固定上下文时退化为 0（5.2 将以 ROW_OVERFLOW 等约束完善）。 */
    private fun fixedWidthOf(draft: ResolvedKeyDraft): Float =
        (draft.width as? AuthoringWidth.Fixed)?.ratio ?: 0f

    // ——————————————————————————————————————————————
    // 合并工具
    // ——————————————————————————————————————————————

    /** 取某层的宽度声明：固定 widthRatio 优先于 width 字段；都缺省返回 null。 */
    private fun layerWidth(spec: KeySpec): AuthoringWidth? =
        spec.widthRatio?.let { AuthoringWidth.Fixed(it) } ?: spec.width

    /** 字段级合并两个 [KeySpec]：[higher] 的非空字段覆盖 [lower]；mainText/subLabel 深合并。 */
    private fun mergeKeySpec(lower: KeySpec, higher: KeySpec): KeySpec = KeySpec(
        keyClass = higher.keyClass ?: lower.keyClass,
        action = higher.action ?: lower.action,
        widthRatio = higher.widthRatio ?: lower.widthRatio,
        width = higher.width ?: lower.width,
        normalBackgroundColor = higher.normalBackgroundColor ?: lower.normalBackgroundColor,
        pressedBackgroundColor = higher.pressedBackgroundColor ?: lower.pressedBackgroundColor,
        mainText = mergeTextSpec(lower.mainText, higher.mainText),
        subLabel = mergeTextSpec(lower.subLabel, higher.subLabel),
        ref = higher.ref ?: lower.ref
    )

    /** 字段级深合并两个 [TextSpec]：content / color / sizeRatio 各自独立按优先级覆盖。 */
    private fun mergeTextSpec(lower: TextSpec?, higher: TextSpec?): TextSpec? {
        if (lower == null) return higher
        if (higher == null) return lower
        return TextSpec(
            content = higher.content ?: lower.content,
            color = higher.color ?: lower.color,
            sizeRatio = higher.sizeRatio ?: lower.sizeRatio
        )
    }

    /** 将某 Key_Class 的 [KeyDefaults] 转为等价 [KeySpec]（仅样式默认，无 content）。 */
    private fun keyDefaultsAsSpec(d: KeyDefaults): KeySpec = KeySpec(
        widthRatio = d.widthRatio,
        normalBackgroundColor = d.normalBackgroundColor,
        pressedBackgroundColor = d.pressedBackgroundColor,
        mainText = d.mainText?.let { TextSpec(content = null, color = it.color, sizeRatio = it.sizeRatio) },
        subLabel = d.subLabel?.let { TextSpec(content = null, color = it.color, sizeRatio = it.sizeRatio) }
    )

    /** Key_Class 缺省值。 */
    private const val DEFAULT_KEY_CLASS = "letter"

    // ——————————————————————————————————————————————
    // 内部类型
    // ——————————————————————————————————————————————

    /**
     * 中间产物：字段已按优先级合并的单个按键，宽度记为 [AuthoringWidth]（Fixed/Fill），
     * 待 [expandFillWidths] 将 fill 解析为具体比例后产出扁平 [KeyConfig]。
     */
    private data class ResolvedKeyDraft(
        val action: String,
        val width: AuthoringWidth,
        val normalBackgroundColor: String,
        val pressedBackgroundColor: String?,
        val mainText: TextStyleConfig,
        val subLabel: SubLabel?
    ) {
        /** 以最终确定的具体宽度比例产出扁平 [KeyConfig]。 */
        fun toKeyConfig(widthRatio: Float): KeyConfig = KeyConfig(
            action = action,
            widthRatio = widthRatio,
            normalBackgroundColor = normalBackgroundColor,
            pressedBackgroundColor = pressedBackgroundColor,
            mainText = mainText,
            subLabel = subLabel
        )
    }

    /** 内部展开异常，携带结构化 [ConfigError]，由 [resolve] 顶层捕获转为 [ResolveResult.Failure]。 */
    private class ResolveException(val error: ConfigError) : RuntimeException()
}

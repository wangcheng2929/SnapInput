package com.study.snapinput.core.config

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * 基于 Android [AssetManager] 的 [ConfigLoader] 实现。
 *
 * 流程：
 *  1. 读取 `keyboard/layouts/{layoutId}.json`，仅解析其中的 `theme` 与 `templates` 引用名；
 *  2. 据此读取 `keyboard/themes/{themeId}.json` 与各 `keyboard/templates/{name}.json`；
 *  3. 缓存已读取的共享 theme/template 文本，避免重复读取（R4.2）；
 *  4. 全部读取置于 2 秒总超时内（R4.1 / R4.6）。
 *
 * 任一文件缺失/不可读 → [LoadResult.Failure]（[Reason.MISSING_REFERENCED_FILE]）；
 * 超时 → [LoadResult.Failure]（[Reason.MISSING_REFERENCED_FILE]，并在 offendingValue 注明超时）。
 */
class AssetConfigLoader(
    private val assets: AssetManager
) : ConfigLoader {

    /** 已读取文件的缓存：asset 路径 -> 原始文本。用于共享 theme/template 的缓存命中。 */
    private val cache = mutableMapOf<String, String>()

    /** 仅用于解析 layout 中的 theme/templates 引用；忽略 rows 等其余字段。 */
    private val refJson = Json { ignoreUnknownKeys = true }

    /** layout 中与读取相关的最小引用模型。 */
    @Serializable
    private data class LayoutRefs(
        val theme: String,
        val templates: List<String> = emptyList()
    )

    override suspend fun loadAuthoringSources(layoutId: String): LoadResult {
        return try {
            // 全部读取（layout + theme + 各 template）置于 2 秒总超时内。
            withTimeout(TOTAL_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    loadInternal(layoutId)
                }
            }
        } catch (e: TimeoutCancellationException) {
            // 超时：读取未在 2 秒内完成。
            LoadResult.Failure(
                ConfigError(
                    field = "keyboard/layouts/$layoutId.json",
                    reason = Reason.MISSING_REFERENCED_FILE,
                    offendingValue = "load timeout after ${TOTAL_TIMEOUT_MS}ms"
                )
            )
        }
    }

    /** 在超时与 IO 调度上下文内执行实际读取与引用解析。 */
    private fun loadInternal(layoutId: String): LoadResult {
        val layoutPath = "keyboard/layouts/$layoutId.json"

        // 1. 读取 layout 文件。
        val layoutJson = readAssetOrNull(layoutPath)
            ?: return missing(layoutPath)

        // 2. 解析 theme/templates 引用名。
        val refs: LayoutRefs = try {
            refJson.decodeFromString(LayoutRefs.serializer(), layoutJson)
        } catch (e: SerializationException) {
            // layout 文本无法解析出引用（语法/缺字段），无法据此继续读取。
            return LoadResult.Failure(
                ConfigError(field = layoutPath, reason = Reason.SYNTAX, offendingValue = e.message)
            )
        }

        // 3. 读取 theme 文件。
        val themePath = "keyboard/themes/${refs.theme}.json"
        val themeJson = readAssetOrNull(themePath)
            ?: return missing(themePath)

        // 4. 依次读取各 template 文件（共享文件命中缓存）。
        val templates = LinkedHashMap<String, String>(refs.templates.size)
        for (name in refs.templates) {
            val templatePath = "keyboard/templates/$name.json"
            val text = readAssetOrNull(templatePath) ?: return missing(templatePath)
            templates[name] = text
        }

        return LoadResult.Success(
            AuthoringSources(
                layoutId = layoutId,
                layoutJson = layoutJson,
                themeId = refs.theme,
                themeJson = themeJson,
                templates = templates
            )
        )
    }

    /**
     * 读取指定 asset 路径的文本；命中缓存直接返回，缺失/不可读返回 null。
     */
    private fun readAssetOrNull(path: String): String? {
        cache[path]?.let { return it }
        return try {
            val text = assets.open(path).bufferedReader().use { it.readText() }
            cache[path] = text
            text
        } catch (e: IOException) {
            null
        }
    }

    /** 构造文件缺失/不可读的失败结果。 */
    private fun missing(path: String): LoadResult.Failure =
        LoadResult.Failure(
            ConfigError(
                field = path,
                reason = Reason.MISSING_REFERENCED_FILE,
                offendingValue = path
            )
        )

    private companion object {
        /** 全部读取的总超时（毫秒）。 */
        const val TOTAL_TIMEOUT_MS = 2000L
    }
}

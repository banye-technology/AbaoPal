package com.withcareer.screenpal_android.core.ui

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.Gson
import kotlin.math.abs
import kotlin.math.max

data class CompactUiElement(
    val sid: String,
    val rid: String?,
    val role: String,
    val label: String?,
    val bounds: List<Int>,
    val center: List<Int>,
    val clickable: Boolean,
    val scrollable: Boolean,
    val focusable: Boolean,
    val enabled: Boolean,
    val visible: Boolean
)

data class CompactUiSnapshot(
    val packageName: String?,
    val elements: List<CompactUiElement>
) {
    fun toJson(): String = Gson().toJson(this)
}

data class UiElement(
    val id: String,
    val className: String?,
    val role: String?,
    val resourceId: String?,
    val text: String?,
    val contentDescription: String?,
    val bounds: Rect,
    val centerX: Int,
    val centerY: Int,
    val visible: Boolean,
    val enabled: Boolean,
    val focusable: Boolean,
    val scrollable: Boolean,
    val clickable: Boolean,
    val path: String,
    val actions: List<String>
)

data class UiTreeSnapshot(
    val packageName: String?,
    val elements: List<UiElement>,
    val textIndex: Map<String, List<String>>,
    val idIndex: Map<String, List<String>>,
    val roleIndex: Map<String, List<String>>,
    val descIndex: Map<String, List<String>>
) {
    fun toJson(): String = Gson().toJson(this)

    fun toCompactSnapshot(
        maxItems: Int = 120,
        textLimit: Int = 80,
        screenWidth: Int = 0,
        screenHeight: Int = 0
    ): CompactUiSnapshot {
        fun clip(s: String?): String? {
            if (s.isNullOrBlank()) return null
            val trimmed = s.trim()
            return if (trimmed.length <= textLimit) trimmed else trimmed.take(textLimit)
        }

        fun toRelX(x: Int): Int {
            if (screenWidth <= 0) return x
            return ((x.toFloat() / screenWidth.toFloat()) * 1000f).toInt().coerceIn(0, 1000)
        }

        fun toRelY(y: Int): Int {
            if (screenHeight <= 0) return y
            return ((y.toFloat() / screenHeight.toFloat()) * 1000f).toInt().coerceIn(0, 1000)
        }

        fun boundsList(r: Rect): List<Int> = listOf(
            toRelX(r.left),
            toRelY(r.top),
            toRelX(r.right),
            toRelY(r.bottom)
        )

        val importantRoles = setOf("edit_text", "button", "tab", "list", "switch", "checkbox")
        val minElements = 20

        fun depthOf(path: String): Int = path.count { it == '/' }

        fun isInside(inner: Rect, outer: Rect): Boolean {
            val cx = inner.centerX()
            val cy = inner.centerY()
            return cx >= outer.left && cx <= outer.right && cy >= outer.top && cy <= outer.bottom
        }

        fun centerDistance(a: UiElement, b: UiElement): Int {
            val dx = a.centerX - b.centerX
            val dy = a.centerY - b.centerY
            return dx * dx + dy * dy
        }

        fun normalizeLabel(raw: String): String {
            var s = raw.replace('\n', ' ')
            s = s.replace(Regex("\\s+"), " ").trim()
            s = s.replace("点击可发起搜索", "").trim()
            return s
        }

        fun labelText(s: String?): String? {
            if (s.isNullOrBlank()) return null
            val normalized = normalizeLabel(s)
            if (normalized.isBlank()) return null
            return if (normalized.length <= textLimit) normalized else normalized.take(textLimit)
        }

        fun labelScore(text: String, el: UiElement): Int {
            val t = text.trim()
            if (t.isBlank()) return Int.MIN_VALUE

            var score = 0
            val rid = (el.resourceId ?: "").lowercase()

            if (rid.contains("title") || rid.contains("name") || rid.contains("shop") || rid.contains("poi")) score += 220
            if (rid.contains("sub_text") || rid.contains("tag") || rid.contains("badge")) score -= 20

            val hasCjk = t.any { it in '\u4e00'..'\u9fff' }
            if (hasCjk) score += 30
            score += (t.length.coerceAtMost(30))

            if (Regex("^\\d+(\\.\\d+)?\\s*(km|m)$", RegexOption.IGNORE_CASE).matches(t)) score -= 260
            if (Regex("^\\d+(\\.\\d+)?分$").matches(t)) score -= 220
            if (t.startsWith("月售") || t.contains("已售") || t.contains("销量")) score -= 120
            if (t.contains("免配送") || t.contains("配送费") || t.contains("买过") || t.contains("浏览过") || t.contains("暂停营业")) score -= 120
            if (t == "广告") score -= 260

            return score
        }

        fun deriveLabel(target: UiElement, pool: List<UiElement>): String? {
            val own = labelText(target.text) ?: labelText(target.contentDescription)
            if (!own.isNullOrBlank()) return own

            val parentDepth = depthOf(target.path)
            val prefix = target.path + "/"
            val childCandidates = pool.asSequence()
                .filter { it.path.startsWith(prefix) }
                .mapNotNull { child ->
                    val text = labelText(child.text) ?: labelText(child.contentDescription)
                    if (text.isNullOrBlank()) null else Pair(child, text)
                }
                .sortedWith(
                    compareBy<Pair<UiElement, String>> { depthOf(it.first.path) - parentDepth }
                        .thenByDescending { labelScore(it.second, it.first) }
                        .thenBy { centerDistance(target, it.first) }
                        .thenBy { it.second.length }
                )
                .map { it.second }
                .toList()
            if (childCandidates.isNotEmpty()) return childCandidates.first()

            val overlapCandidates = pool.asSequence()
                .mapNotNull { other ->
                    val text = labelText(other.text) ?: labelText(other.contentDescription)
                    if (text.isNullOrBlank()) null else other to text
                }
                .filter { (other, _) -> isInside(other.bounds, target.bounds) }
                .sortedWith(
                    compareBy<Pair<UiElement, String>> { centerDistance(target, it.first) }
                        .thenByDescending { labelScore(it.second, it.first) }
                        .thenBy { it.second.length }
                )
                .map { it.second }
                .toList()
            return overlapCandidates.firstOrNull()
        }

        fun rolePriority(role: String): Int = when (role) {
            "edit_text" -> 0
            "button" -> 1
            "tab" -> 2
            "switch" -> 3
            "checkbox" -> 4
            "list" -> 5
            else -> 9
        }

        val pool = elements.filter { it.visible && it.enabled }

        val actionable = pool.filter {
            it.clickable || it.scrollable || it.focusable || (it.role ?: "") in importantRoles
        }
        val informative = pool.filter {
            !it.text.isNullOrBlank() || !it.contentDescription.isNullOrBlank()
        }

        val merged = LinkedHashMap<String, UiElement>()
        actionable.sortedWith(compareBy<UiElement>({ rolePriority(it.role ?: "unknown") }, { it.centerY }, { it.centerX }))
            .forEach { merged.putIfAbsent(it.id, it) }

        // Always try to add informative elements, not just when actionable count is low
        informative.sortedWith(compareBy<UiElement>({ it.centerY }, { it.centerX }))
            .forEach { merged.putIfAbsent(it.id, it) }

        val filtered = merged.values.asSequence()
            .map { el ->
                val role = el.role ?: "unknown"
                val label = deriveLabel(el, pool)
                CompactUiElement(
                    sid = el.id,
                    rid = el.resourceId,
                    role = role,
                    label = label,
                    bounds = boundsList(el.bounds),
                    center = listOf(toRelX(el.centerX), toRelY(el.centerY)),
                    clickable = el.clickable,
                    scrollable = el.scrollable,
                    focusable = el.focusable,
                    enabled = el.enabled,
                    visible = el.visible
                )
            }
            .sortedWith(compareBy<CompactUiElement>({ rolePriority(it.role) }, { it.center[1] }, { it.center[0] }))
            .take(maxItems)
            .toList()

        return CompactUiSnapshot(
            packageName = packageName,
            elements = filtered
        )
    }

    fun toCompactJson(
        maxItems: Int = 120,
        textLimit: Int = 80
    ): String {
        return toCompactSnapshot(maxItems = maxItems, textLimit = textLimit).toJson()
    }

    fun toCompactCatalog(
        maxItems: Int = 20,
        textLimit: Int = 40,
        screenWidth: Int = 0,
        screenHeight: Int = 0
    ): String {
        val snap = toCompactSnapshot(maxItems = maxItems, textLimit = textLimit, screenWidth = screenWidth, screenHeight = screenHeight)
        val sb = StringBuilder()
        sb.append("页面可操作元素(精简, 坐标系0-1000):\n")
        snap.elements.forEachIndexed { idx, el ->
            val rid = el.rid ?: ""
            val role = el.role
            val label = (el.label ?: "").take(textLimit)
            sb.append("- [${idx + 1}] sid=${el.sid} rid=${rid} role=${role} label=${label} center=${el.center} bounds=${el.bounds}\n")
        }
        return sb.toString()
    }

    fun toCatalog(maxItems: Int = 12): String {
        val sb = StringBuilder()
        sb.append("页面关键元素目录:\n")
        val important = elements.filter {
            val r = it.role ?: ""
            r in setOf("edit_text", "button", "tab", "list", "switch") || it.clickable
        }.take(maxItems)
        important.forEachIndexed { idx, el ->
            val id = el.resourceId ?: ""
            val role = el.role ?: ""
            val text = (el.text ?: el.contentDescription ?: "").take(40)
            sb.append("- [${idx+1}] id=${id} role=${role} text=${text} bounds=${el.bounds}\n")
        }
        return sb.toString()
    }
}

class UiTreeProcessor(
    private val maxNodes: Int = 160,
    private val maxDepth: Int = 8
) {
    fun capture(root: AccessibilityNodeInfo?, rootPathPrefix: String = "0"): UiTreeSnapshot? {
        if (root == null) return null
        val elements = ArrayList<UiElement>(maxNodes)
        val textIndex = HashMap<String, MutableList<String>>()
        val idIndex = HashMap<String, MutableList<String>>()
        val roleIndex = HashMap<String, MutableList<String>>()
        val descIndex = HashMap<String, MutableList<String>>()
        val pkg = root.packageName?.toString()
        var count = 0

        fun addIndex(map: MutableMap<String, MutableList<String>>, key: String?, id: String) {
            if (key.isNullOrBlank()) return
            val k = key.lowercase()
            val list = map.getOrPut(k) { mutableListOf() }
            list.add(id)
        }

        fun traverse(node: AccessibilityNodeInfo?, depth: Int, path: String) {
            if (node == null) return
            if (count >= maxNodes || depth > maxDepth) return
            if (!node.isVisibleToUser) {
                recycleNode(node)
                return
            }
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val role = inferRole(node.className?.toString())
            val actions = collectActions(node)
            val id = stableId(pkg, node.className?.toString(), node.viewIdResourceName, node.text?.toString(), bounds, path)
            val el = UiElement(
                id = id,
                className = node.className?.toString(),
                role = role,
                resourceId = node.viewIdResourceName,
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                bounds = Rect(bounds),
                centerX = bounds.centerX(),
                centerY = bounds.centerY(),
                visible = node.isVisibleToUser,
                enabled = node.isEnabled,
                focusable = node.isFocusable,
                scrollable = node.isScrollable,
                clickable = node.isClickable,
                path = path,
                actions = actions
            )
            elements.add(el)
            count++

            addIndex(textIndex, el.text, el.id)
            addIndex(textIndex, el.contentDescription, el.id)
            addIndex(idIndex, el.resourceId, el.id)
            addIndex(roleIndex, el.role, el.id)
            addIndex(descIndex, el.contentDescription, el.id)

            val childCount = node.childCount
            for (i in 0 until childCount) {
                if (count >= maxNodes) break
                val child = node.getChild(i)
                traverse(child, depth + 1, "$path/$i")
            }
            recycleNode(node)
        }

        traverse(root, 0, rootPathPrefix)
        return UiTreeSnapshot(
            packageName = pkg,
            elements = elements,
            textIndex = textIndex,
            idIndex = idIndex,
            roleIndex = roleIndex,
            descIndex = descIndex
        )
    }

    fun findByViewId(snapshot: UiTreeSnapshot, viewId: String): List<UiElement> {
        val ids = snapshot.idIndex[viewId.lowercase()] ?: return emptyList()
        val set = ids.toSet()
        return snapshot.elements.filter { set.contains(it.id) }
    }

    fun findByText(snapshot: UiTreeSnapshot, query: String): List<UiElement> {
        val tokens = tokenize(query)
        val ids = tokens.flatMap { snapshot.textIndex[it] ?: emptyList() }
        val set = ids.toSet()
        val candidates = snapshot.elements.filter { set.contains(it.id) }
        return rankByText(query, candidates)
    }

    fun findByRoleAndText(snapshot: UiTreeSnapshot, role: String, frag: String): List<UiElement> {
        val ids = snapshot.roleIndex[role.lowercase()] ?: emptyList()
        val set = ids.toSet()
        val candidates = snapshot.elements.filter { set.contains(it.id) }
        val filtered = candidates.filter { containsIgnoreCase(it.text, frag) || containsIgnoreCase(it.contentDescription, frag) }
        return rankByText(frag, filtered)
    }

    fun findByDesc(snapshot: UiTreeSnapshot, query: String): List<UiElement> {
        val tokens = tokenize(query)
        val ids = tokens.flatMap { snapshot.descIndex[it] ?: emptyList() }
        val set = ids.toSet()
        val candidates = snapshot.elements.filter { set.contains(it.id) }
        return rankByText(query, candidates)
    }

    fun nearestClickable(snapshot: UiTreeSnapshot, x: Int, y: Int): UiElement? {
        val candidates = snapshot.elements.filter { it.clickable && it.bounds.contains(x, y) }
        if (candidates.isNotEmpty()) return candidates.maxByOrNull { area(it.bounds) }
        val near = snapshot.elements.filter { it.clickable }.minByOrNull { manhattan(it.centerX - x, it.centerY - y) }
        return near
    }

    fun rankCandidates(snapshot: UiTreeSnapshot, candidates: List<UiElement>): List<UiElement> {
        return candidates.sortedWith(compareByDescending<UiElement> { !it.resourceId.isNullOrBlank() }
            .thenByDescending { it.clickable }
            .thenByDescending { !(it.contentDescription ?: "").isBlank() }
            .thenByDescending { area(it.bounds) }
            .thenByDescending { it.visible }
            .thenByDescending { it.enabled })
    }

    private fun tokenize(s: String): List<String> {
        return s.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    private fun containsIgnoreCase(s: String?, q: String): Boolean {
        if (s.isNullOrBlank()) return false
        return s.lowercase().contains(q.lowercase())
    }

    private fun rankByText(query: String, candidates: List<UiElement>): List<UiElement> {
        val q = query.lowercase()
        return candidates.sortedWith(compareByDescending<UiElement> {
            val t = it.text?.lowercase() ?: ""
            scoreContainment(t, q)
        }.thenByDescending {
            val d = it.contentDescription?.lowercase() ?: ""
            scoreContainment(d, q)
        }.thenByDescending { !it.resourceId.isNullOrBlank() }
            .thenByDescending { it.clickable }
            .thenByDescending { area(it.bounds) })
    }

    private fun scoreContainment(s: String, q: String): Int {
        return when {
            s == q -> 3
            s.contains(q) -> 2
            q.contains(s) && s.isNotEmpty() -> 1
            else -> 0
        }
    }

    private fun area(r: Rect): Int = max(1, r.width() * r.height())

    private fun manhattan(dx: Int, dy: Int): Int = abs(dx) + abs(dy)

    private fun collectActions(node: AccessibilityNodeInfo): List<String> {
        val res = ArrayList<String>(4)
        if (node.isClickable) res.add("click")
        if (node.isLongClickable) res.add("long_click")
        if (node.isScrollable) res.add("scroll")
        if (node.isEditable) res.add("set_text")
        return res
    }

    private fun stableId(
        pkg: String?,
        cls: String?,
        viewId: String?,
        text: String?,
        bounds: Rect,
        path: String
    ): String {
        val key = listOf(
            pkg ?: "",
            cls ?: "",
            viewId ?: "",
            text ?: "",
            bounds.left.toString(),
            bounds.top.toString(),
            bounds.right.toString(),
            bounds.bottom.toString(),
            path
        ).joinToString("|")
        return key.hashCode().toString()
    }

    private fun inferRole(className: String?): String? {
        val c = className ?: return null
        return when {
            c.endsWith("Button") -> "button"
            c.endsWith("EditText") -> "edit_text"
            c.endsWith("TextView") -> "text"
            c.endsWith("ImageView") -> "image"
            c.endsWith("CheckBox") -> "checkbox"
            c.endsWith("Switch") -> "switch"
            c.endsWith("RecyclerView") -> "list"
            c.endsWith("TabLayout") -> "tab"
            else -> null
        }
    }

    private fun recycleNode(node: AccessibilityNodeInfo?) {
        try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                node?.recycle()
            }
        } catch (_: Exception) { }
    }
}

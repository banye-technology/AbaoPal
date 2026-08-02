package com.withcareer.screenpal_android.core.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Simplified UI Node for LLM Perception
 *
 * Reduces the complexity of AccessibilityNodeInfo to save tokens and focus on
 * actionable elements (clickable, text, content description).
 */
data class SimplifiedUINode(
    val className: String?,
    val text: String?,
    val contentDesc: String?,
    val resourceId: String?,
    val bounds: String?,
    val isClickable: Boolean,
    val children: List<SimplifiedUINode>
) {
    /**
     * Converts the node and its children to a compact JSON string.
     */
    fun toJson(): JSONObject {
        val json = JSONObject()

        if (!className.isNullOrEmpty()) json.put("class", className)
        if (!text.isNullOrEmpty()) json.put("text", text)
        if (!contentDesc.isNullOrEmpty()) json.put("desc", contentDesc)
        if (!resourceId.isNullOrEmpty()) json.put("id", resourceId)
        if (!bounds.isNullOrEmpty()) json.put("bounds", bounds)
        if (isClickable) json.put("clickable", true)

        if (children.isNotEmpty()) {
            val childrenArray = JSONArray()
            children.forEach { child ->
                childrenArray.put(child.toJson())
            }
            json.put("children", childrenArray)
        }

        return json
    }

    override fun toString(): String {
        return toJson().toString()
    }
}

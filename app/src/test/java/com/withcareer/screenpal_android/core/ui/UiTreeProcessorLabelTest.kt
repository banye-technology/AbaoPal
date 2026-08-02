package com.withcareer.screenpal_android.core.ui

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class UiTreeProcessorLabelTest {
    @Test
    fun deriveLabel_prefersTitleOverDistance() {
        val parent = UiElement(
            id = "parent",
            className = "android.widget.LinearLayout",
            role = "unknown",
            resourceId = null,
            text = null,
            contentDescription = null,
            bounds = Rect(0, 0, 1000, 1000),
            centerX = 500,
            centerY = 500,
            visible = true,
            enabled = true,
            focusable = true,
            scrollable = false,
            clickable = true,
            path = "w6/0",
            actions = listOf("click")
        )

        val title = UiElement(
            id = "title",
            className = "android.widget.TextView",
            role = "text",
            resourceId = "com.sankuai.meituan:id/title",
            text = "肯德基宅急送(天马店)",
            contentDescription = null,
            bounds = Rect(10, 10, 900, 100),
            centerX = 455,
            centerY = 55,
            visible = true,
            enabled = true,
            focusable = false,
            scrollable = false,
            clickable = false,
            path = "w6/0/0",
            actions = emptyList()
        )

        val distance = UiElement(
            id = "distance",
            className = "android.widget.TextView",
            role = "text",
            resourceId = "com.sankuai.meituan:id/uhz",
            text = "1.3km",
            contentDescription = null,
            bounds = Rect(910, 10, 990, 100),
            centerX = 950,
            centerY = 55,
            visible = true,
            enabled = true,
            focusable = false,
            scrollable = false,
            clickable = false,
            path = "w6/0/1",
            actions = emptyList()
        )

        val snapshot = UiTreeSnapshot(
            packageName = "com.sankuai.meituan",
            elements = listOf(parent, title, distance),
            textIndex = emptyMap(),
            idIndex = emptyMap(),
            roleIndex = emptyMap(),
            descIndex = emptyMap()
        )

        val compact = snapshot.toCompactSnapshot(maxItems = 50, textLimit = 80)
        val parentCompact = compact.elements.first { it.sid == "parent" }
        assertEquals("肯德基宅急送(天马店)", parentCompact.label)
    }
}

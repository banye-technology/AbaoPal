package com.withcareer.screenpal_android.recording

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * 坐标转换适配器
 * 将录制时捕获的绝对坐标转换为系统标准的相对坐标格式
 * 确保录制指令可以被现有的 ActionExecutor 系统正确回放
 *
 */
object CoordinateAdapter {

    /**
     * 将录制的绝对坐标转换为系统标准的相对坐标格式
     * 相对坐标范围：0.0 ~ 1.0
     *
     * @param absoluteX 绝对X坐标（像素）
     * @param absoluteY 绝对Y坐标（像素）
     * @param screenWidth 录制设备的屏幕宽度
     * @param screenHeight 录制设备的屏幕高度
     * @return 相对坐标对 (relativeX, relativeY)
     */
    fun toRelativeCoordinate(
        absoluteX: Int,
        absoluteY: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<Float, Float> {
        // 修正坐标偏移问题：录制时采集的是 Raw 坐标，包含状态栏高度
        // 而回放时如果全屏应用可能不包含状态栏，或者系统坐标系差异
        // 简单处理：如果 Y 坐标明显小于状态栏高度（例如 50px），可能需要修正
        // 但目前用户反馈是"稍微往上偏"，说明实际点击位置比录制位置偏上，
        // 或者回放时点击位置比预期位置偏上。
        // 如果录制点位偏上，说明 y 值偏小。
        // 如果用户意思是"回放时点击的位置比我当时点的实际位置偏上"，说明 recordedY < actualY
        // 这通常是因为录制时使用了 getRawY (包含状态栏)，而回放时系统期望的是不包含状态栏的坐标?
        // 不，AccessibilityService 的 dispatchGesture 通常使用全屏绝对坐标。

        // 另一种可能是：getRealMetrics 获取的高度 > 实际可点击区域高度 (包含导航栏等)
        // 导致 Y / Height 计算出的比例偏小，回放时 Y = ratio * Height 也就偏小（偏上）。

        // 尝试修正：根据用户反馈"往上偏"，我们需要增加 Y 值（往下移）
        // 这里添加一个微小的垂直偏移量修正
        // val offsetY = 0 // 暂时不硬编码偏移，先通过日志排查

        val relativeX = absoluteX.toFloat() / screenWidth
        val relativeY = absoluteY.toFloat() / screenHeight

        return Pair(relativeX, relativeY)
    }

    /**
     * 将 RecordedAction 转换为标准的 actionParams JSON 格式
     * 这样可以直接被 ActionExecutor 的各个 Handler 识别和执行
     *
     * @param action 录制的操作
     * @param screenWidth 录制设备的屏幕宽度
     * @param screenHeight 录制设备的屏幕高度
     * @return 标准格式的 JSON 字符串
     */
    fun convertToStandardFormat(
        action: RecordedAction,
        screenWidth: Int,
        screenHeight: Int
    ): String {
        // 即使包含 action_type，也要检查坐标是否归一化
        // 如果是绝对坐标（> 1.5），则需要进行归一化处理
        val params = action.params

        // 检查是否已经是相对坐标
        var needsNormalization = false
        if (params.containsKey("action_type")) {
             when (action.actionType) {
                 "tap", "longPress" -> {
                     val element = params["element"]
                     if (element is List<*> && element.size >= 2) {
                         val x = (element[0] as? Number)?.toFloat() ?: 0f
                         val y = (element[1] as? Number)?.toFloat() ?: 0f
                         if (x > 1.5f || y > 1.5f) needsNormalization = true
                     } else if (!params.containsKey("element")) {
                         // 只有 action_type 但没有 element，可能需要从 x/y 转换
                         needsNormalization = true
                     }
                 }
                 "swipe", "longPressSwipe" -> {
                     val path = params["path"]
                     if (path is List<*>) {
                         // Check first point
                         val first = path.firstOrNull()
                         if (first is Map<*, *>) {
                             val x = (first["x"] as? Number)?.toFloat() ?: 0f
                             val y = (first["y"] as? Number)?.toFloat() ?: 0f
                             if (x > 1.5f || y > 1.5f) needsNormalization = true
                         } else if (first is List<*>) {
                             // [[x,y], [x,y]] format
                             val x = (first[0] as? Number)?.toFloat() ?: 0f
                             val y = (first[1] as? Number)?.toFloat() ?: 0f
                             if (x > 1.5f || y > 1.5f) needsNormalization = true
                         }
                     }
                 }
                 "multiTap" -> {
                     val points = params["points"]
                     if (points is List<*>) {
                         val first = points.firstOrNull()
                         if (first is List<*>) {
                             val x = (first[0] as? Number)?.toFloat() ?: 0f
                             val y = (first[1] as? Number)?.toFloat() ?: 0f
                             if (x > 1.5f || y > 1.5f) needsNormalization = true
                         }
                     }
                 }
             }

             if (!needsNormalization) {
                 // Even if normalization is not needed, we should clean up absolute coordinates
                 // to ensure playback uses the relative coordinates
                 try {
                     val json = com.google.gson.JsonParser.parseString(action.paramsJson).asJsonObject
                     val cleaned = JsonObject()

                     // Copy action_type
                     if (json.has("action_type")) {
                         cleaned.add("action_type", json.get("action_type"))
                     } else {
                         cleaned.addProperty("action_type", action.actionType)
                     }

                     // Copy only necessary fields based on action type
                     when (action.actionType) {
                         "tap", "longPress" -> {
                             if (json.has("element")) cleaned.add("element", json.get("element"))
                         }
                         "swipe", "longPressSwipe" -> {
                             if (json.has("path")) cleaned.add("path", json.get("path"))
                             if (json.has("start")) cleaned.add("start", json.get("start"))
                             if (json.has("end")) cleaned.add("end", json.get("end"))
                             if (json.has("duration")) cleaned.add("duration", json.get("duration"))
                         }
                         "multiTap" -> {
                             if (json.has("points")) cleaned.add("points", json.get("points"))
                         }
                         "type" -> {
                             if (json.has("text")) cleaned.add("text", json.get("text"))
                         }
                         else -> {
                             // For other types, copy everything except known absolute coords
                             json.entrySet().forEach { (k, v) ->
                                 if (k !in setOf("x", "y", "startX", "startY", "endX", "endY")) {
                                     cleaned.add(k, v)
                                 }
                             }
                         }
                     }
                    cleaned.addProperty("screen_width", screenWidth)
                    cleaned.addProperty("screen_height", screenHeight)
                     return cleaned.toString()
                 } catch (e: Exception) {
                     return action.paramsJson
                 }
             }
        }

        val jsonObject = JsonObject()

        when (action.actionType) {
            "tap" -> {
                // 优先使用 x,y 参数，如果没有则尝试从 element 解析
                var x = (action.params["x"] as? Number)?.toInt()
                var y = (action.params["y"] as? Number)?.toInt()

                if (x == null || y == null) {
                    val element = action.params["element"] as? List<*>
                    if (element != null && element.size >= 2) {
                        x = (element[0] as? Number)?.toInt() ?: 0
                        y = (element[1] as? Number)?.toInt() ?: 0
                    } else {
                        x = 0
                        y = 0
                    }
                }

                val (relX, relY) = toRelativeCoordinate(x, y, screenWidth, screenHeight)

                jsonObject.addProperty("action_type", "tap")
                jsonObject.add("element", JsonArray().apply {
                    add(relX)
                    add(relY)
                })
                jsonObject.addProperty("screen_width", screenWidth)
                jsonObject.addProperty("screen_height", screenHeight)
            }

            "swipe", "longPressSwipe" -> {
                // 检查是否包含轨迹
                val pathList = action.params["path"] as? List<*>

                if (pathList != null && pathList.isNotEmpty()) {
                    // 新格式：完整轨迹
                    val relativePath = JsonArray()

                    pathList.forEach { point ->
                        if (point is Map<*, *>) {
                            val xNum = point["x"] as? Number
                            val yNum = point["y"] as? Number
                            val t = (point["t"] as? Number)?.toLong() ?: 0L

                            if (xNum != null && yNum != null) {
                                val xVal = xNum.toFloat()
                                val yVal = yNum.toFloat()

                                // 判断是否已经是相对坐标
                                val (relX, relY) = if (xVal <= 1.5f && yVal <= 1.5f) {
                                    Pair(xVal, yVal)
                                } else {
                                    toRelativeCoordinate(xVal.toInt(), yVal.toInt(), screenWidth, screenHeight)
                                }

                                val pointObj = JsonObject()
                                pointObj.addProperty("x", relX)
                                pointObj.addProperty("y", relY)
                                pointObj.addProperty("t", t)
                                relativePath.add(pointObj)
                            }
                        } else if (point is List<*>) {
                            // 已经是列表格式 [x, y] - 无法携带时间戳，仅作为旧格式兼容
                            val xNum = point[0] as? Number
                            val yNum = point[1] as? Number

                            if (xNum != null && yNum != null) {
                                val xVal = xNum.toFloat()
                                val yVal = yNum.toFloat()

                                val (relX, relY) = if (xVal <= 1.5f && yVal <= 1.5f) {
                                    Pair(xVal, yVal)
                                } else {
                                    toRelativeCoordinate(xVal.toInt(), yVal.toInt(), screenWidth, screenHeight)
                                }

                                // 转换为对象格式以便统一处理，虽然没有时间戳
                                val pointObj = JsonObject()
                                pointObj.addProperty("x", relX)
                                pointObj.addProperty("y", relY)
                                pointObj.addProperty("t", 0L)
                                relativePath.add(pointObj)
                            }
                        }
                    }

                    // 统一转换为 swipe 或 long_press_swipe
                    val outputActionType = if (action.actionType == "longPressSwipe") "long_press_swipe" else "swipe"
                    jsonObject.addProperty("action_type", outputActionType)
                    jsonObject.add("path", relativePath)

                    if (action.actionType == "longPressSwipe") {
                        val duration = action.params["duration"] as? Number
                        if (duration != null) {
                            jsonObject.addProperty("duration", duration)
                        }
                    }
                    jsonObject.addProperty("screen_width", screenWidth)
                    jsonObject.addProperty("screen_height", screenHeight)
                } else {
                    // 旧格式兼容：起点和终点 (仅针对 swipe)
                    if (action.actionType == "swipe") {
                        val startX = (action.params["startX"] as? Number)?.toInt() ?: 0
                        val startY = (action.params["startY"] as? Number)?.toInt() ?: 0
                        val endX = (action.params["endX"] as? Number)?.toInt() ?: 0
                        val endY = (action.params["endY"] as? Number)?.toInt() ?: 0

                        val (relStartX, relStartY) = toRelativeCoordinate(startX, startY, screenWidth, screenHeight)
                        val (relEndX, relEndY) = toRelativeCoordinate(endX, endY, screenWidth, screenHeight)

                        jsonObject.addProperty("action_type", "swipe")
                        jsonObject.add("start", JsonArray().apply {
                            add(relStartX)
                            add(relStartY)
                        })
                        jsonObject.add("end", JsonArray().apply {
                            add(relEndX)
                            add(relEndY)
                        })
                        jsonObject.addProperty("screen_width", screenWidth)
                        jsonObject.addProperty("screen_height", screenHeight)
                    }
                }
            }

            "longPress" -> {
                var x = (action.params["x"] as? Number)?.toInt()
                var y = (action.params["y"] as? Number)?.toInt()

                if (x == null || y == null) {
                    val element = action.params["element"] as? List<*>
                    if (element != null && element.size >= 2) {
                        x = (element[0] as? Number)?.toInt() ?: 0
                        y = (element[1] as? Number)?.toInt() ?: 0
                    } else {
                        x = 0
                        y = 0
                    }
                }

                val duration = action.params["duration"] as? Number ?: 800
                val (relX, relY) = toRelativeCoordinate(x, y, screenWidth, screenHeight)

                jsonObject.addProperty("action_type", "longpress") // 注意大小写，保持一致性
                jsonObject.add("element", JsonArray().apply {
                    add(relX)
                    add(relY)
                })
                jsonObject.addProperty("duration", duration)
                jsonObject.addProperty("screen_width", screenWidth)
                jsonObject.addProperty("screen_height", screenHeight)
            }

            "type" -> {
                // TypeHandler 的格式无需转换：
                // { "action_type": "type", "text": "..." }
                jsonObject.addProperty("action_type", "type")
                jsonObject.addProperty("text", action.params["text"] as? String ?: "")
            }

            "scroll" -> {
                // ScrollHandler 的格式：
                // { "action_type": "scroll", "direction": "down/up" }
                jsonObject.addProperty("action_type", "scroll")
                jsonObject.addProperty("direction", action.params["direction"] as? String ?: "down")
            }

            "multiTap" -> {
                // 多点点击格式：
                // { "action_type": "multiTap", "points": [[relX, relY], ...] }
                val points = action.params["points"] as? List<*>
                val relativePoints = JsonArray()
                points?.forEach { point ->
                    var x = 0
                    var y = 0

                    if (point is Map<*, *>) {
                        x = (point["x"] as? Number)?.toInt() ?: return@forEach
                        y = (point["y"] as? Number)?.toInt() ?: return@forEach
                    } else if (point is List<*>) {
                        x = (point[0] as? Number)?.toInt() ?: return@forEach
                        y = (point[1] as? Number)?.toInt() ?: return@forEach
                    } else {
                        return@forEach
                    }

                    val (relX, relY) = toRelativeCoordinate(x, y, screenWidth, screenHeight)
                    relativePoints.add(JsonArray().apply {
                        add(relX)
                        add(relY)
                    })
                }
                jsonObject.addProperty("action_type", "multiTap")
                jsonObject.add("points", relativePoints)
                jsonObject.addProperty("screen_width", screenWidth)
                jsonObject.addProperty("screen_height", screenHeight)
            }

            else -> {
                // 未知操作类型，保留原始格式
                jsonObject.addProperty("action_type", action.actionType)
                action.params.forEach { (key, value) ->
                    if (key != "action_type") { // 避免重复
                        when (value) {
                            is String -> jsonObject.addProperty(key, value)
                            is Number -> jsonObject.addProperty(key, value)
                            is Boolean -> jsonObject.addProperty(key, value)
                        }
                    }
                }
            }
        }

        // 添加目标应用包名（如果有）
        if (!action.targetPackage.isNullOrBlank()) {
            jsonObject.addProperty("target_package", action.targetPackage)
        }

        return jsonObject.toString()
    }
}

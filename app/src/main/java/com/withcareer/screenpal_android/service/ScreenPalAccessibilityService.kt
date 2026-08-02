package com.withcareer.screenpal_android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.withcareer.screenpal_android.ScreenPalApplication
import android.view.accessibility.AccessibilityNodeInfo
import com.withcareer.screenpal_android.util.AccessibilityServiceHelper
import com.withcareer.screenpal_android.overlay.FloatingAssistantService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred

/**
 * 阿宝无障碍服务
 * 负责执行全局操作、手势模拟、屏幕截图以及节点查找等核心功能
 */
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import com.withcareer.screenpal_android.core.model.SimplifiedUINode
import com.withcareer.screenpal_android.core.ui.UiTreeProcessor
import com.withcareer.screenpal_android.core.ui.UiTreeSnapshot

open class ScreenPalAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: ScreenPalAccessibilityService? = null

        fun getInstance(): ScreenPalAccessibilityService? = instance

        fun isServiceEnabled(): Boolean = instance != null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val uiTreeProcessor = UiTreeProcessor()

    private val _currentApp = MutableStateFlow<String?>(null)
    val currentApp: StateFlow<String?> = _currentApp.asStateFlow()

    /**
     * 主动获取当前前台应用包名
     * 策略调整：优先主动探测 rootInActiveWindow，以确保实时性
     */
    fun getActivePackageName(): String? {
        // 1. 优先主动探测 (Probing First)
        try {
            val root = rootInActiveWindow
            if (root != null) {
                val pkg = root.packageName?.toString()
                if (!pkg.isNullOrBlank()) {
                    // 如果探测结果是本应用，但缓存是其他应用，优先返回缓存
                    val cached = _currentApp.value
                    if (pkg == packageName && !cached.isNullOrBlank() && cached != packageName) {
                        _currentApp.value = cached
                        recycleNode(root)
                        Log.d("ScreenPalService", "Active package fallback used")
                        return cached
                    }
                    _currentApp.value = pkg // 同步更新缓存
                    recycleNode(root)
                    return pkg
                }
                recycleNode(root)
            }
        } catch (e: Exception) {
            Log.w("ScreenPalService", "Error probing active package", e)
        }

        // 2. 探测失败才回退到缓存
        return _currentApp.value
    }

    /**
     * 获取最近一次无障碍事件记录的包名（不主动探测）
     */
    fun getLastKnownPackageName(): String? {
        return _currentApp.value
    }

    /**
     * 基于可交互窗口列表获取前台应用包名（排除悬浮窗影响）
     * 只返回 focused 或 active 的非本应用窗口
     */
    fun getActiveAppPackageNameFromWindows(): String? {
        return try {
            val windows = windows ?: return null
            Log.d("ScreenPalService", "Total windows: ${windows.size}")

            // 仅记录窗口状态，不输出窗口所属应用或节点内容。
            windows.forEachIndexed { index, window ->
                Log.d("ScreenPalService", "Window[$index]: focused=${window.isFocused}, active=${window.isActive}")
            }

            // 策略：找到非系统UI、非自己的第一个窗口
            // 不依赖窗口类型（因为在某些设备上，所有窗口都可能是 SYSTEM 类型）

            // 1. 优先查找 focused 且不是自己的窗口
            val focusedApp = windows.firstOrNull { window ->
                window.isFocused &&
                window.root?.packageName?.toString()?.let { pkg ->
                    pkg != "com.android.systemui" && pkg != packageName
                } == true
            }?.root?.packageName?.toString()

            if (focusedApp != null) {
                Log.d("ScreenPalService", "Focused app found")
                return focusedApp
            }

            // 2. 其次查找 active 且不是自己的窗口
            val activeApp = windows.firstOrNull { window ->
                window.isActive &&
                window.root?.packageName?.toString()?.let { pkg ->
                    pkg != "com.android.systemui" && pkg != packageName
                } == true
            }?.root?.packageName?.toString()

            if (activeApp != null) {
                Log.d("ScreenPalService", "Active app found")
                return activeApp
            }

            // 3. 最后尝试：找任何非系统UI、非自己的窗口（按顺序，通常第一个就是目标应用）
            val anyApp = windows.firstOrNull { window ->
                window.root?.packageName?.toString()?.let { pkg ->
                    pkg != "com.android.systemui" && pkg != packageName
                } == true
            }?.root?.packageName?.toString()

            Log.d("ScreenPalService", "Active app resolution completed: found=${anyApp != null}")
            anyApp
        } catch (e: Exception) {
            Log.w("ScreenPalService", "Failed to get active app from windows", e)
            null
        }
    }

    private val _latestScreenshot = MutableStateFlow<Bitmap?>(null)
    private var lastUiChangeTs: Long = 0L
    private var lastRecordedTapTs: Long = 0L
    private var lastRecordedTapX: Int = 0
    private var lastRecordedTapY: Int = 0

    /**
     * 服务连接时调用
     * 初始化服务实例，绑定 Application，启动保活服务
     */
    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            instance = this
            AccessibilityServiceHelper.setServiceConnected(true)

            try {
                val info = serviceInfo ?: AccessibilityServiceInfo()
                val flags = info.flags or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                info.flags = flags
                serviceInfo = info
            } catch (e: Exception) {
                Log.w("ScreenPalService", "Failed to set serviceInfo flags", e)
            }

            val app = application as? ScreenPalApplication
            scope.launch {
                app?.agentRuntime?.attachAccessibilityService(this@ScreenPalAccessibilityService)
            }

            // 启动保活服务
            KeepAliveService.start(this)
        } catch (e: Exception) {
            Log.e("ScreenPalService", "Error in onServiceConnected", e)
        }
    }

    /**
     * 服务销毁时调用
     * 清理资源，解绑 Application，停止保活服务
     */
    override fun onDestroy() {
        try {
            // 停止保活服务
            KeepAliveService.stop(this)

            val app = application as? ScreenPalApplication
            app?.agentRuntime?.detachAccessibilityService()

            scope.cancel()
            super.onDestroy()
        } catch (e: Exception) {
            Log.e("ScreenPalService", "Error in onDestroy", e)
        } finally {
            instance = null
            AccessibilityServiceHelper.setServiceConnected(false)
        }
    }

    /**
     * 接收无障碍事件
     * 监听窗口状态变化，更新当前应用包名
     * @param event 无障碍事件
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            event?.let {
                when (it.eventType) {
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                        val packageName = it.packageName?.toString()
                        _currentApp.value = packageName
                        lastUiChangeTs = System.currentTimeMillis()
                    }
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                    AccessibilityEvent.TYPE_VIEW_CLICKED,
                    AccessibilityEvent.TYPE_VIEW_FOCUSED,
                    AccessibilityEvent.TYPE_VIEW_SELECTED,
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                        lastUiChangeTs = System.currentTimeMillis()
                    }
                    else -> {}
                }

                // 录制模式：捕获各种交互事件
                val recordingManager = com.withcareer.screenpal_android.recording.RecordingManager.instance
                if (recordingManager?.isRecording == true) {
                    // 录制主来源为全屏蒙层时，默认禁用无障碍录制，避免重复
                    // 但允许文本变化事件，用于输入录制
                    val overlayRecordingActive =
                        com.withcareer.screenpal_android.recording.RecordingService.instance?.isOverlayRecordingActive
                    if (overlayRecordingActive == true) {
                        if (it.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                            return
                        }
                    }
                    // 回放期间忽略无障碍事件，避免录制回放导致死循环
                    val recordingService = com.withcareer.screenpal_android.recording.RecordingService.instance
                    if (recordingService?.isReplaying == true) {
                        return
                    }
                    if (recordingService?.isBatchMultiTapEnabled == true) {
                        return
                    }

                    // 本应用内由蒙层负责录制，避免与无障碍重复录制
                    val pkg = it.packageName?.toString()
                    if (pkg == packageName) {
                        return
                    }

                    when (it.eventType) {
                        AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                            // 捕获点击事件，通过控件位置推断坐标
                            try {
                                val node = it.source
                                if (node != null) {
                                    val rect = Rect()
                                    node.getBoundsInScreen(rect)
                                    // 使用控件中心点作为点击坐标
                                    val centerX = rect.centerX()
                                    val centerY = rect.centerY()
                                    val now = System.currentTimeMillis()
                                    val delta = now - lastRecordedTapTs
                                    val dx = centerX - lastRecordedTapX
                                    val dy = centerY - lastRecordedTapY
                                    val distSq = dx * dx + dy * dy
                                    if (delta > 200 || distSq > 100) {
                                        // 获取目标应用包名
                                        val targetPackage = pkg ?: node.packageName?.toString()
                                        recordingManager.recordTap(centerX, centerY, targetPackage)
                                        lastRecordedTapTs = now
                                        lastRecordedTapX = centerX
                                        lastRecordedTapY = centerY
                                        Log.d("ScreenPalService", "Recorded click")
                                    } else {
                                        Log.d("ScreenPalService", "Skipped duplicate click")
                                    }
                                    recycleNode(node)
                                }
                            } catch (e: Exception) {
                                Log.e("ScreenPalService", "Error recording click", e)
                            }
                        }
                        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                            val text = it.text?.firstOrNull()?.toString()
                            if (!text.isNullOrEmpty()) {
                                com.withcareer.screenpal_android.recording.RecordingService.instance
                                    ?.onTextChangedDuringRecording()
                                recordingManager.recordType(text)
                            }
                        }
                        AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                            // 捕获滚动方向并记录
                            val scrollY = it.scrollY
                            val direction = if (scrollY > 0) "down" else "up"
                            recordingManager.recordScroll(direction)
                        }
                        // 可以添加更多手势检测
                        AccessibilityEvent.TYPE_GESTURE_DETECTION_START,
                        AccessibilityEvent.TYPE_GESTURE_DETECTION_END -> {
                            // 手势检测（需要配置）
                            Log.d("ScreenPalService", "Gesture detected")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenPalService", "Error in onAccessibilityEvent", e)
        }
    }

    /**
     * 服务中断时调用
     */
    override fun onInterrupt() {
        // 服务中断
    }

    fun getUiTreeSnapshot(
        maxNodes: Int = 160,
        maxDepth: Int = 8,
        targetPackage: String? = null
    ): UiTreeSnapshot? {
        try {
            val detectedPkg = targetPackage?.takeIf { it.isNotBlank() }
                ?: getActivePackageName()?.takeIf { it.isNotBlank() }
                ?: rootInActiveWindow?.packageName?.toString()?.takeIf { it.isNotBlank() }
            val excludedPkgs = setOf("android", "com.android.systemui")
            data class RootSelection(
                val roots: List<AccessibilityNodeInfo>,
                val matchedWindows: List<Int>,
                val windowsCount: Int
            )

            fun collectRoots(): RootSelection {
                val roots = mutableListOf<AccessibilityNodeInfo>()
                val matchedWindows = mutableListOf<Int>()
                val winList = try { windows } catch (_: Exception) { null }
                val windowsCount = winList?.size ?: 0
                if (!winList.isNullOrEmpty()) {
                    winList.forEachIndexed { idx, w ->
                        try {
                            val r = w.root
                            if (r != null) {
                                val pkg = r.packageName?.toString()
                                if (!pkg.isNullOrBlank()) {
                                    if (detectedPkg != null && pkg == detectedPkg) {
                                        roots.add(r)
                                        matchedWindows.add(idx)
                                    } else if (detectedPkg == null && !excludedPkgs.contains(pkg)) {
                                        roots.add(r)
                                        matchedWindows.add(idx)
                                    }
                                }
                            }
                        } catch (_: Exception) { }
                    }
                }
                if (roots.isEmpty()) {
                    val root = rootInActiveWindow ?: return RootSelection(emptyList(), emptyList(), windowsCount)
                    roots.add(root)
                }
                return RootSelection(roots = roots, matchedWindows = matchedWindows, windowsCount = windowsCount)
            }

            data class PassResult(
                val snapshot: UiTreeSnapshot,
                val selection: RootSelection,
                val passMaxNodes: Int,
                val passMaxDepth: Int
            )

            fun buildSnapshot(passMaxNodes: Int, passMaxDepth: Int): PassResult? {
                val selection = collectRoots()
                if (selection.roots.isEmpty()) {
                    Log.w("ScreenPalService", "UiTreeSnapshot failed: root null")
                    return null
                }
                val processor = UiTreeProcessor(maxNodes = passMaxNodes, maxDepth = passMaxDepth)
                val mergedElements = ArrayList<com.withcareer.screenpal_android.core.ui.UiElement>(passMaxNodes * selection.roots.size)
                val pkgName: String? = detectedPkg ?: selection.roots.firstOrNull()?.packageName?.toString()
                selection.roots.forEachIndexed { idx, r ->
                    val snap = processor.capture(r, rootPathPrefix = "w$idx")
                    if (snap != null) mergedElements.addAll(snap.elements)
                }

                fun addIndex(map: MutableMap<String, MutableList<String>>, key: String?, id: String) {
                    if (key.isNullOrBlank()) return
                    val k = key.lowercase()
                    val list = map.getOrPut(k) { mutableListOf() }
                    list.add(id)
                }

                val textIndex = HashMap<String, MutableList<String>>()
                val idIndex = HashMap<String, MutableList<String>>()
                val roleIndex = HashMap<String, MutableList<String>>()
                val descIndex = HashMap<String, MutableList<String>>()
                mergedElements.forEach { el ->
                    addIndex(textIndex, el.text, el.id)
                    addIndex(textIndex, el.contentDescription, el.id)
                    addIndex(idIndex, el.resourceId, el.id)
                    addIndex(roleIndex, el.role, el.id)
                    addIndex(descIndex, el.contentDescription, el.id)
                }

                val snapshot = UiTreeSnapshot(
                    packageName = pkgName,
                    elements = mergedElements,
                    textIndex = textIndex,
                    idIndex = idIndex,
                    roleIndex = roleIndex,
                    descIndex = descIndex
                )
                return PassResult(
                    snapshot = snapshot,
                    selection = selection,
                    passMaxNodes = passMaxNodes,
                    passMaxDepth = passMaxDepth
                )
            }

            fun clickableCount(s: UiTreeSnapshot): Int = s.elements.count { it.clickable }
            fun labeledCount(s: UiTreeSnapshot): Int = s.elements.count { !it.text.isNullOrBlank() || !it.contentDescription.isNullOrBlank() }
            fun score(s: UiTreeSnapshot): Int = (s.elements.size * 2) + (clickableCount(s) * 3) + (labeledCount(s) * 2)

            val firstPass = buildSnapshot(passMaxNodes = maxNodes, passMaxDepth = maxDepth) ?: return null
            val firstSnap = firstPass.snapshot
            val firstClickable = clickableCount(firstSnap)
            val firstLabeled = labeledCount(firstSnap)

            val needSecondPass = firstSnap.elements.size < 25 || firstClickable < 8 || firstLabeled < 8
            val passMaxNodes2 = kotlin.math.max(800, maxNodes)
            val passMaxDepth2 = kotlin.math.max(20, maxDepth)
            val snap = if (needSecondPass) {
                val secondPass = buildSnapshot(passMaxNodes = passMaxNodes2, passMaxDepth = passMaxDepth2)
                if (secondPass != null) {
                    val secondSnap = secondPass.snapshot
                    val selected = if (score(secondSnap) > score(firstSnap)) secondSnap else firstSnap
                    selected
                } else {
                    firstSnap
                }
            } else {
                firstSnap
            }
            // snap is guaranteed to be non-null here
            run {
                Log.d("ScreenPalService", "UiTreeSnapshot captured: elements=${snap.elements.size}")
                if (snap.elements.size < 25) {
                    try {
                        val w = try { windows } catch (_: Exception) { null }
                        val wCount = w?.size ?: 0
                        val clickableCount = snap.elements.count { it.clickable }
                        val labeledCount = snap.elements.count { !it.text.isNullOrBlank() || !it.contentDescription.isNullOrBlank() }
                        val selection = collectRoots()
                        Log.w("UiTreeDiag", "LOW_NODES elements=${snap.elements.size} clickable=${clickableCount} labeled=${labeledCount} windows=${wCount} matchedWindows=${selection.matchedWindows}")
                    } catch (_: Exception) { }
                }
            }
            return snap
        } catch (e: Exception) {
            Log.e("ScreenPalService", "UiTreeSnapshot capture error", e)
            return null
        }
    }

    fun getUiTreeSnapshotJson(
        maxNodes: Int = 160,
        maxDepth: Int = 8,
        targetPackage: String? = null
    ): String? {
        return try {
            val snap = getUiTreeSnapshot(maxNodes, maxDepth, targetPackage = targetPackage)
            snap?.toJson()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取屏幕截图 (内部实现)
     * 使用 Android R (API 30) 及以上版本的 takeScreenshot API
     * @param callback 截图结果回调
     */
    private fun takeScreenshot(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Log.d("ScreenPalService", "Starting screenshot...")
                takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            Log.d("ScreenPalService", "Screenshot captured successfully")
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val hardwareBuffer = result.hardwareBuffer
                                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, null)
                                    hardwareBuffer.close()

                                    // 将 HARDWARE 格式的 Bitmap 转换为可访问的格式
                                    val bitmap = if (hardwareBitmap != null && hardwareBitmap.config == Bitmap.Config.HARDWARE) {
                                        // 转换为 ARGB_8888 格式以便访问像素
                                        hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                    } else {
                                        hardwareBitmap
                                    }

                                    // 回收硬件 Bitmap（如果已转换）
                                    if (bitmap != hardwareBitmap) {
                                        hardwareBitmap?.recycle()
                                    }

                                    _latestScreenshot.value = bitmap
                                    Log.d("ScreenPalService", "Screenshot converted successfully")
                                    callback(bitmap)
                                } else {
                                    Log.w("ScreenPalService", "Android version not supported")
                                    callback(null)
                                }
                            } catch (e: Exception) {
                                Log.e("ScreenPalService", "Failed to process screenshot", e)
                                callback(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.e("ScreenPalService", "Screenshot failed, error code: $errorCode")
                            callback(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("ScreenPalService", "Failed to call screenshot API", e)
                callback(null)
            }
        } else {
            Log.w("ScreenPalService", "Android version lower than R; screenshot not supported")
            callback(null)
        }
    }

    /**
     * 挂起函数形式获取屏幕截图
     * 包含隐藏悬浮窗、等待 UI 更新、触发截图、恢复悬浮窗等完整流程
     * @return 截图 Bitmap，如果失败则返回 null
     */
    suspend fun takeScreenshotSuspend(): Bitmap? {
        // 1. 隐藏悬浮窗并等待动画结束
        // 使用 suspendCancellableCoroutine 桥接回调
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                // 在主线程发起隐藏请求
                scope.launch(Dispatchers.Main) {
                    FloatingAssistantService.setScreenshotMode(true) {
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenPalService", "Wait for screenshot mode failed", e)
        }

        // 2. 动画已结束，View.GONE 已设置。等待 SurfaceFlinger 刷新缓冲区。
        // 50ms 缓冲足够应对大多数情况 (3帧)
        delay(50)

        return try {
            val deferred = CompletableDeferred<Bitmap?>()

            // 3. 触发截图
            // 注意：takeScreenshot 是异步调用，但系统通常会尽快锁定当前屏幕缓冲区
            takeScreenshot { bitmap ->
                deferred.complete(bitmap)
            }

            // 4. 等待捕获完成 (盲等)
            // 我们不等待 bitmap 处理完成（可能需要几百毫秒），而是等待"足够长"的时间让 SurfaceFlinger 捕获屏幕
            // 50ms 大约是 3 帧，通常足够系统完成 Buffer Latching，避免悬浮窗残影
            delay(50)

            // 5. 立即恢复悬浮窗
            // 此时 Bitmap 可能还在处理中，但屏幕捕获已完成，我们可以恢复 UI 以减少闪烁感
            withContext(Dispatchers.Main) {
                FloatingAssistantService.setScreenshotMode(false)
            }

            // 6. 等待结果返回
            deferred.await()

        } catch (e: Exception) {
            Log.e("ScreenPalService", "takeScreenshotSuspend error", e)
            // 发生异常确保恢复
            withContext(Dispatchers.Main) {
                FloatingAssistantService.setScreenshotMode(false)
            }
            null
        }
    }


    fun getSimplifiedUiTreeString(
        maxNodes: Int = 120,
        maxDepth: Int = 8,
        maxChars: Int = 8000,
        screenWidth: Int = 0,
        screenHeight: Int = 0
    ): String? {
        val root = rootInActiveWindow ?: return null
        try {
            val budget = UiTreeBudget(maxNodes = maxNodes, maxDepth = maxDepth)
            val simplified = convertToSimplifiedNode(root, depth = 0, budget = budget, screenWidth = screenWidth, screenHeight = screenHeight) ?: return null
            val str = simplified.toString()
            val result = if (str.length <= maxChars) str else str.take(maxChars)
            Log.d("ScreenPalService", "SimplifiedUiTree generated: length=${result.length}")
            return result
        } catch (e: Exception) {
            Log.e("ScreenPalService", "Error getting simplified UI tree", e)
            return null
        } finally {
            recycleNode(root)
        }
    }

    private data class UiTreeBudget(
        var nodes: Int = 0,
        val maxNodes: Int = 120,
        val maxDepth: Int = 8
    )

    private fun convertToSimplifiedNode(
        node: AccessibilityNodeInfo?,
        depth: Int,
        budget: UiTreeBudget,
        screenWidth: Int,
        screenHeight: Int
    ): SimplifiedUINode? {
        if (node == null || !node.isVisibleToUser) return null
        if (budget.nodes >= budget.maxNodes) return null
        if (depth > budget.maxDepth) return null

        fun toRelX(x: Int): Int {
            if (screenWidth <= 0) return x
            return ((x.toFloat() / screenWidth.toFloat()) * 1000f).toInt().coerceIn(0, 1000)
        }

        fun toRelY(y: Int): Int {
            if (screenHeight <= 0) return y
            return ((y.toFloat() / screenHeight.toFloat()) * 1000f).toInt().coerceIn(0, 1000)
        }

        // Collect valid children first
        val validChildren = mutableListOf<SimplifiedUINode>()
        for (i in 0 until node.childCount) {
            if (budget.nodes >= budget.maxNodes) break
            val child = node.getChild(i)
            if (child != null) {
                val convertedChild = convertToSimplifiedNode(child, depth = depth + 1, budget = budget, screenWidth = screenWidth, screenHeight = screenHeight)
                if (convertedChild != null) {
                    validChildren.add(convertedChild)
                }
                recycleNode(child)
            }
        }

        // Check if this node is interesting
        val isActionable = node.isClickable || node.isCheckable || node.isEditable || node.isScrollable
        val hasText = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
        val hasChildren = validChildren.isNotEmpty()

        // Pruning strategy: Keep if actionable, has info, or has valid children
        if (isActionable || hasText || hasChildren) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            budget.nodes++
            return SimplifiedUINode(
                className = node.className?.toString(),
                text = node.text?.toString(),
                contentDesc = node.contentDescription?.toString(),
                resourceId = node.viewIdResourceName,
                bounds = "[${toRelX(bounds.left)},${toRelY(bounds.top)}][${toRelX(bounds.right)},${toRelY(bounds.bottom)}]", // 0-1000 coordinate space
                isClickable = node.isClickable,
                children = validChildren
            )
        }

        return null
    }

    /**
     * 检查当前屏幕是否包含敏感关键词
     * @param keywords 敏感关键词列表
     * @return 是否包含
     */
    fun checkSensitiveContent(keywords: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            // 快速遍历查找
            var found = false

            fun traverse(node: AccessibilityNodeInfo?) {
                if (node == null || found) return

                // 检查节点文本
                if (!node.text.isNullOrBlank()) {
                    val text = node.text.toString()
                    if (keywords.any { text.contains(it, ignoreCase = true) }) {
                        found = true
                        return
                    }
                }
                // 检查节点描述
                if (!node.contentDescription.isNullOrBlank()) {
                    val desc = node.contentDescription.toString()
                    if (keywords.any { desc.contains(it, ignoreCase = true) }) {
                        found = true
                        return
                    }
                }

                for (i in 0 until node.childCount) {
                    traverse(node.getChild(i))
                    if (found) return
                }
            }

            traverse(root)
            return found
        } catch (e: Exception) {
            Log.e("ScreenPalService", "checkSensitiveContent error", e)
            return false
        } finally {
            recycleNode(root)
        }
    }

    /**
     * 根据文本查找节点
     * @param text 要查找的文本
     * @return 找到的第一个节点，如果未找到返回 null
     */
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        try {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (nodes.isNullOrEmpty()) return null

            val target = nodes[0]
            // 回收其他不需要的节点
            for (i in 1 until nodes.size) {
                recycleNode(nodes[i])
            }
            return target
        } catch (e: Exception) {
            Log.e("ScreenPalService", "findNodeByText error", e)
            return null
        } finally {
            recycleNode(root)
        }
    }


    /**
     * 执行点击手势
     * @param x 点击坐标 X
     * @param y 点击坐标 Y
     */
    suspend fun tap(x: Float, y: Float) {
        try {
            val (cx, cy) = clampToTouchableArea(x, y)
            val path = Path().apply { moveTo(cx, cy) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50)) // 降低点击时间到 50ms，提高响应速度
                .build()

            var dispatchedOk = false
            suspendCancellableCoroutine<Unit> { continuation ->
                val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        dispatchedOk = true
                        Log.d("ScreenPalService", "tap dispatch completed")
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        dispatchedOk = false
                        Log.w("ScreenPalService", "tap dispatch cancelled")
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }, null)
                if (!dispatched) {
                    dispatchedOk = false
                    Log.w("ScreenPalService", "tap dispatch failed to start")
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }

            if (!dispatchedOk) {
                try {
                    val node = findClickableNodeAt(cx, cy)
                    if (node != null) {
                        performClick(node)
                    }
                } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            Log.e("ScreenPalService", "tap failed", e)
        }
    }

    /**
     * 执行多点同时点击
     * @param points 点击坐标列表
     * @param durationMs 点击持续时间
     */
    suspend fun multiTap(points: List<Pair<Float, Float>>, durationMs: Long = 50L) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            tap(points.first().first, points.first().second)
            return
        }
        try {
            val builder = GestureDescription.Builder()
            points.forEach { (x, y) ->
                val (cx, cy) = clampToTouchableArea(x, y)
                val path = Path().apply { moveTo(cx, cy) }
                builder.addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            }
            val gesture = builder.build()

            suspendCancellableCoroutine<Unit> { continuation ->
                val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.d("ScreenPalService", "multiTap dispatch completed, points=${points.size}")
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w("ScreenPalService", "multiTap dispatch cancelled, points=${points.size}")
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }, null)
                if (!dispatched) {
                    Log.w("ScreenPalService", "multiTap dispatch failed to start, points=${points.size}")
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenPalService", "multiTap failed", e)
        }
    }

    /**
     * 执行长按手势
     * @param x 长按坐标 X
     * @param y 长按坐标 Y
     */
    /**
     * 长按（使用自定义时长）
     * @param x X坐标
     * @param y Y坐标
     * @param durationMs 长按时长（毫秒）
     */
    suspend fun longPress(x: Float, y: Float, durationMs: Long = 1500L) {
        try {
            val (cx, cy) = clampToTouchableArea(x, y)
            val path = Path().apply {
                moveTo(cx, cy)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()

            var dispatchedOk = false
            suspendCancellableCoroutine<Unit> { continuation ->
                val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        dispatchedOk = true
                        Log.d("ScreenPalService", "longPress dispatch completed, duration=$durationMs")
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        dispatchedOk = false
                        Log.w("ScreenPalService", "longPress dispatch cancelled, duration=$durationMs")
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }, null)

                if (!dispatched) {
                    dispatchedOk = false
                    Log.w("ScreenPalService", "longPress dispatch failed to start")
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }

            if (!dispatchedOk) {
                try {
                    val node = findClickableNodeAt(cx, cy)
                    if (node != null) {
                        performLongClick(node)
                    }
                } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            Log.e("ScreenPalService", "longPress failed", e)
        }
    }

    /**
     * 执行滑动手势
     * @param startX 起始坐标 X
     * @param startY 起始坐标 Y
     * @param endX 结束坐标 X
     * @param endY 结束坐标 Y
     * @param duration 滑动持续时间，默认 300ms
     */
    suspend fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300) {
        try {
            val (sx, sy) = clampToTouchableArea(startX, startY)
            val (ex, ey) = clampToTouchableArea(endX, endY)
            val path = Path().apply {
                moveTo(sx, sy)
                lineTo(ex, ey)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()

            suspendCancellableCoroutine<Unit> { continuation ->
                val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }, null)

                if (!dispatched) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenPalService", "swipe failed", e)
        }
    }

    /**
     * 滑动（使用完整轨迹）
     * @param pathPoints 轨迹点列表
     * @param duration 总时长（毫秒）
     */
    suspend fun swipeWithPath(pathPoints: List<Pair<Float, Float>>, duration: Long = 300) {
        try {
            if (pathPoints.size < 2) {
                Log.w("ScreenPalService", "Path too short: ${pathPoints.size} points")
                return
            }

            val path = Path().apply {
                val first = pathPoints.first()
                val (sx, sy) = clampToTouchableArea(first.first, first.second)
                moveTo(sx, sy)

                pathPoints.drop(1).forEach { (x, y) ->
                    val (cx, cy) = clampToTouchableArea(x, y)
                    lineTo(cx, cy)
                }
            }

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()

            suspendCancellableCoroutine<Unit> { continuation ->
                val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.d("ScreenPalService", "swipeWithPath dispatch completed, duration=$duration")
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w("ScreenPalService", "swipeWithPath dispatch cancelled, duration=$duration")
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }, null)

                if (!dispatched) {
                    Log.w("ScreenPalService", "swipeWithPath dispatch failed to start, duration=$duration")
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenPalService", "swipeWithPath failed", e)
        }
    }

    /**
     * 带时间戳的变速滑动
     * 通过将轨迹切分为多个连续的 Stroke 来模拟速度变化
     * @param points 轨迹点 (x, y, relativeTimeMs)
     */
    suspend fun swipeWithTimedPath(points: List<Triple<Float, Float, Long>>) {
        if (points.size < 2) return

        // 如果系统版本低于 O (26)，不支持连续手势，只能退化为匀速
        // 为了安全起见，我们还是检查一下，虽然目标设备通常都支持
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.w("ScreenPalService", "System version too low; falling back to uniform swipe")
            val pathPoints = points.map { Pair(it.first, it.second) }
            val duration = points.last().third
            swipeWithPath(pathPoints, duration)
            return
        }

        try {
            // 切分策略：降低阈值以提高对变速的响应灵敏度
            // 之前的 200ms 可能导致慢速滑动被平滑成匀速
            val SEGMENT_DURATION_THRESHOLD = 50L

            val segments = mutableListOf<GestureSegment>()

            var segmentStartIdx = 0
            var segmentStartTime = points[0].third

            // 构建 segments
            for (i in 1 until points.size) {
                val p = points[i]
                val dt = p.third - segmentStartTime

                // 如果当前段积累的时间超过阈值，或者是最后一个点，则生成 segment
                if (dt >= SEGMENT_DURATION_THRESHOLD || i == points.size - 1) {
                    val path = Path()
                    val startP = points[segmentStartIdx]
                    val (sx, sy) = clampToTouchableArea(startP.first, startP.second)
                    path.moveTo(sx, sy)

                    // 将中间的点都加入 path
                    for (j in segmentStartIdx + 1..i) {
                        val (ex, ey) = clampToTouchableArea(points[j].first, points[j].second)
                        path.lineTo(ex, ey)
                    }

                    // 确保 duration 至少为 1ms
                    val duration = dt.coerceAtLeast(1L)
                    segments.add(GestureSegment(path, duration))

                    // 准备下一段
                    segmentStartIdx = i
                    segmentStartTime = p.third
                }
            }

            Log.d("ScreenPalService", "swipeWithTimedPath: split ${points.size} points into ${segments.size} segments. Total duration: ${points.last().third}ms")

            // 执行分段手势
            var lastStroke: GestureDescription.StrokeDescription? = null

            for (i in segments.indices) {
                val segment = segments[i]
                val isLast = i == segments.size - 1

                val stroke = if (lastStroke == null) {
                    GestureDescription.StrokeDescription(
                        segment.path,
                        0,
                        segment.duration,
                        !isLast
                    )
                } else {
                    lastStroke!!.continueStroke(
                        segment.path,
                        0,
                        segment.duration,
                        !isLast
                    )
                }

                lastStroke = stroke

                val gesture = GestureDescription.Builder()
                    .addStroke(stroke)
                    .build()

                // 派发并等待完成
                val success = suspendCancellableCoroutine<Boolean> { cont ->
                     val result = dispatchGesture(gesture, object : GestureResultCallback() {
                         override fun onCompleted(desc: GestureDescription?) {
                             if (cont.isActive) cont.resume(true)
                         }
                         override fun onCancelled(desc: GestureDescription?) {
                             Log.w("ScreenPalService", "Gesture segment $i cancelled")
                             if (cont.isActive) cont.resume(false)
                         }
                     }, null)

                     if (!result) {
                         Log.e("ScreenPalService", "Failed to dispatch gesture segment $i")
                         if (cont.isActive) cont.resume(false)
                     }
                }

                if (!success) {
                    Log.e("ScreenPalService", "Segment $i failed, aborting gesture")
                    break
                }
            }

        } catch (e: Exception) {
            Log.e("ScreenPalService", "swipeWithTimedPath failed", e)
        }
    }

    private data class GestureSegment(val path: Path, val duration: Long)

    /**
     * 执行点按然后滑动手势
     * 适用于需要先激活控件（如Slider）再拖动的场景
     * @param startX 起始坐标 X
     * @param startY 起始坐标 Y
     * @param endX 结束坐标 X
     * @param endY 结束坐标 Y
     * @param duration 滑动持续时间，默认 300ms
     */
    suspend fun tapAndSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300) {
        try {
            // 先点击
            tap(startX, startY)
            // 等待一小段时间，确保控件已响应点击（例如获取焦点或激活滑块）
            delay(200)
            // 再滑动
            swipe(startX, startY, endX, endY, duration)
        } catch (e: Exception) {
            Log.e("ScreenPalService", "tapAndSwipe failed", e)
        }
    }

    /**
     * 模拟双指捏合（缩小/Zoom Out）
     * @param centerX 中心点X
     * @param centerY 中心点Y
     * @param distance 手指移动距离（单指）
     */
    suspend fun pinchIn(centerX: Float, centerY: Float, distance: Float = 200f) {
        performPinch(centerX, centerY, distance, 0f)
    }

    /**
     * 模拟双指张开（放大/Zoom In）
     * @param centerX 中心点X
     * @param centerY 中心点Y
     * @param distance 手指移动距离（单指）
     */
    suspend fun pinchOut(centerX: Float, centerY: Float, distance: Float = 200f) {
        performPinch(centerX, centerY, 0f, distance)
    }

    /**
     * 执行捏合/张开手势
     * @param centerX 中心点X
     * @param centerY 中心点Y
     * @param startRadius 起始半径
     * @param endRadius 结束半径
     */
    private suspend fun performPinch(centerX: Float, centerY: Float, startRadius: Float, endRadius: Float) {
        delay(50)
        try {
            val (cx, cy) = clampToTouchableArea(centerX, centerY)
            // 手指1：左上角方向移动
            val path1 = Path()
            path1.moveTo(cx - startRadius, cy - startRadius)
            path1.lineTo(cx - endRadius, cy - endRadius)

            // 手指2：右下角方向移动
            val path2 = Path()
            path2.moveTo(cx + startRadius, cy + startRadius)
            path2.lineTo(cx + endRadius, cy + endRadius)

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path1, 0, 400))
                .addStroke(GestureDescription.StrokeDescription(path2, 0, 400))
                .build()

            suspendCancellableCoroutine<Unit> { continuation ->
                val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }, null)

                if (!dispatched) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenPalService", "performPinch failed", e)
        }
    }

    private fun clampToTouchableArea(x: Float, y: Float): Pair<Float, Float> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val metrics = wm.currentWindowMetrics
                val bounds = metrics.bounds
                // 不再排除 SystemBars insets，允许全屏点击
                // 仅确保坐标在屏幕范围内
                val minX = 0f
                val maxX = bounds.width().toFloat()
                val minY = 0f
                val maxY = bounds.height().toFloat()
                Pair(x.coerceIn(minX, maxX), y.coerceIn(minY, maxY))
            } else {
                Pair(x, y)
            }
        } catch (e: Exception) {
            Pair(x, y)
        }
    }

    private fun findClickableNodeAt(x: Float, y: Float): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        var target: AccessibilityNodeInfo? = null

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            // 如果已经找到目标，后续遍历到的节点直接回收并返回
            if (target != null) {
                if (node != target) recycleNode(node)
                return
            }

            try {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                // 检查坐标是否在范围内
                if (!rect.isEmpty && rect.contains(x.toInt(), y.toInt())) {
                    // 1. 检查自身
                    if (node.isClickable) {
                        target = node
                        return // node 将被保留（不回收）
                    }

                    // 2. 检查父级 (向上传播)
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            target = parent
                            return // parent 被保留作为 target，node 将在 finally 中被回收
                        }
                        val oldParent = parent
                        parent = parent.parent
                        recycleNode(oldParent)
                    }
                }

                // 继续遍历子节点
                for (i in 0 until node.childCount) {
                    traverse(node.getChild(i))
                    if (target != null) break
                }
            } catch (e: Exception) {
                Log.e("ScreenPalService", "traverse error", e)
            } finally {
                // 如果当前节点不是目标节点，则回收
                if (node != target) {
                    recycleNode(node)
                }
            }
        }

        try {
            traverse(root)
            return target
        } catch (e: Exception) {
            recycleNode(target)
            return null
        }
    }

    /**
     * 执行返回操作
     */
    fun performBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * 执行回到桌面操作
     */
    fun performHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * 打开最近任务
     */
    fun performRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * 打开通知栏
     */
    fun performNotifications() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    /**
     * 打开快速设置
     */
    fun performQuickSettings() {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    /**
     * 锁屏
     * 需要 Android P (API 28) 及以上
     */
    fun performLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    /**
     * 打开电源菜单
     */
    fun performPowerDialog() {
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }

    /**
     * 切换分屏
     * 需要 Android N (API 24) 及以上
     */
    fun performToggleSplitScreen() {
        performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
    }

    /**
     * 触发系统截图
     * 需要 Android P (API 28) 及以上
     */
    fun performScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
    }

    /**
     * 点击节点
     * 如果节点不可点击，会尝试向上查找可点击的父节点
     * @param node 目标节点
     * @return 是否成功
     */
    fun performClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        // 如果节点不可点击，尝试找到父节点
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                try {
                    return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } finally {
                    recycleNode(parent)
                }
            }
            val oldParent = parent
            parent = parent.parent
            recycleNode(oldParent)
        }
        return false
    }

    /**
     * 对节点执行长按操作
     */
    fun performLongClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isLongClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        }

        // 尝试查找可长按的父节点
        var parent = node.parent
        while (parent != null) {
            if (parent.isLongClickable) {
                try {
                    return parent.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                } finally {
                    recycleNode(parent)
                }
            }
            val oldParent = parent
            parent = parent.parent
            recycleNode(oldParent)
        }

        return false
    }



    /**
     * 设置节点文本
     * 优先尝试 ACTION_SET_TEXT，如果失败则尝试使用粘贴板 + ACTION_PASTE
     * 这对于 H5 页面、小程序或 Flutter 应用的输入框通常更有效
     *
     * @param node 目标节点
     * @param text 要输入的文本
     * @return 是否成功
     */
    suspend fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        try {
            // 1. 尝试直接设置文本 (最快，无副作用)
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                Log.d("ScreenPalService", "setText succeeded using ACTION_SET_TEXT")
                return true
            }

            // 2. 如果直接设置失败，尝试使用粘贴板方案 (兼容性更好)
            Log.w("ScreenPalService", "ACTION_SET_TEXT failed, falling back to paste")
            return pasteText(node, text)
        } catch (e: Exception) {
            Log.e("ScreenPalService", "Error in setText", e)
            return false
        }
    }

    /**
     * 使用粘贴板输入文本
     * 适用于不支持 SET_TEXT 但支持 PASTE 的节点 (如 WebView, H5)
     */
    private suspend fun pasteText(node: AccessibilityNodeInfo, text: String): Boolean {
        try {
            // 复制到剪贴板
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("ScreenPal Input", text)
            clipboard.setPrimaryClip(clip)

            // 给系统一点时间同步剪贴板
            delay(100)

            // 尝试系统粘贴动作
            if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                // ACTION_PASTE 返回 true 仅代表指令已分发，不代表内容已更新
                // 增加双重验证：等待并检查文本是否发生变化
                delay(250) // 等待 UI 响应

                // 刷新节点状态
                node.refresh()
                val currentText = node.text?.toString() ?: ""

                // 验证逻辑：如果文本包含了目标内容，或者节点变成了非空（针对原本为空的情况）
                // 简单的包含检查通常足够
                if (currentText.contains(text)) {
                    Log.d("ScreenPalService", "pasteText verified success: content updated")
                    return true
                } else {
                    Log.w("ScreenPalService", "pasteText returned true but content verification failed")
                    // 验证失败，继续尝试手动粘贴
                }
            } else {
                Log.w("ScreenPalService", "pasteText ACTION_PASTE returned false")
            }

            Log.w("ScreenPalService", "System paste failed or unverified, trying manual long-press paste...")
            return pasteTextManually(node)
        } catch (e: Exception) {
            Log.e("ScreenPalService", "Error in pasteText", e)
            return false
        }
    }

    /**
     * 手动执行粘贴操作：长按 -> 等待菜单 -> 点击“粘贴”
     */
    private suspend fun pasteTextManually(node: AccessibilityNodeInfo): Boolean {
        try {
            // 1. 计算中心坐标
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val cx = rect.centerX().toFloat()
            val cy = rect.centerY().toFloat()

            // 2. 执行长按
            Log.d("ScreenPalService", "Manual Paste: long press started")
            longPress(cx, cy)

            // 3. 等待菜单弹出
            delay(800)

            // 4. 查找“粘贴”或“Paste”按钮
            // 注意：这里需要重新获取 root，因为菜单可能在新的 Window 中
            val pasteNode = findNodeByText("粘贴") ?: findNodeByText("Paste")

            if (pasteNode != null) {
                Log.d("ScreenPalService", "Manual Paste: Found paste button, clicking...")
                val clicked = performClick(pasteNode)
                recycleNode(pasteNode)
                return clicked
            } else {
                Log.w("ScreenPalService", "Manual Paste: Paste button not found")
                return false
            }
        } catch (e: Exception) {
            Log.e("ScreenPalService", "pasteTextManually failed", e)
            return false
        }
    }

    /**
     * 对节点执行复制操作
     */
    fun performCopy(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_COPY)
    }

    /**
     * 对节点执行剪切操作
     */
    fun performCut(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CUT)
    }

    /**
     * 对节点执行粘贴操作 (公开方法)
     */
    fun performPaste(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    /**
     * 对节点执行 IME Enter 操作 (API 30+)
     */
    fun performImeEnter(node: AccessibilityNodeInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        } else {
            false
        }
    }

    /**
     * 查找当前具有输入焦点的节点
     * 增强版：支持多级降级策略
     * @return 具有焦点的节点，如果未找到返回 null
     */
    fun findFocusedInputNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        try {
            // 策略 1: 系统标准 API
            val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focus != null) {
                Log.d("ScreenPalService", "findFocusedInputNode: focus found via API")
                return focus
            }

            Log.d("ScreenPalService", "findFocusedInputNode: API failed, trying recursive search...")

            // 策略 2: 遍历查找标记为 focused 的输入类节点
            // 很多自定义 View (如 Flutter) 可能不响应 findFocus 但状态是 focused
            val focusedNode = findNodeRecursive(root) {
                it.isFocused && (it.isEditable || it.className?.contains("EditText") == true)
            }
            if (focusedNode != null) {
                Log.d("ScreenPalService", "findFocusedInputNode: Found focus via traversal")
                return focusedNode
            }

            // 策略 3: 宽松模式 - 查找屏幕上唯一的可编辑节点
            // 场景：进入搜索页，键盘弹起，但输入框既没 focus 也没 focused 状态
            val allEditableNodes = findAllNodesRecursive(root) { it.isEditable }
            if (allEditableNodes.size == 1) {
                val target = allEditableNodes[0]
                Log.d("ScreenPalService", "findFocusedInputNode: Found unique editable node")
                return target
            }

            // 清理 list 中多余的节点引用
            allEditableNodes.forEach { recycleNode(it) }

            Log.d("ScreenPalService", "findFocusedInputNode: No input focus found after all strategies")
            return null
        } finally {
            recycleNode(root)
        }
    }

    fun isEditableNodeAt(x: Float, y: Float): Boolean {
        val root = rootInActiveWindow ?: return false
        var found = false
        val targetX = x.toInt()
        val targetY = y.toInt()

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null || found) return
            try {
                val rect = Rect()
                node.getBoundsInScreen(rect)

                // 优化：如果节点不可见或完全不包含目标点，则剪枝
                // 注意：这里假设子节点通常在父节点边界内（标准 Android 行为）
                // 对于极少数 clipChildren=false 的情况，可能会漏判，但在性能和准确性之间取舍，
                // 为了避免遍历整个 DOM 树导致的 10s+ 卡顿，必须进行空间剪枝。
                if (!rect.contains(targetX, targetY)) {
                    return
                }

                if (node.isVisibleToUser) {
                    val className = node.className?.toString() ?: ""
                    if (node.isEditable || className.contains("EditText", ignoreCase = true)) {
                        found = true
                        return
                    }
                }

                for (i in 0 until node.childCount) {
                    traverse(node.getChild(i))
                    if (found) break
                }
            } catch (e: Exception) {
                Log.e("ScreenPalService", "isEditableNodeAt error", e)
            } finally {
                if (node != root) {
                    recycleNode(node)
                }
            }
        }

        try {
            traverse(root)
            return found
        } finally {
            recycleNode(root)
        }
    }

    fun isImeVisible(): Boolean {
        return try {
            val windows = windows ?: return false
            windows.any { window ->
                if (window.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) return@any false
                val rect = Rect()
                window.getBoundsInScreen(rect)
                (window.isFocused || window.isActive) && !rect.isEmpty
            }
        } catch (e: Exception) {
            Log.w("ScreenPalService", "isImeVisible error", e)
            false
        }
    }

    /**
     * 递归查找满足条件的第一个节点
     * 注意：返回的节点需要调用者负责 recycle (如果不是 null)
     */
    private fun findNodeRecursive(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null

        if (predicate(node)) {
            // 找到了，需要保留这个节点，不能在这里 recycle
            // 为了安全，我们可以返回它的副本，或者直接返回它并在外部管理生命周期
            // 这里直接返回它，调用者负责处理
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findNodeRecursive(child, predicate)
            if (result != null) {
                // 找到了结果，result 是需要返回的。
                // child 在递归中已经被处理（要么是 result，要么被 recycle 或者是中间节点）
                // 等等，AccessibilityNodeInfo 的生命周期管理很麻烦。
                // 简单的做法：如果 child 不是 result，就 recycle child。
                // 但 findNodeRecursive 返回了 child，所以不能 recycle。
                // 如果 child 是中间节点且没找到，递归调用里应该 recycle 吗？

                // 修正递归逻辑：
                // node.getChild(i) 返回一个新的实例。
                // 如果递归返回了非空，说明在子树里找到了。
                // 此时 child 本身如果不是 result，应该被 recycle 吗？
                // 否，因为 result 可能是 child 的后代。child 是 result 的祖先链的一部分。
                // 不，AccessibilityNodeInfo 是扁平的句柄。result 是独立的句柄。
                // 所以 child 应该被 recycle，除非 child === result。
                if (child != result) {
                     recycleNode(child)
                }
                return result
            } else {
                recycleNode(child)
            }
        }
        return null
    }

    // 重新实现 findAllNodesRecursive 为非递归或更好管理的递归
    private fun findAllNodesRecursive(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        // 我们不应该 recycle root，因为它是外部传入的
        // 但我们需要遍历它的子节点，子节点需要 recycle

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            queue.add(child)
        }

        while (!queue.isEmpty()) {
            val node = queue.poll() ?: continue

            if (predicate(node)) {
                results.add(node)
                // 找到了，不 recycle node，因为它现在属于 results
                // 继续搜索子节点（虽然输入框通常没有子输入框，但为了完整性）
            }

            // 无论是否匹配，都要搜索子节点
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) queue.add(child)
            }

            // 如果 node 没有被加入 results，现在 recycle
            if (!results.contains(node)) {
                recycleNode(node)
            }
        }

        return results
    }

    /**
     * 回收节点
     */
    fun recycleNode(node: AccessibilityNodeInfo?) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                node?.recycle()
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}

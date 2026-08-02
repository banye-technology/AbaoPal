package com.withcareer.screenpal_android.recording

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.sqrt
import android.util.Log

/**
 * 录制触摸事件监听层
 * 透明的全屏悬浮窗，用于捕获用户的完整手势序列
 *
 * @property recordingManager 录制管理器实例
 */
@SuppressLint("ViewConstructor")
class RecordingOverlayView(
    context: Context,
    private val recordingManager: RecordingManager
) : FrameLayout(context) {

    companion object {
        // 点击和滑动的距离阈值（像素）
        private const val CLICK_THRESHOLD = 20f
        // 长按时间阈值（毫秒）
        private const val LONG_PRESS_THRESHOLD = 500L
        // 多点同时点击的最大时间窗口（毫秒）
        private const val MULTI_TAP_MAX_DURATION = 600L
        // 多点同时点击允许的轻微移动阈值（像素）
        private const val MULTI_TAP_MOVE_THRESHOLD = 60f
        private const val TAG = "RecordingOverlayView"
    }

    // 手势序列缓存：存储完整的手势事件
    private val currentGestureEvents = mutableListOf<MotionEvent>()

    // 悬浮窗控制区域（该区域不拦截触摸）
    var floatingControllerRect: android.graphics.Rect? = null

    // 长按检测相关
    private val handler = Handler(Looper.getMainLooper())
    private var gestureStartTime: Long = 0
    private var isLongPress = false
    private var floatingPassThroughActive = false
    private var gestureStartedInFloating = false

    // 多点触控检测
    private val multiTouchStartPoints = mutableMapOf<Int, Pair<Float, Float>>()
    private val multiTouchLastPoints = mutableMapOf<Int, Pair<Float, Float>>()
    private var multiTouchActive = false
    private var multiTouchMaxDistance = 0f
    private var multiTouchStartTime: Long = 0L
    private var multiTouchLastUpTime: Long = 0L

    /**
     * 重置悬浮窗触摸穿透状态（在回放结束后调用）
     */
    fun resetFloatingTouchState() {
        floatingPassThroughActive = false
        gestureStartedInFloating = false
        Log.d(TAG, "Reset floating touch state")
    }

    private val longPressRunnable = Runnable {
        isLongPress = true
        Log.d(TAG, "Long press detected!")
    }

    init {
        // 设置为透明背景
        setBackgroundColor(0x00000000)
        // 确保不可点击，不消费事件
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
    }

    /**
     * 分发触摸事件
     * 捕获完整手势序列，手势结束后通知 RecordingService 处理
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!recordingManager.isRecording) {
            return false
        }
        val x = event.rawX
        val y = event.rawY

        val service = RecordingService.instance
        if (service?.shouldIgnoreTouch(android.os.SystemClock.uptimeMillis()) == true) {
            // 回放期间或冷却期：清理事件并放行
            handler.removeCallbacks(longPressRunnable)
            currentGestureEvents.clear()
            Log.d(TAG, "✗ Ignored touch during replay/cooldown")
            return false
        }

        // 调试日志：只打印非MOVE事件，避免日志过多导致延迟
        val isReplayingOrCooldown = service?.isReplaying == true || service?.shouldIgnoreTouch(android.os.SystemClock.uptimeMillis()) == true
        if (event.actionMasked != MotionEvent.ACTION_MOVE) {
            Log.d(
                TAG,
                "dispatchTouchEvent: pointerCount=${event.pointerCount}, replayingOrCooldown=$isReplayingOrCooldown"
            )
        }

        // 1. 优先检查触摸点是否在悬浮窗控制区域内
        val inFloatingController = floatingControllerRect?.contains(x.toInt(), y.toInt()) ?: false

        if (!isReplayingOrCooldown && (inFloatingController || gestureStartedInFloating)) {
            // 将触摸事件转发给悬浮窗，传递原始屏幕坐标
            if (event.action == MotionEvent.ACTION_DOWN && inFloatingController) {
                floatingPassThroughActive = true
                gestureStartedInFloating = true
                Log.d(TAG, "✓ Touch in floating controller area (ACTION_DOWN), forwarding event")
            }

            // 直接传递原始事件，不做坐标转换
            service?.dispatchTouchToFloating(event)

            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                floatingPassThroughActive = false
                gestureStartedInFloating = false
                Log.d(TAG, "✓ Touch in floating controller area (ACTION_UP/CANCEL), reset flags")
            }

            Log.d(TAG, "✓ Touch in floating controller area, event forwarded")
            return true
        }

        Log.d(TAG, "✗ Touch outside floating controller, will capture")

        // 2. 捕获手势序列
        // 如果没有 ACTION_DOWN 就收到其他事件，直接忽略（防止回放尾部事件被误录）
        if (currentGestureEvents.isEmpty() && event.action != MotionEvent.ACTION_DOWN) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 手势开始，清空缓存
                currentGestureEvents.clear()
                currentGestureEvents.add(MotionEvent.obtain(event))
                gestureStartTime = event.eventTime
                isLongPress = false
                multiTouchActive = false
                multiTouchMaxDistance = 0f
                multiTouchStartTime = event.eventTime
                multiTouchLastUpTime = event.eventTime
                multiTouchStartPoints.clear()
                multiTouchLastPoints.clear()
                val startPoint = getPointerScreenPoint(event, 0)
                multiTouchStartPoints[event.getPointerId(0)] = startPoint
                multiTouchLastPoints[event.getPointerId(0)] = startPoint

                // 更新底层应用（在手势开始时获取）
                service?.updateUnderlyingApp()

                // 启动长按检测（先清除旧的回调）
                handler.removeCallbacks(longPressRunnable)
                handler.postDelayed(longPressRunnable, LONG_PRESS_THRESHOLD)

                Log.d(TAG, "Gesture started: pointerCount=${event.pointerCount}")
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                currentGestureEvents.add(MotionEvent.obtain(event))
                multiTouchActive = true
                handler.removeCallbacks(longPressRunnable)
                isLongPress = false
                if (multiTouchStartTime == 0L) {
                    multiTouchStartTime = event.eventTime
                }

                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val point = getPointerScreenPoint(event, i)
                    if (!multiTouchStartPoints.containsKey(id)) {
                        multiTouchStartPoints[id] = point
                    }
                    multiTouchLastPoints[id] = point
                }
                Log.d(TAG, "Pointer added: pointerCount=${event.pointerCount}")
            }

            MotionEvent.ACTION_MOVE -> {
                // 记录移动轨迹
                currentGestureEvents.add(MotionEvent.obtain(event))

                // 如果移动距离超过阈值，取消长按检测
                if (currentGestureEvents.isNotEmpty()) {
                    val first = currentGestureEvents.first()
                    val distance = calculateDistance(first.x, first.y, event.x, event.y)
                    if (distance > CLICK_THRESHOLD) {
                        handler.removeCallbacks(longPressRunnable)
                        isLongPress = false
                    }
                }

                if (multiTouchActive) {
                    for (i in 0 until event.pointerCount) {
                        val id = event.getPointerId(i)
                        val current = getPointerScreenPoint(event, i)
                        val start = multiTouchStartPoints[id]
                        if (start != null) {
                            val dist = calculateDistance(start.first, start.second, current.first, current.second)
                            if (dist > multiTouchMaxDistance) {
                                multiTouchMaxDistance = dist
                            }
                        }
                        multiTouchLastPoints[id] = current
                    }
                }

                // if (currentGestureEvents.size % 10 == 0) {
                //    Log.d(TAG, "Gesture MOVE: ${currentGestureEvents.size} events captured")
                // }
            }

            MotionEvent.ACTION_UP -> {
                // 手势结束，取消长按检测
                handler.removeCallbacks(longPressRunnable)
                multiTouchLastPoints[event.getPointerId(0)] = getPointerScreenPoint(event, 0)
                multiTouchLastUpTime = event.eventTime
                currentGestureEvents.add(MotionEvent.obtain(event))
                Log.d(TAG, "Gesture ended: eventCount=${currentGestureEvents.size}")
                onGestureComplete()
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val id = event.getPointerId(pointerIndex)
                multiTouchLastPoints[id] = getPointerScreenPoint(event, pointerIndex)
                multiTouchLastUpTime = event.eventTime
                currentGestureEvents.add(MotionEvent.obtain(event))
                Log.d(TAG, "Pointer removed: pointerCount=${event.pointerCount}")
            }

            MotionEvent.ACTION_CANCEL -> {
                // 手势取消
                handler.removeCallbacks(longPressRunnable)
                currentGestureEvents.add(MotionEvent.obtain(event))
                Log.d(TAG, "Gesture cancelled")
                onGestureComplete()
            }
        }

        // 3. 消费事件，防止原始事件继续传递
        return true
    }

    /**
     * 手势完成回调
     * 判断手势类型，记录到 RecordingManager，并通知 RecordingService 处理
     */
    private fun onGestureComplete() {
        Log.d(TAG, ">>> onGestureComplete() called, events: ${currentGestureEvents.size}")

        // 检查是否正在回放（避免录制回放的手势）
        val service = RecordingService.instance ?: (context as? RecordingService)
        if (service?.isReplaying == true) {
            Log.d(TAG, "✗ Currently replaying, skip recording this gesture")
            currentGestureEvents.clear()
            return
        }

        if (currentGestureEvents.isEmpty()) {
            Log.w(TAG, "Gesture completed but no events recorded")
            return
        }

        val first = currentGestureEvents.first()
        val last = currentGestureEvents.last()
        val distance = calculateDistance(first.x, first.y, last.x, last.y)
        val duration = last.eventTime - gestureStartTime

        // 从RecordingService获取当前底层应用包名（手势开始时记录的）
        val recordingService = context as? RecordingService
        val targetPackage = recordingService?.getCurrentUnderlyingApp()

        val batchEnabled = recordingService?.isBatchMultiTapEnabled == true

        // 多点同时点击
        if (multiTouchActive && multiTouchStartPoints.size >= 2) {
            val multiTapDuration = (if (multiTouchLastUpTime > 0) multiTouchLastUpTime else last.eventTime) -
                (if (multiTouchStartTime > 0) multiTouchStartTime else first.eventTime)
            val isMultiTap = multiTapDuration <= MULTI_TAP_MAX_DURATION &&
                duration < LONG_PRESS_THRESHOLD &&
                multiTouchMaxDistance < MULTI_TAP_MOVE_THRESHOLD
            val pointsSnapshot = multiTouchLastPoints
                .toSortedMap()
                .values
                .map { it.first to it.second }
            Log.d(
                TAG,
                "MultiTap check: pointCount=${pointsSnapshot.size}, duration=${multiTapDuration}ms, matched=$isMultiTap"
            )
            if (isMultiTap) {
                val points = pointsSnapshot.map { it.first.toInt() to it.second.toInt() }
                Log.d(TAG, "Detected MULTI TAP with ${points.size} points, duration=${multiTapDuration}ms")
                if (batchEnabled) {
                    recordingService?.onMultiTapComplete(points)
                } else {
                    recordingManager.recordMultiTap(points, targetPackage)
                    if (recordingService != null) {
                        recordingService.onMultiTapComplete(points)
                    }
                }

                currentGestureEvents.clear()
                multiTouchActive = false
                return
            }
            Log.d(TAG, "MultiTap classification failed")
            currentGestureEvents.clear()
            multiTouchActive = false
            return
        }

        if (batchEnabled && !isLongPress && distance < CLICK_THRESHOLD) {
            val point = Pair(last.rawX.toInt(), last.rawY.toInt())
            recordingService?.onMultiTapComplete(listOf(point))
            currentGestureEvents.clear()
            return
        }

        if (batchEnabled) {
            currentGestureEvents.clear()
            return
        }

        // 判断手势类型
        when {
            isLongPress -> {
                // 长按
                Log.d(TAG, "Detected LONG PRESS, duration=${duration}ms")
                recordingManager.recordLongPress(last.rawX.toInt(), last.rawY.toInt(), duration, targetPackage)
            }
            distance < CLICK_THRESHOLD -> {
                // 点击
                Log.d(TAG, "Detected TAP")
                recordingManager.recordTap(last.rawX.toInt(), last.rawY.toInt(), targetPackage)
            }
            else -> {
                // 滑动 - 提取完整轨迹
                Log.d(TAG, "Detected SWIPE with ${currentGestureEvents.size} points")

                // 采样轨迹点（每隔 N 个点取一个，避免过多）
                val sampleRate = if (currentGestureEvents.size > 50) 3 else 1

                // 获取起始时间
                val startTime = currentGestureEvents.firstOrNull()?.eventTime ?: 0L

                val path = currentGestureEvents
                    .filterIndexed { index, _ -> index % sampleRate == 0 }
                    .map { event ->
                        Triple(
                            event.rawX.toInt(),
                            event.rawY.toInt(),
                            event.eventTime - startTime
                        )
                    }

                // 确保包含最后一个点
                val lastEvent = currentGestureEvents.last()
                val lastPoint = Triple(
                    lastEvent.rawX.toInt(),
                    lastEvent.rawY.toInt(),
                    lastEvent.eventTime - startTime
                )

                val finalPath = if (path.isEmpty() || path.last().third != lastPoint.third) {
                    path + lastPoint
                } else {
                    path
                }

                Log.d(TAG, "Swipe path: ${finalPath.size} points (sampled from ${currentGestureEvents.size})")

                // 检测长按滑动：持续时间 > 500ms
                val swipeDuration = if (duration > 500) duration else 0L
                if (swipeDuration > 0) {
                    Log.d(TAG, "Long press swipe detected, duration=${duration}ms")
                }

                recordingManager.recordSwipeWithPath(finalPath, targetPackage, swipeDuration)
            }
        }

        // 通知 RecordingService 处理手势（移除蒙层 → 回放 → 重新添加蒙层）
        Log.d(TAG, "Attempting to notify RecordingService...")
        Log.d(TAG, "Context is RecordingService: ${context is RecordingService}")

        try {
            if (context is RecordingService) {
                Log.d(TAG, "✓ Context is RecordingService, calling onGestureComplete()")
                val service = context as RecordingService
                service.onGestureComplete(ArrayList(currentGestureEvents))
                Log.d(TAG, "✓ onGestureComplete() called successfully")
            } else {
                Log.e(TAG, "✗ Context is NOT RecordingService! Cannot notify service.")
                Log.e(TAG, "Expected RecordingService context")
            }
        } catch (e: ClassCastException) {
            Log.e(TAG, "✗ ClassCastException when casting context to RecordingService", e)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Exception when notifying RecordingService", e)
        }

        Log.d(TAG, "<<< onGestureComplete() finished")
    }

    private fun getPointerScreenPoint(event: MotionEvent, pointerIndex: Int): Pair<Float, Float> {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return (event.getX(pointerIndex) + location[0]) to (event.getY(pointerIndex) + location[1])
    }

    /**
     * 不拦截触摸事件，让事件传递给子View和底层应用
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return false
    }

    /**
     * 不处理触摸事件，确保事件能传递到底层
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return false
    }

    /**
     * 计算两点之间的距离
     * @return 欧几里得距离
     */
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
}

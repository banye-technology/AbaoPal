package com.withcareer.screenpal_android.core.action.handler

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.hardware.camera2.CameraManager
import android.content.Context
import android.media.AudioManager
import android.provider.CalendarContract
import android.util.Log
import android.view.KeyEvent
import com.google.gson.JsonObject
import com.withcareer.screenpal_android.core.action.ExecuteResult
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService

/**
 * 系统静默指令处理器
 * 处理 SystemIntent 操作，直接调用系统 Intent 而非点击 UI
 *
 * 支持的操作：
 * - set_alarm: 设置闹钟
 * - set_timer: 设置倒计时
 * - insert_event: 添加日历日程
 * - dial: 拨号（跳转拨号盘）
 * - view_url: 打开网页
 * - send_sms: 发送短信
 * - navigate: 地图导航
 * - send_email: 发送邮件
 * - open_market: 打开应用市场
 * - open_settings: 打开系统设置
 * - torch: 手电筒开关
 * - media_control: 媒体控制 (播放/暂停/音量)
 *
 */
class SystemIntentHandler : ActionHandler {
    override fun getActionType(): String = "SystemIntent"

    override suspend fun execute(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        val action = params.get("action")?.asString?.lowercase()
            ?: throw IllegalArgumentException("Missing action type for SystemIntent")

        return try {
            val message = when (action) {
                "set_alarm" -> setAlarm(service, params)
                "set_timer" -> setTimer(service, params)
                "insert_event" -> insertEvent(service, params)
                "dial" -> dial(service, params)
                "view_url", "open_url" -> viewUrl(service, params)
                "send_sms" -> sendSms(service, params)
                "navigate" -> navigate(service, params)
                "send_email" -> sendEmail(service, params)
                "open_market" -> openMarket(service, params)
                "open_settings" -> openSettings(service, params)
                "torch" -> controlTorch(service, params)
                "media_control" -> controlMedia(service, params)
                else -> throw IllegalArgumentException("Unsupported system intent action: $action")
            }

            ExecuteResult(success = true, message = message)
        } catch (e: Exception) {
            Log.e("SystemIntentHandler", "Failed to execute system intent", e)
            ExecuteResult(success = false, message = "操作失败: ${e.message}")
        }
    }

    private fun setAlarm(service: ScreenPalAccessibilityService, params: JsonObject): String {
        val hour = params.get("hour")?.asInt
            ?: throw IllegalArgumentException("Missing 'hour' parameter")
        val minute = params.get("minute")?.asInt
            ?: throw IllegalArgumentException("Missing 'minute' parameter")
        val message = params.get("message")?.asString ?: "闹钟"
        val skipUi = params.get("skip_ui")?.asBoolean ?: true

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        service.startActivity(intent)
        return "已设置闹钟: $hour:$minute ($message)"
    }

    private fun setTimer(service: ScreenPalAccessibilityService, params: JsonObject): String {
        val seconds = params.get("seconds")?.asInt
            ?: throw IllegalArgumentException("Missing 'seconds' parameter")
        val message = params.get("message")?.asString ?: "倒计时"
        val skipUi = params.get("skip_ui")?.asBoolean ?: true

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        service.startActivity(intent)
        return "已设置倒计时: ${seconds}秒 ($message)"
    }

    private fun insertEvent(service: ScreenPalAccessibilityService, params: JsonObject): String {
        val title = params.get("title")?.asString ?: "日程"
        val description = params.get("description")?.asString
        val location = params.get("location")?.asString

        // begin_time 必须是毫秒时间戳
        val beginTime = params.get("begin_time")?.asLong
            ?: System.currentTimeMillis()
        val endTime = params.get("end_time")?.asLong
            ?: (beginTime + 60 * 60 * 1000) // 默认1小时

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        service.startActivity(intent)
        return "已发起创建日程请求: $title"
    }

    private fun dial(service: ScreenPalAccessibilityService, params: JsonObject): String {
        val number = params.get("number")?.asString
            ?: throw IllegalArgumentException("Missing 'number' parameter")

        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        service.startActivity(intent)
        return "已跳转拨号页面: $number"
    }

    private fun viewUrl(service: ScreenPalAccessibilityService, params: JsonObject): String {
        val url = params.get("url")?.asString
            ?: throw IllegalArgumentException("Missing 'url' parameter")

        var finalUrl = url
        if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
            finalUrl = "https://$finalUrl"
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(finalUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        service.startActivity(intent)
        return "已尝试打开链接: $finalUrl"
    }

    private fun sendSms(service: ScreenPalAccessibilityService, params: JsonObject): String {
        val number = params.get("number")?.asString
            ?: throw IllegalArgumentException("Missing 'number' parameter")
        val message = params.get("message")?.asString

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$number")
            if (!message.isNullOrEmpty()) {
                putExtra("sms_body", message)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        service.startActivity(intent)
        return "已跳转短信页面: $number"
    }

    private fun navigate(service: ScreenPalAccessibilityService, params: JsonObject): String {
        val query = params.get("query")?.asString
            ?: throw IllegalArgumentException("Missing 'query' parameter")

        // 使用 geo URI scheme
        // geo:0,0?q=address
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        service.startActivity(intent)
        return "已发起导航请求: $query"
    }

    private fun sendEmail(service: ScreenPalAccessibilityService, params: JsonObject): String {
        val email = params.get("email")?.asString
            ?: throw IllegalArgumentException("Missing 'email' parameter")
        val subject = params.get("subject")?.asString
        val body = params.get("body")?.asString

        val uri = Uri.parse("mailto:$email")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (!subject.isNullOrEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (!body.isNullOrEmpty()) putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        service.startActivity(intent)
        return "已跳转邮件发送页面: $email"
    }

    private fun openMarket(service: ScreenPalAccessibilityService, params: JsonObject): String {
        val packageName = params.get("package_name")?.asString
        val query = params.get("query")?.asString

        val uri = when {
            !packageName.isNullOrBlank() -> Uri.parse("market://details?id=$packageName")
            !query.isNullOrBlank() -> Uri.parse("market://search?q=${Uri.encode(query)}")
            else -> throw IllegalArgumentException("Missing 'package_name' or 'query' parameter")
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        service.startActivity(intent)
        return "已尝试打开应用市场"
    }

    private fun openSettings(service: ScreenPalAccessibilityService, params: JsonObject): String {
        val page = params.get("page")?.asString?.lowercase()

        val action = when (page) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "date" -> Settings.ACTION_DATE_SETTINGS
            "locale", "language" -> Settings.ACTION_LOCALE_SETTINGS
            "input" -> Settings.ACTION_INPUT_METHOD_SETTINGS
            "app_details" -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }

        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (page == "app_details") {
                val pkg = params.get("package_name")?.asString ?: service.packageName
                data = Uri.parse("package:$pkg")
            }
        }

        service.startActivity(intent)
        return "已尝试打开设置页面: $page"
    }

    private fun controlTorch(service: ScreenPalAccessibilityService, params: JsonObject): String {
        val enable = params.get("enable")?.asBoolean ?: true

        try {
            val cameraManager = service.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: throw Exception("设备不支持闪光灯")

            cameraManager.setTorchMode(cameraId, enable)
            return if (enable) "已开启手电筒" else "已关闭手电筒"
        } catch (e: Exception) {
            Log.e("SystemIntentHandler", "Torch control failed", e)
            throw Exception("手电筒控制失败: ${e.message}")
        }
    }

    private fun controlMedia(service: ScreenPalAccessibilityService, params: JsonObject): String {
        val command = params.get("command")?.asString?.lowercase()
            ?: throw IllegalArgumentException("Missing 'command' parameter")

        val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val eventTime = android.os.SystemClock.uptimeMillis()

        fun sendMediaKey(keyCode: Int) {
            val keyEventDown = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            val keyEventUp = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)

            // 方案1: 发送有序广播 (兼容性较好)
            // 注意: Android 8.0+ 后台应用发送广播受限，但在无障碍服务中通常可行
            try {
                val intentDown = Intent(Intent.ACTION_MEDIA_BUTTON)
                intentDown.putExtra(Intent.EXTRA_KEY_EVENT, keyEventDown)
                // 添加 FLAG_RECEIVER_FOREGROUND 尝试优先传递给前台接收者
                intentDown.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                service.sendOrderedBroadcast(intentDown, null)

                val intentUp = Intent(Intent.ACTION_MEDIA_BUTTON)
                intentUp.putExtra(Intent.EXTRA_KEY_EVENT, keyEventUp)
                intentUp.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                service.sendOrderedBroadcast(intentUp, null)
            } catch (e: Exception) {
                Log.w("SystemIntentHandler", "Failed to send media broadcast", e)
            }

            // 方案2: 直接分发按键 (作为备选，需要音频焦点或特定权限)
            try {
                audioManager.dispatchMediaKeyEvent(keyEventDown)
                audioManager.dispatchMediaKeyEvent(keyEventUp)
            } catch (e: Exception) {
                // Ignore, expected on some devices without focus
            }
        }

        return when (command) {
            "play_pause" -> {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                "已执行播放/暂停操作 (注意：结果取决于当前运行的媒体应用)"
            }
            "next" -> {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                "已执行下一曲操作"
            }
            "previous" -> {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                "已执行上一曲操作"
            }
            "volume_up" -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "已调大媒体音量"
            }
            "volume_down" -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "已调小媒体音量"
            }
            "mute" -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                "已静音媒体"
            }
            "unmute" -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                "已取消媒体静音"
            }
            else -> throw IllegalArgumentException("Unsupported media command: $command")
        }
    }
}

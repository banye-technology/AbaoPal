package com.withcareer.screenpal_android.core.safety

import android.util.Log
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService

/**
 * 安全卫士
 * 负责对敏感操作进行拦截
 */
object SafetyGuard {

    private const val TAG = "SafetyGuard"

    // 1. 严格禁止类 (Strict Block): 禁止一切操作 (Back/Home除外)
    private val STRICT_PACKAGES = setOf(
        "com.unionpay",             // 云闪付
        "com.icbc",                 // 工商银行
        "com.ccb.android",          // 建设银行
        "com.cmbchina.ccd.pluto.cmbActivity", // 招商银行
        "com.android.bankabc",      // 农业银行
        "com.boc.bocsoft.mobile"    // 中国银行
    )

    // 2. 敏感监管类 (Sensitive Watch): 检查屏幕关键词，命中则拦截
    private val SENSITIVE_PACKAGES = setOf(
        "com.eg.android.AlipayGphone", // 支付宝 (User requested to move here)
        "com.sankuai.meituan",         // 美团
        "com.taobao.taobao",           // 淘宝
        "com.tencent.mm",              // 微信
        "com.jingdong.app.mall"        // 京东
    )

    // 3. 支付相关关键词
    private val PAYMENT_KEYWORDS = listOf(
        "确认支付", "立即支付", "Confirm Payment", "Pay Now",
        "输入密码", "Enter Password", "支付密码", "Payment Password",
        "指纹验证", "Fingerprint","免密支付",
        "确认交易", "Confirm Transaction"
        // "¥" 符号太常见，可能会误杀商品列表页，暂不加入，依靠“支付/密码”等强特征词
    )

    // 允许的安全操作
    private val SAFE_ACTIONS = setOf(
        "Back", "Home", "Recents", "LockScreen", "Take_over", "Finish", "Wait", "RecordNote"
    )

    private val SAFE_ACTIONS_NORMALIZED = SAFE_ACTIONS.map { normalizeActionName(it) }.toSet()

    sealed class SafetyResult {
        object Allowed : SafetyResult()
        data class Blocked(val reason: String) : SafetyResult()
    }

    fun checkContext(packageName: String?): SafetyResult {
        if (packageName == null) return SafetyResult.Allowed

        if (STRICT_PACKAGES.contains(packageName)) {
            Log.w(TAG, "Blocked strict package")
            return SafetyResult.Blocked("检测到金融类应用 ($packageName)，为保障资金安全，禁止自动操作。")
        }

        if (SENSITIVE_PACKAGES.contains(packageName)) {
            val service = ScreenPalAccessibilityService.getInstance()
            if (service != null) {
                val hasSensitiveContent = service.checkSensitiveContent(PAYMENT_KEYWORDS)
                if (hasSensitiveContent) {
                    Log.w(TAG, "Blocked sensitive content")
                    return SafetyResult.Blocked("检测到支付或密码相关界面，已启动安全熔断。")
                }
            }
        }

        return SafetyResult.Allowed
    }

    /**
     * 检查操作是否安全
     * @param packageName 当前应用包名
     * @param actionType 动作类型 (如 Tap, Type)
     * @return SafetyResult
     */
    fun checkAction(packageName: String?, actionType: String): SafetyResult {
        if (packageName == null) return SafetyResult.Allowed

        // 如果是安全操作（如返回），直接放行
        if (SAFE_ACTIONS_NORMALIZED.contains(normalizeActionName(actionType))) {
            return SafetyResult.Allowed
        }

        // 1. 检查严格禁止名单
        if (STRICT_PACKAGES.contains(packageName)) {
            Log.w(TAG, "Blocked strict package")
            return SafetyResult.Blocked("检测到金融类应用 ($packageName)，为保障资金安全，禁止自动操作。")
        }

        // 2. 检查敏感监管名单
        if (SENSITIVE_PACKAGES.contains(packageName)) {
            val service = ScreenPalAccessibilityService.getInstance()
            if (service != null) {
                val hasSensitiveContent = service.checkSensitiveContent(PAYMENT_KEYWORDS)
                if (hasSensitiveContent) {
                    Log.w(TAG, "Blocked sensitive content")
                    return SafetyResult.Blocked("检测到支付或密码相关界面，已启动安全熔断。")
                }
            } else {
                // 如果无法获取服务进行检测，为安全起见，选择放行还是阻断？
                // 考虑到用户体验，如果没有服务，可能是因为权限没开，Agent 本身也跑不了。
                // 这里选择放行，因为没有服务也就无法 checkSensitiveContent。
                // 但如果 service 为空，Agent 也没法 Tap，所以这里逻辑其实无所谓。
            }
        }

        return SafetyResult.Allowed
    }

    private fun normalizeActionName(actionType: String): String {
        return actionType.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
    }
}

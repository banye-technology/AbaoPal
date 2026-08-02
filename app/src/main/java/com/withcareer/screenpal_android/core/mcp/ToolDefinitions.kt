package com.withcareer.screenpal_android.core.mcp

/**
 * MCP Tool 定义
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val usageFormat: String
)

/**
 * 内置工具库
 */
object ToolRegistry {

    private val tools = mapOf(
        "Tap" to ToolDefinition(
            name = "Tap",
            description = "点击屏幕上的特定坐标点。必须使用 [x, y] 坐标，严禁使用元素名称、描述或ID。",
            usageFormat = """{"action_type": "do", "action": "Tap", "element": [x, y]}"""
        ),
        "Scroll" to ToolDefinition(
            name = "Scroll",
            description = "滑动屏幕以查看更多内容。direction 可选: up/down/left/right。",
            usageFormat = """{"action_type": "do", "action": "Scroll", "direction": "down", "element": <optional_id>}"""
        ),
        "Type" to ToolDefinition(
            name = "Type",
            description = "在当前焦点的输入框中输入文本。必须确保键盘已弹起。",
            usageFormat = """{"action_type": "do", "action": "Type", "text": "内容"}"""
        ),
        "Back" to ToolDefinition(
            name = "Back",
            description = "返回上一页或关闭键盘/弹窗。",
            usageFormat = """{"action_type": "do", "action": "Back"}"""
        ),
        "Home" to ToolDefinition(
            name = "Home",
            description = "返回桌面。",
            usageFormat = """{"action_type": "do", "action": "Home"}"""
        ),
        "Launch" to ToolDefinition(
            name = "Launch",
            description = "精准启动应用。仅当你明确知道应用名称时使用。",
            usageFormat = """{"action_type": "do", "action": "Launch", "app": "应用名"}"""
        ),
        "WebSearch" to ToolDefinition(
            name = "WebSearch",
            description = "联网搜索。当需要获取实时信息、新闻、股价或回答通用知识问题时使用。",
            usageFormat = """{"action_type": "do", "action": "WebSearch", "query": "关键词"}"""
        ),
        "Wait" to ToolDefinition(
            name = "Wait",
            description = "等待一段时间，用于页面加载。",
            usageFormat = """{"action_type": "do", "action": "Wait", "duration": "2 seconds"}"""
        ),
        "RunInstructionSet" to ToolDefinition(
            name = "RunInstructionSet",
            description = "执行本地按键集（指令集）。请提供 instruction_id 或 instruction_title。可选 repeat 表示重复次数。",
            usageFormat = """{"action_type": "do", "action": "RunInstructionSet", "instruction_title": "按键集名称", "repeat": 3}"""
        ),
        "RecordNote" to ToolDefinition(
            name = "RecordNote",
            description = "记录笔记或更新当前任务的备注信息。用于保存执行过程中的关键信息、用户偏好或需要稍后处理的内容。",
            usageFormat = """{"action_type": "do", "action": "RecordNote", "note": "笔记内容"}"""
        ),
        "Replan" to ToolDefinition(
            name = "Replan",
            description = "请求重新规划。当发现当前计划无法执行、环境发生重大变化或步骤失效时使用。",
            usageFormat = """{"action_type": "do", "action": "Replan", "reason": "重新规划的原因"}"""
        ),
        "Swipe" to ToolDefinition(
            name = "Swipe",
            description = "自定义坐标滑动。适用于非标准列表的滑动操作。坐标必须使用 0-1 相对值（0.5 表示屏幕中心）。",
            usageFormat = """{"action_type": "do", "action": "Swipe", "start": [0.5, 0.8], "end": [0.5, 0.2]}"""
        ),
        "TapAndSwipe" to ToolDefinition(
            name = "TapAndSwipe",
            description = "先点击起始点，然后滑动到结束点。适用于拖拽、游戏操作。坐标必须使用 0-1 相对值。",
            usageFormat = """{"action_type": "do", "action": "TapAndSwipe", "start": [0.5, 0.6], "end": [0.8, 0.3]}"""
        ),
        "LongPress" to ToolDefinition(
            name = "LongPress",
            description = "长按屏幕上的特定坐标点。当用户明确要求'长按'或需要触发长按菜单时使用。",
            usageFormat = """{"action_type": "do", "action": "LongPress", "element": [x, y]}"""
        ),
        "DoubleTap" to ToolDefinition(
            name = "DoubleTap",
            description = "双击屏幕元素。",
            usageFormat = """{"action_type": "do", "action": "DoubleTap", "element": [x, y]}"""
        ),
        "ZoomIn" to ToolDefinition(
            name = "ZoomIn",
            description = "放大屏幕内容（双指张开）。",
            usageFormat = """{"action_type": "do", "action": "ZoomIn", "element": [x, y]}"""
        ),
        "ZoomOut" to ToolDefinition(
            name = "ZoomOut",
            description = "缩小屏幕内容（双指捏合）。",
            usageFormat = """{"action_type": "do", "action": "ZoomOut", "element": [x, y]}"""
        ),
        "Recents" to ToolDefinition(
            name = "Recents",
            description = "打开最近任务列表。",
            usageFormat = """{"action_type": "do", "action": "Recents"}"""
        ),
        "Notifications" to ToolDefinition(
            name = "Notifications",
            description = "打开通知栏。",
            usageFormat = """{"action_type": "do", "action": "Notifications"}"""
        ),
        "QuickSettings" to ToolDefinition(
            name = "QuickSettings",
            description = "打开快捷设置面板。",
            usageFormat = """{"action_type": "do", "action": "QuickSettings"}"""
        ),
        "LockScreen" to ToolDefinition(
            name = "LockScreen",
            description = "锁定屏幕。",
            usageFormat = """{"action_type": "do", "action": "LockScreen"}"""
        ),
        "Power" to ToolDefinition(
            name = "Power",
            description = "打开电源菜单。",
            usageFormat = """{"action_type": "do", "action": "Power"}"""
        ),
        "SplitScreen" to ToolDefinition(
            name = "SplitScreen",
            description = "触发分屏模式。",
            usageFormat = """{"action_type": "do", "action": "SplitScreen"}"""
        ),
        "Screenshot" to ToolDefinition(
            name = "Screenshot",
            description = "触发系统截图，便于用户分享或编辑。",
            usageFormat = """{"action_type": "do", "action": "Screenshot"}"""
        ),
        "GetCurrentScreenshot" to ToolDefinition(
            name = "GetCurrentScreenshot",
            description = "获取当前步骤的屏幕截图（作为信息补充）。仅当 UI 树信息不足时使用。",
            usageFormat = """{"tool":"get_current_screenshot","parameters":{}}"""
        ),
        "Interact" to ToolDefinition(
            name = "Interact",
            description = "触发交互模式，询问用户如何选择（当遇到多义性时）。",
            usageFormat = """{"action_type": "do", "action": "Interact"}"""
        ),
        "Take_over" to ToolDefinition(
            name = "Take_over",
            description = "请求用户接管操作（用于登录、验证码等敏感场景）。",
            usageFormat = """{"action_type": "do", "action": "Take_over", "message": "原因"}"""
        ),
        "Finish" to ToolDefinition(
            name = "Finish",
            description = "任务结束或无法完成。注意 action_type 为 finish。",
            usageFormat = """{"action_type": "finish", "message": "结果说明"}"""
        ),
        "Enter" to ToolDefinition(
            name = "Enter",
            description = "按下回车键 (IME Action Enter)。",
            usageFormat = """{"action_type": "do", "action": "Enter"}"""
        ),
        "Copy" to ToolDefinition(
            name = "Copy",
            description = "复制当前选中的文本。",
            usageFormat = """{"action_type": "do", "action": "Copy"}"""
        ),
        "Cut" to ToolDefinition(
            name = "Cut",
            description = "剪切当前选中的文本。",
            usageFormat = """{"action_type": "do", "action": "Cut"}"""
        ),
        "Paste" to ToolDefinition(
            name = "Paste",
            description = "粘贴文本。",
            usageFormat = """{"action_type": "do", "action": "Paste"}"""
        )
    )

    /**
     * 获取指定工具的定义
     */
    fun getTools(names: List<String>): List<ToolDefinition> {
        val result = mutableListOf<ToolDefinition>()
        // 如果列表包含 "*"，则返回所有工具
        if (names.contains("*")) {
            return tools.values.toList()
        }

        names.forEach { name ->
            tools[name]?.let { result.add(it) }
        }
        return result
    }

    /**
     * 生成工具描述 Prompt
     */
    fun generateToolsPrompt(tools: List<ToolDefinition>): String {
        if (tools.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("\n**可用工具 (Tools):**\n")
        sb.append("你可以使用以下工具来执行操作。请严格按照 JSON 格式输出。\n\n")

        tools.forEach { tool ->
            sb.append("- **${tool.name}**: ${tool.description}\n")
            sb.append("  格式: `${tool.usageFormat}`\n")
        }
        return sb.toString()
    }
}

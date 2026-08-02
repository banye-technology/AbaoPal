package com.withcareer.screenpal_android.core.agent.framework

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Agent 消息总线
 * 负责分发消息给所有订阅者
 */
class AgentBus {

    private val _events = MutableSharedFlow<AgentMessage>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentMessage> = _events.asSharedFlow()

    suspend fun emit(message: AgentMessage) {
        _events.emit(message)
    }

    fun tryEmit(message: AgentMessage): Boolean {
        return _events.tryEmit(message)
    }
}

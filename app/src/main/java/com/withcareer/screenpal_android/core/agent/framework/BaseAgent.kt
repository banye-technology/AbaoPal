package com.withcareer.screenpal_android.core.agent.framework

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 基础 Agent 类
 */
abstract class BaseAgent(
    protected val bus: AgentBus,
    protected val scope: CoroutineScope
) {
    private var job: Job? = null

    open fun start() {
        job = scope.launch(Dispatchers.Default) {
            bus.events.collect { message ->
                try {
                    handleMessage(message)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(this@BaseAgent.javaClass.simpleName, "Message handling failed: type=${message::class.simpleName}")
                }
            }
        }
    }

    open fun stop() {
        job?.cancel()
        job = null
    }

    protected abstract suspend fun handleMessage(message: AgentMessage)
}

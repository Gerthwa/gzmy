package com.gzmy.app.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-wide event bus backed by SharedFlow.
 * Replaces LocalBroadcastManager for in-process events.
 */
object AppEventBus {

    data class NewMessageEvent(
        val title: String,
        val body: String,
        val type: String
    )

    private val _newMessageEvents = MutableSharedFlow<NewMessageEvent>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val newMessageEvents = _newMessageEvents.asSharedFlow()

    fun emitNewMessage(event: NewMessageEvent) {
        _newMessageEvents.tryEmit(event)
    }
}

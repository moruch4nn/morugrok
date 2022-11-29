package dev.mr3n.model.ws

import kotlinx.serialization.Serializable

@Serializable
data class WebSocketAuth(val user: String,val token: String)
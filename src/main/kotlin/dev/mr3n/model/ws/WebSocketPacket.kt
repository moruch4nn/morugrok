package dev.mr3n.model.ws

import kotlinx.serialization.Serializable

@Serializable
class WebSocketPacket<T>(val type: PacketType, val data: T?)
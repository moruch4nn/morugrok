package dev.mr3n.model.ws

import dev.mr3n.model.Protocol
import kotlinx.serialization.Serializable

@Serializable
data class CreateTunnelRequest(val port: Int, val protocol: Protocol, val address: String, val iat: Long)
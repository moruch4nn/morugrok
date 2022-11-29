package dev.mr3n.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class ConnectionRequest(val port: Int, val protocol: Protocol, val filter: Filter = Filter(Filter.Type.BLACKLIST, listOf()))
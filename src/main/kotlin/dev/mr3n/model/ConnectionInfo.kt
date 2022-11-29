package dev.mr3n.model

import kotlinx.serialization.Serializable


@Serializable
class ConnectionInfo(val user: String, val port: Int, val protocol: Protocol, val filter: Filter, val token: String)
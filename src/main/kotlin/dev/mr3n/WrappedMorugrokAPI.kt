package dev.mr3n

import dev.mr3n.model.ConnectionInfo
import dev.mr3n.model.Filter
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class WrappedMorugrokAPI(connectionInfo: ConnectionInfo, private val client: HttpClient, private val host: String, private val morugrokToken: String) {
    val user = connectionInfo.user
    val protocol = connectionInfo.protocol
    val token = connectionInfo.token
    // このコネクションの名前です。
    var name: String = connectionInfo.name
        private set

    // このコネクションの公開ポートです。
    var port: Int = connectionInfo.port
        private set

    var filter: Filter = connectionInfo.filter
        private set

    fun setName(name: String): WrappedMorugrokAPI {
        this.name = name
        return this
    }

    fun setPort(port: Int): WrappedMorugrokAPI {
        this.port = port
        return this
    }

    fun setFilter(filter: Filter): WrappedMorugrokAPI {
        this.filter = filter
        return this
    }

    suspend fun sync() {
        client.patch("http://${host}:8080/con/${token}") {
            header(HttpHeaders.Authorization, "Bearer $morugrokToken")
            contentType(ContentType.Application.Json)
            setBody(ConnectionInfo(name, user, port, protocol, filter, token))
        }
    }
}
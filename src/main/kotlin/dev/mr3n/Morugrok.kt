package dev.mr3n

import dev.mr3n.model.ConnectionInfo
import dev.mr3n.model.ConnectionRequest
import dev.mr3n.model.Protocol
import dev.mr3n.model.ws.CreateTunnelRequest
import dev.mr3n.model.ws.PacketType
import dev.mr3n.model.ws.WebSocketAuth
import dev.mr3n.model.ws.WebSocketPacket
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.SocketException
import java.time.Duration
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.Logger

object Morugrok {
    private const val HOST = "rp.mr3n.dev"
    suspend fun start(hostName: String, port: Int, publicPort: Int, name: String?, token: String, logger: Logger = Logger.getLogger("MORUGROK,${name?:"${hostName}:${port}"}")) {
        val client = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = Duration.ofSeconds(120).toMillis()
            }
            install(ContentNegotiation) {
                json()
            }
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(DefaultJson)
            }
        }
        val response = client.post("http://${HOST}:8080/con") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(ConnectionRequest(name, publicPort, Protocol.TCP))
        }
        check(response.status.isSuccess()) { response.bodyAsText() }
        val conData: ConnectionInfo = response.body()
        logger.config("コネクションの新規トークン: ${conData.token}")
        client.webSocket(host = HOST, port = 8080) {
            sendSerialized(WebSocketAuth(conData.user, conData.token))
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> onWebSocketMessage(frame, hostName, port, logger)
                    else -> {}
                }
            }
        }
    }

    private suspend fun WebSocketSession.onWebSocketMessage(frame: Frame.Text, hostName: String, port: Int, logger: Logger) {
        val parsedJsonElement = DefaultJson.parseToJsonElement(frame.readText())
        val type = parsedJsonElement.jsonObject["type"]?.jsonPrimitive?.content ?: return
        when (PacketType.valueOf(type)) {
            PacketType.CREATE_TUNNEL -> {
                val data = parsedJsonElement.jsonObject["data"]?.jsonObject.toString()
                val createTunnelRequest = DefaultJson.decodeFromString<CreateTunnelRequest>(data)
                val selectorManager = SelectorManager(Dispatchers.IO)
                val serverSocket = aSocket(selectorManager).tcp().connect(HOST, createTunnelRequest.port)
                val localSocket = aSocket(selectorManager).tcp().connect(hostName, port)
                val serverConnection = serverSocket.connection()
                val localConnection = localSocket.connection()
                ConnectionSocket(localConnection, serverConnection)
                ConnectionSocket(serverConnection, localConnection)
                logger.finer("${createTunnelRequest.address}からの新規コネクションを確立しました。")
            }
            PacketType.PING -> {
                logger.finest("receive PING! from morugrok server.")
                val json = DefaultJson.encodeToString(WebSocketPacket(PacketType.PONG, null))
                send(json)
                logger.finest("send PONG! to morugrok server.")
            }
            PacketType.PONG -> {}
        }
    }

    class ConnectionSocket(private val receive: Connection, private val send: Connection) : Thread() {
        private var closed = false

        private fun close() {
            try {
                receive.socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                send.socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            closed = true
        }

        override fun run() {
            try {
                runBlocking {
                    val inputStream = receive.input
                    val outputStream = send.output
                    val buffer = ByteArray(30000) // TODO magic number ,2022/12/20追記: なにこの値??
                    while (true) {
                        val bytesRead: Int = inputStream.readAvailable(buffer)
                        if (bytesRead == -1) throw SocketException() // end
                        outputStream.writeFully(buffer, 0, bytesRead)
                        outputStream.flush()
                    }
                }
            } catch (e: Exception) {
                this.close()
            }
        }

        init {
            this.start()
        }
    }
}
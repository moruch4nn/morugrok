package dev.mr3n

import dev.mr3n.model.ConnectionInfo
import dev.mr3n.model.ConnectionRequest
import dev.mr3n.model.Protocol
import dev.mr3n.model.ws.CreateTunnelRequest
import dev.mr3n.model.ws.PacketType
import dev.mr3n.model.ws.WebSocketAuth
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.SocketException
import java.security.Security

val host = "rp.mr3n.dev"

var hostName = ""
var port = 0
var publicPort = 0

suspend fun main(args: Array<String>) {
    hostName = args.getOrNull(1)?:System.getenv("RP_HOSTNAME")
    port = args.getOrNull(2)?.toIntOrNull()?:System.getenv("RP_PORT").toInt()
    publicPort = args.getOrNull(3)?.toIntOrNull()?:System.getenv("RP_PUBLIC_PORT").toInt()

    System.setProperty("io.ktor.random.secure.random.provider", "DRBG")
    Security.setProperty("securerandom.drbg.config", "HMAC_DRBG,SHA-512,256,pr_and_reseed")
    val token = System.getenv("RP_TOKEN")
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(WebSockets) {
            pingInterval = 20_000
            contentConverter = KotlinxWebsocketSerializationConverter(DefaultJson)
        }
    }
    val response = client.post("http://${host}:8080/con") {
        header(HttpHeaders.Authorization,"Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(ConnectionRequest(publicPort,Protocol.TCP))
    }
    check(response.status.isSuccess()) { response.bodyAsText() }
    val conData: ConnectionInfo = response.body()
    println(conData.token)
    client.webSocket(host = host, port = 8080) {
        sendSerialized(WebSocketAuth(conData.user,conData.token))
        for(frame in incoming) {
            when(frame) {
                is Frame.Text -> onMessage(frame)
                else -> {}
            }
        }
    }
}

suspend fun onMessage(frame: Frame.Text) {
    val type = DefaultJson.parseToJsonElement(frame.readText()).jsonObject["type"]?.jsonPrimitive?.content?:return
    when(PacketType.valueOf(type)) {
        PacketType.CREATE_TUNNEL -> {
            val data = DefaultJson.parseToJsonElement(frame.readText()).jsonObject["data"]?.jsonObject.toString()
            val createTunnelRequest = DefaultJson.decodeFromString<CreateTunnelRequest>(data)
            val selectorManager = SelectorManager(Dispatchers.IO)
            val serverSocket = aSocket(selectorManager).tcp().connect(host,createTunnelRequest.port)
            val localSocket = aSocket(selectorManager).tcp().connect(hostName, port)
            val serverConnection = serverSocket.connection()
            val localConnection = localSocket.connection()
            ConnectionSocket(serverConnection, localConnection)
            ConnectionSocket(localConnection, serverConnection)
        }
    }
}

class ConnectionSocket(val receive: Connection, val send: Connection): Thread() {
    var closed = false
        private set

    fun close() {
        try { receive.socket.close() } catch(e: Exception) {e.printStackTrace()}
        try { send.socket.close() } catch(e: Exception) {e.printStackTrace()}
        closed = true
    }

    override fun run() {
        try {
            runBlocking {
                val inputStream = receive.input
                val outputStream = send.output
                val buffer = ByteArray(30000) // TODO magic number
                while(true) {
                    val bytesRead: Int = inputStream.readAvailable(buffer)
                    if (bytesRead == -1) throw SocketException() // end
                    outputStream.writeFully(buffer,0,bytesRead)
                    outputStream.flush()
                }
            }
        } catch(e: Exception) {
            this.close()
        }
    }
    init { this.start() }
}
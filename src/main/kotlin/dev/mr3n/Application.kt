package dev.mr3n

import kotlinx.coroutines.runBlocking
import java.security.Security

fun main(args: Array<String>) {
    val hostName = args.getOrNull(1)?:System.getenv("MORUGROK_HOSTNAME")
    val port = args.getOrNull(2)?.toIntOrNull()?:System.getenv("MORUGROK_PORT").toInt()
    val publicPort = args.getOrNull(3)?.toIntOrNull()?:System.getenv("MORUGROK_PUBLIC_PORT").toInt()
    val name = args.getOrNull(4)?:System.getenv("MORUGROK_NAME")

    System.setProperty("io.ktor.random.secure.random.provider", "DRBG")
    Security.setProperty("securerandom.drbg.config", "HMAC_DRBG,SHA-512,256,pr_and_reseed")
    val token = System.getenv("RP_TOKEN")
    runBlocking {
        Morugrok.start(hostName, port, publicPort, name, token)
    }
}
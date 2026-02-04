package se.onemanstudio

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import se.onemanstudio.core.configureHTTP
import se.onemanstudio.core.configureSecurity
import se.onemanstudio.db.DatabaseFactory
import se.onemanstudio.services.GeoLocationService

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init()
    GeoLocationService.init("src/main/resources/geo/geolite2-city.mmdb")

    install(ContentNegotiation) {
        json()
    }

    configureHTTP()
    configureSecurity()
    configureRouting()
}

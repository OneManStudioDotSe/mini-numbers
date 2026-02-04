package se.onemanstudio.core

import io.ktor.server.application.*
import io.ktor.server.auth.*

fun Application.configureSecurity() {
    install(Authentication) {
        basic("admin-auth") {
            realm = "Access to the admin panel"
            validate { credentials ->
                // @TODO: In production, use environment variables for these!
                if (credentials.name == "admin" && credentials.password == "your-password") {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }
}

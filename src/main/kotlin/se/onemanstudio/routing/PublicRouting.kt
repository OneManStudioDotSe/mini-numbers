package se.onemanstudio.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import se.onemanstudio.config.models.AppConfig
import se.onemanstudio.core.ServiceManager
import se.onemanstudio.services.GeoLocationService

fun Route.publicRoutes(config: AppConfig) {
    // Prevent favicon 404
    get("/favicon.ico") {
        call.respond(HttpStatusCode.NoContent)
    }

    // Prometheus-style metrics endpoint
    get("/metrics") {
        val geoStats = GeoLocationService.cacheStats()
        val uptimeSeconds = ServiceManager.getUptimeSeconds()
        
        val metrics = StringBuilder()
        metrics.append("# HELP mini_numbers_uptime_seconds Uptime of the application in seconds\n")
        metrics.append("# TYPE mini_numbers_uptime_seconds counter\n")
        metrics.append("mini_numbers_uptime_seconds $uptimeSeconds\n\n")
        
        metrics.append("# HELP mini_numbers_geoip_cache_hit_rate GeoIP cache hit rate\n")
        metrics.append("# TYPE mini_numbers_geoip_cache_hit_rate gauge\n")
        metrics.append("mini_numbers_geoip_cache_hit_rate ${geoStats["hitRate"]}\n\n")
        
        metrics.append("# HELP mini_numbers_geoip_cache_size Current GeoIP cache size\n")
        metrics.append("# TYPE mini_numbers_geoip_cache_size gauge\n")
        metrics.append("mini_numbers_geoip_cache_size ${geoStats["size"]}\n")
        
        call.respondText(metrics.toString(), ContentType.Text.Plain)
    }

    // ── Tracker Configuration Endpoint ─────────────────────────────
    get("/tracker/config") {
        call.respond(buildJsonObject {
            put("heartbeatInterval", config.tracker.heartbeatIntervalSeconds)
            put("spaEnabled", config.tracker.spaTrackingEnabled)
        })
    }

    // Static resources for setup wizard and tracker
    staticResources("/setup", "setup", index = "wizard.html")
    staticResources("/tracker", "tracker")
    staticResources("/assets", "assets")
    staticResources("/css", "static/css")
    staticResources("/js", "static/js")
    
    // Serve minified tracker script directly
    get("/tracker/tracker.min.js") {
        // Must be served as JavaScript: every response carries X-Content-Type-Options: nosniff,
        // and browsers refuse to execute a cross-origin script with a non-script MIME type.
        val resource = call.application.environment.classLoader.getResource("tracker/tracker.min.js")
        if (resource != null) {
            call.respondBytes(resource.readBytes(), ContentType.Application.JavaScript)
        } else {
            // Fallback to unminified if minified doesn't exist (e.g. dev)
            val devResource = call.application.environment.classLoader.getResource("tracker/tracker.js")
            if (devResource != null) {
                call.respondBytes(devResource.readBytes(), ContentType.Application.JavaScript)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}

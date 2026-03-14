package se.onemanstudio.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.api.models.ApiError
import se.onemanstudio.api.models.dashboard.ProjectStats
import se.onemanstudio.api.models.dashboard.TopPage
import se.onemanstudio.api.models.dashboard.VisitSnippet
import se.onemanstudio.core.models.UserRole
import se.onemanstudio.core.models.UserSession
import se.onemanstudio.middleware.getUserRole
import se.onemanstudio.db.*
import se.onemanstudio.middleware.QueryCache
import se.onemanstudio.middleware.WidgetCache
import se.onemanstudio.middleware.requireRole
import java.time.LocalDateTime
import java.util.UUID

import se.onemanstudio.api.models.admin.DemoDataRequest
import se.onemanstudio.api.models.admin.DemoDataResponse

fun Route.adminProjectRoutes() {
    // List all projects (with pagination)
    get("/projects") {
        val userRole = call.getUserRole()
        val page = call.request.queryParameters["page"]?.toIntOrNull()
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()

        val allProjects = transaction {
            Projects.selectAll().map {
                mutableMapOf(
                    "id" to it[Projects.id].toString(),
                    "name" to it[Projects.name],
                    "domain" to it[Projects.domain]
                ).apply {
                    // SECURITY FIX: Hide API key from viewers
                    if (userRole == UserRole.ADMIN) {
                        this["apiKey"] = it[Projects.apiKey]
                    } else {
                        this["apiKey"] = "********"
                    }
                }
            }
        }

        // If pagination params provided, return paginated response
        if (page != null && limit != null) {
            val total = allProjects.size.toLong()
            val start = (page * limit).coerceAtMost(allProjects.size)
            val end = ((page + 1) * limit).coerceAtMost(allProjects.size)
            val paged = allProjects.subList(start, end)
            call.respond(mapOf(
                "data" to paged,
                "total" to total,
                "page" to page,
                "limit" to limit,
                "totalPages" to ((total + limit - 1) / limit)
            ))
        } else {
            // Backward compatible: return flat array
            call.respond(allProjects)
        }
    }

    // Create a new project
    post("/projects") {
        if (!call.requireRole(UserRole.ADMIN)) return@post
        val params = call.receive<Map<String, String>>()
        val newName = params["name"] ?: return@post call.respond(HttpStatusCode.BadRequest,
            ApiError.badRequest("Project name is required"))
        val newDomain = params["domain"] ?: return@post call.respond(HttpStatusCode.BadRequest,
            ApiError.badRequest("Project domain is required"))

        // Always generate API key server-side for security
        val resolvedApiKey = UUID.randomUUID().toString().replace("-", "")

        // Normalize domain: strip protocol, www prefix, trailing slashes
        val normalizedDomain = newDomain
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')

        transaction {
            Projects.insert {
                it[id] = UUID.randomUUID()
                it[name] = newName
                it[domain] = normalizedDomain
                it[apiKey] = resolvedApiKey
            }
        }
        call.respond(HttpStatusCode.Created)
    }

    // Update a project
    post("/projects/{id}") {
        if (!call.requireRole(UserRole.ADMIN)) return@post
        val uuid = safeParseUUID(call.parameters["id"])
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val updates = call.receive<Map<String, String>>()
        val newName = updates["name"]?.trim()

        if (newName.isNullOrBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Project name is required"))
        }

        transaction {
            Projects.update({ Projects.id eq uuid }) {
                it[name] = newName
            }
        }
        call.respond(HttpStatusCode.OK, buildJsonObject { put("success", true) })
    }

    // Delete a project
    delete("/projects/{id}") {
        if (!call.requireRole(UserRole.ADMIN)) return@delete
        val uuid = safeParseUUID(call.parameters["id"])
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        try {
            transaction {
                val funnelIds = Funnels.selectAll().where { Funnels.projectId eq uuid }
                    .map { it[Funnels.id] }
                if (funnelIds.isNotEmpty()) {
                    FunnelSteps.deleteWhere { FunnelSteps.funnelId inList funnelIds }
                }
                Funnels.deleteWhere { Funnels.projectId eq uuid }
                ConversionGoals.deleteWhere { ConversionGoals.projectId eq uuid }
                Segments.deleteWhere { Segments.projectId eq uuid }
                Events.deleteWhere { Events.projectId eq uuid }
                Projects.deleteWhere { Projects.id eq uuid }
            }
            QueryCache.invalidateProject(uuid.toString())
            WidgetCache.invalidateProject(uuid.toString())
            call.respond(HttpStatusCode.NoContent)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            call.application.environment.log.error("Failed to delete project $uuid: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError,
                ApiError.internalError("Failed to delete project"))
        }
    }

    // Rotate API key for a project
    post("/projects/{id}/rotate-api-key") {
        if (!call.requireRole(UserRole.ADMIN)) return@post
        val uuid = safeParseUUID(call.parameters["id"])
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val newKey = java.security.SecureRandom().let { rng ->
            val bytes = ByteArray(32)
            rng.nextBytes(bytes)
            bytes.joinToString("") { "%02x".format(it) }
        }

        val updated = transaction {
            Projects.update({ Projects.id eq uuid }) {
                it[apiKey] = newKey
            } > 0
        }

        if (!updated) {
            return@post call.respond(HttpStatusCode.NotFound, ApiError.notFound("Project not found"))
        }

        QueryCache.invalidateProject(uuid.toString())
        WidgetCache.invalidateProject(uuid.toString())
        call.respond(buildJsonObject { put("apiKey", newKey) })
    }

    // Get stats for a specific project
    get("/projects/{id}/stats") {
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val stats = QueryCache.getOrCompute("$pid:stats") {
            transaction {
                val totalViews = Events.selectAll().where { Events.projectId eq pid }.count()
                val uniqueVisitors = Events.select(Events.visitorHash)
                    .where { Events.projectId eq pid }
                    .withDistinct()
                    .count()
                val topPages = Events.select(Events.path, Events.path.count())
                    .where { Events.projectId eq pid }
                    .groupBy(Events.path)
                    .orderBy(Events.path.count(), SortOrder.DESC)
                    .limit(5)
                    .map { TopPage(path = it[Events.path], count = it[Events.path.count()]) }

                ProjectStats(totalViews = totalViews, uniqueVisitors = uniqueVisitors, topPages = topPages)
            }
        }
        call.respond(stats)
    }

    // Get live activity for a specific project
    get("/projects/{id}/live") {
        val id = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val fiveMinutesAgo = LocalDateTime.now().minusMinutes(5)

        val liveData = transaction {
            Events.selectAll()
                .where { (Events.projectId eq id) and (Events.timestamp greaterEq fiveMinutesAgo) }
                .orderBy(Events.timestamp, SortOrder.DESC)
                .limit(20)
                .map { VisitSnippet(it[Events.path], it[Events.timestamp].toString(), it[Events.city], it[Events.country]) }
        }
        call.respond(liveData)
    }

    // Real-time visitor count (distinct visitors in last 5 minutes)
    get("/projects/{id}/realtime-count") {
        val id = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val fiveMinutesAgo = LocalDateTime.now().minusMinutes(5)

        val count = transaction {
            Events.select(Events.visitorHash)
                .where {
                    (Events.projectId eq id) and
                            (Events.timestamp greaterEq fiveMinutesAgo)
                }
                .withDistinct()
                .count()
        }
        call.respond(buildJsonObject {
            put("activeVisitors", count)
            put("timestamp", LocalDateTime.now().toString())
        })
    }

    // Globe visualization data
    get("/projects/{id}/globe") {
        val id = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val range = call.request.queryParameters["range"] ?: "realtime"
        val cutoff = when (range) {
            "1m" -> LocalDateTime.now().minusMinutes(1)
            "1h" -> LocalDateTime.now().minusHours(1)
            "1d" -> LocalDateTime.now().minusDays(1)
            else -> LocalDateTime.now().minusMinutes(5) // realtime = last 5 min
        }

        val globeData = if (range == "realtime") {
            computeGlobeData(id, cutoff)
        } else {
            val cacheKey = "$id:globe:$range"
            QueryCache.getOrCompute(cacheKey) {
                computeGlobeData(id, cutoff)
            }
        }
        call.respond(globeData)
    }

    // Data retention dry-run preview
    get("/projects/{id}/retention-preview") {
        val id = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val days = call.request.queryParameters["days"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Query parameter 'days' is required"))

        if (days < 1) {
            return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("'days' must be at least 1"))
        }

        val cutoff = LocalDateTime.now().minusDays(days.toLong())

        val (count, oldest) = transaction {
            val toDelete = Events.selectAll()
                .where { (Events.projectId eq id) and (Events.timestamp lessEq cutoff) }
                .count()
            val oldestTs = Events.select(Events.timestamp)
                .where { Events.projectId eq id }
                .orderBy(Events.timestamp, SortOrder.ASC)
                .limit(1)
                .map { it[Events.timestamp].toString() }
                .firstOrNull()
            toDelete to oldestTs
        }

        call.respond(buildJsonObject {
            put("eventsToDelete", count)
            if (oldest != null) put("oldestEvent", oldest) else put("oldestEvent", "")
        })
    }

    // Demo data generation
    post("/projects/{id}/demo-data") {
        if (!call.requireRole(UserRole.ADMIN)) return@post
        val id = safeParseUUID(call.parameters["id"])
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        
        val params = call.receive<DemoDataRequest>()
        val count = params.count
        val timeScope = params.timeScope
        
        try {
            // Also seed goals, funnels and segments for a better demo experience
            seedDemoGoalsFunnelsSegments(id)
            
            val generated = generateDemoData(id, count, timeScope)
            QueryCache.invalidateProject(id.toString())
            WidgetCache.invalidateProject(id.toString())
            
            call.respond(DemoDataResponse(generated = generated))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            call.application.environment.log.error("Demo data generation failed for project $id: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError,
                ApiError.internalError("Failed to generate demo data: ${e.message}"))
        }
    }
}

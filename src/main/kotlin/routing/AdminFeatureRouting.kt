package se.onemanstudio.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.api.models.*
import se.onemanstudio.api.models.admin.*
import se.onemanstudio.api.models.dashboard.*
import se.onemanstudio.core.models.UserRole
import se.onemanstudio.db.*
import se.onemanstudio.middleware.QueryCache
import se.onemanstudio.middleware.requireRole
import se.onemanstudio.services.EmailService
import se.onemanstudio.services.WebhookService
import se.onemanstudio.services.WebhookTrigger
import se.onemanstudio.utils.*
import java.time.LocalDateTime
import java.util.UUID

fun Route.adminFeatureRoutes() {
    // ── Conversion Goals ──────────────────────────────────────────

    get("/projects/{id}/goals") {
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val goals = transaction {
            ConversionGoals.selectAll().where { ConversionGoals.projectId eq pid }
                .orderBy(ConversionGoals.createdAt, SortOrder.DESC)
                .map {
                    GoalResponse(
                        id = it[ConversionGoals.id].toString(),
                        name = it[ConversionGoals.name],
                        goalType = it[ConversionGoals.goalType],
                        matchValue = it[ConversionGoals.matchValue],
                        isActive = it[ConversionGoals.isActive],
                        createdAt = it[ConversionGoals.createdAt].toString()
                    )
                }
        }
        call.respond(goals)
    }

    post("/projects/{id}/goals") {
        if (!call.requireRole(UserRole.ADMIN)) return@post
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val request = try {
            call.receive<GoalRequest>()
        } catch (_: ContentTransformationException) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid request body"))
        }

        if (request.name.isBlank() || request.matchValue.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Name and match value are required"))
        }

        if (request.goalType !in listOf("url", "event")) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Goal type must be 'url' or 'event'"))
        }

        val goalId = UUID.randomUUID()
        transaction {
            ConversionGoals.insert {
                it[id] = goalId
                it[projectId] = pid
                it[name] = request.name.trim()
                it[goalType] = request.goalType
                it[matchValue] = request.matchValue.trim()
            }
        }

        call.respond(HttpStatusCode.Created, buildJsonObject {
            put("id", goalId.toString())
            put("success", true)
        })
    }

    put("/projects/{id}/goals/{goalId}") {
        if (!call.requireRole(UserRole.ADMIN)) return@put
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@put call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val goalId = safeParseUUID(call.parameters["goalId"])
            ?: return@put call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing goal ID"))

        val updates = call.receive<Map<String, String>>()

        transaction {
            ConversionGoals.update({
                (ConversionGoals.id eq goalId) and (ConversionGoals.projectId eq pid)
            }) {
                updates["name"]?.let { name -> it[ConversionGoals.name] = name.trim() }
                updates["goalType"]?.let { type -> it[ConversionGoals.goalType] = type }
                updates["matchValue"]?.let { value -> it[ConversionGoals.matchValue] = value.trim() }
                updates["isActive"]?.let { active -> it[ConversionGoals.isActive] = active.toBoolean() }
            }
        }
        call.respond(HttpStatusCode.OK, buildJsonObject { put("success", true) })
    }

    delete("/projects/{id}/goals/{goalId}") {
        if (!call.requireRole(UserRole.ADMIN)) return@delete
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val goalId = safeParseUUID(call.parameters["goalId"])
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing goal ID"))

        transaction {
            ConversionGoals.deleteWhere {
                (ConversionGoals.id eq goalId) and (ConversionGoals.projectId eq pid)
            }
        }
        call.respond(HttpStatusCode.NoContent)
    }

    get("/projects/{id}/goals/stats") {
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val filter = call.request.queryParameters["filter"] ?: "7d"

        val stats = QueryCache.getOrCompute("$pid:goalstats:$filter") {
            calculateGoalStats(pid, filter)
        }
        call.respond(stats)
    }

    // ── Funnels ──────────────────────────────────────────────────

    get("/projects/{id}/funnels") {
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val funnels = transaction {
            Funnels.selectAll().where { Funnels.projectId eq pid }
                .orderBy(Funnels.createdAt, SortOrder.DESC)
                .map { funnel ->
                    val funnelId = funnel[Funnels.id]
                    val steps = FunnelSteps.selectAll()
                        .where { FunnelSteps.funnelId eq funnelId }
                        .orderBy(FunnelSteps.stepNumber, SortOrder.ASC)
                        .map { step ->
                            FunnelStepResponse(
                                id = step[FunnelSteps.id].toString(),
                                stepNumber = step[FunnelSteps.stepNumber],
                                name = step[FunnelSteps.name],
                                stepType = step[FunnelSteps.stepType],
                                matchValue = step[FunnelSteps.matchValue]
                            )
                        }

                    FunnelResponse(
                        id = funnelId.toString(),
                        name = funnel[Funnels.name],
                        steps = steps,
                        createdAt = funnel[Funnels.createdAt].toString()
                    )
                }
        }
        call.respond(funnels)
    }

    post("/projects/{id}/funnels") {
        if (!call.requireRole(UserRole.ADMIN)) return@post
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val request = try {
            call.receive<FunnelRequest>()
        } catch (_: ContentTransformationException) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid request body"))
        }

        if (request.name.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Funnel name is required"))
        }

        if (request.steps.size < 2) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Funnel must have at least 2 steps"))
        }

        for (step in request.steps) {
            if (step.stepType !in listOf("url", "event")) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Step type must be 'url' or 'event'"))
            }
        }

        val funnelId = UUID.randomUUID()
        transaction {
            Funnels.insert {
                it[id] = funnelId
                it[projectId] = pid
                it[name] = request.name.trim()
            }

            request.steps.forEachIndexed { index, step ->
                FunnelSteps.insert {
                    it[id] = UUID.randomUUID()
                    it[FunnelSteps.funnelId] = funnelId
                    it[stepNumber] = index + 1
                    it[name] = step.name.trim()
                    it[stepType] = step.stepType
                    it[matchValue] = step.matchValue.trim()
                }
            }
        }

        call.respond(HttpStatusCode.Created, buildJsonObject {
            put("id", funnelId.toString())
            put("success", true)
        })
    }

    delete("/projects/{id}/funnels/{funnelId}") {
        if (!call.requireRole(UserRole.ADMIN)) return@delete
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val funnelId = safeParseUUID(call.parameters["funnelId"])
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing funnel ID"))

        val deleted = transaction {
            // Verify funnel belongs to the project before deleting steps
            val funnelExists = Funnels.selectAll().where {
                (Funnels.id eq funnelId) and (Funnels.projectId eq pid)
            }.count() > 0

            if (funnelExists) {
                FunnelSteps.deleteWhere { FunnelSteps.funnelId eq funnelId }
                Funnels.deleteWhere {
                    (Funnels.id eq funnelId) and (Funnels.projectId eq pid)
                }
            }
            funnelExists
        }

        if (deleted) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound,
                ApiError.notFound("Funnel not found"))
        }
    }

    get("/projects/{id}/funnels/{funnelId}/analysis") {
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val funnelId = safeParseUUID(call.parameters["funnelId"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing funnel ID"))
        val filter = call.request.queryParameters["filter"] ?: "7d"

        val (start, end) = getCurrentPeriod(filter)
        val analysis = try {
            analyzeFunnel(funnelId, pid, start, end)
        } catch (_: IllegalArgumentException) {
            return@get call.respond(HttpStatusCode.NotFound,
                ApiError.notFound("Funnel not found"))
        }
        call.respond(analysis)
    }

    // ── User Segments ─────────────────────────────────────────────

    get("/projects/{id}/segments") {
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val segments = transaction {
            Segments.selectAll().where { Segments.projectId eq pid }
                .orderBy(Segments.createdAt, SortOrder.DESC)
                .map {
                    SegmentResponse(
                        id = it[Segments.id].toString(),
                        name = it[Segments.name],
                        description = it[Segments.description],
                        filters = try {
                            Json.decodeFromString<List<SegmentFilter>>(it[Segments.filtersJson])
                        } catch (_: SerializationException) { emptyList() },
                        createdAt = it[Segments.createdAt].toString()
                    )
                }
        }
        call.respond(segments)
    }

    post("/projects/{id}/segments") {
        if (!call.requireRole(UserRole.ADMIN)) return@post
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val request = try {
            call.receive<SegmentRequest>()
        } catch (_: ContentTransformationException) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid request body"))
        }

        if (request.name.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Segment name is required"))
        }

        if (request.filters.isEmpty()) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("At least one filter is required"))
        }

        val validFields = setOf("browser", "os", "device", "country", "city", "path", "referrer", "eventType")
        val validOperators = setOf("equals", "not_equals", "contains", "starts_with")
        for (filter in request.filters) {
            if (filter.field !in validFields) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid filter field: ${filter.field}. Valid: $validFields"))
            }
            if (filter.operator !in validOperators) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid operator: ${filter.operator}. Valid: $validOperators"))
            }
        }

        val segmentId = UUID.randomUUID()
        transaction {
            Segments.insert {
                it[id] = segmentId
                it[projectId] = pid
                it[name] = request.name.trim()
                it[description] = request.description?.trim()
                it[filtersJson] = Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SegmentFilter.serializer()), request.filters)
            }
        }

        call.respond(HttpStatusCode.Created, buildJsonObject {
            put("id", segmentId.toString())
            put("success", true)
        })
    }

    delete("/projects/{id}/segments/{segmentId}") {
        if (!call.requireRole(UserRole.ADMIN)) return@delete
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val segmentId = safeParseUUID(call.parameters["segmentId"])
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing segment ID"))

        transaction {
            Segments.deleteWhere {
                (Segments.id eq segmentId) and (Segments.projectId eq pid)
            }
        }
        call.respond(HttpStatusCode.NoContent)
    }

    get("/projects/{id}/segments/{segmentId}/analysis") {
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val segmentId = safeParseUUID(call.parameters["segmentId"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing segment ID"))
        val filter = call.request.queryParameters["filter"] ?: "7d"

        val segment = transaction {
            Segments.selectAll().where {
                (Segments.id eq segmentId) and (Segments.projectId eq pid)
            }.singleOrNull()
        } ?: return@get call.respond(HttpStatusCode.NotFound,
            ApiError.notFound("Segment not found"))

        val filters = try {
            Json.decodeFromString<List<SegmentFilter>>(segment[Segments.filtersJson])
        } catch (_: SerializationException) { emptyList() }

        val (start, end) = getCurrentPeriod(filter)

        val analysis = transaction {
            val allEvents = Events.selectAll().where {
                (Events.projectId eq pid) and (Events.timestamp greaterEq start) and (Events.timestamp lessEq end)
            }.toList()

            // Apply segment filters
            val filtered = allEvents.filter { event ->
                applySegmentFilters(event, filters)
            }

            val sessions = filtered.groupBy { it[Events.sessionId] }
            val bouncedSessions = sessions.count { (_, sessionEvents) ->
                val uniquePages = sessionEvents.map { it[Events.path] }.distinct().size
                val hasHeartbeat = sessionEvents.any { it[Events.eventType] == "heartbeat" }
                uniquePages == 1 && !hasHeartbeat
            }
            val bounceRate = if (sessions.isNotEmpty()) (bouncedSessions.toDouble() / sessions.size) * 100.0 else 0.0

            val topPages = filtered.groupBy { it[Events.path] }
                .map { (path, events) -> StatEntry(path, events.size.toLong()) }
                .sortedByDescending { it.value }
                .take(10)

            SegmentAnalysis(
                segmentId = segmentId.toString(),
                segmentName = segment[Segments.name],
                totalViews = filtered.size.toLong(),
                uniqueVisitors = filtered.map { it[Events.visitorHash] }.distinct().size.toLong(),
                bounceRate = bounceRate,
                topPages = topPages,
                matchingEvents = filtered.size.toLong()
            )
        }
        call.respond(analysis)
    }

    // ── Webhooks ─────────────────────────────────────────────────

    get("/projects/{id}/webhooks") {
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val webhooks = transaction {
            Webhooks.selectAll().where { Webhooks.projectId eq pid }.map {
                WebhookResponse(
                    id = it[Webhooks.id].toString(),
                    projectId = it[Webhooks.projectId].toString(),
                    url = it[Webhooks.url],
                    events = it[Webhooks.events].split(",").map { e -> e.trim() },
                    isActive = it[Webhooks.isActive],
                    createdAt = it[Webhooks.createdAt].toString()
                )
            }
        }
        call.respond(webhooks)
    }

    post("/projects/{id}/webhooks") {
        if (!call.requireRole(UserRole.ADMIN)) return@post
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val request = try {
            call.receive<CreateWebhookRequest>()
        } catch (_: ContentTransformationException) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid request body"))
        }

        if (request.url.isBlank() || !request.url.startsWith("https://")) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Webhook URL must use HTTPS"))
        }

        val webhookId = UUID.randomUUID()
        val secret = java.security.SecureRandom().let { rng ->
            val bytes = ByteArray(32)
            rng.nextBytes(bytes)
            bytes.joinToString("") { "%02x".format(it) }
        }

        transaction {
            Webhooks.insert {
                it[Webhooks.id] = webhookId
                it[Webhooks.projectId] = pid
                it[Webhooks.url] = request.url
                it[Webhooks.secret] = secret
                it[Webhooks.events] = request.events.joinToString(",")
            }
        }

        WebhookTrigger.invalidateProject(pid.toString())

        call.respond(HttpStatusCode.Created, buildJsonObject {
            put("id", webhookId.toString())
            put("secret", secret)
            put("success", true)
        })
    }

    delete("/projects/{id}/webhooks/{webhookId}") {
        if (!call.requireRole(UserRole.ADMIN)) return@delete
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val webhookId = safeParseUUID(call.parameters["webhookId"])
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing webhook ID"))

        val deleted = transaction {
            val exists = Webhooks.selectAll().where {
                (Webhooks.id eq webhookId) and (Webhooks.projectId eq pid)
            }.count() > 0

            if (exists) {
                WebhookDeliveries.deleteWhere { WebhookDeliveries.webhookId eq webhookId }
                Webhooks.deleteWhere {
                    (Webhooks.id eq webhookId) and (Webhooks.projectId eq pid)
                }
            }
            exists
        }

        if (deleted) {
            WebhookTrigger.invalidateProject(pid.toString())
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound, ApiError.notFound("Webhook not found"))
        }
    }

    get("/projects/{id}/webhooks/{webhookId}/deliveries") {
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val webhookId = safeParseUUID(call.parameters["webhookId"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing webhook ID"))

        val webhookExists = transaction {
            Webhooks.selectAll().where {
                (Webhooks.id eq webhookId) and (Webhooks.projectId eq pid)
            }.count() > 0
        }
        if (!webhookExists) {
            return@get call.respond(HttpStatusCode.NotFound,
                ApiError.notFound("Webhook not found"))
        }

        val deliveries = transaction {
            WebhookDeliveries.selectAll()
                .where { WebhookDeliveries.webhookId eq webhookId }
                .orderBy(WebhookDeliveries.createdAt, SortOrder.DESC)
                .limit(50)
                .map {
                    WebhookDeliveryResponse(
                        id = it[WebhookDeliveries.id].toString(),
                        eventType = it[WebhookDeliveries.eventType],
                        responseCode = it[WebhookDeliveries.responseCode],
                        attempt = it[WebhookDeliveries.attempt],
                        status = it[WebhookDeliveries.status],
                        createdAt = it[WebhookDeliveries.createdAt].toString(),
                        deliveredAt = it[WebhookDeliveries.deliveredAt]?.toString()
                    )
                }
        }
        call.respond(deliveries)
    }

    post("/projects/{id}/webhooks/{webhookId}/test") {
        if (!call.requireRole(UserRole.ADMIN)) return@post
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val webhookId = safeParseUUID(call.parameters["webhookId"])
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing webhook ID"))

        val webhook = transaction {
            Webhooks.selectAll().where {
                (Webhooks.id eq webhookId) and (Webhooks.projectId eq pid)
            }.singleOrNull()
        } ?: return@post call.respond(HttpStatusCode.NotFound,
            ApiError.notFound("Webhook not found"))

        val testPayload = buildJsonObject {
            put("event", "test")
            put("projectId", pid.toString())
            put("message", "This is a test webhook delivery from Mini Numbers")
            put("timestamp", LocalDateTime.now().toString())
        }.toString()

        WebhookService.deliverAsync(
            webhookId = webhookId,
            url = webhook[Webhooks.url],
            secret = webhook[Webhooks.secret],
            eventType = "test",
            payload = testPayload
        )

        call.respond(HttpStatusCode.OK, buildJsonObject {
            put("success", true)
            put("message", "Test webhook queued for delivery")
        })
    }

    // ── Email Reports ────────────────────────────────────────────

    get("/projects/{id}/email-reports") {
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val reports = transaction {
            EmailReports.selectAll().where {
                EmailReports.projectId eq pid
            }.map {
                EmailReportResponse(
                    id = it[EmailReports.id].toString(),
                    projectId = it[EmailReports.projectId].toString(),
                    recipientEmail = it[EmailReports.recipientEmail],
                    schedule = it[EmailReports.schedule],
                    sendHour = it[EmailReports.sendHour],
                    sendDay = it[EmailReports.sendDay],
                    timezone = it[EmailReports.timezone],
                    subjectTemplate = it[EmailReports.subjectTemplate],
                    headerText = it[EmailReports.headerText],
                    footerText = it[EmailReports.footerText],
                    includeSections = it[EmailReports.includeSections].split(",").map { s -> s.trim() },
                    isActive = it[EmailReports.isActive],
                    lastSentAt = it[EmailReports.lastSentAt]?.toString(),
                    createdAt = it[EmailReports.createdAt].toString()
                )
            }
        }
        call.respond(reports)
    }

    post("/projects/{id}/email-reports") {
        if (!call.requireRole(UserRole.ADMIN)) return@post
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val request = try {
            call.receive<CreateEmailReportRequest>()
        } catch (_: ContentTransformationException) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid request body"))
        }

        if (request.recipientEmail.isBlank() || !request.recipientEmail.contains("@")) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid email address"))
        }

        val validSchedules = listOf("DAILY", "WEEKLY", "MONTHLY")
        if (request.schedule.uppercase() !in validSchedules) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Schedule must be one of: $validSchedules"))
        }

        val reportId = UUID.randomUUID()
        transaction {
            EmailReports.insert {
                it[EmailReports.id] = reportId
                it[EmailReports.projectId] = pid
                it[EmailReports.recipientEmail] = request.recipientEmail.take(320)
                it[EmailReports.schedule] = request.schedule.uppercase()
                it[EmailReports.sendHour] = request.sendHour.coerceIn(0, 23)
                it[EmailReports.sendDay] = request.sendDay.coerceIn(1, 28)
                it[EmailReports.timezone] = request.timezone.take(50)
                it[EmailReports.subjectTemplate] = request.subjectTemplate.take(500)
                it[EmailReports.headerText] = request.headerText?.take(500)
                it[EmailReports.footerText] = request.footerText?.take(500)
                it[EmailReports.includeSections] = request.includeSections.joinToString(",")
            }
        }

        call.respond(HttpStatusCode.Created, buildJsonObject {
            put("id", reportId.toString())
            put("success", true)
        })
    }

    put("/projects/{id}/email-reports/{reportId}") {
        if (!call.requireRole(UserRole.ADMIN)) return@put
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@put call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val reportId = safeParseUUID(call.parameters["reportId"])
            ?: return@put call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing report ID"))

        val request = try {
            call.receive<UpdateEmailReportRequest>()
        } catch (_: ContentTransformationException) {
            return@put call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid request body"))
        }

        val updated = transaction {
            val exists = EmailReports.selectAll().where {
                (EmailReports.id eq reportId) and
                        (EmailReports.projectId eq pid)
            }.count() > 0

            if (exists) {
                EmailReports.update({
                    (EmailReports.id eq reportId) and
                            (EmailReports.projectId eq pid)
                }) {
                    request.isActive?.let { v -> it[EmailReports.isActive] = v }
                    request.schedule?.let { v -> it[EmailReports.schedule] = v.uppercase() }
                    request.sendHour?.let { v -> it[EmailReports.sendHour] = v.coerceIn(0, 23) }
                    request.sendDay?.let { v -> it[EmailReports.sendDay] = v.coerceIn(1, 28) }
                    request.timezone?.let { v -> it[EmailReports.timezone] = v.take(50) }
                    request.subjectTemplate?.let { v -> it[EmailReports.subjectTemplate] = v.take(500) }
                    request.headerText?.let { v -> it[EmailReports.headerText] = v.take(500) }
                    request.footerText?.let { v -> it[EmailReports.footerText] = v.take(500) }
                    request.includeSections?.let { v -> it[EmailReports.includeSections] = v.joinToString(",") }
                }
            }
            exists
        }

        if (updated) {
            call.respond(HttpStatusCode.OK, buildJsonObject { put("success", true) })
        } else {
            call.respond(HttpStatusCode.NotFound, ApiError.notFound("Email report not found"))
        }
    }

    delete("/projects/{id}/email-reports/{reportId}") {
        if (!call.requireRole(UserRole.ADMIN)) return@delete
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val reportId = safeParseUUID(call.parameters["reportId"])
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing report ID"))

        val deleted = transaction {
            EmailReports.deleteWhere {
                (EmailReports.id eq reportId) and
                        (EmailReports.projectId eq pid)
            } > 0
        }

        if (deleted) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound, ApiError.notFound("Email report not found"))
        }
    }

    post("/projects/{id}/email-reports/{reportId}/test") {
        if (!call.requireRole(UserRole.ADMIN)) return@post
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val reportId = safeParseUUID(call.parameters["reportId"])
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing report ID"))

        val report = transaction {
            EmailReports.selectAll().where {
                (EmailReports.id eq reportId) and
                        (EmailReports.projectId eq pid)
            }.singleOrNull()
        } ?: return@post call.respond(HttpStatusCode.NotFound,
            ApiError.notFound("Email report not found"))

        if (!EmailService.isConfigured()) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("SMTP is not configured. Set SMTP_HOST and SMTP_FROM environment variables."))
        }

        EmailService.sendReportAsync(
            projectId = pid,
            recipientEmail = report[EmailReports.recipientEmail],
            period = when (report[EmailReports.schedule]) {
                "DAILY" -> "24h"
                "WEEKLY" -> "7d"
                "MONTHLY" -> "30d"
                else -> "7d"
            },
            reportId = reportId
        )

        call.respond(HttpStatusCode.OK, buildJsonObject {
            put("success", true)
            put("message", "Test email report queued for delivery")
        })
    }

    get("/smtp/status") {
        val status = EmailService.getSmtpStatus()
        call.respond(
            SmtpStatusResponse(
                configured = status["configured"] as Boolean,
                host = status["host"] as? String,
                port = status["port"] as? Int,
                from = status["from"] as? String
            )
        )
    }

    // ── Revenue Analytics ─────────────────────────────────────────

    get("/projects/{id}/revenue") {
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val filter = call.request.queryParameters["filter"] ?: "7d"
        val (start, end) = getCurrentPeriod(filter)
        val stats = QueryCache.getOrCompute("$pid:revenue:$filter") {
            RevenueAnalysisUtils.calculateRevenue(pid, start, end)
        }
        call.respond(stats)
    }

    get("/projects/{id}/revenue/breakdown") {
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val filter = call.request.queryParameters["filter"] ?: "7d"
        val (start, end) = getCurrentPeriod(filter)
        val breakdown = QueryCache.getOrCompute("$pid:revenue-breakdown:$filter") {
            RevenueAnalysisUtils.calculateRevenueByEvent(pid, start, end)
        }
        call.respond(breakdown)
    }

    get("/projects/{id}/revenue/attribution") {
        val pid = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val filter = call.request.queryParameters["filter"] ?: "7d"
        val (start, end) = getCurrentPeriod(filter)
        val attribution = QueryCache.getOrCompute("$pid:revenue-attribution:$filter") {
            RevenueAnalysisUtils.calculateRevenueAttribution(pid, start, end)
        }
        call.respond(attribution)
    }
}

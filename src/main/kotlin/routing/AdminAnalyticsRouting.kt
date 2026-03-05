package se.onemanstudio.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.api.models.ApiError
import se.onemanstudio.api.models.dashboard.ComparisonReport
import se.onemanstudio.api.models.dashboard.RawEvent
import se.onemanstudio.api.models.dashboard.RawEventsResponse
import se.onemanstudio.db.Events
import se.onemanstudio.middleware.QueryCache
import se.onemanstudio.utils.*

fun Route.adminAnalyticsRoutes() {
    // Main dashboard report
    get("/projects/{id}/report") {
        val id = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val filter = call.request.queryParameters["filter"] ?: "7d"

        val report = QueryCache.getOrCompute("$id:report:$filter") {
            val (start, end) = getCurrentPeriod(filter)
            generateReport(id, start, end)
        }
        call.respond(report)
    }

    // Comparison report (current vs previous period)
    get("/projects/{id}/report/comparison") {
        val id = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))
        val filter = call.request.queryParameters["filter"] ?: "7d"

        val comparisonReport = QueryCache.getOrCompute("$id:comparison:$filter") {
            val (currentStart, currentEnd) = getCurrentPeriod(filter)
            val (previousStart, previousEnd) = getPreviousPeriod(filter)

            val current = generateReport(id, currentStart, currentEnd)
            val previous = generateReport(id, previousStart, previousEnd)
            val timeSeries = generateTimeSeries(id, currentStart, currentEnd, filter)

            ComparisonReport(current = current, previous = previous, timeSeries = timeSeries)
        }
        call.respond(comparisonReport)
    }

    // Contribution calendar (visitor activity heatmap)
    get("/projects/{id}/calendar") {
        val id = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing project ID"))

        val calendar = QueryCache.getOrCompute("$id:calendar") {
            generateContributionCalendar(id)
        }
        call.respond(calendar)
    }

    // Raw events viewer with pagination and filtering
    get("/projects/{id}/events") {
        val id = safeParseUUID(call.parameters["id"])
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid project ID"))

        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 1000)
        val filterType = call.request.queryParameters["filter"]
        val sortBy = call.request.queryParameters["sortBy"] ?: "timestamp"
        val sortOrder = call.request.queryParameters["order"] ?: "desc"

        val response = transaction {
            val baseQuery = { Events.selectAll().where {
                if (filterType != null) {
                    (Events.projectId eq id) and (Events.eventType eq filterType)
                } else {
                    Events.projectId eq id
                }
            }}

            val total = baseQuery().count()

            val sortColumn = when(sortBy) {
                "path" -> Events.path
                "country" -> Events.country
                "browser" -> Events.browser
                else -> Events.timestamp
            }

            val order = if (sortOrder == "asc") SortOrder.ASC else SortOrder.DESC

            val results = baseQuery()
                .orderBy(sortColumn, order)
                .limit(limit).offset((page * limit).toLong())
                .map { row ->
                    RawEvent(
                        id = row[Events.id],
                        timestamp = row[Events.timestamp].toString(),
                        eventType = row[Events.eventType],
                        eventName = row[Events.eventName],
                        path = row[Events.path],
                        referrer = row[Events.referrer],
                        country = row[Events.country],
                        city = row[Events.city],
                        browser = row[Events.browser],
                        os = row[Events.os],
                        device = row[Events.device],
                        sessionId = row[Events.sessionId],
                        duration = row[Events.duration],
                        utmSource = row[Events.utmSource],
                        utmCampaign = row[Events.utmCampaign],
                        scrollDepth = row[Events.scrollDepth],
                        region = row[Events.region],
                        targetUrl = row[Events.targetUrl],
                        properties = row[Events.properties],
                        latitude = row[Events.latitude],
                        longitude = row[Events.longitude]
                    )
                }

            RawEventsResponse(
                events = results,
                total = total,
                page = page,
                limit = limit
            )
        }

        call.respond(response)
    }
}

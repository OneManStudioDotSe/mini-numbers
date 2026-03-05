package se.onemanstudio

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.api.models.widget.*
import se.onemanstudio.core.resolveWidgetProject
import se.onemanstudio.db.Events
import se.onemanstudio.middleware.RateLimiter
import se.onemanstudio.middleware.WidgetCache
import se.onemanstudio.middleware.models.RateLimitResult
import se.onemanstudio.utils.getCurrentPeriod
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Validate a widget request: resolve the project from the API key and enforce rate limits.
 * Returns the project UUID on success, or null after responding with an error.
 */
private suspend fun validateWidgetRequest(call: io.ktor.server.routing.RoutingCall, rateLimiter: RateLimiter): java.util.UUID? {
    val projectId = resolveWidgetProject(call)
    if (projectId == null) {
        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Invalid API key"))
        return null
    }

    val ip = call.request.origin.remoteHost
    val apiKey = call.request.queryParameters["key"] ?: ""
    if (rateLimiter.checkRateLimit(ip, apiKey) is RateLimitResult.Exceeded) {
        call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate limit exceeded"))
        return null
    }

    return projectId
}

fun Application.configureWidgetRouting(rateLimiter: RateLimiter) {
    routing {
        // Serve the widget script
        staticResources("/widget", "widget")

        route("/widget") {
            // Real-time visitor counter — distinct visitors in last 5 minutes
            get("/realtime") {
                val projectId = validateWidgetRequest(call, rateLimiter) ?: return@get

                val result = WidgetCache.getOrCompute("widget:$projectId:realtime") {
                    val cutoff = LocalDateTime.now().minusMinutes(5)
                    transaction {
                        val count = Events.select(Events.visitorHash)
                            .where {
                                (Events.projectId eq projectId) and
                                        (Events.timestamp greaterEq cutoff)
                            }
                            .withDistinct()
                            .count()
                        RealtimeCounterWidget(
                            activeVisitors = count,
                            timestamp = LocalDateTime.now().toString()
                        )
                    }
                }
                call.respond(result)
            }

            // Page view counter — total views, filterable by scope and time range
            get("/pageviews") {
                val projectId = validateWidgetRequest(call, rateLimiter) ?: return@get

                val scope = call.request.queryParameters["scope"] ?: "site"
                val filter = call.request.queryParameters["filter"] ?: "7d"
                val path = call.request.queryParameters["path"]

                val cacheKey = "widget:$projectId:pageviews:$scope:$filter:${path ?: "all"}"
                val result = WidgetCache.getOrCompute(cacheKey) {
                    val (start, end) = getCurrentPeriod(filter)
                    transaction {
                        val query = Events.selectAll().where {
                            val base = (Events.projectId eq projectId) and
                                    (Events.eventType eq "pageview") and
                                    (Events.timestamp greaterEq start) and
                                    (Events.timestamp lessEq end)
                            if (scope == "page" && path != null) {
                                base and (Events.path eq path)
                            } else {
                                base
                            }
                        }
                        PageViewWidget(
                            views = query.count(),
                            scope = scope,
                            filter = filter,
                            path = if (scope == "page") path else null
                        )
                    }
                }
                call.respond(result)
            }

            // Top pages list — most viewed pages
            get("/toppages") {
                val projectId = validateWidgetRequest(call, rateLimiter) ?: return@get

                val filter = call.request.queryParameters["filter"] ?: "7d"
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 5).coerceIn(1, 10)

                val cacheKey = "widget:$projectId:toppages:$filter:$limit"
                val result = WidgetCache.getOrCompute(cacheKey) {
                    val (start, end) = getCurrentPeriod(filter)
                    transaction {
                        val pages = Events.select(Events.path, Events.path.count())
                            .where {
                                (Events.projectId eq projectId) and
                                        (Events.eventType eq "pageview") and
                                        (Events.timestamp greaterEq start) and
                                        (Events.timestamp lessEq end)
                            }
                            .groupBy(Events.path)
                            .orderBy(Events.path.count(), SortOrder.DESC)
                            .limit(limit)
                            .map { TopPageEntry(path = it[Events.path], views = it[Events.path.count()]) }
                        TopPagesWidget(pages = pages, filter = filter)
                    }
                }
                call.respond(result)
            }

            // Visitor sparkline — daily page views for the last 7 days
            get("/sparkline") {
                val projectId = validateWidgetRequest(call, rateLimiter) ?: return@get

                val result = WidgetCache.getOrCompute("widget:$projectId:sparkline") {
                    val today = LocalDate.now()
                    val days = (0..6).map { today.minusDays(it.toLong()) }.reversed()
                    val start = days.first().atStartOfDay()
                    val end = today.plusDays(1).atStartOfDay()
                    val formatter = DateTimeFormatter.ISO_LOCAL_DATE

                    transaction {
                        val events = Events.selectAll().where {
                            (Events.projectId eq projectId) and
                                    (Events.eventType eq "pageview") and
                                    (Events.timestamp greaterEq start) and
                                    (Events.timestamp less end)
                        }.toList()

                        val countsByDay = events.groupBy { it[Events.timestamp].toLocalDate() }
                        val points = days.map { day ->
                            SparklinePoint(
                                date = day.format(formatter),
                                views = (countsByDay[day]?.size ?: 0).toLong()
                            )
                        }
                        val maxValue = points.maxOfOrNull { it.views } ?: 0L
                        SparklineWidget(points = points, maxValue = maxValue)
                    }
                }
                call.respond(result)
            }
        }
    }
}

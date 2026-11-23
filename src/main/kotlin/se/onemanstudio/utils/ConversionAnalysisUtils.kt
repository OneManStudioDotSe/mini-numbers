/**
 * Conversion tracking engine — goals and funnels.
 *
 * ## Goals
 * A goal is a simple pass/fail test: did a session visit a specific URL
 * (`url` type) or fire a specific custom event (`event` type)?
 * [calculateGoalConversions] counts how many unique sessions matched the
 * goal pattern in the time window and returns a conversion rate.
 * [calculateGoalStats] wraps this with current-vs-previous-period
 * comparison for each active goal in a project.
 *
 * ## Funnels
 * A funnel is an ordered list of steps (each step is a URL or event goal).
 * [analyzeFunnel] walks through all sessions and checks whether they
 * completed each step **in order** — a session qualifies for step N only
 * if it already completed steps 1 … N-1 and the matching event happened
 * **after** the previous step's event timestamp. This produces drop-off
 * rates and average time-between-steps for each stage.
 */
package se.onemanstudio.utils

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import se.onemanstudio.db.ConversionGoals
import se.onemanstudio.db.Events
import se.onemanstudio.db.FunnelSteps
import se.onemanstudio.db.Funnels
import se.onemanstudio.api.models.StatEntry
import se.onemanstudio.api.models.admin.*
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import kotlin.collections.iterator

/**
 * Calculate conversions for a single goal within a time period.
 * Returns a pair of (conversion count, conversion rate as percentage).
 */
fun calculateGoalConversions(
    goalType: String,
    matchValue: String,
    projectId: UUID,
    start: LocalDateTime,
    end: LocalDateTime
): Pair<Long, Double> {
    val events = Events.selectAll().where {
        (Events.projectId eq projectId) and
        (Events.timestamp greaterEq start) and
        (Events.timestamp lessEq end)
    }.toList()

    // Total unique sessions in period
    val totalSessions = events.map { it[Events.sessionId] }.distinct().size
    if (totalSessions == 0) return Pair(0L, 0.0)

    // Find sessions that match the goal criteria
    val convertedSessions = when (goalType) {
        "url" -> events.filter {
            it[Events.eventType] == "pageview" && it[Events.path] == matchValue
        }.map { it[Events.sessionId] }.distinct().size

        "event" -> events.filter {
            it[Events.eventType] == "custom" && it[Events.eventName] == matchValue
        }.map { it[Events.sessionId] }.distinct().size

        else -> 0
    }

    val rate = (convertedSessions.toDouble() / totalSessions) * 100.0
    return Pair(convertedSessions.toLong(), rate)
}

/**
 * Calculate stats for all active goals in a project, with current vs previous period comparison.
 */
fun calculateGoalStats(projectId: UUID, filter: String): List<GoalStats> {
    return transaction {
        val goals = ConversionGoals.selectAll().where {
            (ConversionGoals.projectId eq projectId) and (ConversionGoals.isActive eq true)
        }.toList()

        if (goals.isEmpty()) return@transaction emptyList()

        val (currentStart, currentEnd) = getCurrentPeriod(filter)
        val (previousStart, previousEnd) = getPreviousPeriod(filter)

        goals.map { goal ->
            val goalResponse = GoalResponse(
                id = goal[ConversionGoals.id].toString(),
                name = goal[ConversionGoals.name],
                goalType = goal[ConversionGoals.goalType],
                matchValue = goal[ConversionGoals.matchValue],
                isActive = goal[ConversionGoals.isActive],
                createdAt = goal[ConversionGoals.createdAt].toApiString()
            )

            val (currentConversions, currentRate) = calculateGoalConversions(
                goal[ConversionGoals.goalType],
                goal[ConversionGoals.matchValue],
                projectId,
                currentStart,
                currentEnd
            )

            val (previousConversions, previousRate) = calculateGoalConversions(
                goal[ConversionGoals.goalType],
                goal[ConversionGoals.matchValue],
                projectId,
                previousStart,
                previousEnd
            )

            GoalStats(
                goal = goalResponse,
                conversions = currentConversions,
                conversionRate = currentRate,
                previousConversions = previousConversions,
                previousConversionRate = previousRate
            )
        }
    }
}

/** The few columns of an event that funnel step matching reads. */
private data class StepHit(val eventType: String, val path: String, val eventName: String?, val timestamp: LocalDateTime)

/**
 * Analyze a funnel: track sessions through sequential steps, calculating drop-off at each stage.
 * A session completes a step if it has a matching event AND completed all previous steps in order.
 */
fun analyzeFunnel(
    funnelId: UUID,
    projectId: UUID,
    start: LocalDateTime,
    end: LocalDateTime
): FunnelAnalysis {
    return transaction {
        // Load funnel info (validate it belongs to the requested project)
        val funnel = Funnels.selectAll().where {
            (Funnels.id eq funnelId) and (Funnels.projectId eq projectId)
        }.singleOrNull()
            ?: throw IllegalArgumentException("Funnel not found for this project")

        // Load steps ordered by step number
        val steps = FunnelSteps.selectAll()
            .where { FunnelSteps.funnelId eq funnelId }
            .orderBy(FunnelSteps.stepNumber, SortOrder.ASC)
            .toList()

        val funnelResponse = FunnelResponse(
            id = funnel[Funnels.id].toString(),
            name = funnel[Funnels.name],
            steps = steps.map { step ->
                FunnelStepResponse(
                    id = step[FunnelSteps.id].toString(),
                    stepNumber = step[FunnelSteps.stepNumber],
                    name = step[FunnelSteps.name],
                    stepType = step[FunnelSteps.stepType],
                    matchValue = step[FunnelSteps.matchValue]
                )
            },
            createdAt = funnel[Funnels.createdAt].toApiString()
        )

        if (steps.isEmpty()) {
            return@transaction FunnelAnalysis(
                funnel = funnelResponse,
                totalSessions = 0,
                steps = emptyList()
            )
        }

        val inPeriod: Op<Boolean> = (Events.projectId eq projectId) and
            (Events.timestamp greaterEq start) and
            (Events.timestamp lessEq end)

        // Every session of the period counts as entered, whether or not it touches a step
        val totalSessions = Events.select(Events.sessionId.countDistinct())
            .where { inPeriod }
            .single()[Events.sessionId.countDistinct()]

        // Only the rows that can match some step, and only the columns the matching needs
        val urlValues = steps.filter { it[FunnelSteps.stepType] == "url" }.map { it[FunnelSteps.matchValue] }.distinct()
        val eventValues = steps.filter { it[FunnelSteps.stepType] == "event" }.map { it[FunnelSteps.matchValue] }.distinct()
        val anyStep: Op<Boolean>? = listOfNotNull(
            if (urlValues.isEmpty()) null else (Events.eventType eq "pageview") and (Events.path inList urlValues),
            if (eventValues.isEmpty()) null else (Events.eventType eq "custom") and (Events.eventName inList eventValues)
        ).reduceOrNull { a, b -> a or b }

        // Rows are read one by one, oldest first, and kept per session as small records
        val sessionEvents = LinkedHashMap<String, MutableList<StepHit>>()
        if (anyStep != null) {
            Events.select(Events.sessionId, Events.eventType, Events.path, Events.eventName, Events.timestamp)
                .where { inPeriod and anyStep }
                .orderBy(Events.timestamp to SortOrder.ASC, Events.id to SortOrder.ASC)
                .forEach { row ->
                    sessionEvents.getOrPut(row[Events.sessionId]) { mutableListOf() }.add(
                        StepHit(row[Events.eventType], row[Events.path], row[Events.eventName], row[Events.timestamp])
                    )
                }
        }

        // Track which sessions make it through each step
        // For each session, find the timestamp of the event that matched the step
        var qualifiedSessions: Map<String, LocalDateTime> = sessionEvents.keys.associateWith {
            LocalDateTime.MIN // No previous step timestamp constraint for step 1
        }

        val stepAnalyses = mutableListOf<FunnelStepAnalysis>()
        var enteredSessions = 0L

        for ((index, step) in steps.withIndex()) {
            val stepType = step[FunnelSteps.stepType]
            val matchValue = step[FunnelSteps.matchValue]
            val stepNumber = step[FunnelSteps.stepNumber]

            // For each qualified session, check if it has a matching event AFTER the previous step's event
            val newQualified = mutableMapOf<String, LocalDateTime>()
            var totalTimeDiff = 0.0
            var timeDiffCount = 0

            for ((sessionId, afterTimestamp) in qualifiedSessions) {
                val hits = sessionEvents[sessionId] ?: continue

                // Find the first matching event that occurs after the previous step's timestamp
                val matchingEvent = hits.firstOrNull { evt ->
                    val eventTime = evt.timestamp
                    val isAfterPrevious = if (index == 0) true else eventTime.isAfter(afterTimestamp)

                    isAfterPrevious && when (stepType) {
                        "url" -> evt.eventType == "pageview" && evt.path == matchValue
                        "event" -> evt.eventType == "custom" && evt.eventName == matchValue
                        else -> false
                    }
                }

                if (matchingEvent != null) {
                    val matchTime = matchingEvent.timestamp
                    newQualified[sessionId] = matchTime

                    // Calculate time from previous step (skip for first step)
                    if (index > 0 && afterTimestamp != LocalDateTime.MIN) {
                        val diff = Duration.between(afterTimestamp, matchTime).seconds.toDouble()
                        totalTimeDiff += diff
                        timeDiffCount++
                    }
                }
            }

            val sessionsAtStep = newQualified.size.toLong()
            val previousStepSessions = if (index == 0) totalSessions else qualifiedSessions.size.toLong()

            // Share of the sessions that entered the funnel (reached step 1), so step 1 is always 100%
            if (index == 0) enteredSessions = sessionsAtStep
            val conversionRate = if (enteredSessions > 0) {
                (sessionsAtStep.toDouble() / enteredSessions) * 100.0
            } else 0.0

            val dropOffRate = if (previousStepSessions > 0) {
                ((previousStepSessions - sessionsAtStep).toDouble() / previousStepSessions) * 100.0
            } else 0.0

            val avgTime = if (timeDiffCount > 0) totalTimeDiff / timeDiffCount else null

            stepAnalyses.add(
                FunnelStepAnalysis(
                    stepNumber = stepNumber,
                    name = step[FunnelSteps.name],
                    sessions = sessionsAtStep,
                    conversionRate = conversionRate,
                    dropOffRate = dropOffRate,
                    avgTimeFromPrevious = avgTime
                )
            )

            qualifiedSessions = newQualified
        }

        FunnelAnalysis(
            funnel = funnelResponse,
            totalSessions = totalSessions,
            steps = stepAnalyses
        )
    }
}

/**
 * The database condition for one segment filter. Text is compared case-insensitively, and an event whose field
 * is empty never matches (not even "not equals").
 */
private fun segmentCondition(filter: SegmentFilter): Op<Boolean> {
    val column: Column<out String?> = when (filter.field) {
        "browser" -> Events.browser
        "os" -> Events.os
        "device" -> Events.device
        "country" -> Events.country
        "city" -> Events.city
        "path" -> Events.path
        "referrer" -> Events.referrer
        "eventType" -> Events.eventType
        else -> return Op.FALSE
    }
    val value = filter.value.lowercase()
    // LIKE wildcards in the value are plain characters
    val literal = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    val text = column.lowerCase()
    val matches: Op<Boolean> = when (filter.operator) {
        "equals" -> text eq value
        "not_equals" -> text neq value
        "contains" -> text like LikePattern("%$literal%", '\\')
        "starts_with" -> text like LikePattern("$literal%", '\\')
        else -> return Op.FALSE
    }
    return column.isNotNull() and matches
}

/** All filters of a segment as one condition; each filter's logic joins it to the next, evaluated left to right. */
private fun segmentCondition(filters: List<SegmentFilter>): Op<Boolean> {
    if (filters.isEmpty()) return Op.TRUE
    var result = segmentCondition(filters[0])
    for (i in 1 until filters.size) {
        val next = segmentCondition(filters[i])
        result = if (filters[i - 1].logic.uppercase() == "OR") result or next else result and next
    }
    return result
}

/**
 * Analyze a segment: views, visitors, bounce rate and top pages of the events that match its filters.
 * The database does the matching and counting; no event rows are loaded.
 */
fun analyzeSegment(
    segmentId: UUID,
    segmentName: String,
    projectId: UUID,
    filters: List<SegmentFilter>,
    start: LocalDateTime,
    end: LocalDateTime
): SegmentAnalysis {
    val matching: Op<Boolean> = (Events.projectId eq projectId) and
        (Events.timestamp greaterEq start) and
        (Events.timestamp lessEq end) and
        segmentCondition(filters)

    return transaction {
        val views = Events.id.count()
        val visitors = Events.visitorHash.countDistinct()
        val totals = Events.select(views, visitors).where { matching }.single()

        // Ties keep the order in which a page first appeared
        val pageViews = Events.id.count()
        val firstSeen = Events.id.min()
        val topPages = Events.select(Events.path, pageViews, firstSeen)
            .where { matching }
            .groupBy(Events.path)
            .orderBy(pageViews to SortOrder.DESC, firstSeen to SortOrder.ASC)
            .limit(10)
            .map { StatEntry(it[Events.path], it[pageViews]) }

        // One row per session: how many pages it saw and whether it sent a heartbeat
        val pages = Events.path.countDistinct()
        val heartbeats = Sum(Case().When(Events.eventType eq "heartbeat", intLiteral(1)).Else(intLiteral(0)), IntegerColumnType())
        var sessions = 0L
        var bounced = 0L
        Events.select(Events.sessionId, pages, heartbeats)
            .where { matching }
            .groupBy(Events.sessionId)
            .forEach {
                sessions++
                if (it[pages] == 1L && (it[heartbeats] ?: 0) == 0) bounced++
            }
        val bounceRate = if (sessions > 0) (bounced.toDouble() / sessions) * 100.0 else 0.0

        SegmentAnalysis(
            segmentId = segmentId.toString(),
            segmentName = segmentName,
            totalViews = totals[views],
            uniqueVisitors = totals[visitors],
            bounceRate = bounceRate,
            topPages = topPages,
            matchingEvents = totals[views]
        )
    }
}

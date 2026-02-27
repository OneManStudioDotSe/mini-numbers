package se.onemanstudio

import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.db.ConversionGoals
import se.onemanstudio.db.Events
import se.onemanstudio.db.FunnelSteps
import se.onemanstudio.db.Funnels
import se.onemanstudio.api.models.GoalResponse
import se.onemanstudio.api.models.GoalStats
import se.onemanstudio.api.models.FunnelAnalysis
import se.onemanstudio.api.models.FunnelResponse
import se.onemanstudio.api.models.FunnelStepAnalysis
import se.onemanstudio.api.models.FunnelStepResponse
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/**
 * Calculate conversions for a single goal within a time period.
 * Returns pair of (conversion count, conversion rate as percentage).
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
                createdAt = goal[ConversionGoals.createdAt].toString()
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
            createdAt = funnel[Funnels.createdAt].toString()
        )

        if (steps.isEmpty()) {
            return@transaction FunnelAnalysis(
                funnel = funnelResponse,
                totalSessions = 0,
                steps = emptyList()
            )
        }

        // Load all events for this project in the time range
        val events = Events.selectAll().where {
            (Events.projectId eq projectId) and
            (Events.timestamp greaterEq start) and
            (Events.timestamp lessEq end)
        }.toList()

        // Group events by session, sorted by timestamp within each session
        val sessionEvents = events.groupBy { it[Events.sessionId] }
            .mapValues { (_, evts) -> evts.sortedBy { it[Events.timestamp] } }

        val totalSessions = sessionEvents.size.toLong()

        // Track which sessions make it through each step
        // For each session, find the timestamp of the event that matched the step
        var qualifiedSessions: Map<String, LocalDateTime> = sessionEvents.keys.associateWith {
            LocalDateTime.MIN // No previous step timestamp constraint for step 1
        }

        val stepAnalyses = mutableListOf<FunnelStepAnalysis>()

        for ((index, step) in steps.withIndex()) {
            val stepType = step[FunnelSteps.stepType]
            val matchValue = step[FunnelSteps.matchValue]
            val stepNumber = step[FunnelSteps.stepNumber]

            // For each qualified session, check if it has a matching event AFTER the previous step's event
            val newQualified = mutableMapOf<String, LocalDateTime>()
            var totalTimeDiff = 0.0
            var timeDiffCount = 0

            for ((sessionId, afterTimestamp) in qualifiedSessions) {
                val sessionEvts = sessionEvents[sessionId] ?: continue

                // Find the first matching event that occurs after the previous step's timestamp
                val matchingEvent = sessionEvts.firstOrNull { evt ->
                    val eventTime = evt[Events.timestamp]
                    val isAfterPrevious = if (index == 0) true else eventTime.isAfter(afterTimestamp)

                    isAfterPrevious && when (stepType) {
                        "url" -> evt[Events.eventType] == "pageview" && evt[Events.path] == matchValue
                        "event" -> evt[Events.eventType] == "custom" && evt[Events.eventName] == matchValue
                        else -> false
                    }
                }

                if (matchingEvent != null) {
                    val matchTime = matchingEvent[Events.timestamp]
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
            val previousStepSessions = qualifiedSessions.size.toLong()

            val conversionRate = if (totalSessions > 0) {
                (sessionsAtStep.toDouble() / totalSessions) * 100.0
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

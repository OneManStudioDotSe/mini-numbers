package se.onemanstudio.routing

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.api.models.admin.SegmentFilter
import se.onemanstudio.api.models.dashboard.*
import se.onemanstudio.core.AnalyticsSecurity
import se.onemanstudio.db.*
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.roundToInt

fun safeParseUUID(value: String?): UUID? {
    if (value == null) return null
    return try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * Apply segment filters to an event row
 */
fun applySegmentFilters(event: ResultRow, filters: List<SegmentFilter>): Boolean {
    if (filters.isEmpty()) return true

    var result = matchesFilter(event, filters[0])

    for (i in 1 until filters.size) {
        val logic = filters[i - 1].logic.uppercase()
        val matches = matchesFilter(event, filters[i])

        result = if (logic == "OR") result || matches else result && matches
    }

    return result
}

fun matchesFilter(event: ResultRow, filter: SegmentFilter): Boolean {
    val fieldValue = when (filter.field) {
        "browser" -> event[Events.browser]
        "os" -> event[Events.os]
        "device" -> event[Events.device]
        "country" -> event[Events.country]
        "city" -> event[Events.city]
        "path" -> event[Events.path]
        "referrer" -> event[Events.referrer]
        "eventType" -> event[Events.eventType]
        else -> null
    } ?: return false

    return when (filter.operator) {
        "equals" -> fieldValue.equals(filter.value, ignoreCase = true)
        "not_equals" -> !fieldValue.equals(filter.value, ignoreCase = true)
        "contains" -> fieldValue.contains(filter.value, ignoreCase = true)
        "starts_with" -> fieldValue.startsWith(filter.value, ignoreCase = true)
        else -> false
    }
}

/**
 * Compute aggregated globe visualization data.
 * Groups visitors by rounded lat/lon (1 decimal place, ~11km) for clustering.
 */
fun computeGlobeData(projectId: UUID, cutoff: LocalDateTime): GlobeData {
    return transaction {
        val events = Events.selectAll().where {
            (Events.projectId eq projectId) and
                    (Events.timestamp greaterEq cutoff) and
                    (Events.latitude.isNotNull()) and
                    (Events.longitude.isNotNull())
        }.toList()

        // Round coordinates to 1 decimal place for clustering
        data class GeoKey(val lat: Double, val lng: Double)

        val grouped = events.groupBy { row ->
            GeoKey(
                (row[Events.latitude]!! * 10.0).roundToInt() / 10.0,
                (row[Events.longitude]!! * 10.0).roundToInt() / 10.0
            )
        }

        val visitors = grouped.map { (key, rows) ->
            val mostRecent = rows.maxByOrNull { it[Events.timestamp] }
            GlobeVisitor(
                lat = key.lat,
                lng = key.lng,
                city = mostRecent?.get(Events.city),
                country = mostRecent?.get(Events.country),
                count = rows.size.toLong(),
                lastSeen = (mostRecent?.get(Events.timestamp) ?: LocalDateTime.now()).toString()
            )
        }.sortedByDescending { it.count }

        val totalActive = events.map { it[Events.visitorHash] }.distinct().size.toLong()

        GlobeData(visitors = visitors, totalActive = totalActive)
    }
}

/**
 * Generate realistic demo/dummy data for testing
 */
fun generateDemoData(projectId: UUID, count: Int, timeScope: Int = 30): Int {
    if (count == 0) return 0

    val paths = listOf(
        "/", "/about", "/contact", "/blog", "/products", "/services",
        "/blog/getting-started", "/blog/tutorial", "/blog/announcement",
        "/products/item-1", "/products/item-2", "/products/item-3",
        "/pricing", "/faq", "/docs", "/docs/api", "/docs/guide"
    )

    val referrers = listOf(
        null, "https://google.com/search", "https://twitter.com",
        "https://facebook.com", "https://github.com", "https://reddit.com",
        "https://linkedin.com", "https://news.ycombinator.com"
    )

    val customEventNames = listOf(
        "signup", "download", "purchase", "newsletter_subscribe", "share",
        "contact_form", "add_to_cart", "video_play", "search", "scroll_depth"
    )
    val browsers = listOf("Chrome", "Firefox", "Safari", "Edge", "Opera")
    val oses = listOf("Windows", "macOS", "Linux", "iOS", "Android")
    val devices = listOf("Desktop", "Mobile", "Tablet")

    val countries = listOf("United States", "United Kingdom", "Canada", "Germany", "France", "Spain", "Italy", "Australia", "Japan", "Brazil")
    val cities = mapOf(
        "United States" to listOf("New York", "Los Angeles", "Chicago", "Houston", "Phoenix"),
        "United Kingdom" to listOf("London", "Manchester", "Birmingham", "Leeds", "Glasgow"),
        "Canada" to listOf("Toronto", "Montreal", "Vancouver", "Calgary", "Ottawa"),
        "Germany" to listOf("Berlin", "Munich", "Hamburg", "Frankfurt", "Cologne"),
        "France" to listOf("Paris", "Marseille", "Lyon", "Toulouse", "Nice"),
        "Spain" to listOf("Madrid", "Barcelona", "Valencia", "Seville", "Zaragoza"),
        "Italy" to listOf("Rome", "Milan", "Naples", "Turin", "Florence"),
        "Australia" to listOf("Sydney", "Melbourne", "Brisbane", "Perth", "Adelaide"),
        "Japan" to listOf("Tokyo", "Osaka", "Yokohama", "Nagoya", "Sapporo"),
        "Brazil" to listOf("São Paulo", "Rio de Janeiro", "Brasília", "Salvador", "Fortaleza")
    )
    val regions = mapOf(
        "United States" to listOf("California", "New York", "Texas", "Florida", "Illinois"),
        "United Kingdom" to listOf("England", "Scotland", "Wales"),
        "Canada" to listOf("Ontario", "Quebec", "British Columbia", "Alberta"),
        "Germany" to listOf("Bavaria", "North Rhine-Westphalia", "Berlin", "Hamburg"),
        "France" to listOf("Île-de-France", "Provence-Alpes-Côte d'Azur", "Auvergne-Rhône-Alpes"),
        "Spain" to listOf("Catalonia", "Madrid", "Andalusia", "Valencia"),
        "Italy" to listOf("Lombardy", "Lazio", "Campania", "Tuscany"),
        "Australia" to listOf("New South Wales", "Victoria", "Queensland", "Western Australia"),
        "Japan" to listOf("Tokyo", "Osaka", "Kanagawa", "Aichi"),
        "Brazil" to listOf("São Paulo", "Rio de Janeiro", "Minas Gerais", "Bahia")
    )
    val cityCoordinates = mapOf(
        "New York" to (40.7128 to -74.0060), "Los Angeles" to (34.0522 to -118.2437),
        "Chicago" to (41.8781 to -87.6298), "Houston" to (29.7604 to -95.3698),
        "Phoenix" to (33.4484 to -112.0740), "London" to (51.5074 to -0.1278),
        "Manchester" to (53.4808 to -2.2426), "Birmingham" to (52.4862 to -1.8904),
        "Leeds" to (53.8008 to -1.5491), "Glasgow" to (55.8642 to -4.2518),
        "Toronto" to (43.6532 to -79.3832), "Montreal" to (45.5017 to -73.5673),
        "Vancouver" to (49.2827 to -123.1207), "Calgary" to (51.0447 to -114.0719),
        "Ottawa" to (45.4215 to -75.6972), "Berlin" to (52.5200 to 13.4050),
        "Munich" to (48.1351 to 11.5820), "Hamburg" to (53.5511 to 9.9937),
        "Frankfurt" to (50.1109 to 8.6821), "Cologne" to (50.9375 to 6.9603),
        "Paris" to (48.8566 to 2.3522), "Marseille" to (43.2965 to 5.3698),
        "Lyon" to (45.7640 to 4.8357), "Toulouse" to (43.6047 to 1.4442),
        "Nice" to (43.7102 to 7.2620), "Madrid" to (40.4168 to -3.7038),
        "Barcelona" to (41.3874 to 2.1686), "Valencia" to (39.4699 to -0.3763),
        "Seville" to (37.3891 to -5.9845), "Zaragoza" to (41.6488 to -0.8891),
        "Rome" to (41.9028 to 12.4964), "Milan" to (45.4642 to 9.1900),
        "Naples" to (40.8518 to 14.2681), "Turin" to (45.0703 to 7.6869),
        "Florence" to (43.7696 to 11.2558), "Sydney" to (-33.8688 to 151.2093),
        "Melbourne" to (-37.8136 to 144.9631), "Brisbane" to (-27.4698 to 153.0251),
        "Perth" to (-31.9505 to 115.8605), "Adelaide" to (-34.9285 to 138.6007),
        "Tokyo" to (35.6762 to 139.6503), "Osaka" to (34.6937 to 135.5023),
        "Yokohama" to (35.4437 to 139.6380), "Nagoya" to (35.1815 to 136.9066),
        "Sapporo" to (43.0618 to 141.3545), "São Paulo" to (-23.5505 to -46.6333),
        "Rio de Janeiro" to (-22.9068 to -43.1729), "Brasília" to (-15.8267 to -47.9218),
        "Salvador" to (-12.9714 to -38.5124), "Fortaleza" to (-3.7172 to -38.5433)
    )
    val utmSources = listOf(null, null, null, "google", "facebook", "twitter", "newsletter", "linkedin")
    val utmMediums = listOf(null, null, null, "cpc", "social", "email", "organic", "referral")
    val utmCampaigns = listOf(null, null, null, "spring_sale", "product_launch", "brand_awareness", "retargeting")
    val outboundUrls = listOf(
        "https://github.com/example", "https://docs.example.com", "https://twitter.com/share",
        "https://linkedin.com/post", "https://medium.com/article"
    )
    val downloadUrls = listOf(
        "https://example.com/files/report.pdf", "https://example.com/files/data.csv",
        "https://example.com/files/sdk.zip", "https://example.com/files/guide.docx"
    )

    val random = java.util.Random()
    val now = LocalDateTime.now()

    var inserted = 0
    var remaining = count

    transaction {
        while (remaining > 0) {
            val daysAgo = random.nextInt(timeScope)
            val sessionStart = now.minusDays(daysAgo.toLong())
                .minusHours(random.nextInt(24).toLong())
                .minusMinutes(random.nextInt(60).toLong())

            val country = countries.random()
            val city = cities[country]?.random()
            val sessionId = UUID.randomUUID().toString()
            val browser = browsers.random()
            val os = oses.random()
            val device = devices.random()
            val referrer = referrers.random()

            val visitorHash = AnalyticsSecurity.generateVisitorHash(
                ip = "192.168.${random.nextInt(255)}.${random.nextInt(255)}",
                userAgent = browser,
                projectId = projectId.toString()
            )

            val isBounce = random.nextDouble() < 0.4
            val sessionEvents = if (isBounce) 1 else minOf(2 + random.nextInt(4), remaining)

            var currentTimestamp = sessionStart
            val firstPath = paths.random()

            for (eventIdx in 0 until sessionEvents) {
                if (remaining <= 0) break

                val isFirstEvent = eventIdx == 0
                val eventType: String
                val path: String
                val customEventName: String?

                if (isFirstEvent) {
                    eventType = "pageview"
                    path = firstPath
                    customEventName = null
                } else if (!isBounce && random.nextDouble() < 0.25) {
                    eventType = "custom"
                    path = firstPath
                    customEventName = customEventNames.random()
                } else if (!isBounce && random.nextDouble() < 0.4) {
                    eventType = "pageview"
                    path = paths.filter { it != firstPath }.random()
                    customEventName = null
                } else {
                    eventType = "heartbeat"
                    path = firstPath
                    customEventName = null
                }

                val coords = city?.let { cityCoordinates[it] }
                Events.insert {
                    it[Events.projectId] = projectId
                    it[Events.visitorHash] = visitorHash
                    it[Events.sessionId] = sessionId
                    it[Events.eventType] = eventType
                    it[Events.eventName] = customEventName
                    it[Events.path] = path
                    it[Events.referrer] = if (isFirstEvent) referrer else null
                    it[Events.country] = country
                    it[Events.city] = city
                    it[Events.browser] = browser
                    it[Events.os] = os
                    it[Events.device] = device
                    it[Events.duration] = if (eventType == "heartbeat") 30 else 0
                    it[Events.timestamp] = currentTimestamp
                    it[Events.region] = regions[country]?.random()
                    it[Events.latitude] = coords?.first
                    it[Events.longitude] = coords?.second
                    if (eventType == "pageview" && isFirstEvent) {
                        it[Events.utmSource] = utmSources.random()
                        it[Events.utmMedium] = utmMediums.random()
                        it[Events.utmCampaign] = utmCampaigns.random()
                    }
                    if (eventType == "custom") {
                        val evName = customEventName ?: ""
                        when {
                            evName == "purchase" -> {
                                val rev = listOf(9.99, 19.99, 29.99, 49.99, 99.99, 149.99).random()
                                it[Events.properties] = """{"revenue":$rev,"currency":"USD","product":"${listOf("Starter","Pro","Enterprise","Add-on").random()}"}"""
                            }
                            evName == "add_to_cart" && random.nextDouble() < 0.3 -> {
                                val rev = listOf(9.99, 19.99, 29.99, 49.99).random()
                                it[Events.properties] = """{"revenue":$rev,"currency":"USD","item":"${listOf("Widget","Plugin","Theme").random()}"}"""
                            }
                            random.nextDouble() < 0.3 -> {
                                it[Events.properties] = """{"plan":"${listOf("free","pro","enterprise").random()}","value":"${random.nextInt(100)}"}"""
                            }
                        }
                    }
                }

                if (eventType == "pageview" && random.nextDouble() < 0.6) {
                    val scrollDepths = listOf(25, 50, 75, 100)
                    val maxScroll = scrollDepths[random.nextInt(scrollDepths.size)]
                    for (depth in scrollDepths) {
                        if (depth > maxScroll || remaining <= 0) break
                        Events.insert {
                            it[Events.projectId] = projectId
                            it[Events.visitorHash] = visitorHash
                            it[Events.sessionId] = sessionId
                            it[Events.eventType] = "scroll"
                            it[Events.path] = path
                            it[Events.country] = country
                            it[Events.city] = city
                            it[Events.browser] = browser
                            it[Events.os] = os
                            it[Events.device] = device
                            it[Events.scrollDepth] = depth
                            it[Events.region] = regions[country]?.random()
                            it[Events.latitude] = coords?.first
                            it[Events.longitude] = coords?.second
                            it[Events.timestamp] = currentTimestamp.plusSeconds(depth.toLong())
                        }
                        inserted++
                        remaining--
                    }
                }

                if (!isFirstEvent && random.nextDouble() < 0.1 && remaining > 0) {
                    val isDownload = random.nextBoolean()
                    Events.insert {
                        it[Events.projectId] = projectId
                        it[Events.visitorHash] = visitorHash
                        it[Events.sessionId] = sessionId
                        it[Events.eventType] = if (isDownload) "download" else "outbound"
                        it[Events.eventName] = if (isDownload) "report.pdf" else "github.com"
                        it[Events.path] = path
                        it[Events.country] = country
                        it[Events.city] = city
                        it[Events.browser] = browser
                        it[Events.os] = os
                        it[Events.device] = device
                        it[Events.targetUrl] = if (isDownload) downloadUrls.random() else outboundUrls.random()
                        it[Events.region] = regions[country]?.random()
                        it[Events.latitude] = coords?.first
                        it[Events.longitude] = coords?.second
                        it[Events.timestamp] = currentTimestamp.plusSeconds(5)
                    }
                    inserted++
                    remaining--
                }

                currentTimestamp = currentTimestamp.plusSeconds(30)
                inserted++
                remaining--
            }
        }
    }
    return inserted
}

/**
 * Seed demo conversion goals, funnels, and segments for a project.
 */
fun seedDemoGoalsFunnelsSegments(projectId: UUID) {
    val hasGoals = ConversionGoals.selectAll().where { ConversionGoals.projectId eq projectId }.count() > 0
    val hasFunnels = Funnels.selectAll().where { Funnels.projectId eq projectId }.count() > 0
    val hasSegments = Segments.selectAll().where { Segments.projectId eq projectId }.count() > 0

    if (!hasGoals) {
        val goals = listOf(
            Triple("Signups", "event", "signup"),
            Triple("Purchases", "event", "purchase"),
            Triple("Newsletter subscribers", "event", "newsletter_subscribe"),
            Triple("Pricing page visits", "url", "/pricing"),
            Triple("Blog readers", "url", "/blog"),
        )
        for ((name, type, matchValue) in goals) {
            ConversionGoals.insert {
                it[id] = UUID.randomUUID()
                it[ConversionGoals.projectId] = projectId
                it[ConversionGoals.name] = name
                it[goalType] = type
                it[ConversionGoals.matchValue] = matchValue
            }
        }
    }

    if (!hasFunnels) {
        val purchaseFunnelId = UUID.randomUUID()
        Funnels.insert {
            it[id] = purchaseFunnelId
            it[Funnels.projectId] = projectId
            it[name] = "Product purchase"
        }
        val purchaseSteps = listOf(
            Triple(1, "View products", "/products" to "url"),
            Triple(2, "View product detail", "/products/item-1" to "url"),
            Triple(3, "Add to cart", "add_to_cart" to "event"),
            Triple(4, "Complete purchase", "purchase" to "event"),
        )
        for ((order, stepName, matchPair) in purchaseSteps) {
            FunnelSteps.insert {
                it[id] = UUID.randomUUID()
                it[funnelId] = purchaseFunnelId
                it[stepNumber] = order
                it[name] = stepName
                it[stepType] = matchPair.second
                it[matchValue] = matchPair.first
            }
        }
    }

    if (!hasSegments) {
        val segments = listOf(
            Triple(
                "Mobile visitors",
                "Visitors using mobile devices",
                """[{"field":"device","operator":"equals","value":"Mobile","logic":"AND"}]"""
            ),
            Triple(
                "US traffic",
                "Visitors from the United States",
                """[{"field":"country","operator":"equals","value":"United States","logic":"AND"}]"""
            ),
            Triple(
                "Chrome desktop users",
                "Desktop visitors using Chrome",
                """[{"field":"browser","operator":"equals","value":"Chrome","logic":"AND"},{"field":"device","operator":"equals","value":"Desktop","logic":"AND"}]"""
            ),
        )
        for ((name, description, filtersJson) in segments) {
            Segments.insert {
                it[id] = UUID.randomUUID()
                it[Segments.projectId] = projectId
                it[Segments.name] = name
                it[Segments.description] = description
                it[Segments.filtersJson] = filtersJson
            }
        }
    }
}

package se.onemanstudio.core

import io.ktor.server.application.*
import io.ktor.server.request.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import se.onemanstudio.db.Projects
import java.util.UUID

/**
 * Resolve an API key from query param or header to a project UUID.
 * Used by widget endpoints for stateless, key-based authentication.
 * Returns null if the key is missing, does not match any project, or the project has not
 * turned its widgets on: the key is public (it is in the site's HTML), so it must not be
 * enough by itself to read a project's numbers.
 */
fun resolveWidgetProject(call: ApplicationCall): UUID? {
    val apiKey = call.request.queryParameters["key"]
        ?: call.request.headers["X-Widget-Key"]
        ?: return null

    return transaction {
        Projects.selectAll()
            .where { (Projects.apiKey eq apiKey) and (Projects.widgetsEnabled eq true) }
            .singleOrNull()
            ?.get(Projects.id)
    }
}

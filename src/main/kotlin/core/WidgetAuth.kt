package se.onemanstudio.core

import io.ktor.server.application.*
import io.ktor.server.request.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.db.Projects
import java.util.UUID

/**
 * Resolve an API key from query param or header to a project UUID.
 * Used by widget endpoints for stateless, key-based authentication.
 * Returns null if the key is missing or does not match any project.
 */
fun resolveWidgetProject(call: ApplicationCall): UUID? {
    val apiKey = call.request.queryParameters["key"]
        ?: call.request.headers["X-Widget-Key"]
        ?: return null

    return transaction {
        Projects.selectAll()
            .where { Projects.apiKey eq apiKey }
            .singleOrNull()
            ?.get(Projects.id)
    }
}

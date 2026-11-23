package se.onemanstudio.services

import java.net.InetAddress
import java.net.URI

/**
 * Which webhook addresses Mini Numbers will call.
 *
 * Link-local addresses (169.254.0.0/16 and fe80::/10, where cloud metadata services live) are
 * refused, checked when a webhook is saved and again before every delivery. Loopback and private
 * ranges stay allowed on purpose: self-hosters run receivers on their own network. Only admins
 * can create webhooks. A host name is resolved at the check, so a name that later resolves to a
 * different address is not caught between the check and the connection.
 */
object WebhookAddress {
    const val LINK_LOCAL_MESSAGE = "Webhook URL must not point at a link-local address (169.254.0.0/16, fe80::/10)"

    /** True when [url]'s host is, or resolves to, a link-local address. An unresolvable host is not blocked here. */
    fun isLinkLocal(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return false
        val addresses = runCatching { InetAddress.getAllByName(host) }.getOrNull() ?: return false
        return addresses.any { it.isLinkLocalAddress }
    }
}

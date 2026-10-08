package se.onemanstudio.utils

import java.net.URI

/**
 * Classifies referrers that come from AI assistants (ChatGPT, Claude, Perplexity, …).
 *
 * Only the referral side is covered: a JavaScript tracker never sees AI crawlers, because
 * they do not execute scripts. Matching is by host, including subdomains, on the stored
 * referrer URL; nothing is stored in addition to what the tracker already sends.
 */
object AiTraffic {

    private val assistants: List<Pair<String, List<String>>> = listOf(
        "ChatGPT" to listOf("chatgpt.com", "chat.openai.com", "openai.com"),
        "Claude" to listOf("claude.ai"),
        "Perplexity" to listOf("perplexity.ai"),
        "Gemini" to listOf("gemini.google.com", "bard.google.com"),
        "Copilot" to listOf("copilot.microsoft.com"),
        "Grok" to listOf("grok.com", "x.ai"),
        "Meta AI" to listOf("meta.ai"),
        "Mistral" to listOf("chat.mistral.ai", "mistral.ai"),
        "DeepSeek" to listOf("chat.deepseek.com", "deepseek.com"),
        "You.com" to listOf("you.com"),
        "Poe" to listOf("poe.com"),
        "Phind" to listOf("phind.com"),
        "Duck.ai" to listOf("duck.ai"),
    )

    /** The assistant name for a referrer URL, or null when it is not an AI assistant. */
    fun assistantFor(referrer: String?): String? {
        val host = hostOf(referrer) ?: return null
        return assistants.firstOrNull { (_, domains) ->
            domains.any { domain -> host == domain || host.endsWith(".$domain") }
        }?.first
    }

    private fun hostOf(referrer: String?): String? {
        if (referrer.isNullOrBlank()) return null
        val withScheme = if (referrer.contains("://")) referrer else "https://$referrer"
        val host = runCatching { URI(withScheme).host }.getOrNull() ?: return null
        return host.lowercase().removePrefix("www.")
    }
}

package se.onemanstudio.analytics

import org.junit.Test
import se.onemanstudio.utils.AiTraffic
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AiTrafficTest {

    @Test
    fun `recognises the major assistants by host, with and without www, with paths`() {
        assertEquals("ChatGPT", AiTraffic.assistantFor("https://chatgpt.com/c/123"))
        assertEquals("ChatGPT", AiTraffic.assistantFor("https://chat.openai.com/"))
        assertEquals("Claude", AiTraffic.assistantFor("https://claude.ai/chat/abc"))
        assertEquals("Perplexity", AiTraffic.assistantFor("https://www.perplexity.ai/search?q=x"))
        assertEquals("Gemini", AiTraffic.assistantFor("https://gemini.google.com/app"))
        assertEquals("Copilot", AiTraffic.assistantFor("https://copilot.microsoft.com/"))
        assertEquals("Grok", AiTraffic.assistantFor("https://grok.com/"))
    }

    @Test
    fun `matches subdomains but not look-alike hosts`() {
        assertEquals("Perplexity", AiTraffic.assistantFor("https://labs.perplexity.ai/"))
        assertNull(AiTraffic.assistantFor("https://notperplexity.ai/"))
        assertNull(AiTraffic.assistantFor("https://claude.ai.example.com/"))
    }

    @Test
    fun `ignores ordinary referrers, blanks and garbage`() {
        assertNull(AiTraffic.assistantFor("https://www.google.com/search?q=x"))
        assertNull(AiTraffic.assistantFor("https://news.ycombinator.com/"))
        assertNull(AiTraffic.assistantFor(null))
        assertNull(AiTraffic.assistantFor(""))
        assertNull(AiTraffic.assistantFor("not a url at all ://"))
    }

    @Test
    fun `accepts a bare host without scheme`() {
        assertEquals("ChatGPT", AiTraffic.assistantFor("chatgpt.com"))
    }
}

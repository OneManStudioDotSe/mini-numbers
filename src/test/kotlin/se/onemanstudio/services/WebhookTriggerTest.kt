package se.onemanstudio.services

import org.junit.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebhookTriggerTest {
    @Test
    fun `a goal fires once per session, like the conversion stats count it`() {
        val goal = UUID.randomUUID()
        assertTrue(WebhookTrigger.firstConversionInSession(goal, "session-a"))
        assertFalse(WebhookTrigger.firstConversionInSession(goal, "session-a"), "same session again")
        assertTrue(WebhookTrigger.firstConversionInSession(goal, "session-b"), "another visitor")
        assertTrue(WebhookTrigger.firstConversionInSession(UUID.randomUUID(), "session-a"), "another goal")
    }
}

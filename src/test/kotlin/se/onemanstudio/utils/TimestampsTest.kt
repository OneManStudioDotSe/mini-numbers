package se.onemanstudio.utils

import org.junit.Test
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

class TimestampsTest {
    @Test
    fun `api string carries the server offset and round-trips to the stored wall-clock time`() {
        val stored = LocalDateTime.of(2026, 11, 3, 2, 24, 52, 145_000_000)
        val sent = stored.toApiString()
        val parsed = OffsetDateTime.parse(sent)
        assertEquals(stored, parsed.toLocalDateTime(), sent)
        assertEquals(ZoneId.systemDefault().rules.getOffset(stored), parsed.offset, sent)
    }
}

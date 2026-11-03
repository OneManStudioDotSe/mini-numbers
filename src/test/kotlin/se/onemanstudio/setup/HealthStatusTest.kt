package se.onemanstudio.setup

import io.ktor.http.*
import org.junit.Test
import se.onemanstudio.core.ServiceManager.State
import kotlin.test.assertEquals

class HealthStatusTest {
    @Test
    fun `failed services are 503`() =
        assertEquals(HttpStatusCode.ServiceUnavailable to "unhealthy", healthStatus(State.ERROR, null))

    @Test
    fun `an unreachable database is 503 even when services say ready`() =
        assertEquals(HttpStatusCode.ServiceUnavailable to "unhealthy", healthStatus(State.READY, false))

    @Test
    fun `ready with a reachable database is healthy`() =
        assertEquals(HttpStatusCode.OK to "healthy", healthStatus(State.READY, true))

    @Test
    fun `setup mode answers ok so the wizard can be reached`() =
        assertEquals(HttpStatusCode.OK to "ok", healthStatus(State.UNINITIALIZED, null))
}

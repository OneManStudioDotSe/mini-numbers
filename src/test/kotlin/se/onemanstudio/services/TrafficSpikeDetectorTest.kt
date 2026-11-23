package se.onemanstudio.services

import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spike rule with known counts and a fake clock; see TrafficSpikeDetector for the rule itself. */
class TrafficSpikeDetectorTest {
    private val minute = 60_000L
    private var now = 0L
    private lateinit var detector: TrafficSpikeDetector

    @Before
    fun start() {
        now = 1_000_000 * minute
        detector = TrafficSpikeDetector { now }
    }

    /** Move the clock on, then record [count] visits for [key] and return whatever fired. */
    private fun visits(count: Int, minutesLater: Long = 0, key: String = "p"): List<TrafficSpikeDetector.Spike> {
        now += minutesLater * minute
        return (1..count).mapNotNull { detector.record(key) }
    }

    /** 70 minutes of one visit a minute: the detector is warmed up and the usual window holds 10 visits. */
    private fun steadyHour() {
        repeat(70) { visits(1, 1) }
    }

    @Test
    fun `a burst well above the previous hour fires once with the counts`() {
        steadyHour()
        val fired = visits(40, 1)

        assertEquals(1, fired.size, "fires once, then the cooldown holds")
        assertTrue(fired.single().windowVisits >= 20, fired.single().toString())
        assertTrue(fired.single().baselinePerWindow in 9.0..11.0, fired.single().toString())
    }

    @Test
    fun `ordinary traffic and small bumps do not fire`() {
        steadyHour()
        assertTrue(visits(1, 1).isEmpty(), "the normal rate")
        assertTrue(visits(5, 1).isEmpty(), "a little more than normal")
    }

    @Test
    fun `a jump on a nearly empty site needs at least twenty visits`() {
        repeat(70) { visits(0, 1) } // an hour of nothing
        assertTrue(visits(19, 1).isEmpty(), "19 visits from zero is not a spike")
        assertEquals(1, visits(1).size, "the twentieth is")
    }

    @Test
    fun `nothing fires during the first hour after start, when there is nothing to compare with`() {
        assertTrue(visits(100, 1).isEmpty())
        repeat(30) { visits(1, 1) }
        assertTrue(visits(100, 1).isEmpty())
    }

    @Test
    fun `a spike fires at most once per ten minutes and again after that while it lasts`() {
        steadyHour()
        assertEquals(1, visits(40, 1).size)
        assertTrue(visits(40, 5).isEmpty(), "five minutes after the first")
        assertTrue(visits(40, 4).isEmpty(), "nine minutes after the first")
        assertEquals(1, visits(60, 2).size, "eleven minutes after, traffic still far above normal")
    }

    @Test
    fun `projects are counted and held back separately`() {
        steadyHour()
        assertEquals(1, visits(40, 1).size)
        assertEquals(1, visits(25, 0, key = "other").size, "the other project is not in p's cooldown")
        assertTrue(visits(10, 0, key = "quiet").isEmpty(), "and a third one stays quiet")
    }
}

@file:OptIn(ExperimentalCoroutinesApi::class)

package challenges.flow

import challenges.flow.f06_backpressure.LatLng
import challenges.flow.f06_backpressure.RiderLocationFeed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class F06RiderLocationFeedTest {

    private val fixes = 20

    private fun point(i: Int) = LatLng(12.9 + i * 0.001, 77.5)

    /** Emits a fix every 100ms and records when each one was actually emitted. */
    private class FakeGps(private val scope: TestScope, private val count: Int, private val point: (Int) -> LatLng) {
        val emitTimes = mutableListOf<Long>()

        val flow: Flow<LatLng> = flow {
            repeat(count) { i ->
                emitTimes += scope.currentTime
                emit(point(i))
                delay(100)
            }
        }
    }

    private fun expectedSchedule() = List(fixes) { it * 100L }

    @Test
    fun `map - GPS is never slowed down`() = runTest {
        val gps = FakeGps(this, fixes, ::point)

        RiderLocationFeed(gps.flow).showOnMap { delay(300) }

        assertEquals(expectedSchedule(), gps.emitTimes, "The slow map renderer delayed the GPS source")
    }

    @Test
    fun `map - renders the newest position, every started render finishes, ends up at the final position`() = runTest {
        val gps = FakeGps(this, fixes, ::point)
        val started = mutableListOf<LatLng>()
        val finished = mutableListOf<LatLng>()
        var lastFinishTime = 0L

        RiderLocationFeed(gps.flow).showOnMap { p ->
            started += p
            delay(300)
            finished += p
            lastFinishTime = currentTime
        }

        assertEquals(started, finished, "Some renders were cancelled halfway (half-drawn animation)")
        assertEquals(point(fixes - 1), finished.last(), "The marker never reached the final position")
        assertTrue(finished.size < fixes, "Rendered all $fixes points one by one, so the marker lags behind")
        assertTrue(lastFinishTime <= 2_500, "Marker reached the final position at ${lastFinishTime}ms. GPS stopped at ~2000ms.")
    }

    @Test
    fun `route - GPS is never slowed down`() = runTest {
        val gps = FakeGps(this, fixes, ::point)

        RiderLocationFeed(gps.flow).recordRoute { delay(150) }

        assertEquals(expectedSchedule(), gps.emitTimes, "The slow DB writes delayed the GPS source")
    }

    @Test
    fun `route - every point recorded, in order`() = runTest {
        val gps = FakeGps(this, fixes, ::point)
        val recorded = mutableListOf<LatLng>()

        RiderLocationFeed(gps.flow).recordRoute { p ->
            delay(150)
            recorded += p
        }

        assertEquals(List(fixes) { point(it) }, recorded, "Analytics lost or reordered points")
    }
}

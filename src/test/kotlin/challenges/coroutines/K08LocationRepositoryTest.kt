@file:OptIn(ExperimentalCoroutinesApi::class)

package challenges.coroutines

import challenges.coroutines.k08_location.CancellationHandle
import challenges.coroutines.k08_location.Location
import challenges.coroutines.k08_location.LocationCallback
import challenges.coroutines.k08_location.LocationClient
import challenges.coroutines.k08_location.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class K08LocationRepositoryTest {

    /** A fake SDK the test controls by hand: it fires callbacks when the test says so. */
    private class FakeLocationClient : LocationClient {
        var callback: LocationCallback? = null
        var gpsOn = false
        var requests = 0

        override fun requestSingleUpdate(callback: LocationCallback): CancellationHandle {
            this.callback = callback
            gpsOn = true
            requests++
            return CancellationHandle { gpsOn = false }
        }

        fun deliver(location: Location) {
            callback!!.onLocation(location)
        }

        fun fail(error: Exception) {
            callback!!.onError(error)
        }
    }

    private class GpsDisabledException : Exception("GPS is disabled")

    /**
     * The "screen" runs on the test's virtual clock but is NOT a child of the test,
     * so a coroutine that can't be cancelled fails its assertion instead of hanging the test runner.
     */
    private fun TestScope.screenScope() = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))

    private val bangalore = Location(12.97, 77.59)

    @Test
    fun `returns the location`() = runTest {
        val sdk = FakeLocationClient()
        val result = screenScope().async { LocationRepository(sdk).currentLocation() }
        runCurrent()

        sdk.deliver(bangalore)

        assertEquals(bangalore, result.await())
    }

    @Test
    fun `throws the SDK error`() = runTest {
        val sdk = FakeLocationClient()
        val result = screenScope().async { LocationRepository(sdk).currentLocation() }
        runCurrent()
        val error = GpsDisabledException()

        sdk.fail(error)

        val thrown = assertFailsWith<GpsDisabledException> { result.await() }
        assertSame(error, thrown)
    }

    @Test
    fun `user leaves the screen - GPS is turned off and the coroutine stops immediately`() = runTest {
        val sdk = FakeLocationClient()
        val screen = screenScope().launch { LocationRepository(sdk).currentLocation() }
        runCurrent()
        assertTrue(sdk.gpsOn)

        screen.cancel()
        runCurrent()

        assertFalse(sdk.gpsOn, "GPS is still on after the caller was cancelled. Battery drain!")
        assertTrue(screen.isCompleted, "The coroutine is still stuck waiting for the SDK")
    }

    @Test
    fun `SDK bug - onLocation fires twice - no crash, first location wins`() = runTest {
        val sdk = FakeLocationClient()
        val result = screenScope().async { LocationRepository(sdk).currentLocation() }
        runCurrent()

        sdk.deliver(bangalore)
        sdk.deliver(Location(0.0, 0.0)) // must not throw "Already resumed"

        assertEquals(bangalore, result.await())
    }

    @Test
    fun `SDK bug - callback after cancellation - no crash`() = runTest {
        val sdk = FakeLocationClient()
        val screen = screenScope().launch { LocationRepository(sdk).currentLocation() }
        runCurrent()
        screen.cancel()
        runCurrent()

        sdk.deliver(bangalore) // late callback: must not throw
        sdk.fail(GpsDisabledException())
    }

    @Test
    fun `orNull - timeout returns null and turns GPS off`() = runTest {
        val sdk = FakeLocationClient()
        val result = screenScope().async { LocationRepository(sdk).currentLocationOrNull(3_000) }

        advanceTimeBy(3_001)

        assertTrue(result.isCompleted, "Still waiting after the 3s timeout")
        assertNull(result.await())
        assertFalse(sdk.gpsOn, "GPS left on after the timeout")
    }

    @Test
    fun `orNull - SDK error is thrown, not turned into null`() = runTest {
        val sdk = FakeLocationClient()
        val result = screenScope().async { LocationRepository(sdk).currentLocationOrNull(3_000) }
        runCurrent()

        sdk.fail(GpsDisabledException())

        assertFailsWith<GpsDisabledException>("'Please enable GPS' must be shown, not the default city") { result.await() }
    }

    @Test
    fun `orNull - caller cancellation is not turned into null`() = runTest {
        val sdk = FakeLocationClient()
        var continuedWith: String? = null
        val screen = screenScope().launch {
            val location = LocationRepository(sdk).currentLocationOrNull(3_000)
            continuedWith = "showing results for $location" // must never run after cancellation
        }
        runCurrent()

        screen.cancel()
        runCurrent()

        assertNull(continuedWith, "Code kept running after the screen was cancelled: '$continuedWith'")
        assertFalse(sdk.gpsOn)
    }
}

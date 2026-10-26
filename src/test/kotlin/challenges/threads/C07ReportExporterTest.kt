package challenges.threads

import challenges.runConcurrently
import challenges.threads.c07_reportexport.ReportExporter
import challenges.threads.c07_reportexport.ReportSection
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Timeout(60, unit = TimeUnit.SECONDS)
class C07ReportExporterTest {

    private fun exporterThreads(): Int =
        Thread.getAllStackTraces().keys.count { it.isAlive && it.name.startsWith(ReportExporter.THREAD_PREFIX) }

    @Test
    fun `small report renders correctly`() {
        ReportExporter().use { exporter ->
            val report = exporter.export(listOf(ReportSection("Sales", 2), ReportSection("Refunds", 1)))
            assertEquals(
                listOf("Sales: Sales-chart-1, Sales-chart-2", "Refunds: Refunds-chart-1"),
                report.sections
            )
        }
    }

    @Test
    fun `big restaurant chain - many sections - export finishes`() {
        ReportExporter().use { exporter ->
            val sections = List(6) { ReportSection("Branch-$it", 4) }
            runConcurrently(threadCount = 1, timeoutMs = 5_000, namePrefix = "export-request") {
                assertEquals(6, exporter.export(sections).sections.size)
            }
        }
    }

    @Test
    fun `no thread leak - all threads gone after close`() {
        val before = exporterThreads()
        val exporter = ReportExporter()
        repeat(20) { exporter.export(listOf(ReportSection("Daily", 1))) }
        exporter.close()

        val deadline = System.currentTimeMillis() + 2_000
        while (exporterThreads() > before && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(
            before, exporterThreads(),
            "${exporterThreads() - before} exporter threads still alive 2s after close()"
        )
    }

    @Test
    fun `thread count stays bounded under load`() {
        val before = exporterThreads()
        val peak = AtomicInteger()
        val done = AtomicBoolean(false)
        val sampler = thread(isDaemon = true) {
            while (!done.get()) {
                peak.accumulateAndGet(exporterThreads() - before, ::maxOf)
                Thread.sleep(5)
            }
        }

        ReportExporter().use { exporter ->
            runConcurrently(threadCount = 3, timeoutMs = 15_000, namePrefix = "export-request") {
                exporter.export(List(8) { ReportSection("S$it", 8) })
            }
        }
        done.set(true)
        sampler.join()

        assertTrue(peak.get() <= 8, "Exporter had ${peak.get()} threads alive at once (limit is 8)")
    }

    @Test
    fun `charts inside a section still render in parallel`() {
        ReportExporter().use { exporter ->
            exporter.export(listOf(ReportSection("warmup", 2)))

            val start = System.nanoTime()
            exporter.export(listOf(ReportSection("Menu", 8)))
            val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

            val sequentialMs = 8 * ReportExporter.CHART_RENDER_MS
            assertTrue(tookMs < sequentialMs * 0.75, "8 charts took ${tookMs}ms, which looks sequential (${sequentialMs}ms)")
        }
    }

    @Test
    fun `export after close is rejected`() {
        val exporter = ReportExporter()
        exporter.export(listOf(ReportSection("Daily", 1)))
        exporter.close()

        assertFailsWith<IllegalStateException> { exporter.export(listOf(ReportSection("Daily", 1))) }
    }
}

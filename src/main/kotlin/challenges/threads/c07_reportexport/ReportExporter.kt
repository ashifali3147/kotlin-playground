package challenges.threads.c07_reportexport

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

class ReportSection(val title: String, val chartCount: Int)

class Report(val sections: List<String>)

/**
 * Builds the weekly sales PDF for restaurant partners. It runs inside the partner-dashboard
 * backend, and every "Export" tap calls [export]. Sections render in parallel, and so do the
 * charts inside each section.
 */
class ReportExporter : AutoCloseable {

    fun export(sections: List<ReportSection>): Report {
        val pool = Executors.newFixedThreadPool(4, namedThreads("report-export"))
        val futures = sections.map { section ->
            pool.submit<String> { renderSection(pool, section) }
        }
        return Report(futures.map { it.get() })
    }

    private fun renderSection(pool: ExecutorService, section: ReportSection): String {
        val charts = (1..section.chartCount).map { n ->
            pool.submit<String> { renderChart(section, n) }
        }
        return section.title + ": " + charts.joinToString { it.get() }
    }

    private fun renderChart(section: ReportSection, n: Int): String {
        Thread.sleep(CHART_RENDER_MS) // rasterize the chart
        return "${section.title}-chart-$n"
    }

    override fun close() {
        // Nothing to clean up: pools are created per export.
    }

    companion object {
        const val CHART_RENDER_MS = 100L
        const val THREAD_PREFIX = "report-"

        /** Every thread this class creates must have a name starting with [THREAD_PREFIX]. */
        fun namedThreads(name: String): ThreadFactory {
            val counter = AtomicInteger()
            return ThreadFactory { task -> Thread(task, "$THREAD_PREFIX$name-${counter.incrementAndGet()}") }
        }
    }
}

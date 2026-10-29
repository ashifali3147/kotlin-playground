// Dojo test reporter: replaces Gradle's raw stack-trace dump with a compact pass/fail view.
//
//   ./gradlew test                      -> one line per test, summary at the end
//   ./gradlew test -Pdojo.details       -> also why each failure happened, where, and what it printed
//   ./gradlew test -Pdojo.color=false   -> no ANSI colours
//
// The ./dojo script wraps this and also hides Gradle's own "BUILD FAILED" noise.

import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.TestResult
import java.util.concurrent.ConcurrentHashMap

val details = providers.gradleProperty("dojo.details").map { it != "false" }.getOrElse(false)
val color = providers.gradleProperty("dojo.color").map { it != "false" }.getOrElse(System.getenv("NO_COLOR") == null)
val markerFile = layout.buildDirectory.file("dojo/last-run.txt")

class Style(private val on: Boolean) {
    private fun wrap(code: String, s: String) = if (on) "\u001B[${code}m$s\u001B[0m" else s
    fun green(s: String) = wrap("32", s)
    fun red(s: String) = wrap("31", s)
    fun yellow(s: String) = wrap("33", s)
    fun cyan(s: String) = wrap("36", s)
    fun bold(s: String) = wrap("1", s)
    fun dim(s: String) = wrap("2", s)
    fun boldGreen(s: String) = wrap("1;32", s)
    fun boldRed(s: String) = wrap("1;31", s)
}

class DojoReporter(
    private val details: Boolean,
    private val st: Style,
    private val marker: java.io.File,
) : TestListener, TestOutputListener {

    private val output = ConcurrentHashMap<TestDescriptor, StringBuilder>()
    private val failedTests = mutableListOf<String>()
    private val shownFailures = mutableSetOf<String>()
    private var lastHeader: String? = null
    private var passed = 0
    private var failed = 0
    private var skipped = 0

    override fun beforeSuite(suite: TestDescriptor) {
        if (suite.parent == null) {
            println()
        } else if (suite.className != null && suite.className != lastHeader) {
            lastHeader = suite.className
            println()
            println("  " + st.bold(challengeTitle(suite.className!!)))
        }
    }

    override fun beforeTest(test: TestDescriptor) {}

    override fun onOutput(test: TestDescriptor, event: TestOutputEvent) {
        output.getOrPut(test) { StringBuilder() }.append(event.message)
    }

    override fun afterTest(test: TestDescriptor, result: TestResult) {
        val name = testName(test)
        val ms = result.endTime - result.startTime
        val time = st.dim(if (ms >= 1000) "%.1fs".format(ms / 1000.0) else "${ms}ms")
        when (result.resultType) {
            TestResult.ResultType.SUCCESS -> {
                passed++
                println("    ${st.green("✔")} $name  $time")
            }
            TestResult.ResultType.FAILURE -> {
                failed++
                failedTests += "${test.className?.substringAfterLast('.')?.take(3)}: $name"
                println("    ${st.red("✘")} ${st.red(name)}  $time")
                if (details) printDetails(test, result)
            }
            else -> {
                skipped++
                println("    ${st.yellow("○")} ${st.dim(name)}  ${st.dim("skipped")}")
            }
        }
        if (details && result.resultType == TestResult.ResultType.SUCCESS) printOutput(test)
    }

    override fun afterSuite(suite: TestDescriptor, result: TestResult) {
        if (suite.parent != null) return
        val total = passed + failed + skipped
        if (total == 0) return
        val seconds = "%.1fs".format((result.endTime - result.startTime) / 1000.0)
        val bar = st.dim("  " + "─".repeat(56))
        println()
        println(bar)
        val counts = buildList {
            add(st.boldGreen("$passed passed"))
            if (failed > 0) add(st.boldRed("$failed failed"))
            if (skipped > 0) add(st.yellow("$skipped skipped"))
        }.joinToString(st.dim("  ·  "))
        println("  $counts   ${st.dim("($total tests in $seconds)")}")
        if (failed == 0) {
            println("  " + st.boldGreen("All green.") + st.dim(" Journal it, then ask Claude for a review."))
        } else if (!details) {
            println("  " + st.dim("Why did it fail?  ") + st.cyan(detailsHint()))
        }
        println(bar)
        println()
        marker.parentFile.mkdirs()
        marker.writeText("passed=$passed\nfailed=$failed\nskipped=$skipped\n" + failedTests.joinToString("\n"))
    }

    // @RepeatedTest reports "repetition 2 of 3"; show it as "<real name> (2/3)".
    private fun testName(test: TestDescriptor): String {
        val rep = Regex("""^repetition (\d+) of (\d+)$""").find(test.displayName)
        val parent = test.parent?.displayName?.removeSuffix("()")
        return if (rep != null && parent != null) "$parent (${rep.groupValues[1]}/${rep.groupValues[2]})"
        else test.displayName.removeSuffix("()")
    }

    private fun detailsHint() =
        if (System.getenv("DOJO_SCRIPT") != null) "add -d" else "re-run with -Pdojo.details"

    private fun printDetails(test: TestDescriptor, result: TestResult) {
        val error = result.exceptions.firstOrNull() ?: return
        val pad = "        "
        val message = cleanThreadDump(error.message?.trim().orEmpty().ifEmpty { error.javaClass.simpleName })

        // Repetitions of the same failing test usually fail identically; print it once.
        val key = "${test.parent?.displayName}|" + message.replace(Regex("""@[0-9a-f]+|Id=\d+"""), "")
        if (!shownFailures.add(key)) {
            println(pad + st.dim("same failure as above"))
            return
        }
        val parsed = Regex("""(?s)^(.*?)\s*==> expected: <(.*)> but was: <(.*)>$""").find(message)
        if (parsed != null) {
            val (why, expected, actual) = parsed.destructured
            if (why.isNotBlank()) why.lines().forEach { println(pad + it) }
            println(pad + st.dim("expected ") + st.green(expected))
            println(pad + st.dim("  actual ") + st.red(actual))
        } else {
            message.lines().forEach { println(pad + it) }
        }
        if (error !is AssertionError) println(pad + st.dim("(${error.javaClass.name})"))

        // Only frames from this repo's challenge code: the test line and the line in your solution.
        val frames = generateSequence(error) { it.cause }.flatMap { it.stackTrace.asSequence() }
            .filter { it.className.startsWith("challenges.") && it.lineNumber > 0 }
            .map { "${it.fileName}:${it.lineNumber}" }
            .distinct()
            .take(4)
            .toList()
        if (frames.isNotEmpty()) println(pad + st.dim("at ") + st.cyan(frames.joinToString(st.dim(" ← "))))

        generateSequence(error.cause) { it.cause }.take(3).forEach {
            println(pad + st.dim("caused by ") + "${it.javaClass.simpleName}: ${it.message}")
        }
        printOutput(test)
        println()
    }

    // Thread dumps from TestSupport: keep thread headers, lock lines and frames in this repo's code.
    private fun cleanThreadDump(message: String): String {
        if ("Thread dump" !in message) return message
        val noise = listOf("\$\$Lambda", "TestSupportKt", "kotlin.concurrent.ThreadsKt", "Unknown Source")
        return message.lines()
            .filterNot { line -> noise.any { it in line } }
            .map { it.replace("\t", "  ").replace("app//", "").replace(Regex("""^"(.+?)" daemon prio=\d+ Id=\d+"""), "\"$1\"") }
            .joinToString("\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trimEnd()
    }

    private fun printOutput(test: TestDescriptor) {
        val text = output[test]?.toString()?.trimEnd().orEmpty()
        if (text.isEmpty()) return
        println("        " + st.dim("┌ printed:"))
        text.lines().forEach { println("        " + st.dim("│ ") + it) }
    }

    // "challenges.coroutines.K01HomeRepositoryTest" -> "K01 · HomeRepository". The threads track
    // is T01..T08 in the docs but C01..C08 in test names.
    private fun challengeTitle(className: String): String {
        val simple = className.substringAfterLast('.').removeSuffix("Test")
        val m = Regex("""^([A-Z])(\d{2})(.*)$""").find(simple) ?: return simple
        val (letter, num, rest) = m.destructured
        val id = (if (letter == "C") "T" else letter) + num
        return "$id · $rest"
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events = emptySet()
        showExceptions = false
        showStackTraces = false
        showCauses = false
        showStandardStreams = false
    }
    val reporter = DojoReporter(details, Style(color), markerFile.get().asFile)
    addTestListener(reporter)
    addTestOutputListener(reporter)
}

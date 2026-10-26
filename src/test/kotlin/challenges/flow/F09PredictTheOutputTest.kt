package challenges.flow

import challenges.flow.f09_predict.FlowPredictions
import challenges.flow.f09_predict.FlowPuzzles
import challenges.flow.f09_predict.FlowPuzzles.puzzle1
import challenges.flow.f09_predict.FlowPuzzles.puzzle2
import challenges.flow.f09_predict.FlowPuzzles.puzzle3
import challenges.flow.f09_predict.FlowPuzzles.puzzle4
import challenges.flow.f09_predict.FlowPuzzles.puzzle5
import challenges.flow.f09_predict.FlowPuzzles.puzzle6
import challenges.flow.f09_predict.FlowPuzzles.puzzle7
import challenges.flow.f09_predict.FlowPuzzles.puzzle8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.fail

class F09PredictTheOutputTest {

    private fun check(name: String, prediction: List<String>, puzzle: suspend CoroutineScope.((String) -> Unit) -> Unit) {
        if (prediction.isEmpty()) fail("$name: no prediction yet. Fill it in FlowPredictions.kt")

        val actual = mutableListOf<String>()
        runTest { with(FlowPuzzles) { puzzle { actual += it } } }

        if (actual != prediction) {
            if (FlowPredictions.REVEAL) {
                fail("$name: wrong.\n  you predicted: $prediction\n  actual output: $actual")
            } else {
                fail("$name: wrong. Think again (set REVEAL = true only after an honest best effort on all 8)")
            }
        }
    }

    @Test fun `puzzle 1`() = check("puzzle1", FlowPredictions.puzzle1) { puzzle1(it) }
    @Test fun `puzzle 2`() = check("puzzle2", FlowPredictions.puzzle2) { puzzle2(it) }
    @Test fun `puzzle 3`() = check("puzzle3", FlowPredictions.puzzle3) { puzzle3(it) }
    @Test fun `puzzle 4`() = check("puzzle4", FlowPredictions.puzzle4) { puzzle4(it) }
    @Test fun `puzzle 5`() = check("puzzle5", FlowPredictions.puzzle5) { puzzle5(it) }
    @Test fun `puzzle 6`() = check("puzzle6", FlowPredictions.puzzle6) { puzzle6(it) }
    @Test fun `puzzle 7`() = check("puzzle7", FlowPredictions.puzzle7) { puzzle7(it) }
    @Test fun `puzzle 8`() = check("puzzle8", FlowPredictions.puzzle8) { puzzle8(it) }
}

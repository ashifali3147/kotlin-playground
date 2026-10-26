package challenges.coroutines

import challenges.coroutines.k10_predict.Predictions
import challenges.coroutines.k10_predict.Puzzles
import challenges.coroutines.k10_predict.Puzzles.puzzle1
import challenges.coroutines.k10_predict.Puzzles.puzzle2
import challenges.coroutines.k10_predict.Puzzles.puzzle3
import challenges.coroutines.k10_predict.Puzzles.puzzle4
import challenges.coroutines.k10_predict.Puzzles.puzzle5
import challenges.coroutines.k10_predict.Puzzles.puzzle6
import challenges.coroutines.k10_predict.Puzzles.puzzle7
import challenges.coroutines.k10_predict.Puzzles.puzzle8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.fail

class K10PredictTheOutputTest {

    private fun check(name: String, prediction: List<String>, puzzle: suspend CoroutineScope.((String) -> Unit) -> Unit) {
        if (prediction.isEmpty()) fail("$name: no prediction yet. Fill it in Predictions.kt")

        val actual = mutableListOf<String>()
        runBlocking { with(Puzzles) { puzzle { actual += it } } }

        if (actual != prediction) {
            if (Predictions.REVEAL) {
                fail("$name: wrong.\n  you predicted: $prediction\n  actual output: $actual")
            } else {
                fail("$name: wrong. Think again (set REVEAL = true only after an honest best effort on all 8)")
            }
        }
    }

    @Test fun `puzzle 1`() = check("puzzle1", Predictions.puzzle1) { puzzle1(it) }
    @Test fun `puzzle 2`() = check("puzzle2", Predictions.puzzle2) { puzzle2(it) }
    @Test fun `puzzle 3`() = check("puzzle3", Predictions.puzzle3) { puzzle3(it) }
    @Test fun `puzzle 4`() = check("puzzle4", Predictions.puzzle4) { puzzle4(it) }
    @Test fun `puzzle 5`() = check("puzzle5", Predictions.puzzle5) { puzzle5(it) }
    @Test fun `puzzle 6`() = check("puzzle6", Predictions.puzzle6) { puzzle6(it) }
    @Test fun `puzzle 7`() = check("puzzle7", Predictions.puzzle7) { puzzle7(it) }
    @Test fun `puzzle 8`() = check("puzzle8", Predictions.puzzle8) { puzzle8(it) }
}

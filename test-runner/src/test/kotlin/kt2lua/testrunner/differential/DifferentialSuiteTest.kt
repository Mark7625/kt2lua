package kt2lua.testrunner.differential

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import kotlin.test.fail

private const val REGRESSION_CATEGORY = "regression"

/**
 * Differential tests: every fixture under src/test/resources/differential-fixtures is run two
 * ways — compiled and executed for real on the JVM with the Kotlin compiler, and transpiled with
 * kt2lua then executed by kt2lua's own Lua interpreter — and their captured output is compared.
 * The JVM run is the reference; a mismatch means the generated Lua doesn't behave like the Kotlin
 * it came from.
 *
 * Fixtures under the "regression" category are confirmed, tracked transpiler bugs: a mismatch
 * there is expected and the test passes (the bug is still present), but if one of them ever starts
 * *matching*, that's reported as a failure telling you to move it out of regression/ — the fix
 * landed and the fixture now belongs in its real category as ordinary coverage.
 *
 * Filter by category: `./gradlew differentialTest -Pcategory=collections`
 */
@Tag("differential")
class DifferentialSuiteTest {
    @TestFactory
    fun `differential fixtures`(): List<DynamicTest> {
        val category = System.getProperty("kt2lua.differential.category")
        val cases = DifferentialFixtures.discover(category)
        check(cases.isNotEmpty()) { "No differential fixtures found" + (category?.let { " for category '$it'" } ?: "") }

        val kotlinResults = KotlinBatchExecutor().use { it.run(cases) }

        return cases.map { case ->
            dynamicTest(case.id) {
                val kotlinResult = kotlinResults.getValue(case.id).result
                val luaRun = LuaFixtureExecutor.run(case.source)
                val outcome = ResultComparator.compare(kotlinResult, luaRun.result)
                val isRegression = case.category == REGRESSION_CATEGORY
                when {
                    outcome is ComparisonOutcome.Match && isRegression -> fail(
                        "Regression fixture '${case.id}' now matches Kotlin behavior - the bug looks fixed. " +
                            "Move this fixture out of differential-fixtures/regression/ into its real category.",
                    )
                    outcome is ComparisonOutcome.Mismatch && !isRegression -> fail(
                        buildString {
                            appendLine("DIFFERENTIAL TEST FAILED: ${case.id}")
                            appendLine(outcome.detail)
                            appendLine("generated lua:")
                            appendLine(luaRun.generatedLua)
                        },
                    )
                    else -> {} // Match on ordinary coverage, or the expected Mismatch on a tracked regression.
                }
            }
        }
    }
}

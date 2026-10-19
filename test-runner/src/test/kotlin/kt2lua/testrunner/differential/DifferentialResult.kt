package kt2lua.testrunner.differential

sealed class DifferentialResult {
    data class Success(val output: String) : DifferentialResult()
    data class CompileFailure(val diagnostics: String) : DifferentialResult()
    data class RuntimeFailure(val exceptionType: String, val message: String?) : DifferentialResult()
    data class TimedOut(val timeoutMs: Long) : DifferentialResult()

    fun describe(): String = when (this) {
        is Success -> "output:\n$output"
        is CompileFailure -> "compile failure:\n$diagnostics"
        is RuntimeFailure -> "runtime failure: $exceptionType: ${message ?: "<no message>"}"
        is TimedOut -> "timed out after ${timeoutMs}ms"
    }
}

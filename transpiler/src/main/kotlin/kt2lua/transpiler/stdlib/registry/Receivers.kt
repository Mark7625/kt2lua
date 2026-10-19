package kt2lua.transpiler.stdlib.registry

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Receivers(vararg val kinds: String)

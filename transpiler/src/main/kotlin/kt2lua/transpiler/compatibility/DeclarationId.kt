package kt2lua.transpiler.compatibility

data class DeclarationId(
    val packageName: String,
    val receiver: String?,
    val name: String,
    val parameters: List<String> = emptyList(),
    val isProperty: Boolean = false,
) {
    override fun toString(): String {
        val owner = receiver?.let { "$it." } ?: ""
        val params = if (isProperty) "" else "(${parameters.joinToString(", ")})"
        return "$packageName.$owner$name$params"
    }

    companion object {
        fun parse(text: String): DeclarationId {
            val parenIdx = text.indexOf('(')
            val isProperty = parenIdx < 0
            val head = if (isProperty) text else text.substring(0, parenIdx)
            val parameters = if (isProperty) {
                emptyList()
            } else {
                require(text.endsWith(")")) { "Malformed declaration id (missing closing paren): $text" }
                val inside = text.substring(parenIdx + 1, text.length - 1)
                if (inside.isBlank()) emptyList() else splitTopLevelCommas(inside)
            }
            val segments = head.split('.')
            require(segments.size >= 2) { "Malformed declaration id (need at least a package and a name): $text" }
            val name = segments.last()
            val withoutName = segments.dropLast(1)
            val receiverSegments = withoutName.takeLastWhile { it.isNotEmpty() && it[0].isUpperCase() }
            val receiver = receiverSegments.takeIf { it.isNotEmpty() }?.joinToString(".")
            val packageName = withoutName.subList(0, withoutName.size - receiverSegments.size).joinToString(".")
            return DeclarationId(packageName, receiver, name, parameters, isProperty)
        }

        private fun splitTopLevelCommas(text: String): List<String> {
            val parts = mutableListOf<String>()
            var depth = 0
            var start = 0
            for (i in text.indices) {
                when {
                    text[i] == '(' || text[i] == '<' -> depth++
                    text[i] == ')' -> depth--
                    text[i] == '>' && (i == 0 || text[i - 1] != '-') -> depth--
                    text[i] == ',' && depth == 0 -> {
                        parts.add(text.substring(start, i).trim())
                        start = i + 1
                    }
                }
            }
            parts.add(text.substring(start).trim())
            return parts
        }
    }
}

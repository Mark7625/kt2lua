package kt2lua.transpiler

class LexException(val rawMessage: String, val line: Int, val col: Int) :
    RuntimeException("$rawMessage (line $line, col $col)")

class ParseException(val rawMessage: String, val line: Int, val col: Int) :
    RuntimeException("$rawMessage (line $line, col $col)")

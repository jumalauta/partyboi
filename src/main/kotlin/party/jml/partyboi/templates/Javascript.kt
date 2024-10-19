package party.jml.partyboi.templates

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Javascript {
    fun scope(block: Javascript.() -> Unit): Statement {
        val js = Javascript()
        with(js) { block() }
        return Statements(js.stack)
    }
    fun goto(url: String) = push(JAssign("location", url))
    fun httpGet(url: String) = push(JCall("await fetch", JStr(url), JObj("method" to JStr("GET"))))
    fun httpPut(url: String) = push(JCall("await fetch", JStr(url), JObj("method" to JStr("PUT"))))
    fun httpPost(url: String) = push(JCall("await fetch", JStr(url), JObj("method" to JStr("POST"))))
    fun httpDelete(url: String) = push(JCall("await fetch", JStr(url), JObj("method" to JStr("DELETE"))))
    fun refresh() = push(JCall("smoothReload"))
    fun confirm(message: String, onTrue: Javascript.() -> Unit) = push(JIf(JCall("confirm", JStr(message)), scope(onTrue) ))

    interface Statement {
        fun toJS(): String
    }

    data class JStr(val text: String) : Statement {
        override fun toJS(): String = Json.encodeToString(text)
    }

    data class JObj(val props: Map<String, Statement> = emptyMap()) : Statement {
        constructor(vararg pairs: Pair<String, Statement>) : this(mapOf(*pairs))
        override fun toJS(): String =
            props
                .toList()
                .joinToString(
                    prefix = "{",
                    separator = ",",
                    postfix = "}"
                ) { JStr(it.first).toJS() + ":" + it.second.toJS() }
    }

    data class JAssign(val varName: String, val value: Statement) : Statement {
        constructor(varName: String, value: String) : this(varName, JStr(value))
        override fun toJS(): String = "$varName=${value.toJS()}"
    }

    data class JCall(val name: String, val args: List<Statement> = emptyList()) : Statement {
        constructor(name: String, vararg args: Statement) : this(name, listOf(*args))
        override fun toJS(): String = "$name(${args.joinToString(separator = ", ") { it.toJS() }})"
    }

    data class JIf(val condition: Statement, val onTrue: Statement) : Statement {
        override fun toJS(): String = "if (${condition.toJS()}) { ${onTrue.toJS()} }"
    }

    data class Statements(val statements: List<Statement>) : Statement {
        override fun toJS(): String = statements.joinToString(separator = "; ") { it.toJS() }
    }

    private val stack = mutableListOf<Statement>()
    private fun push(s: Statement) { stack.add(s) }

    companion object {
        fun build(block: Javascript.() -> Unit): String {
            val js = Javascript()
            val code = js.scope(block).toJS()
            return "(async ()=>{$code})()"
        }
    }
}
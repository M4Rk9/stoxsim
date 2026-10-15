package com.stoxsim.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.commonmark.ext.gfm.tables.*
import org.commonmark.node.*
import org.commonmark.parser.Parser

/** Native selectable text: no HTML execution, WebView, remote image loading or automatic links. */
@Composable internal fun FinwizAnswer(source: String) {
    val answer = remember(source) { formatFinwizAnswer(source) }
    Text(answer, style = MaterialTheme.typography.bodyLarge)
}

private val answerParser = Parser.builder().extensions(listOf(TablesExtension.create())).build()

internal fun formatFinwizAnswer(source: String): AnnotatedString {
    val output = AnnotatedString.Builder()
    fun gap() {
        if (output.length > 0 && !output.toString().endsWith("\n\n")) {
            if (!output.toString().endsWith("\n")) output.append("\n")
            output.append("\n")
        }
    }
    fun children(node: Node): List<Node> = generateSequence(node.firstChild) { it.next }.toList()
    fun render(node: Node, depth: Int = 0) {
        // Malformed/deep model output must not exhaust the UI thread's stack.
        if (depth > 64) return
        fun contents() { children(node).forEach { render(it, depth + 1) } }
        fun styled(style: SpanStyle) { output.pushStyle(style); contents(); output.pop() }
        when (node) {
            is org.commonmark.node.Text -> output.append(node.literal)
            is Heading -> { gap(); styled(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = if (node.level <= 2) 21.sp else 18.sp)); gap() }
            is Paragraph -> { contents(); if (node.parent !is ListItem) gap() }
            is StrongEmphasis -> styled(SpanStyle(fontWeight = FontWeight.Bold))
            is Emphasis -> styled(SpanStyle(fontStyle = FontStyle.Italic))
            is Code -> { output.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace)); output.append(node.literal); output.pop() }
            is FencedCodeBlock -> { gap(); output.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace)); output.append(node.literal.trimEnd()); output.pop(); gap() }
            is IndentedCodeBlock -> { gap(); output.append(node.literal.trimEnd()); gap() }
            is BulletList, is OrderedList -> {
                gap()
                children(node).forEachIndexed { index, item ->
                    output.append(if (node is OrderedList) "${node.startNumber + index}. " else "• ")
                    render(item, depth + 1)
                    if (!output.toString().endsWith("\n")) output.append("\n")
                }
                gap()
            }
            is SoftLineBreak -> output.append(" ")
            is HardLineBreak -> output.append("\n")
            is ThematicBreak -> gap()
            is BlockQuote -> { gap(); contents(); gap() }
            is Link -> contents() // Retain the readable label; never activate model-supplied URLs.
            is Image -> contents() // Alt text only; no external fetch.
            is HtmlInline, is HtmlBlock -> Unit
            is TableBlock -> {
                gap()
                val sections = children(node)
                val header = sections.filterIsInstance<TableHead>().flatMap(::children).firstOrNull()?.let(::children).orEmpty()
                // Stack table cells with their column headings so a phone never clips wide tables.
                sections.filterIsInstance<TableBody>().flatMap(::children).forEach { row ->
                    children(row).forEachIndexed { index, cell ->
                        header.getOrNull(index)?.let { output.pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold)); render(it, depth + 1); output.pop(); output.append(": ") }
                        render(cell, depth + 1); output.append("\n")
                    }
                    gap()
                }
            }
            else -> contents()
        }
    }
    render(answerParser.parse(readableFinwizMath(source)))
    val result = output.toAnnotatedString()
    return result.subSequence(0, result.text.trimEnd().length)
}

/** Convert the common LaTeX wrappers seen in existing answers; ordinary currency is untouched. */
internal fun readableFinwizMath(source: String): String {
    fun formula(value: String): String {
        var index = 0
        fun expression(group: Boolean = false): String {
            val out = StringBuilder()
            fun argument(): String {
                while (index < value.length && value[index].isWhitespace()) index++
                return if (index < value.length && value[index] == '{') { index++; expression(true) } else ""
            }
            while (index < value.length) {
                val char = value[index++]
                when {
                    char == '}' && group -> return out.toString()
                    char == '{' -> out.append(expression(true))
                    char == '\\' -> {
                        val start = index
                        while (index < value.length && value[index].isLetter()) index++
                        when (val command = value.substring(start, index)) {
                            "text", "textrm", "mathrm", "mathbf", "operatorname" -> out.append(argument())
                            "frac", "dfrac", "tfrac" -> { val top = argument(); val bottom = argument(); out.append("($top) / ($bottom)") }
                            "sqrt" -> out.append("√(").append(argument()).append(')')
                            "times", "cdot" -> out.append("×")
                            "div" -> out.append("÷")
                            "le", "leq" -> out.append("≤")
                            "ge", "geq" -> out.append("≥")
                            "approx" -> out.append("≈")
                            "left", "right" -> Unit
                            "" -> if (index < value.length) out.append(value[index++])
                            else -> out.append(command)
                        }
                    }
                    else -> out.append(char)
                }
            }
            return out.toString()
        }
        return expression().trim()
    }
    // Bound deeply nested math separately from Markdown; retain unfamiliar input instead of crashing.
    val math = Regex("\\$\\$([\\s\\S]*?)\\$\\$|\\\\\\[([\\s\\S]*?)\\\\\\]|\\\\\\(([\\s\\S]*?)\\\\\\)")
    return math.replace(source) { match ->
        val value = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty()
        if (value.count { it == '{' } > 64) value else formula(value)
    }
}

package com.stoxsim.app.ui

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.*
import org.junit.Test

class FinwizAnswerTest {
    @Test fun screenshotStyleAnswerRendersHeadingsEmphasisAndLists() {
        val answer = formatFinwizAnswer("### 2. Why It Matters\n\nRead **Company A** and *its earnings*.\n\n---\n\n* **Stock Price:** $100\n* **P/E Ratio:** 15x")
        assertEquals("2. Why It Matters\n\nRead Company A and its earnings.\n\n• Stock Price: $100\n• P/E Ratio: 15x", answer.text)
        assertTrue(answer.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertFalse(answer.text.contains("###"))
    }
    @Test fun formulasBecomeReadableWithoutDamagingCurrencyOrMultiplication() {
        val source = "${'$'}${'$'}\\text{Market Cap} = \\text{Total Shares} \\times \\text{Current Price}${'$'}${'$'}\n\n" +
            "${'$'}${'$'}\\text{P/E} = \\frac{\\text{Stock Price}}{\\text{EPS}}${'$'}${'$'}\n\nShares cost $50; earnings $2.50; 2 * 3 = 6."
        val text = formatFinwizAnswer(source).text
        assertTrue(text.contains("Market Cap = Total Shares × Current Price"))
        assertTrue(text.contains("P/E = (Stock Price) / (EPS)"))
        assertTrue(text.contains("Shares cost $50; earnings $2.50; 2 * 3 = 6."))
        assertFalse(text.contains("\\text"))
    }
    @Test fun numberedListsCodeAndNestedFractionsKeepTheirMeaning() {
        val answer = formatFinwizAnswer("3. Check `price * shares`\n4. Read **EPS**\n\n\\(\\frac{1}{\\frac{2}{3}}\\)")
        assertTrue(answer.text.contains("3. Check price * shares\n4. Read EPS"))
        assertTrue(answer.text.contains("(1) / ((2) / (3))"))
    }
    @Test fun singleDollarFormulasRenderAlongsideOrdinaryCurrency() {
        val dollar = '$'
        val source = "Shares cost ${dollar}50 and ${dollar}2.50. Ratio: ${dollar}\\frac{P}{E}${dollar}; ${dollar}P/E${dollar}; ${dollar}2 \\times 3${dollar}."
        assertEquals("Shares cost ${dollar}50 and ${dollar}2.50. Ratio: (P) / (E); P/E; 2 × 3.", formatFinwizAnswer(source).text)
        assertEquals("${dollar}50 to ${dollar}100; ${dollar}USD${dollar}", readableFinwizMath("${dollar}50 to ${dollar}100; ${dollar}USD${dollar}"))
        assertEquals("\\${dollar}P/E\\${dollar}", readableFinwizMath("\\${dollar}P/E\\${dollar}"))
    }
    @Test fun tableCellsKeepHeadersWithoutRawPipes() {
        val text = formatFinwizAnswer("| Metric | Value |\n| --- | --- |\n| P/E | 15x |\n| EPS | $2 |" ).text
        assertEquals("Metric: P/E\nValue: 15x\n\nMetric: EPS\nValue: $2", text)
    }
    @Test fun untrustedHtmlAndImagesNeverBecomeActiveContent() {
        val text = formatFinwizAnswer("[Lesson](javascript:alert(1)) ![Chart](https://example.com/tracker.png)\n\n<script>alert('x')</script>").text
        assertEquals("Lesson Chart", text)
    }
    @Test fun plainTextEscapesAndEmptyMathDoNotCrashOrLoseLiteralSymbols() {
        assertEquals("A #1 company: 5% + ₹100.", formatFinwizAnswer("A #1 company: 5% + ₹100.").text)
        assertEquals("Use * for multiplication", formatFinwizAnswer("Use \\* for multiplication").text)
        assertEquals("", formatFinwizAnswer("${'$'}${'$'}${'$'}${'$'}").text)
    }
}

package com.stoxsim.app.data

/** Topic IDs match the website and server. Reading a bundled lesson makes no AI request. */
internal data class LearningModule(val id: String, val title: String, val summary: String,
    val lessons: List<Pair<String, String>>, val prompt: String, val reference: String)
private const val STATEMENTS = "https://www.sec.gov/investor/pubs/begfinstmtguide.htm"
private const val ORDERS = "https://www.investor.gov/introduction-investing/general-resources/news-alerts/alerts-bulletins/investor-bulletins-14"
private const val DIVERSIFICATION = "https://www.investor.gov/introduction-investing/getting-started/asset-allocation"
internal val learningModules = listOf(
    LearningModule("LEARN", "Market basics", "Stocks, ETFs, exchanges and orders", listOf(
        "Shares & ETFs" to "A share represents ownership in a company. An ETF pools investments and trades on an exchange; its holdings and strategy determine its risks.",
        "Market or limit?" to "A market order seeks execution at the available price. A buy limit sets the highest price you will pay; a sell limit sets the lowest you will accept. A limit order may remain unfilled.",
        "Try it in StoxSim" to "Search a symbol, inspect its quote time, and review a small practice order. Virtual cash and simulated execution do not reproduce every real-market condition."),
        "Explain market and limit orders with a simple example.", ORDERS),
    LearningModule("STOCK_FUNDAMENTALS", "Stock fundamentals", "Understand the business behind a ticker", listOf(
        "Start with the business" to "Describe what the company sells, who its customers are and what drives its costs. Read its own reports before relying on a headline ratio.",
        "Compare like with like" to "Compare profitability, growth and debt with similar businesses over several periods. Industry and accounting differences can distort comparisons.",
        "Try it in StoxSim" to "Open a stock's fundamentals. Note the reporting period and unavailable fields, then write down one question about the business."),
        "How do I study a company's business model and competitive position?", STATEMENTS),
    LearningModule("TECHNICAL_ANALYSIS", "Technical analysis", "Trends, price action and chart signals", listOf(
        "Read the chart" to "A trend describes observed price direction. Moving averages smooth past prices; they lag changes rather than knowing what comes next.",
        "Signals have limits" to "Support, resistance and momentum indicators interpret historical data. They can fail and are not promises of future returns.",
        "Try it in StoxSim" to "Compare the one-month and one-year charts. Describe the observed move, its time window and any missing observations before forming a hypothesis."),
        "Teach me trend, moving averages, support and resistance without treating them as predictions.", DIVERSIFICATION),
    LearningModule("FUNDAMENTAL_ANALYSIS", "Financial statements", "Income, balance sheet and financial health", listOf(
        "Three connected views" to "The income statement shows earnings over a period. The balance sheet shows assets, liabilities and equity at a date. The cash-flow statement tracks cash movements.",
        "Look beyond one quarter" to "Read trends and the notes to the accounts. One strong period may reflect a temporary event rather than a durable improvement.",
        "Try it in StoxSim" to "Switch between quarterly and yearly financials. Compare reporting dates before comparing values."),
        "Explain how the income statement, balance sheet and cash-flow statement connect.", STATEMENTS),
    LearningModule("VALUATION", "Valuation", "Price, earnings and expectations", listOf(
        "Price versus value" to "A valuation estimates what assumptions about future results could justify a price. Changing those assumptions changes the estimate.",
        "Multiples in context" to "P/E divides share price by earnings per share. Negative or unusual earnings can make it unhelpful. A low ratio alone is not a buy signal.",
        "Try it in StoxSim" to "Choose a ratio in the fundamentals and ask what could explain it. Separate observed data from assumptions."),
        "Explain P/E and its limitations with a hypothetical example.", STATEMENTS),
    LearningModule("CASH_FLOW", "Cash flow", "Follow where money comes from and goes", listOf(
        "Cash versus profit" to "Reported profit and cash generated can differ. Timing, working capital and non-cash expenses help explain the difference.",
        "Three cash categories" to "Operating cash reflects business activities. Investing cash includes long-term asset purchases or sales. Financing cash includes borrowing, repayment and funding from owners.",
        "Try it in StoxSim" to "Ask how a profitable company could still have a cash shortage and what its statements would need to show."),
        "Explain operating, investing and financing cash flow using a simple example.", STATEMENTS),
    LearningModule("MARKET_EVALUATION", "Market evaluation", "Indices, breadth and the bigger picture", listOf(
        "An index is a lens" to "An index follows a defined basket of instruments. Its movement does not describe every company or sector.",
        "Use several observations" to "Consider earnings, rates, sector behaviour and breadth together. Separate an observed relationship from an explanation you have not tested.",
        "Try it in StoxSim" to "Compare an index snapshot with gainers and losers. Check timestamps and market phase; one day's move is not a forecast."),
        "How can I evaluate a market without relying on one index or one trading day?", DIVERSIFICATION),
    LearningModule("PORTFOLIO_EDUCATION", "Portfolio & risk", "Diversification, concentration and drawdown", listOf(
        "Know your exposure" to "Diversification spreads exposure across different investments. Several stocks in one sector or a narrowly focused ETF can still leave a portfolio concentrated.",
        "Read a drawdown" to "Drawdown measures a decline from an earlier peak. A 20% fall from 100 leaves 80; returning to 100 requires a 25% gain.",
        "Try it in StoxSim" to "Inspect sector allocation and your largest position. Use Scenario Lab to explore hypothetical moves without treating the outcome as a prediction."),
        "Explain portfolio concentration, diversification and drawdown with a simple example.", DIVERSIFICATION)
)
internal data class FinwizMessage(val id: String, val role: String, val text: String,
    val provider: String = "", val generatedAt: String = "", val disclaimer: String = "", val suggestions: List<String> = emptyList())
internal data class FinwizConversation(val id: String, val title: String, val region: String, val topic: String = "LEARN",
    val symbol: String = "", val experience: String = "BEGINNER", val messages: List<FinwizMessage> = emptyList())

/** Keep the latest question intact and stay inside the server's 2,000-character contract. */
internal fun finwizQuestion(question: String, history: List<FinwizMessage>): String {
    val current = question.trim()
    require(current.length in 1..2000)
    val previous = history.filter { it.role in setOf("user", "assistant") }.takeLast(4)
    val prefix = "Recent conversation, for context:\n"
    val suffix = "\n\nAnswer the latest question:\n$current"
    val room = 2000 - prefix.length - suffix.length
    if (previous.isEmpty() || room < 160) return current
    val context = previous.joinToString("\n") { "${if (it.role == "user") "User" else "Finwiz"}: ${it.text.take(400)}" }.takeLast(room)
    return prefix + context + suffix
}

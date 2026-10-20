package com.stoxsim.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import com.stoxsim.app.data.Portfolio

internal fun priceSourceLabel(source: String) = when (source) {
    "EXTERNAL" -> "Market price"
    "SIMULATED" -> "Simulated fallback price"
    "COST_BASIS" -> "Average cost fallback"
    else -> "Price source unconfirmed"
}

@Composable internal fun PricingNotice(source: String) {
    if (source == "EXTERNAL") return
    Text(priceSourceLabel(source))
    Muted(if (source == "SIMULATED")
        "Market data is unavailable. This virtual price is used for paper trading and is excluded from observed performance history."
    else if (source == "COST_BASIS")
        "This holding is valued at its average purchase cost because a market price is unavailable."
    else "A verified market price is unavailable. This value is excluded from observed performance history.")
}

@Composable internal fun PortfolioPricingNotice(portfolio: Portfolio?) {
    if (portfolio?.positions?.any { it.pricingSource != "EXTERNAL" } == true)
        Muted("Estimated portfolio value: some holdings use fallback or unconfirmed prices. Account value and returns are estimates; these snapshots are excluded from observed performance history.")
}

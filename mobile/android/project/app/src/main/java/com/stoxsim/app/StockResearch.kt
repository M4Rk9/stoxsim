package com.stoxsim.app

/** Opening a result is not research until its quote has loaded successfully. */
internal fun AppState.hasLoadedStockResearch(): Boolean =
    screen == "Stock" && selected != null && quote != null && !working && "quote" !in sectionErrors

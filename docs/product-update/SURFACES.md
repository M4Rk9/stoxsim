# Source surface inventory

Baseline source scan before edits: routes, local styling and principal UI headings. Runtime validation is reported separately.

| Route | Source | Headings / state handling |
| --- | --- | --- |
| /admin/analytics | `frontend/app/admin/analytics/page.tsx` | Owner analytics; Current learner accounts; Learners who joined in this period; loading, error, disabled |
| /admin/billing | `frontend/app/admin/billing/page.tsx` | Test subscriptions; Test checkout is disabled; Start a test; loading, error, disabled |
| /billing | `frontend/app/billing/page.tsx` | Plans and billing; Subscription history; loading, error, disabled |
| /campus | `frontend/app/campus/page.tsx` | Campus competitions; Your membership; Membership request; loading, error, disabled |
| /competitions | `frontend/app/competitions/page.tsx` | Learning competitions; Opt in to this season; Standard leaderboard; loading, error, empty, disabled |
| /cookies | `frontend/app/cookies/page.tsx` | 1. Essential refresh cookie; 2. Browser storage; 3. No advertising cookies; disabled |
| /delete-account | `frontend/app/delete-account/page.tsx` | Delete from account settings; If you cannot sign in; What is deleted;  |
| /disclaimer | `frontend/app/disclaimer/page.tsx` | No real trading; No investment advice; Data limitations;  |
| /finwiz | `frontend/app/finwiz/page.tsx` | Recent chats; Finwiz AI; What would you like to understand?; loading, error, disabled |
| /forgot-password | `frontend/app/forgot-password/page.tsx` | Reset your password; error, disabled |
| /google-sign-in/manage | `frontend/app/google-sign-in/manage/page.tsx` | Dynamic/component-composed;  |
| /google-sign-in | `frontend/app/google-sign-in/page.tsx` | Dynamic/component-composed;  |
| /google-sign-in/register | `frontend/app/google-sign-in/register/page.tsx` | Dynamic/component-composed;  |
| /learn | `frontend/app/learn/page.tsx` | One useful next step; Your learning path; Finwiz AI;  |
| /learn-stock-trading | `frontend/app/learn-stock-trading/page.tsx` | Dynamic/component-composed;  |
| /markets/derivatives | `frontend/app/markets/derivatives/page.tsx` | Futures &amp; Options; Availability; NSE index contracts; loading, error, disabled |
| /notifications/unsubscribe | `frontend/app/notifications/unsubscribe/page.tsx` | Optional notification emails; error, disabled |
| / | `frontend/app/page.tsx` | A free paper-trading simulator for India and the US; Understand StoxSim before you begin; Good day, {session.user.displayName.split(" ")[0]}.; loading, error, empty, disabled |
| /paper-trading | `frontend/app/paper-trading/page.tsx` | Dynamic/component-composed;  |
| /portfolio | `frontend/app/portfolio/page.tsx` | Your portfolio; Allocation and performance; loading, error, empty |
| /privacy | `frontend/app/privacy/page.tsx` | 1. Who is responsible; 2. Data we collect; 3. Why we use it; error |
| /progress | `frontend/app/progress/page.tsx` | Learning path; Your next learning steps; Learning milestones; loading, error |
| /reset-password | `frontend/app/reset-password/page.tsx` | Choose a new password; error, disabled |
| /scenario-lab | `frontend/app/scenario-lab/page.tsx` | Dynamic/component-composed; error |
| /settings | `frontend/app/settings/page.tsx` | Profile &amp; security; Profile details; Google account security; loading, error, empty, disabled |
| /status | `frontend/app/status/page.tsx` | Dynamic/component-composed; disabled |
| /stock-market-simulator-india | `frontend/app/stock-market-simulator-india/page.tsx` | Dynamic/component-composed;  |
| /stocks/[exchange]/[symbol] | `frontend/app/stocks/[exchange]/[symbol]/page.tsx` | Price history; Company fundamentals; Financial performance; loading, error, empty |
| /terms | `frontend/app/terms/page.tsx` | 1. Agreement and eligibility; 2. Educational simulator only; 3. Your account; disabled |
| /verify-email | `frontend/app/verify-email/page.tsx` | Dynamic/component-composed; error |

## Native screen inventory

- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/AccountHub.kt`: AccountHub, AccountLink, WalletScreen, LedgerPanel
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/AuthScreen.kt`: AuthScreen
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/BrandScreens.kt`: LaunchScreen, WelcomeScreen
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/CampusScreen.kt`: CampusScreen, CampusManagement, CampusDecisionDialog
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/CompanyFundamentals.kt`: CompanyFundamentals, MetricCards, MetricCard, CompanyDescription, FinancialBars, ChartLegend, FinancialDetails
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/Components.kt`: Panel, Kicker, Muted, Message, SectionError, Status, ChoiceRow, ValueRow, LineChart, SectorChart
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/EmailLinkScreen.kt`: EmailLinkScreen
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/FinwizAnswer.kt`: FinwizAnswer
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/FinwizReactor.kt`: FinwizReactor, ReactorCore, ModuleNode
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/FinwizScreens.kt`: FinwizHub, InlineFinwizConversation, FinwizLesson, FinwizChatScreen, NewFinwizChat
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/GuidedJourney.kt`: GuidedJourney, FeatureWalkthrough
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/HoldingsScreen.kt`: PortfolioScreen, HoldingsWorkspace, HoldingMetric, SnapshotLabel, HoldingsList, HoldingRow
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/LearnerScreens.kt`: LearnerHome, LearnHub, LearnerProfileScreen, NotificationsScreen, ReflectionsScreen, ReflectionEditor
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/LearningScreens.kt`: ExploreScreen, ScenarioScreen, LearningScreen, CompetitionScreen, Standings
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/PortfolioScreen.kt`: PortfolioAnalysisScreen, AnalysisSummary, HistoryPanel
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/PricingNotice.kt`: PricingNotice, PortfolioPricingNotice
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/ScenarioWorkspace.kt`: ScenarioWorkspace, ScenarioHero, ScenarioResult
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/SettingsScreen.kt`: SettingsScreen, PasswordField, ReportsScreen, ReportSnapshot, SubscriptionScreen
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/StoxSimApp.kt`: StoxSimApp, AppFrame, StocksTabs
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/Theme.kt`: StoxSimTheme
- `mobile/android/project/app/src/main/java/com/stoxsim/app/ui/TradingScreens.kt`: AccountSelector, PortfolioMetrics, DashboardScreen, StocksExplore, StockMoverGrid, MarketBanner, Indices, Movers, MarketsScreen, StockResults, WatchlistScreen, WatchlistPanel, StockScreen, WatchStockButton, OrderTicket, OrdersScreen, TradesPanel, EditPendingOrder

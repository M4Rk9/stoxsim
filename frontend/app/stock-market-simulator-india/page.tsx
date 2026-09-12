import type { Metadata } from "next";

import MarketingPage from "../components/MarketingPage";
import { createPublicMetadata } from "../seo";

export const metadata: Metadata = createPublicMetadata(
  "Indian Stock Market Simulator",
  "Practise Indian stock-market trading with a virtual ₹5 lakh portfolio. Explore Indian instruments, simulated charges, holdings and portfolio performance on StoxSim.",
  "/stock-market-simulator-india",
);

export default function IndiaStockSimulatorPage() {
  return (
    <MarketingPage
      eyebrow="INDIAN STOCK MARKET SIMULATOR"
      title="Learn the Indian market with a virtual ₹5 lakh portfolio."
      introduction="Explore Indian stocks through an educational simulator built around local market sessions, rupee-denominated portfolios and simulated trading costs. StoxSim helps you practise a process before you consider using real money."
      highlights={[
        {
          title: "Built for Indian learners",
          body: "Practise with a rupee portfolio and an experience designed around Indian instruments and market timings.",
        },
        {
          title: "Understand the full position",
          body: "See available cash, invested value, holdings and unrealised results together instead of focusing on one price move.",
        },
        {
          title: "Keep competition educational",
          body: "Compare entry-relative percentage returns in opt-in learning competitions without turning virtual capital into real financial risk.",
        },
      ]}
      stepsTitle="Practise a disciplined Indian-market workflow"
      stepsIntroduction="The simulator encourages observation and review instead of promising shortcuts or guaranteed returns."
      steps={[
        { title: "Build a watchlist", body: "Collect Indian companies you want to understand and observe them across more than one session." },
        { title: "Study the setup", body: "Use available charts, fundamentals and market context to write down why a simulated trade makes sense to you." },
        { title: "Measure the outcome", body: "Review portfolio impact and learning feedback, then decide what your process should improve next time." },
      ]}
      note={<>Prices and company data may be delayed, stale, incomplete or unavailable. Simulated results are hypothetical and are not evidence of future investment performance.</>}
    />
  );
}
